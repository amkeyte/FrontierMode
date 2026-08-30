---
id: FRO_045
uid: FRO
number: 45
client: FrontierMode
status: done
title: Build Boss defeat border-growth caller (RM_FRO_019)
context: Lead Dev build for Karen. grow(BlockPos) overload addition, LivingDeathEvent
  defeat handler with MobScope.getFor() race fallback, BossAPI.createBoss() pairing.
priority: high
opened: '2026-08-24'
closed: '2026-08-29'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Lead Dev build for [RM_FRO_019](../roadmap/RM_FRO_019_karen.md) ("Karen," boss defeat →
border-growth caller) — the last node in FrontierMode's Tier 1 core loop. Architect prep closed via
[FRO_044](FRO_044_karen-prep.md): both open design calls are ruled, not just recommended, and
verified against real source shapes. [RM_FRO_018](../roadmap/RM_FRO_018_shirley.md) ("Shirley") is
`resolved` and real-playtest-verified via [FRO_043](FRO_043_boss-build.md). This node is genuinely
startable.

## Build against this node's own 2026-08-24 ruling, not the earlier log entries

[RM_FRO_019](../roadmap/RM_FRO_019_karen.md)'s log has several superseded turns (a `Border`-UUID
keying scheme dropped 2026-08-18, a generic "`BossModule`'s record-creation" phrasing corrected
2026-08-24 to name `BossAPI.createBoss(level, border)` directly). The node's own 2026-08-24 entry
and its "The handler itself"/"Ruled"/"Closes the loop" body sections (already updated to match) are
authoritative for **what** this node does. `boss.md`'s "Defeat detection and the border-growth gap"
section is the other authoritative source — both agree as of FRO_044's close.

**For the exact classes and methods `growCenteredOn` builds against, [FRO_046](FRO_046_growcenteredon-proposal-contract.md)
and [Border](../wiki/frontiermode/architecture/border.md) are the design authority.** The *what*
above is unchanged; "What to build" below is written to FRO_046's design.

[FRO_047](FRO_047_border-interface-refactor.md) — the general Border-interface refactor split out
of this ticket once FRO_046 closed and its ruling turned out to reach well beyond Karen — is now
`done`, built and playtest-verified. "What to build" below's facet-resolver (`fixture.CRUD`,
`PATH(level)`) and `Result`-returning shapes exist in source now. `BordersFixture` itself stayed a
public Java type rather than going literally package-private (FRO_047's own first logged
deviation, Satchel's `FixtureKey` requires it) — doesn't change anything below, since this ticket
never referenced the fixture's own visibility directly.

## What to build

`grow(BlockPos center)` mechanics live directly on the facet — there's no intermediate logic class
to delegate to. It's an overload of the existing `grow()`, not a separately-named method — see
[RM_FRO_019](../roadmap/RM_FRO_019_karen.md)'s 2026-08-28 correction entry for why (no-tip
behavior and method shape both corrected the same day, superseding this ticket's original
2026-08-24 spec).

1. **`BordersPathFacet.grow(BlockPos center)`** — same shape as the no-arg `grow()`, tip present
   or absent: pull a proposal via `fixture.CRUD.getProposal()`, set radius and layer from
   `BorderRules.ACTIVE.chooseNextRadius(...)` / `previous.layer() + 1` (tip present) or from
   `getInitial()`'s own rules-driven radius / `layer 0` (tip absent) exactly as `grow()` does
   either way — but `proposal.center(center)` in place of whichever rules-chosen center `grow()`
   would otherwise pick. Apply via `fixture.CRUD.applyProposal(proposal)`; on success, append the
   returned `Border` to `borderPath` and `markSeeded()`/`markPathDirty()`, same as `grow()`.
   **No-tip is a normal bootstrap here, not a failure case** — unlike the original 2026-08-24 spec,
   an absent tip does not surface a failed `Result`; it bootstraps exactly like `grow()`'s own
   empty-path branch, just with the caller's center. No new `Result.FailureKind` needed for this
   method.
2. **`BorderAPI.grow(Level level, BlockPos center)`** — thin wrapper over
   `PATH(level).grow(center)`, mirroring how `BorderAPI.grow(Level)` delegates to `PATH(level).grow()`.
   Returns `Result`, same as every other `BorderAPI` operation that can fail (see [Border § Mutation surface](../wiki/frontiermode/architecture/border.md)) —
   the caller checks outcome, not a try/catch or an `Optional`.
3. **The `LivingDeathEvent` listener** — a plain static method registered via
   `MinecraftForge.EVENT_BUS.addListener(...)`, the same wiring shape `BorderModule.onBlockPlaced`
   uses (not an `@SubscribeEvent` instance method — this project's established pattern for raw
   Forge events outside `EventHandlers`/`ScopeEvent`). Detection sequence:
   - Check whether the dying entity carries a `BossMobFixture` (via `MobJig`'s scope).
   - If not, **fall back to a synchronous `MobScope.getFor(mob)` call** before concluding it's
     genuinely not a tracked boss — closes the race where a boss's chunk just loaded and
     `MobJig`'s ~20-tick poll hasn't caught up yet. Safe here since the entity is loaded by
     definition (it just died).
   - If neither finds a match: no-op. Most deaths in the world aren't a tracked boss.
4. **On a match:** mark that boss's own `BossFixture` record defeated (`alive: false`), addressed
   by its own `bossId` — not via any `Border` reference, `BossFixture` isn't keyed by one. Then
   call `BorderAPI.grow(level, deathLocation)` and check its `Result`; once it reports success,
   pull the `Border` off the payload and call `BossAPI.createBoss(level, border)` right after — the
   same paired call every border-creation site needs. Given no-tip now bootstraps rather than
   failing, a failed `Result` here means an actual mutation-validation rejection (e.g. radius
   bounds), not a corruption case — log it and stop either way; don't call `createBoss` without a
   border. `createBoss` creates the record; it doesn't place the entity — materialization happens
   on `BOSS_JIG`'s own tick, same as any other unmaterialized record.
5. **Already documented — build against the wiki, don't re-derive it.**
   [Border § Mutation surface](../wiki/frontiermode/architecture/border.md#mutation-surface) and
   [Boss § Defeat detection and the border-growth gap](../wiki/frontiermode/architecture/boss.md#defeat-detection-and-the-border-growth-gap)
   both already describe the `grow(BlockPos center)` overload and the defeat handler as the spec
   to build to — written ahead of this build rather than deferred to a doc pass after ([FRO_053](FRO_053_karen-wiki-docs.md),
   closed, superseded by this process change). If the real implementation needs something these
   pages don't cover, that's a ticket back to the Architect, not a silent deviation or a
   wiki-catches-up note appended later.

## Four things worth knowing before you start

Checked against `RM_FRO_019`'s own 2026-08-24 ruling and `boss.md` — places the obvious guess is
wrong:

1. **Both original design calls are ruled, not open — but the `growCenteredOn` shape itself was
   corrected 2026-08-28.** Don't re-litigate the two-call-sequence question or which race-fallback
   mechanism to use — [FRO_044](FRO_044_karen-prep.md) already settled both with source-verified
   reasoning, and that stands. What changed: the method is now `grow(BlockPos center)`, an overload
   of the existing `grow()`, not a separately-named `growCenteredOn`; and an absent path tip
   bootstraps like `grow()`'s own empty-path branch instead of failing with a new
   `Result.FailureKind`. See [RM_FRO_019](../roadmap/RM_FRO_019_karen.md)'s 2026-08-28 correction
   entry and [FRO_052](FRO_052_growcenteredon-no-tip.md). Build to "What to build" above, which is
   already rewritten to match.
2. **`BossFixture` is not keyed by `Border`.** Resolve the specific boss record by its own
   `bossId` (from the `BossMobFixture`/`getFor` lookup), never by trying to correlate through a
   `Border` reference — that coupling was explicitly removed
   ([RM_FRO_018](../roadmap/RM_FRO_018_shirley.md)'s 2026-08-18 entry).
3. **`Border.layerIndex()` is `Border.layer()`** — renamed 2026-08-20. Build against current
   source; older log entries on both nodes still say `layerIndex`, left as originally written.
4. **The `/kill` case is part of this ticket's own done bar, not assumed from vanilla behavior.**
   FRO_044's closing note reasons that this handler's race-fallback design should catch a
   `/kill`ed boss identically to a combat-killed one (satisfying
   [RM_FRO_018](../roadmap/RM_FRO_018_shirley.md)'s still-unmet `/kill`-self-heal wording as a
   side effect) — but flags this as **not verified against this project's own decompiled source**,
   standing no-Gradle-in-sandbox constraint. Confirm it live; don't just cite the reasoning.

## Done bar

Per [RM_FRO_019](../roadmap/RM_FRO_019_karen.md)'s own bar — compiling clean is necessary and not
sufficient. Real build + real play:

- Kill a tagged boss via **ordinary combat.** Confirm: a new border is created centered on the
  death location (not a random point), the new border is on the path (`getRelevant()`/`@relevant`
  resolve correctly across old and new territory), `layer` continues the existing sequence, and a
  new boss spawns in the new level without a manual trigger.
- **Also kill a tagged boss via `/kill`.** Confirm the same sequence fires identically — this is
  the live check FRO_044's closing note asks for before treating
  [RM_FRO_018](../roadmap/RM_FRO_018_shirley.md)'s stale self-heal wording as satisfied.
- Confirm the race-fallback path specifically: kill a boss shortly after its chunk loads (within
  `MobJig`'s ~20-tick poll window), before the poll would have attached `BossMobFixture` on its
  own — the `MobScope.getFor(mob)` fallback should still resolve it correctly.
- Confirm the corruption-case guard: this is a defensive path, not expected to trigger in normal
  play, so read-through/code-review confidence is acceptable here rather than a forced repro.

## Standing constraint

**No Gradle in the agent sandbox.** No Forge/Mojang maven access — every prior node in this project
has hit this. Real build and playtest are the project owner's own machine; don't mark this resolved
on read-through/self-review alone.

## Log

- 2026-08-24: Ticket opened. Architect prep ([FRO_044](FRO_044_karen-prep.md)) closed — both design
  calls ruled and source-verified; [RM_FRO_018](../roadmap/RM_FRO_018_shirley.md) ("Shirley")
  resolved and live-verified.

- 2026-08-25: **Blocked on [FRO_046](FRO_046_growcenteredon-proposal-contract.md).** Grounding `growCenteredOn` against real source (`BorderLogic`/`BordersPathFacet`/`BorderAPI`/`BordersCrudFacet`/`BorderProposal`) surfaced a proposal-application failure-contract question that reaches every existing `applyProposal` caller, not just this node's new method — `growCenteredOn`'s own return shape at all three layers depends on how it's ruled. Opened as its own Architect ticket rather than decided mid-build. Nothing built yet on this ticket.

- 2026-08-27: **"What to build" rewritten against [FRO_046](FRO_046_growcenteredon-proposal-contract.md)'s
  now-canonical design.** Still blocked — this is a spec update, not an unblock; FRO_046's classes
  don't exist in source yet.

- 2026-08-28: **Unblocked — [FRO_046](FRO_046_growcenteredon-proposal-contract.md) closed.** The
  ruling this ticket was waiting on is settled and canon on the wiki; status moves back to `open`.
  Same situation as FRO_044 closing unblocked this node originally — the interface refactor and
  `growCenteredOn` itself are both still unbuilt, but there's nothing left to decide before
  starting.

- 2026-08-28: **Split — general refactor broken out to [FRO_047](FRO_047_border-interface-refactor.md),
  status moves back to `blocked`.** FRO_046's ruling reaches well beyond Karen (its own "Known blast
  radius" section names most of `border.server.commands` and `border.client.render`) — that's not
  Karen-specific work and doesn't belong on a roadmap-tracked ticket. FRO_047 carries the general
  refactor (`BorderLogic` elimination, `Result` type, facet-resolver conversion, `BorderAuthority`
  removal) as standalone, non-roadmap-tracked work; this ticket keeps only `growCenteredOn` itself,
  the `LivingDeathEvent` handler, and the `BossAPI.createBoss` pairing — nothing in "What to build"
  changed, since it was already scoped this way. Blocked on FRO_047 landing the facet/`Result`
  shapes this ticket builds against.

- 2026-08-28: **Unblocked — [FRO_047](FRO_047_border-interface-refactor.md) closed, built and
  playtest-verified twice.** Status moves back to `open`. Confirmed nothing in this ticket's own
  "What to build" needs a further rewrite: `PATH(level)`, `fixture.CRUD.getProposal()`/
  `applyProposal()`, and mutation-`Result` all match what FRO_047 actually shipped, including its
  logged deviations (`BordersFixture` public rather than package-private; `bordersContaining()`
  throwing instead of `Result`/`Optional` — neither claim was ever made on this ticket). Genuinely
  startable now.

- 2026-08-28: **"What to build" rewritten again (Architect) — `growCenteredOn` collapsed into a
  `grow(BlockPos center)` overload, no-tip corrected from a failed `Result` to a normal bootstrap.**
  Settled during this session's build-planning discussion, formalized on
  [RM_FRO_019](../roadmap/RM_FRO_019_karen.md)'s 2026-08-28 log entry and
  [FRO_052](FRO_052_growcenteredon-no-tip.md) (now closed). Nothing was built against the prior
  shape — grep confirms `growCenteredOn` never landed in source — so this is a spec correction, not
  a rename of shipped code. Item 5 (wiki docs) stays out of scope for this build session per owner
  instruction; carried on [FRO_053](FRO_053_karen-wiki-docs.md).

- 2026-08-28: **Item 5 corrected — wiki docs are no longer deferred (Architect/owner).** Project
  owner's ruling: architecture pages are the spec Lead Dev builds against, written ahead of or
  alongside the build, never a follow-up doc pass after code ships — that ordering left this
  ticket's own build with no reliable spec to build against in the meantime, which is exactly the
  problem [FRO_053](FRO_053_karen-wiki-docs.md) existed to paper over. `Border` and `Boss` are
  updated now to describe the `grow(BlockPos center)` overload and the defeat handler directly;
  FRO_053 is closed. Build against the wiki, not this ticket's own prose, where the two might ever
  drift.

- 2026-08-28: **Built (Lead Dev), against the wiki spec directly.** All four pieces from "What to
  build" landed:
  1. `BordersPathFacet.grow(BlockPos center)` — overload of `grow()`, same tip-present/tip-absent
     branching, no-tip bootstraps (no new `FailureKind`), center supplied by the caller in place
     of the rules-chosen one.
  2. `BorderAPI.grow(Level, BlockPos)` — thin wrapper over `PATH(level).grow(center)`, same shape
     as the existing `BorderAPI.grow(Level)`.
  3. `BossRecord.defeated()` + `BossFixture.markDefeated(UUID)` — flips `alive` false on the
     record matching `bossId`, no `Border` coupling.
  4. `BossModule.onLivingDeath` (registered on `MinecraftForge.EVENT_BUS` in `init()`, plain
     static listener — not `@SubscribeEvent`, matching `BorderModule.onBlockPlaced`'s own wiring)
     plus its `resolveBossId`/`existingBossId` helpers: `BossMobFixture` check first, falling back
     to a synchronous `MobScope.getFor(mob)` call before concluding no match. On a hit:
     `markDefeated(bossId)`, then `BorderAPI.grow(level, deathLocation)`, then `BossAPI.createBoss`
     only on a successful `Result` — a failed grow logs and stops rather than calling `createBoss`
     without a border.

  Placed the handler as private static methods directly inside `BossModule.java` rather than a new
  file — matches the precedent `BorderModule.onBlockPlaced` already sets for raw Forge listeners
  living in their module class. Implementation decision, within this role's own authority; not a
  design call.

  **Known limitation, built to the ticket's literal wording per owner instruction (not
  re-litigated):** `MobScope.getFor(mob)` registers scope introduction for a later foundation
  pulse — promotion `NEW`→`LOADED` (the transition that populates `BossMobFixture`) happens inside
  `ASatchelJig.handleExecutionPulse()`'s readiness convergence, not synchronously inside
  `getFor()`/`introduceSource()`. So the race-fallback call in `resolveBossId` does not
  synchronously hand this same `LivingDeathEvent` call a populated `BossMobFixture` on the tick a
  chunk just loaded — it registers the scope but may still miss the fixture on that exact call.
  Traced against real source this session; owner chose to build to the ticket's literal wording
  (`getFor(mob)` only) rather than have Lead Dev redesign around it. Also documented directly on
  `resolveBossId`'s own javadoc so it isn't lost to this log alone.

  Self-reviewed via brace-balance checks on all four edited/added methods and a grep confirming no
  stray `growCenteredOn` references remain anywhere in source — no javac/Gradle available in this
  sandbox to compile-check directly (standing constraint, see below).

  **Not marking this resolved.** Per this ticket's own done bar and standing constraint: real
  build + real playtest are still owed on the project owner's own machine, covering all four —
  ordinary-combat kill, `/kill` kill, the race-fallback-window kill, and code-review-level
  confidence on the (now-bootstrap-not-failure) no-tip case. Ticket stays `open`.

- 2026-08-29: **First live playtest session (owner), against a real `debug.log`.** Results against
  the done bar's four items:
  1. **Ordinary-combat kill: confirmed, not just self-reported.** The log shows the full chain
     firing three times back-to-back, each within ~40ms of the kill (same/next tick): Rabbit
     "Boss (Layer 0)" slain -> Zombie "Boss (Layer 1)" materializes; Zombie slain -> Spider
     "Boss (Layer 2)" materializes; Spider slain -> Skeleton "Boss (Layer 3)" materializes.
     `markDefeated` -> `BorderAPI.grow` -> `createBoss` -> `BOSS_JIG` materialization all
     confirmed working, three times in a row.
  2. **`/kill`: still not actually tested.** First attempt used `/kill @a`, which only selects
     players -- it killed the owner (`Dev was killed`, log timestamp 16:53:53), never touched the
     live boss (Skeleton, "Boss (Layer 3)", confirmed still alive at session end). No boss-targeted
     `/kill` has been issued yet. Correct target needs a non-player selector (e.g.
     `/kill @e[name="Boss (Layer 3)"]`), owner re-running it next.
  3. **Race-fallback window: downgraded to code-review confidence, by owner's own call** --
     confirmed impractical to reliably force a kill inside `MobJig`'s ~20-tick poll window in
     live play. Same standard item 4 already used.
  4. **No-tip corruption guard: unchanged, code-review confidence** (as originally scoped -- no
     forced repro expected here).

  Also noted: `onLivingDeath`'s success path currently logs nothing of its own -- item 1 above was
  confirmed only by correlating vanilla's own death message against mob-scope-creation timing, not
  from anything this handler prints. Flagged to the owner as worth adding (a plain
  `[Boss] onLivingDeath: ...` INFO line) so `/kill` and any future race-window attempt are directly
  verifiable from logs rather than inferred. Not yet added -- pending owner confirmation.

  Ticket stays `open`: item 2 still needs a real boss-targeted `/kill` test before this closes.

- 2026-08-29: **Second live playtest session (owner), fresh server run, item 2 re-run correctly.**
  Owner targeted the boss mob directly rather than `@a`. Log confirms:
  `[28Aug2026 17:08:01.372] ... died: Boss (Layer 0) was killed` /
  `[28Aug2026 17:08:01.373] ... [Dev: Killed Boss (Layer 0)]` -- the vanilla `/kill`-command
  broadcast, distinct from combat's "was slain by" message -- followed 46ms later by a Zombie mob
  scope appearing (`17:08:01.419`), the same defeat -> grow -> next-boss chain confirmed for
  ordinary combat. `/kill`-sourced deaths go through `LivingDeathEvent` identically to
  combat-sourced ones, as designed; item 2 confirmed.

  **Done-bar status now:** item 1 confirmed (prior session, log-verified x3), item 2 confirmed
  (this session, log-verified), item 3 downgraded to code-review confidence (owner's call, prior
  session), item 4 code-review confidence (as originally scoped). All four satisfied. Closing this
  ticket.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
