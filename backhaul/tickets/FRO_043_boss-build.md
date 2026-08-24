---
id: FRO_043
uid: FRO
number: 43
client: FrontierMode
status: done
title: Build Boss entity/spawn system (RM_FRO_018)
context: Lead Dev build for Shirley. Boss + BossMob two-fixture model, MobJig-based
  presence poll, direct-call record creation paired with border growth.
priority: high
opened: '2026-08-24'
closed: '2026-08-24'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Lead Dev build for [RM_FRO_018](../roadmap/RM_FRO_018_shirley.md) ("Shirley," boss entity/spawn
system) — FrontierMode's Tier 1 core loop node. Architect prep closed via
[FRO_042](FRO_042_shirley-prep.md): [Boss](../wiki/frontiermode/architecture/boss.md) is `verified`,
[MobScope.getFor() Contract](../wiki/satchel/spec/mobscope-getfor.md) is `verified`, and the two
prerequisite Satchel nodes — [RM_SAT_021](../roadmap/RM_SAT_021_frank.md) ("Frank," `MobJig`) and
[RM_SAT_022](../roadmap/RM_SAT_022_roger.md) ("Roger," side-agnostic `MobJig`) — are both `resolved`
and dedicated-server/client verified. This node is genuinely startable, not just graph-actionable.

## Build against `boss.md`, not the node log

Same convention Frank's and Roger's builds used. [RM_FRO_018](../roadmap/RM_FRO_018_shirley.md)'s
own log is a decision history — nine revisions, several reversed mid-discussion (tick-driven vs.
direct-call record creation, `BossFixture`/`BordersBundle` coupling considered and rejected, the
`isLoaded` retry-loop false start). Building from it front-to-back means building at least one
wrong shape before arriving at the right one. [Boss](../wiki/frontiermode/architecture/boss.md) is
the current, `verified` design and is authoritative over anything in the node. Use the node for its
**done bar** section only, which stays authoritative there.

## What to build

1. **`BossFixture`** (`LevelScope`) in its own `BossBundle` — not folded into `BordersBundle` (see
   `boss.md`'s "Data model", and [New Module Checklist](../wiki/satchel/architecture/new-module-checklist.md)
   item 7). Fields: `{bossId, position, layer, bossEntityId, alive}` — note `layer`, not `level`
   (see `boss.md`'s vocabulary note). Needs real persistence: `capabilities(true, ...)` **and** the
   matching `policies().persistence(...)` call — [Border](../wiki/frontiermode/architecture/border.md#runtime-wiring)
   documents this exact two-call requirement being missed once ([FRO_014](FRO_014_border-persistence-crash.md)).
2. **`BossMobFixture`** (`MobJig`-scoped, via `MobScope`) in its own `BossMobBundle` — needed even
   though nothing else is registered on `MobScope` yet (ordinary `JigConfigValidator` shape, not a
   footgun). **Not persisted** — cheap-to-recompute live view, same shape `BorderPlayerStatusFixture`
   already established.
3. **`BOSS_JIG`** — a dedicated `LevelJigConfig` (`BossJigConfig`), `executionPulse`/`.withTick(true)`
   set. Not borrowed from Border's `BORDERS_JIG` — `BossBundle` is fully decoupled.
4. **The `MobJig` side**, `sideApplicability = SERVER` (Boss's own explicit choice — defeat
   detection is server-only; `MobJigConfig` ships no default, state it explicitly). Register
   interest via `MobInterestRegistry.register(key, supplier)` — a **separate call** from the
   `MobJigConfig` itself, keyed by the same `JigKey` `reconcile` receives as `info.key`. Write the
   supplier against `Level`, not `ServerLevel` — `MobInterestSupplier.interestedMobs()` is
   `Map<Level, Set<UUID>>` now that Roger shipped `ForgeEgress`, even though this consumer's own
   `sideApplicability` stays `SERVER`. **`init()` must actually be called from `SatchelMod`'s
   constructor** — [SAT_035](SAT_035_mobjig-build.md) found `MobTrackingModule` compiled clean but
   was never installed because that call was missing, so its jig was never registered at all. Easy
   to repeat here.
5. **`BossRules`/`DefaultBossRules`** — mirrors `BorderRules`/`DefaultBorderRules` exactly. Two
   genuinely separate mechanisms, not one fused loop (`boss.md`'s "Spawn algorithm" section is
   explicit about why the earlier retry-loop draft was wrong):
   - **Position** — picked once, immediately, at record-creation time: a uniform random XZ column
     within the border's `center()`/`radius()` disk (new helper on `BorderMath`, same shape
     `chooseNextCenter()`'s angle/distance math already uses). No chunk-loaded check involved.
   - **Materialization** — on `BOSS_JIG`'s own tick: for any record with `alive: true` and
     `bossEntityId == null`, check `Level.isLoaded(position)`; if true, resolve ground `Y`, spawn
     the vanilla mob, tag it via `MobScope.getFor(mob)` right there (entity's guaranteed loaded,
     no reason to wait a poll cycle), write `bossEntityId`. If not loaded, no-op, retry next tick —
     same fixed position, never re-rolled.
   - **Mob type / stat scaling** by the record's own copy-once `layer` field, **never** a live
     `Border.layer()` lookup — a placeholder table (layer 1 = a rabbit, per
     [Progression & Frontier Mechanics](../wiki/plans/operational-tiers.md)), not a locked curve.
6. **Record creation is a direct call, not tick-driven.** For this ticket's own scope, the one real
   call site is the level-bootstrap path (below) — the post-defeat pairing into
   `growCenteredOn()` belongs to [RM_FRO_019](../roadmap/RM_FRO_019_karen.md) ("Karen"), not blocking
   here, and explicitly out of scope (see below).
7. **Level-bootstrap hook, shared with Border, needed for this node's own done bar:** a persisted
   `seeded` boolean on `BordersFixture`, set inside `BordersPathFacet.grow()`'s own append, plus a
   new `BorderModule` subscription to `ScopeEvent.Loaded` (filtered to `LevelJig`'s key and the
   overworld dimension) calling `BorderAPI.grow(level)` only when `!seeded`. Right after that
   `grow()` call succeeds for a level's very first border, make the paired call into `BossModule` to
   create its `BossFixture` record, extracting `position`/`layer` from the `Border` just returned.
   This is Border-side plumbing, but nothing else is currently building it and Shirley's own done
   bar ("a fresh level gets a boss... without any command/trigger") depends on it directly — build
   it as part of this ticket. Full design: [Border § Known gaps](../wiki/frontiermode/architecture/border.md#known-gaps),
   [Boss § Defeat detection and the border-growth gap](../wiki/frontiermode/architecture/boss.md#defeat-detection-and-the-border-growth-gap).
8. **Defensive reconciliation check**, on `BOSS_JIG`'s own tick alongside materialization: compare
   the path's set of `layer()` values against `BossFixture`'s set of `layer` values. A mismatch is a
   real data bug (missed call site, crash between paired calls, manual world editing) — log loudly,
   don't silently self-heal.

## Explicitly not in scope

- **`BordersPathFacet.growCenteredOn(BlockPos center)`** and the post-defeat boss-creation pairing
  into it — both belong to [RM_FRO_019](../roadmap/RM_FRO_019_karen.md) ("Karen"), which depends on
  this node and isn't actionable yet. `boss.md` describes the shape for context; don't build it here.
- **The `LivingDeathEvent` listener / defeat detection itself** — Karen's, not this node's.
- **"Boss removed without a `LivingDeathEvent`" reconciliation** (`boss.md`'s "What can actually go
  wrong" — an "expected but absent for N consecutive polls" check) — flagged there as an open design
  question, not a settled one. Don't invent an answer to close this ticket; leave it open if reached.

## Four things worth knowing before you start

Checked against `boss.md`/`runtime.md` 2026-08-24 — places the obvious guess is wrong:

1. **`layer`, not `level`.** `BossFixture`'s field is named `layer` per
   [Border Vocabulary](../wiki/frontiermode/architecture/border-vocabulary.md) — `level` is reserved
   for player-facing text only. Also note `Border.layerIndex()` was renamed to `Border.layer()`
   2026-08-20; build against current source, not any pre-rename log entry.
2. **`MobInterestSupplier.interestedMobs()` is `Map<Level, Set<UUID>>`**, not `Map<ServerLevel,...>`
   — Roger widened it. `MobTrackingModule`/`SatchelHealth` are the shipped worked examples for the
   registration shape.
3. **`MobScope.getFor(Mob)` is the tagging fast path**, used once at materialization time — not the
   presence poll's job to attach `BossMobFixture` on first sight. The poll (via
   `MobInterestRegistry`) is for ongoing presence after that.
4. **Two fixtures, two bundles, two different persistence answers.** `BossFixture` persists
   (identity state, can't be cheaply recomputed). `BossMobFixture` does not (cheap live view, same
   shape `BorderPlayerStatusFixture` established). Getting this backwards repeats
   [FRO_014](FRO_014_border-persistence-crash.md)'s exact mistake.

## Done bar

Per [RM_FRO_018](../roadmap/RM_FRO_018_shirley.md)'s own bar — compiling clean is necessary and not
sufficient:

- A fresh level gets a boss within its cylinder with no command/trigger (the bootstrap hook above).
- The boss is a recognizable tagged vanilla mob (visible marker — custom name and/or glowing — not
  just the `BossFixture` record).
- `BossFixture`/`BossMobFixture` agree with each other — no orphaned record on either side after a
  spawn.
- A `/kill`ed boss triggers the bootstrap catch-up to respawn one — proving the self-heal path, not
  just the happy path.
- **Full defeat → growth confirmation is [RM_FRO_019](../roadmap/RM_FRO_019_karen.md)'s own done
  bar, not this ticket's** — don't block this ticket's close on it.

## Standing constraint

**No Gradle in the agent sandbox.** No Forge/Mojang maven access — every prior node in this project
has hit this. Real build and playtest are the project owner's own machine; don't mark this resolved
on read-through/self-review alone.

## Log

- 2026-08-24: Ticket opened. Architect prep ([FRO_042](FRO_042_shirley-prep.md)) closed; both
  Satchel prerequisites ([RM_SAT_021](../roadmap/RM_SAT_021_frank.md) "Frank",
  [RM_SAT_022](../roadmap/RM_SAT_022_roger.md) "Roger") resolved and live-verified.
- 2026-08-24: Lead Dev build complete for items 1-8 (`BossFixture`/`BossBundle`,
  `BossMobFixture`/`BossMobBundle`, `BOSS_JIG`, `BOSS_MOB_JIG` + `MobInterestRegistry`
  registration, `BossRules`/`DefaultBossRules`, direct-call record creation, the shared
  Border/Boss level-bootstrap hook, and the defensive path/`BossFixture` reconciliation
  check) against [Boss](../wiki/frontiermode/architecture/boss.md), now updated to
  describe the built shape. `BorderModule.init()`/`BossModule.init()` ordering,
  persistence's two-call requirement, and `withExecutionPulse(true)` alongside
  `withTick(true)` all followed per `boss.md`'s own callouts.

  Done bar's `/kill` self-heal item left unmet, on the project owner's own call: nothing
  in this ticket's scope can distinguish a `/kill`'d boss from an ordinary chunk unload
  (`MobJig`'s teardown is reason-agnostic by design), and the only clean disambiguator
  is [RM_FRO_019](../roadmap/RM_FRO_019_karen.md) ("Karen")'s own `LivingDeathEvent`
  listener, not yet built and out of scope here. Documented as a known limitation on
  `BossModule`'s own class-level Javadoc and on [boss.md's "Known
  gaps"](../wiki/frontiermode/architecture/boss.md#known-gaps) rather than closed with
  an invented reconciliation heuristic. Every other done-bar item is met on
  read-through; per this ticket's own standing constraint, none of it is build- or
  playtest-verified — that's the project owner's own machine, not this sandbox.
- 2026-08-24: First real playtest (client+server dev run) crashed the client on login:
  `IllegalStateException` at `BordersFixture.requireServerSide()`, thrown from
  `BordersPathFacet.grow()` via `BorderAPI.grow()` via
  `BorderModule.onBordersScopeLoaded()`. Cause: `BORDERS_JIG` is `sideApplicability =
  BOTH`, so `ScopeEvent.Loaded` fires on the client's own `LevelJig` too, and
  `onBordersScopeLoaded` called `BorderAPI.grow()`/`BossAPI.createBoss()` — both
  persisted-state mutations — with no side guard. Every other handler on this jig
  (`BordersTriggers`' own `Tick` handlers) already guards with `Satchel.require().side()
  == LogicalSide.CLIENT`; this new handler was the one place that guard got missed.
  Fixed by adding the same guard, first thing after the jig-key check, in
  `BorderModule.java`. Server side of the same run worked correctly end to end: border
  grew, boss record created, rabbit materialized and tracked by `SatchelHealth`, player
  logged in and the boss's `MobScope` scoped correctly — the bug was purely a
  client-side crash-on-tick, not a server-side logic error. `boss.md`/`border.md`
  updated to state the guard as part of the mechanism. Not yet re-verified after the fix
  — needs another login pass.
- 2026-08-24: Closed. Second playtest pass confirmed the fix: no crash, fresh-level
  bootstrap produced a border and a tagged boss (`Rabbit['Boss (Layer 0)']`) with no
  command/trigger, and the boss died cleanly (`MobScope` unloaded, no `[Boss]`
  reconciliation warnings) when killed by the player. Three of the done bar's four
  bullets are real-playtest-verified, not read-through: fresh-level bootstrap,
  recognizable tagged mob, and `BossFixture`/`BossMobFixture` agreement. The fourth —
  `/kill` triggering the bootstrap catch-up to respawn — is accepted as unmet per the
  project owner's own call logged above, closed on that basis rather than blocked on it.
  Worth noting for whoever reads this next: the done bar's "bootstrap catch-up" wording,
  in both this ticket and [RM_FRO_018](../roadmap/RM_FRO_018_shirley.md) itself, dates
  to the node's own bullet 4 design (a recurring `BORDERS_JIG` tick check against the
  path-tip border, which the node's log claims would self-heal a `/kill` even without
  `LivingDeathEvent`) — a mechanism `boss.md` walked back to the one-shot `seeded`-flag
  bootstrap actually built here, punting self-heal to
  [RM_FRO_019](../roadmap/RM_FRO_019_karen.md). The done-bar text was never updated to
  match that walk-back, so it still literally promises more than the settled design
  delivers on its own; see the status-trail note added to RM_FRO_018.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
