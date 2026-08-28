---
id: FRO_045
uid: FRO
number: 45
client: FrontierMode
status: blocked
title: Build Boss defeat border-growth caller (RM_FRO_019)
context: Lead Dev build for Karen. growCenteredOn() two-layer addition, LivingDeathEvent
  defeat handler with MobScope.getFor() race fallback, BossAPI.createBoss() pairing.
  Blocked on FRO_047 (general Border-interface refactor -- facet resolvers, Result type --
  split out and not roadmap-tracked).
priority: high
opened: '2026-08-24'
closed: null
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

**Blocked on [FRO_047](FRO_047_border-interface-refactor.md).** "What to build" below already
assumes the facet-resolver (`fixture.CRUD`, `PATH(level)`) and `Result`-returning shapes FRO_046
ruled — those don't exist in source until FRO_047 builds them. FRO_047 is the general
Border-interface refactor split out of this ticket once FRO_046 closed and its ruling turned out to
reach well beyond Karen; it's standalone architecture/health work, not itself tied to
[RM_FRO_019](../roadmap/RM_FRO_019_karen.md) or any roadmap node — only this ticket is.

## What to build

`growCenteredOn`'s mechanics live directly on the facet — there's no intermediate logic class to
delegate to.

1. **`BordersPathFacet.growCenteredOn(BlockPos center)`** — same shape as `grow()`: pull a
   proposal via `fixture.CRUD.getProposal()`, set radius and layer from
   `BorderRules.ACTIVE.chooseNextRadius(...)` / `previous.layer() + 1` exactly as `grow()` does,
   but `proposal.center(center)` in place of `BorderRules.ACTIVE.chooseNextCenter(...)`. Apply via
   `fixture.CRUD.applyProposal(proposal)`; on success, append the returned `Border` to `borderPath`
   and `markSeeded()`/`markPathDirty()`, same as `grow()`. **One case `grow()` doesn't have to
   handle that this does:** `grow()` falls back to its own no-tip bootstrap branch when the path is
   empty; `growCenteredOn` has no such fallback — a post-defeat call always requires an existing
   tip (a boss can't be defeated on a level with no border), so an absent tip here is a genuine
   data-corruption case, not a bootstrap case. **Surface it as a failed `Result`** (its own
   failure-kind, distinct from "not-ready" or "validation rejected"), logged loudly server-side —
   not a thrown exception, and not a silent substitution that would discard the caller's requested
   center.
2. **`BorderAPI.growCenteredOn(Level level, BlockPos center)`** — thin wrapper over
   `PATH(level).growCenteredOn(center)`, mirroring how `BorderAPI.grow(Level)` delegates to
   `PATH(level).grow()`. Returns `Result`, same as every other `BorderAPI` operation that can fail (see [Border § Mutation surface](../wiki/frontiermode/architecture/border.md)) —
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
   call `BorderAPI.growCenteredOn(level, deathLocation)` and check its `Result`; once it reports
   success, pull the `Border` off the payload and call `BossAPI.createBoss(level, border)` right
   after — the same paired call every border-creation site needs. A failed `Result` here (the
   corruption case above) should log loudly and stop — don't call `createBoss` without a border.
   `createBoss` creates the record; it doesn't place the entity — materialization happens on
   `BOSS_JIG`'s own tick, same as any other unmaterialized record.
5. **Document `growCenteredOn` on [Border](../wiki/frontiermode/architecture/border.md)** as part
   of this build, mirroring how [FRO_043](FRO_043_boss-build.md) updated `boss.md` with the built
   shape rather than leaving the wiki describing pre-build design only.

## Four things worth knowing before you start

Checked against `RM_FRO_019`'s own 2026-08-24 ruling and `boss.md` — places the obvious guess is
wrong:

1. **Both design calls are ruled, not open.** Don't re-litigate `growCenteredOn` vs. a two-call
   sequence, or which race-fallback mechanism to use — [FRO_044](FRO_044_karen-prep.md) already
   settled both with source-verified reasoning. Build to the ruling above.
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
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
