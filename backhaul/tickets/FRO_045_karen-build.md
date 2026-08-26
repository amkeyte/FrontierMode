---
id: FRO_045
uid: FRO
number: 45
client: FrontierMode
status: blocked
title: Build Boss defeat border-growth caller (RM_FRO_019)
context: Lead Dev build for Karen. growCenteredOn() three-layer addition, LivingDeathEvent
  defeat handler with MobScope.getFor() race fallback, BossAPI.createBoss() pairing.
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
authoritative. `boss.md`'s "Defeat detection and the border-growth gap" section is the other
authoritative source — both agree as of FRO_044's close.

## What to build

1. **`BorderLogic.growCenteredOn(Border previous, BlockPos center)`** — identical shape to the
   existing `grow(Border)`, but `prop.center(center)` in place of
   `rules.chooseNextCenter(level, previous)`. Radius and `layer` still come from
   `rules.chooseNextRadius(level, previous)` / `previous.layer() + 1`, unchanged.
2. **`BordersPathFacet.growCenteredOn(BlockPos center)`** — same append/`markPathDirty()` shape
   `grow()` already has, delegating to `BorderLogic.growCenteredOn`. **One case `grow()` doesn't
   have to handle that this does:** `grow()` falls back to `logic.getInitial()` when the path has
   no tip yet. `growCenteredOn` has no such fallback — a post-defeat call always requires an
   existing tip (a boss can't be defeated on a level with no border), so an absent tip here is a
   genuine data-corruption case, not a bootstrap case. **Implement as a loud log-and-no-op**, not a
   silent `getInitial()` substitution, which would discard the caller's requested center.
3. **`BorderAPI.growCenteredOn(Level level, BlockPos center)`** — thin wrapper, mirroring
   `grow(Level)`'s own one-line delegation to `borders.PATH.grow()`.
4. **The `LivingDeathEvent` listener** — a plain static method registered via
   `MinecraftForge.EVENT_BUS.addListener(...)`, the same wiring shape `BorderModule.onBlockPlaced`
   uses (not an `@SubscribeEvent` instance method — this project's established pattern for raw
   Forge events outside `EventHandlers`/`ScopeEvent`). Detection sequence:
   - Check whether the dying entity carries a `BossMobFixture` (via `MobJig`'s scope).
   - If not, **fall back to a synchronous `MobScope.getFor(mob)` call** before concluding it's
     genuinely not a tracked boss — closes the race where a boss's chunk just loaded and
     `MobJig`'s ~20-tick poll hasn't caught up yet. Safe here since the entity is loaded by
     definition (it just died).
   - If neither finds a match: no-op. Most deaths in the world aren't a tracked boss.
5. **On a match:** mark that boss's own `BossFixture` record defeated (`alive: false`), addressed
   by its own `bossId` — not via any `Border` reference, `BossFixture` isn't keyed by one. Then:
   `BordersPathFacet.growCenteredOn(deathLocation)` (via `BorderAPI.growCenteredOn`), and once that
   succeeds, `BossAPI.createBoss(level, border)` right after — the same paired call every
   border-creation site needs. This call creates the record; it doesn't place the entity —
   materialization happens on `BOSS_JIG`'s own tick, same as any other unmaterialized record.
6. **Document `growCenteredOn` on [Border](../wiki/frontiermode/architecture/border.md)** as part
   of this build, mirroring how [FRO_043](FRO_043_boss-build.md) updated `boss.md` with the built
   shape rather than leaving the wiki describing pre-build design only.

## Four things worth knowing before you start

Checked against `RM_FRO_019`'s own 2026-08-24 ruling and `boss.md` — places the obvious guess is
wrong:

1. **Both design calls are ruled, not open.** Don't re-litigate `growCenteredOn` vs. a two-call
   sequence, or which race-fallback mechanism to use — [FRO_044](FRO_044_karen-prep.md) already
   settled both with source-verified reasoning. Build to the ruling.
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
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
