---
id: FRO_060
uid: FRO
number: 60
client: FrontierMode
status: open
title: Boss/Border mutation validation build
context: Lead Dev build ticket for FRO_058 and FRO_059's finalized specs (both Architect
  spec-review tickets, closed with rulings written onto boss.md's 'Mutation validation
  boundary' and border.md's 'Proposal identity and validation' sections). This ticket
  is the build itself and the record of what actually landed, same split as FRO_056
  -> FRO_057 for Boss commands.
priority: low
opened: '2026-08-29'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

Lead Dev build ticket for [FRO_058](FRO_058_boss-mutation-validation-reconciliation.md) and
[FRO_059](FRO_059_border-proposal-center-bounds-id-display.md) -- both Architect spec-review
tickets, closed with the actual rulings written onto
[boss.md](../wiki/frontiermode/architecture/boss.md)'s "Mutation validation boundary" section and
[border.md](../wiki/frontiermode/architecture/border.md)'s "Proposal identity and validation"
section. This ticket is the build itself and the record of what actually landed, same split as
[FRO_056](FRO_056_boss-control-commands.md) -> [FRO_057](FRO_057_boss-control-commands-build.md)
for Boss commands.

## What to build

Per the two closed spec tickets' rulings, no new scoping decisions -- straight implementation:

1. `BossFixture.create(BlockPos, int)` rejects `layer < 0`, returns `Optional<BossRecord>`.
2. `BossFixture.materialize(...)` gets a fixture-level already-materialized guard, returns
   `boolean`.
3. `BossFixture.markDefeated(UUID)` gets a fixture-level already-defeated guard, returns
   `boolean` -- closes the real, shipped double-`/boss transform defeat` bug FRO_058 found.
4. `BossAPI.forceDefeat()` checks `markDefeated()`'s return before running the grow/createBoss
   cascade; `BossAPI.createBoss()` simplified now that `create()` returns `Optional` itself.
5. `BossCommandHandler.addExplicit`/`addHere` updated for `create()`'s new `Optional` return.
6. `BordersCrudFacet.failureReason()` gains two new checks: `displayName` (reject blank, cap at
   `BorderConstants.MAX_DISPLAY_NAME_LENGTH` = 32) and `center` (X/Z against
   `WorldBorder.MAX_SIZE/2`, Y against `fixture.resolveLevel().getMinBuildHeight()`/
   `getMaxBuildHeight()`).
7. `BorderProposal.id()`/`displayName()` get doc comments recording the sanctioned-contract
   ruling (FRO_059: `insert(Border)` is the one sanctioned way to build a colliding-id proposal)
   -- documentation only, no behavior change, per that ticket's own ruling.

**Not touched, per the specs' own explicit rulings:** `BossModule.onLivingDeath` (Karen's
already-verified combat path -- naturally immune to the double-defeat bug since a `Mob` can only
die once); the reverse-direction (boss-to-border) reconciliation check (deferred pending
`borderId`, per boss.md's "Known gaps"); `remove()`'s live-entity orphaning on `/boss delete`
(flagged, not resolved, per the same section); `BorderProposal.id()`'s collision behavior itself
(documentation fix only -- `transformBorder()` depends on the exact colliding-id shape via
`insert()`, so nothing was rejected in code).

## Standing constraint

**No Gradle in the agent sandbox.** Real build/playtest happens on the project owner's own
machine -- confirm via a real `build.log` and/or in-game session evidence before closing, same as
every other Lead Dev ticket this project (see FRO_057's own log for the precedent). Brace-balance
checked per modified file in this session as a weak sanity pass only -- not a substitute for a
real `javac`/Gradle compile.

## Log

- 2026-08-29: Ticket opened, scope copied forward from FRO_058/FRO_059's closed spec rulings.

- 2026-08-29: **Code written** (device-bridge session, no Gradle available -- not yet compiled or
  playtested; both owed before this ticket can close). Modified:
  `boss/common/fixture/BossFixture.java` (`create`/`materialize`/`markDefeated` per items 1-3
  above), `boss/BossAPI.java` (`createBoss`/`forceDefeat` per item 4),
  `boss/server/commands/BossCommandHandler.java` (`addExplicit`/`addHere` per item 5),
  `border/common/fixture/BordersCrudFacet.java` (`failureReason()` per item 6, new imports
  `BlockPos`/`ServerLevel`/`WorldBorder`), `border/common/BorderConstants.java`
  (`MAX_DISPLAY_NAME_LENGTH` constant), `border/common/fixture/BorderProposal.java` (doc comments
  per item 7).

  All other call sites of `materialize()`/`markDefeated()` (`BossModule
  .materializeUnresolvedBosses`, `.forceMaterialize`, `.onLivingDeath`) already discard the
  return value or were already upstream-guarded before this change (each checks
  `record.materialized()`/iterates only unmaterialized records already), so none needed edits --
  confirmed by grep across the whole `boss/` and `border/` trees for every call site of the four
  changed methods before stopping.

  One thing flagged for the build-verification pass, same spirit as FRO_057's own flagged line:
  `WorldBorder.MAX_SIZE` and `LevelHeightAccessor.getMinBuildHeight()`/`getMaxBuildHeight()` are
  believed-correct official 1.20.1 mapping names (used elsewhere in this codebase already for the
  latter two, via `Heightmap`/`Level` call sites) but not compiler-checked in this session --
  first thing to look at if the build fails on `BordersCrudFacet`.

- 2026-08-29: **Real bug found via the project owner's own playtest (not by reading source
  alone), pre-existing in FRO_057's build, not introduced by this ticket -- fixed anyway since it
  was live and directly in the area this ticket already touches.** Reported symptom:
  `/boss transform defeat @all` defeats one boss, then errors out before the rest are processed.

  Root cause: `BossFixture.all()` returned `Collections.unmodifiableList(bosses)` -- a live
  *view* over the fixture's mutable backing list, not a snapshot copy.
  `BossSelector.resolve()`'s `@all` case hands that view straight to
  `BossCommands.applySelector`'s `for (BossRecord b : bosses)` loop. `transform defeat` is the
  one boss operation that adds a brand-new record as a side effect mid-loop (`forceDefeat` ->
  `createBoss` -> `create()` -> `bosses.add(...)`), which structurally modifies the exact list
  the for-each is iterating -- `ConcurrentModificationException` on the iterator's next advance.
  That exception comes from the for-each's own iterator bookkeeping, not from `op.run(...)`
  itself, so it lands outside `applySelector`'s per-boss `try/catch` and aborts the whole command
  -- "defeats one, then errors out" is exactly what that looks like in chat.

  Fix: `BossFixture.all()` now returns `List.copyOf(bosses)`, a real defensive copy, closed at
  the fixture boundary rather than patched at the one call site that happened to trigger it --
  same "fix it where the data crosses out of the fixture" shape this ticket's own FRO_058 work
  already used for the validation guards. Checked every current caller
  (`BossSelector.resolve`, `BossCommandHandler.info`'s `indexOf`, `BossModule`'s two one-shot
  lookups) -- all already treat the result as a point-in-time snapshot, so nothing else changes
  behavior. Not yet re-verified in-game; owed alongside the rest of this ticket's build-verify
  pass.

  **Flagged, not fixed (at the time):** `BordersFixture`'s equivalent `all()` (via
  `BordersCrudFacet.all()`) has the exact same live-view shape
  (`Collections.unmodifiableList(borders)`), but no current Border operation reachable through a
  selector adds a new border as a side effect mid-loop (`delete`/`transform`/path ops all remove
  or replace in place) -- so this was a structural risk, not a live bug, for Border. Flagged on
  [RM_FRO_021](../roadmap/RM_FRO_021_susan-02.md) rather than fixed immediately, since it wasn't
  needed to close the reported symptom.

- 2026-08-29: **Border-side hardening applied after all, on the project owner's own call --**
  `BordersCrudFacet.all()` changed from `return fixture.all();` (forwarding the fixture's live,
  package-private view unchanged) to `return List.copyOf(fixture.all());`. Turns out this isn't
  even a new pattern for this codebase: `BordersPathFacet.all()` already does exactly this
  (`return List.copyOf(fixture.borderPath);`), copying at the public facet boundary while
  reading raw internal fixture state directly -- `BordersCrudFacet.all()`'s uncopied forward was
  the inconsistent one, not the norm. `BordersFixture.all()` itself stays untouched (package-
  private, live view, correct for the internal same-call-frame reads
  `getDefaultDisplayName()` already makes). Not yet re-verified in-game.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
