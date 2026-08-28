---
id: FRO_047
uid: FRO
number: 47
client: FrontierMode
status: open
title: Build Border external interface refactor
context: 'Lead Dev build for FRO_046''s ruling: eliminate BorderLogic, Result type
  across every applyProposal-reachable operation, BordersFixture package-private behind
  per-facet BorderAPI resolvers, remove BorderAuthority. Not roadmap-tracked -- foundation
  Karen''s build depends on, not a roadmap node itself.'
priority: high
opened: '2026-08-28'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

Lead Dev build for [FRO_046](FRO_046_growcenteredon-proposal-contract.md)'s closing ruling
("Border external interface ruling") — eliminates `BorderLogic`, moves every
`applyProposal`-reachable operation onto a new `Result` type, makes `BordersFixture`
package-private behind per-facet `BorderAPI` resolvers, and removes `BorderAuthority`. Split out
of [FRO_045](FRO_045_karen-build.md) because the ruling's actual blast radius reaches well beyond
Karen/Boss — most of `border.server.commands` and `border.client.render`, per FRO_046's own "Known
blast radius" section — and none of it is Karen-specific work. `growCenteredOn` itself (the
Karen-specific addition) is **not** part of this ticket; it stays on FRO_045, which depends on this
one.

**Not roadmap-tracked.** This is foundation/health work forced by FRO_046's ruling, not a
deliverable on any `RM_FRO_*` node — it doesn't get a roadmap entry, and closing it doesn't resolve
anything on the roadmap by itself. [RM_FRO_019](../roadmap/RM_FRO_019_karen.md) ("Karen") stays
tied to FRO_045 only.

[Border § Mutation surface](../wiki/frontiermode/architecture/border.md) and FRO_046's own ruling
body are the authority for every item below — already updated to the settled design; this ticket
is what makes source match wiki.

## What to build

1. **Eliminate `BorderLogic` outright.**
   - Delete `requireAuthority(BorderAuthority)`, `nearest(List<Border>, BlockPos)`,
     `requireServerSide()` — confirmed zero call sites for all three.
   - Fold `containing(List<Border>, BlockPos)` into `BordersRulesFacet.containing(BlockPos)`
     directly — do the `BorderMath.isInside` loop over `setting.all()` inline, no intermediate
     method, no ownership check (the only caller already always passed `setting.all()`).
   - Move `getInitial()`/`grow(Border)`'s mechanics directly into `BordersPathFacet.grow()` — it
     computes center/radius/layer from `BorderRules` and calls
     `CRUD.getProposal()`/`CRUD.applyProposal()` itself.
   - Delete `defaultRadius()`/`defaultCenter()`; have `BorderProposal.applyGeometryDefaults()` call
     `BorderRules.ACTIVE` directly.
   - Move `getDefaultDisplayName()` onto `BordersFixture`/`BordersCrudFacet` (needs `CRUD.all()`
     for name dedup — `BorderRules` never touches fixtures).
   - Change `BorderProposal`'s constructor to take the owning `BordersFixture` instead of a
     `BorderLogic`; `BordersCrudFacet.getProposal()` supplies it.
   - Remove the redundant private `new DefaultBorderRules()` instance `BorderLogic`'s constructor
     currently creates — route through the shared `BorderRules.ACTIVE` singleton, same as
     `BorderPlayerLogic`/`BordersTriggers` already do.
   - Update the `DefaultBorderRules.growPathCriteria()` comment referencing
     `fixture.logic.getInitial()` — no code depends on it, just stop describing a deleted class.
   - Net shape: `border.server.rules` should end up holding only `BorderRules`,
     `DefaultBorderRules`, `BordersTriggers`, `PlayerRules`, `items/BorderPathCompass` — no
     mutation-orchestration class.
2. **Introduce the `Result` type and move every failing operation onto it.** Modeled on
   `BorderSelectorResult` (`border.server.commands`) — static factories, final fields, tagged by an
   enum. Carries: an outcome enum, a failure-kind enum (populated only on failure — distinguishes
   transient-not-ready / permanent-validation-rejected / not-found), a message string, and the
   `Border` itself on success.
   - `BordersCrudFacet.applyProposal()` is where this actually lives — it's a real, general,
     directly-callable entry point, not just something `BorderAPI` wraps. Replace its current
     `IllegalStateException`-on-rejection with a `Result` return.
   - `getProposal()`/`applyProposal()` and `BorderProposal` itself (type + fluent configuration
     methods) stay public — a public method returning a package-private type is a dead end for
     outside callers. `BorderProposal`'s constructor stays package-private (only
     `CRUD.getProposal()` constructs one).
   - `BorderAPI.grow()`, `addBorder()`, `transformBorder()`, `removeBorder()`,
     `bordersContaining()` all become thin wrappers forwarding whatever `Result` comes back from
     `applyProposal()` (or the relevant facet method) — not re-wrapping it in a new type.
   - Update `BorderCommandHandler` — its current catch of `applyProposal`'s
     `IllegalStateException` moves to reading the `Result`.
3. **Make `BordersFixture` package-private; replace `BorderAPI.borders(Level)` with per-facet
   resolvers.** Add `BorderAPI.PATH(Level)`, `CRUD(Level)`, `RULES(Level)`, `INFO(Level)` — same
   resolve-then-return shape every existing `BorderAPI` method already does, handing back the
   requested facet instead of the fixture. Remove `BorderAPI.borders(Level)` and its
   `Optional<BordersFixture>` return once every caller below is migrated.
   - Migrate every confirmed caller of `BorderAPI.borders(Level)`: `BorderCommandHandler`,
     `BorderSelector`, `RenderContext`, `DefaultBorderRules`, `BorderModule`, and
     `BossModule.reconcilePathAgainstBossRecords()` (currently `borders.PATH.all()` /
     `borders.CRUD.get(id)` off the raw fixture — moves to `BorderAPI.PATH(level)` /
     `BorderAPI.CRUD(level)` calls directly). Expect this to touch most of
     `border.server.commands` and `border.client.render`, not just the Boss-side caller.
4. **Remove `BorderAuthority` entirely.** Delete the interface, `Border.authority()`, and
   `BordersFixture implements BorderAuthority` — confirmed no caller outside Border's own package
   boundary ever touches `.authority()` (Boss imports `Border` but never calls it), and both prior
   implementors (`BorderLogic.ensureOwned()`/`requireAuthority()`) are already gone under item 1.
5. **No wiki work needed.** [Border](../wiki/frontiermode/architecture/border.md),
   [Border Vocabulary](../wiki/frontiermode/architecture/border-vocabulary.md), and
   [Path/Layer Reconciliation](../wiki/frontiermode/architecture/path-layer-reconciliation.md)
   were already rewritten to this design when FRO_046 closed — this ticket is source catching up to
   already-canon wiki, not the reverse. Flag it here (not edit) if implementation surfaces any
   mismatch against what's documented.

## Scope note

`growCenteredOn` (the new facet method, `BorderAPI` wrapper, and the `LivingDeathEvent` handler
that calls it) is explicitly **out of scope** here — that's Karen's own feature work and stays on
[FRO_045](FRO_045_karen-build.md), which builds on top of this ticket's facet-resolver/`Result`
shape rather than duplicating it.

## Done bar

Compiling clean is necessary and not sufficient — this touches enough call sites that a clean
compile mainly proves nothing was missed, not that behavior is right.

- Every existing `BorderAPI` operation (`grow`, `addBorder`, `transformBorder`, `removeBorder`,
  `bordersContaining`) still behaves the same from a player/command perspective — same commands,
  same in-game outcomes — with `Result`-reading replacing the old throw/`Optional.empty()` paths.
- `BorderCommandHandler`'s player-facing messages on rejection are unchanged in substance (still
  read from a `Result` now, not a caught exception).
- No remaining reference to `BorderLogic`, `BorderAuthority`, or `BorderAPI.borders(Level)`
  anywhere in the FrontierMode source.
- Confirm `BossModule.reconcilePathAgainstBossRecords()` still reconciles correctly after moving
  off the raw fixture reference — this is the one behavior-bearing caller among the migrations, not
  just a mechanical rename.

## Standing constraint

**No Gradle in the agent sandbox.** No Forge/Mojang maven access. Real build and playtest are the
project owner's own machine; don't mark this resolved on read-through/self-review alone.

## Log

- 2026-08-28: Ticket opened. Split out of [FRO_045](FRO_045_karen-build.md) once
  [FRO_046](FRO_046_growcenteredon-proposal-contract.md) closed and its ruling turned out to be a
  project-wide Border-interface refactor (per its own "Known blast radius" section), not a
  Karen-scoped change. Deliberately not roadmap-tracked — standalone architecture/health work
  Karen's build depends on, not a roadmap deliverable itself.

- 2026-08-28: **Items 1-4 built** (Lead Dev, Cowork sandbox -- no Gradle, read-through/self-review
  only per this ticket's own standing constraint). `BorderLogic`/`BorderAuthority` deleted;
  `Border`/`BorderProposal`/`BordersFixture`/`BordersPathFacet`/`BordersRulesFacet`/
  `BordersCrudFacet` folded per "What to build"; new `Result` type added
  (`border.common.fixture.Result`, `NOT_READY`/`VALIDATION_REJECTED`/`NOT_FOUND`); `BorderAPI`
  gained `PATH/CRUD/RULES/INFO(Level)`, lost `borders(Level)`/`borders(LevelScope)`; `grow`/
  `addBorder`/`transformBorder`/`removeBorder` now return `Result`. Four deviations from this
  ticket's literal text, each verified against real source before deciding, not guessed:
  - **`BordersFixture` stays a public Java type.** `FrontierKeys` (`FixtureKey<BordersFixture>`),
    `BordersBundle` (`border.common.bundle`), and `BorderModule.init()`'s own `FixtureDecl`
    registration all reference the class by name from outside `border.common.fixture` --
    Satchel's `FixtureKey<T extends SatchelFixture>` requires `T` accessible everywhere its key
    is built/consumed. Making the class literally package-private would require moving those
    three out of their own packages, well beyond this ticket's scope. Encapsulation is enforced
    the way this ticket actually cares about instead: `borders(Level)` is gone, and the four
    facet resolvers are the only path in from outside. Documented on the class itself.
  - **Facet resolvers return `Optional<Facet>`, not something `Result`-shaped.** The wiki's
    "Readiness" section reads as if not-ready surfaces through `Result`, but `Result` only
    carries a single `Border`, and none of the seven migrated call sites ever reach a *mutating*
    operation through a not-ready facet -- every real not-ready case is a plain read (client
    render, pre-token-round-trip) or a defensive branch that's already provably unreachable in
    practice (`BorderModule`'s own doc: "by the time `ScopeEvent.Loaded` fires, this scope has
    already converged to ready"). `Optional<Facet>` mirrors the pre-refactor `Optional
    <BordersFixture>` shape exactly, so every render/tick call site kept its existing
    `.map()`/`.orElseGet()` idiom untouched.
  - **`bordersContaining(Level, BlockPos)` stays `List<Border>`**, not `Result` -- it's a
    zero-to-many query, not a single-outcome mutation, and `Result`'s "the Border itself on
    success" doesn't fit. Not-ready now degrades to an empty list instead of the old
    `orElseThrow(ScopeNotReady)`, which is a real (small) behavior improvement, not a regression:
    its callers (`BorderSelector`'s `@containing`/`@coord`) had no catch for that exception, so an
    uncaught not-ready throw would have hit the player as Brigadier's generic error -- the exact
    failure mode RM_FRO_011/FRO_023 fixed for every other rejection path.
  - **`BordersRevisionMonitor.poll(Level)` migrated too**, even though this ticket's own
    "confirmed caller" list for `BorderAPI.borders(Level)` names six files, not this one -- it's a
    seventh, real, grep-confirmed caller. Folded into this ticket's scope rather than filed
    separately, per the project owner's direction when this was flagged mid-build.
  - Also dropped `BordersCrudFacet.validateProposal(BorderProposal)` (dead: its own javadoc's
    claimed caller, `BorderLogic`'s organic-growth path, no longer exists post-item-1, and grep
    confirmed zero real callers) rather than leave a public method with a now-false doc comment.
  - `BorderCommandHandler.debugCreate()` deliberately left untouched -- it already bypassed the
    `isReady()` gate on purpose (to distinguish "bundle exists, facet absent" from "not ready"),
    and still compiles as-is since `BordersFixture` stays public.
  - **Not yet done:** item 5 (`growCenteredOn` docs) is FRO_045's, not this ticket's, per its own
    "No wiki work needed" note -- no wiki edits made. Full done-bar (in-game command parity,
    `BossModule` reconciliation correctness) is unverified -- no Gradle in this sandbox, per the
    standing constraint above. Real build + playtest on the project owner's own machine is still
  outstanding before this closes.

- 2026-08-28: **Refined `bordersContaining()`'s shape, same day, after project-owner discussion.**
  The `List<Border>`-with-empty-on-not-ready choice logged above was a half-step -- it avoided
  forcing a query into `Result`'s single-`Border` mutation-outcome shape, but still collapsed
  "not ready" and "ready, zero matches" into the same empty list. Sharper principle settled on:
  `Result` exists to carry a rejection *reason* a caller must act on (reject a proposal, tell a
  player why); a query has no such reason to carry, so not-ready there is just routine absence,
  same category as `border(Level, UUID)` already returning `Optional.empty()` for "not found" --
  and a genuine bug during resolution already propagates as a real exception
  (`SatchelException.AccessFailed` inside `BorderAPI.resolveFixture`), never as a value, so there
  was never a third case needing `Result`'s failure-kind vocabulary. `bordersContaining(Level,
  BlockPos)` now returns `Optional<List<Border>>` -- absent means not-ready, present covers both
  zero and many matches as genuinely distinct from absent. `BorderSelector.resolveInside`/
  `resolveCoord` updated (both still flatten to an empty list either way, unchanged behavior --
  neither distinguishes the two cases today, same caveat as before).

- 2026-08-28: **Reverted `bordersContaining()` to throwing, same day, after further project-owner
  discussion -- the `Optional<List<Border>>` step above was itself one step too clever.** Landed
  on: `Result` carries a rejection reason a caller must act on; a query has none, so absence there
  is either routine (skip it) or a bug (let it throw) -- there's no third, Optional-shaped
  in-between worth an API surface. Which one applies depends on who's actually calling: for
  `RenderContext` (client render/tick, pre-token-round-trip) not-ready is genuinely routine --
  that's why the four facet resolvers (`PATH`/`CRUD`/`RULES`/`INFO`) stay `Optional`-returning,
  unchanged. But `bordersContaining()`'s own real callers (`BorderSelector`'s
  `@containing`/`@coord` selectors) run from ordinary server-side command dispatch, where the
  Border facet is expected to already be resolvable by the time a player can type a command --
  not-ready there would mean something else is already wrong, not a state to quietly paper over.
  `bordersContaining(Level, BlockPos)` now throws `SatchelException.ScopeNotReady` again, exactly
  the shape it always had (`RULES(level).orElseThrow(...)` instead of the old raw-fixture
  `borders(level).orElseThrow(...)`) -- restores this one method's original behavior verbatim,
  now expressed through the facet resolver. `BorderSelector.resolveInside`/`resolveCoord` reverted
  to their original form (`.stream().toList()` on the plain `List<Border>`, no unwrapping needed).

- 2026-08-28: **Build + playtest review, same day (client `run/logs/latest.log` + dedicated
  server `run-server/logs/latest.log`, both fresh runs post-refactor).** Confirms: clean compile
  (fresh `Result.class`/`Result$Outcome.class`/`Result$FailureKind.class` from this build), clean
  mod init on both sides (Border Module + Boss Module both initiate without error), clean
  connect/join/leave/shutdown, no exceptions or stack traces anywhere in either log.
  `BordersFixture.onLoaded()`'s persistence path (this ticket's rewritten `Border.load`) fires
  cleanly on both sides. Two things flagged, neither read as a regression from this ticket's
  actual diff (no code this ticket touched sits in either path) but worth a note: (1) server-side
  `Borders loaded: 0` (fresh world, expected) vs. client-side `Borders loaded: 1` for the same
  `LevelScope[minecraft:overworld]`, logged ~20s later than the server's read, right after a
  `[engine] CLIENT bundle became dirty (read-only violation): BordersBundle[...]` warning -- looks
  like a client-side stale/local read rather than the synced server state, in Satchel's bundle
  engine layer, not this ticket's border-facet code; (2) **this run never exercised the mutation
  paths this ticket actually rewrote** -- no border command was issued in either log, so
  `grow`/`addBorder`/`transformBorder`/`removeBorder`, `applyProposal`'s `Result` handling, and
  `BossModule.reconcilePathAgainstBossRecords()` against a real boss record are all still
  build-verified only, not playtest-verified. Full done-bar (in-game command parity, `BossModule`
  reconciliation correctness) remains open pending a run that actually drives those commands.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
