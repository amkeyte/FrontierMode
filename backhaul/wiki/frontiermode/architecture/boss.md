---
id: frontiermode/architecture/boss
category: frontiermode/architecture
slug: boss
title: Boss
summary: Boss entity/spawn system for Tier 1 -- data model, mutation validation boundary,
  spawn algorithm, and the defeat-detection caller into BorderAPI. RM_FRO_019 builds
  against this.
keywords: null
status: verified
updated: '2026-09-03'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · frontiermode / architecture
<!-- bh-header:end -->

# Boss

Boss entity/spawn system for [FrontierMode Operational Tiers](../../plans/operational-tiers.md)
Tier 1 — "Core loop operational." [RM_FRO_019](../../../roadmap/RM_FRO_019_karen.md) ("Karen,"
defeat detection) builds its `MobDied` handler (see [Mob Lifecycle
Signals](../../satchel/architecture/mob-lifecycle-signals.md) — Satchel's own dispatch, not a raw
`LivingDeathEvent` listener) against the "Defeat detection and the border-growth gap" section
below. Depends on Satchel's [RM_SAT_021](../../../roadmap/RM_SAT_021_frank.md) ("Frank,"
`MobJig`/`MobScope`).

## Scope

Tier 1's bar, per [FrontierMode Operational Tiers](../../plans/operational-tiers.md#the-tiers): a boss
exists somewhere in the current level, can be found by ordinary exploration (no discovery aids —
that's Tier 2), can be killed, and killing it grows the border and spawns the next one. Boss
variety, tells, and loot/reward density are explicitly out of scope here (Tier 2/3).

**Boss = a tagged vanilla mob, not a new entity type** (project owner's call). A boss is an
ordinary Minecraft mob whose stats are scaled by level and which Satchel has been told to track —
"boss-ness" is a fact recorded in Satchel state, not a distinct Java entity class.

## The central fact that shapes this whole design

A boss's chunk is usually *not* loaded. The core loop's entire premise is that the player has to
go find the boss — which means, by construction, a boss spends most of its existence somewhere
nobody's near. A server restart alone puts every boss in the world into "not currently loaded"
state on boot, independent of anything else. That's the default state the data model below is
built around, not a corner case it hardens against after the fact.

## Data model — a persisted record and a live view, not two peers

**`BossFixture`** — level-scoped (`LevelScope`), living in its **own `BossBundle`, not folded
into `BordersBundle`.** `BordersBundle` is Border's own bundle, named and owned for Border's own
concern; a bundle is meant to be one coherent body of data, and a scope hosting several independent
concerns is expected to host several independent bundles rather than one growing catch-all (see
[Bundle](../../satchel/architecture/bundle.md)'s "a scope may host multiple bundles simultaneously,
each representing an independent concern"). This isn't a new call for this node — it's the same
ruling [RM_SAT_020](../../../roadmap/RM_SAT_020_jerry.md)/[RM_FRO_006](../../../roadmap/RM_FRO_006_sandra.md)
already made for per-player border state, for the identical reason: hosting unrelated state on
`BordersBundle` was explicitly rejected there as "the dumping ground every future module reaches
for, the exact SavedData-sprawl problem Satchel exists to prevent" — and that same discussion
named "mob bosses" by name as a case `Scope`'s generalization exists to cover. `BossBundle` is
registered by `BossModule.init()` itself (see "Module wiring" below), the same way
`BorderPlayerBundle` is a second, independent bundle registered alongside the world-scoped one in
`BorderModule.init()` — precedent for "one module, more than one bundle, each its own concern,"
already established in this codebase.

`BossFixture` is the **sole durable source of truth** for boss identity — **not keyed by `Border`
UUID, and not a reference to any `Border` at all.** It's a self-contained collection of records,
one per boss: `{bossId, position, layer, bossEntityId, alive}`. `position` is an XZ column (the
chosen home for this boss) — **nullable, and no longer committed the instant a record is
created.** [Border Pregeneration](border-pregeneration.md#what-this-changes-in-boss) supersedes
this page's earlier claim that position is "picked once, immediately, at creation" and "doesn't
wait on anything"; it now starts unset and is finalized once that page's readiness gate passes —
see "Three questions, three different mechanisms" below for the current sequencing. `layer` is a
plain copied number (whatever the originating `Border`'s `layer()` was at the moment of creation),
not a live reference — once copied, this record never looks at a `Border` again; unaffected by the
pregeneration change, since `layer` never depended on `position`. Named `layer`, not `level` — per
[Border Vocabulary](border-vocabulary.md), "level" is reserved for player-facing text only; an
internal data-model field is exactly the kind of place it's supposed to have retired from.
`bossEntityId` is **nullable** (null until the entity has actually been placed in the world; a
record can legitimately exist with no entity yet — and, since `position` above is nullable too,
with no finalized position yet either). `alive` is false once defeated.

**A boss is created at the same time as a border, but not tied to it.** See "Defeat detection and
the border-growth gap" below for exactly where that creation gets triggered — the short version is
that whatever code creates a new `Border` also creates the matching boss record right there,
extracting `position`/`layer` once and then never referencing the `Border` again. This is
deliberate, not an oversight: it means a boss record needs nothing but a chosen `position` and
`layer` to exist, which is also what makes a hand-placed special-event boss (a fixed position, a
chosen layer, no `Border` involved at all) trivial later — it's the same record shape, just
inserted a different way. Also worth being explicit about scope: not every `Border` gets a boss.
Only borders that actually enter the level's progression (level 1's initial border, and every
border grown onto the path afterward) do — an off-path border created some other way (a debug
trigger, a directly-issued admin command) correctly gets none, since nothing pairs boss-creation
with those call sites.

Needs real persistence: `capabilities(true, ...)` **and** the matching `policies().persistence(...)`
call on its `JigConfig` — [Border](border.md#runtime-wiring) already documents this exact
two-call requirement being missed once ([FRO_014](../../../tickets/FRO_014_border-persistence-crash.md)).

**`BossMobFixture`** — `MobJig`-scoped (`RM_SAT_021`'s jig kind), one per *currently live* boss
entity, living in its own **`BossMobBundle`** (`MobScope`). Needed for the same structural reason
`BossBundle` is, not the same footgun `BossBundle` was fixed for: `JigConfigValidator` requires
the two-level `FixtureDecl` → `BundleDecl` → `Schema` shape regardless of how many fixtures a
bundle ends up holding — `TrackingModule`'s own `TRACKER` fixture gets a dedicated `BUNDLE`
wrapper the same way despite being the only fixture in it. Unlike `BossFixture`'s case, there's no
pre-existing bundle on `MobScope` to be tempted to dump into — `MobJig` is infrastructure with
nothing else registered against it yet — so this is just the ordinary registration shape, not a
second instance of checklist item 7's mistake. Unlike `BossFixture`, this is **not persisted** —
it's fed from `BossFixture`'s record whenever a boss's entity happens to be present, the same
"cheap to rebuild, don't bother saving it" shape `BorderPlayerStatusFixture` already established
in this codebase for exactly this reason. It exists only while the entity is confirmed live —
with one caveat worth being precise about: "confirmed" means as of the last poll cycle or explicit
attach, not continuously re-verified between events. [Mob Lifecycle Signals §
MobDied](../../satchel/architecture/mob-lifecycle-signals.md#mobdied) means a real death can be
signaled up to a poll cycle before the corresponding `MobLostInterest` tears this fixture down —
anything holding a live `Mob` reference through that window must check `isAlive()`/`isRemoved()`
itself rather than trust that "still attached" implies "still alive." It disappears cleanly once
torn down — no data is ever lost by that, because it was never the authoritative copy of anything. This is the live-interaction surface: whatever needs to actually
touch the entity (defeat correlation now, a health-bar or diegetic danger-marker render in a later
tier) reads through this, not `BossFixture`.

Concretely: `BossFixture` is what answers "does level N have a boss, and is it still alive" at any
moment, restart or not. `BossMobFixture` is what answers "here's the live handle, right now,
because the entity happens to be loaded this tick" — and it's fine for it to hold the actual `Mob`
reference directly, since its whole lifecycle is bounded by confirmed presence (see "[Three
questions, three different mechanisms](#three-questions-three-different-mechanisms)" below), not by
an assumption that might quietly go stale.

## Mutation validation boundary

[FRO_054](../../../tickets/FRO_054_mutation-data-security.md)'s QA pass found `BossFixture` had no
validation boundary at all -- `create()`/`materialize()`/`markDefeated()`/`remove()` accepted
whatever they were given, unlike `BordersCrudFacet`'s own `failureReason()` (radius bounds,
non-negative `layerIndex`). [FRO_058](../../../tickets/FRO_058_boss-mutation-validation-reconciliation.md)
asked the Architect to settle the shape before Lead Dev builds against it. Ruling below.

**Shape: stay with Boss's own existing lighter idiom, don't adopt Border's `Result`.** Border's
`Result` carries a rejection message because `BorderProposal` validation spans several
independently-failing fields (radius, `layerIndex`, soon `center` -- see
[FRO_059](../../../tickets/FRO_059_border-proposal-center-bounds-id-display.md)) and a caller
building a raw proposal needs to know which field failed. Boss's mutations each have at most one
thing to reject, and the fixture already has a working boolean/`Optional` idiom (`remove()` returns
`boolean`; `get()` returns `Optional<BossRecord>`) --
[FRO_057](../../../tickets/FRO_057_boss-control-commands-build.md)'s own `MaterializeOutcome`/
`DefeatOutcome` are exactly this pattern taken one step further, and it shipped and played fine.
Introducing `Result` here would be a second validation-carrier convention for Boss to maintain
alongside the one that already works. So: reject -> log via `OUT.warn` (matching Border's own
`failureReason()` discipline of always warning on rejection) -> return the type's own "nothing
happened" value. No new carrier type needed.

- **`create(BlockPos, int layer)` should reject `layer < 0`,** mirroring
  `BordersCrudFacet.failureReason()`'s identical check on `layerIndex` exactly. Return
  `Optional<BossRecord>` (empty on rejection) instead of the current unconditional `BossRecord` --
  matches the `Optional` idiom already used elsewhere on this fixture.
- **`materialize()` needs an already-materialized guard at the fixture level, not just at the
  caller.** Today `BossModule.forceMaterialize` checks `record.materialized()` before calling in,
  but the fixture method itself has no defense -- any other future caller re-materializes a live
  record with a fresh `entityId`, silently orphaning the old one. Change `materialize()` to return
  `boolean` (mirroring `remove()`'s own signature): `true` if the record existed, was
  unmaterialized, and is now materialized; `false` if the record is missing or already
  materialized -- logged via `OUT.warn`, no state change.
- **`markDefeated()` needs the same guard, and it's not hypothetical -- it's a real, shipped bug.**
  `BossAPI.forceDefeat(Level, UUID)` ([FRO_057](../../../tickets/FRO_057_boss-control-commands-build.md),
  `/boss transform defeat`) calls `fixture.markDefeated(bossId)` with no check that `record.alive()`
  is still `true` first, then unconditionally runs `BorderAPI.grow` + `createBoss`. Running
  `/boss transform defeat <selector>` a second time against an already-defeated boss re-triggers
  the full grow-and-spawn cascade a second time -- a duplicate progression step with no real defeat
  behind it, reachable through the exact command FRO_057 just shipped and playtest-verified. Fix:
  `markDefeated()` returns `boolean` (`true` = was alive, now defeated; `false` = record missing or
  already dead, logged, no state change) and `BossAPI.forceDefeat()` must check that return value
  -- on `false`, return a `DefeatOutcome` signaling nothing happened (no `grow`, no `createBoss`)
  rather than proceeding. The combat path (`onLivingDeath`, "Defeat detection and the
  border-growth gap" below) is naturally immune to this -- a `Mob` can only die once -- so this
  guard matters specifically for the command-triggered path, and belongs there, not retrofitted
  onto Karen's already-verified listener.
- **`remove(UUID)` is already validated in the sense that matters (its existence check via the
  `boolean` return); what's still open is what happens to a *materialized* record's live entity.**
  Not hypothetical: `/boss delete` shipped in FRO_057 and calls straight through to `remove()`
  today, so removing a materialized boss this way already, in production, leaves the live entity
  behind -- untracked, but still standing in the world. Not resolved by this pass -- see "Known
  gaps" below -- flagged here because, unlike the three items above (all latent -- no shipped
  command reaches them incorrectly yet), this one is live today.

## Module wiring

`BossModule.init()` (`boss/BossModule.java`), following the same shape `BorderModule.init()`
establishes (see [New Module Checklist](../../satchel/architecture/new-module-checklist.md)),
registers two independent jigs:

1. `FixtureDecl`/`BundleDecl`/`Schema` for `BossFixture`, wrapped in `BossBundle` (`LevelScope`) —
   its own bundle, not folded into `BordersBundle` (see "Data model" above, and checklist item 7).
   `BossBundle` has since picked up two Tier 2 sibling fixtures riding this same tick/pulse wiring
   -- `BossTellFixture` and `BossGuardiansFixture` — see [Boss Discovery Systems](discovery-systems.md#guardian-mobs)
   for both; neither is Tier 1 scope, named here only so this section's fixture list stays current.
2. `BOSS_JIG`, a `LevelJigConfig` distinct from Border's `BORDERS_JIG`, left at `sideApplicability`'s
   own `SERVER` default — unlike Border, Boss has no client-rendering need in Tier 1 (no discovery
   aids). `withTick(true)` and `withExecutionPulse(true)` are both set — `BossBundle` is fully
   decoupled from `BordersBundle` (see "Data model" above), so it needs its own tick capability for
   materialization below, not a borrowed ride on Border's; persistence flush only ever runs from
   inside `onExecutionPulse`, so `withTick(true)` alone would be silent inertness (the exact lesson
   [RM_FRO_018](../../../roadmap/RM_FRO_018_shirley.md)'s own log recorded on Border).
3. `FixtureDecl`/`BundleDecl`/`Schema` for `BossMobFixture`, wrapped in `BossMobBundle`
   (`MobScope`) — see "Data model" above for why this still needs its own bundle wrapper even
   though `MobScope` has no pre-existing bundle to fold into. `BOSS_MOB_JIG`, a `MobJigConfig` with
   `sideApplicability = SERVER` — Boss's own explicit choice (defeat-detection is server-only), not
   a default `MobJig` itself imposes; `MobJigConfig` ships with no opinionated default.
4. **Registering interest is a separate call, not part of the `MobJigConfig` above** —
   `MobJigConfig`/`Presets`/`CompiledJigConfig` have no generic slot for it, and `JigInfo` never
   holds a reference back to the originating config instance. `BossModule` calls
   `MobInterestRegistry.register(FrontierKeys.BOSS_MOB_JIG, () -> INTERESTS)` right alongside
   `Satchel.registerJigConfig(config)`, the same shape `MobTrackingModule`/`SatchelHealth` already
   establish. `INTERESTS` is a `Map<Level, Set<UUID>>` (widened per
   [RM_SAT_022](../../../roadmap/RM_SAT_022_roger.md) "Roger"'s `ForgeEgress`, even though this
   consumer's own `sideApplicability` stays `SERVER`), populated at materialization time and never
   pruned here — a stale UUID that no longer resolves is a normal, cheap no-op for the poll, the
   same precedent `SatchelHealth`'s own never-unwatched interest map sets; defeat detection owns
   clearing the underlying record, not this map.
5. `EventHandlers` on `BOSS_JIG`'s own `ScopeEvent.Tick` drive both materialization and the
   defensive reconciliation check (see "What can actually go wrong" below) — cheap enough to share
   one tick, logically independent of each other. Separate handlers on `BOSS_MOB_JIG`'s
   `MobGainedInterest`/`MobLostInterest` (see [Mob Lifecycle
   Signals](../../satchel/architecture/mob-lifecycle-signals.md) — the Mob-kind-specific siblings
   fired alongside the generic `ScopeEvent.Loaded`/`Unloaded`) attach/release `BossMobFixture` when
   `MobJig` confirms a tracked boss becomes present or stops being present.
6. `BossModule.init()` is called once from `FrontierMode`'s constructor, immediately after
   `BorderModule.init()` — Boss depends on Border (its level-bootstrap pairing reads Border's own
   `ScopeEvent.Loaded` hook, see [Border § Runtime wiring](border.md#runtime-wiring)), never the
   reverse; Border has no knowledge Boss exists.

The `MobDied` handler itself belongs to [RM_FRO_019](../../../roadmap/RM_FRO_019_karen.md)
("Karen") — not part of `BossModule` as it stands; see "Defeat detection and the border-growth gap"
below for where it plugs in. The raw `LivingDeathEvent` registration this handler used to be lives
in `ServerForgeIngress` now, not here — see [Mob Lifecycle
Signals](../../satchel/architecture/mob-lifecycle-signals.md).

## Three questions, three different mechanisms

Easy to conflate; worth keeping visibly separate, since each is answered a different way:

**"Should a boss record exist at all?"** — **not tick-driven, not a scan.** A direct call, paired
with whatever code just created a new `Border` in the level's progression — see "Defeat detection
and the border-growth gap" below for exactly where those calls live. Creating the record is
instant and unconditional: copy the new `Border`'s `layer()` value as `layer` and write
`{position: null, layer, bossEntityId: null, alive: true}`. No periodic check ever asks "does the
path-tip have a boss" — `BossFixture` doesn't know what the path even is.

**Finalizing `position` is layered onto this same question, and is genuinely tick-driven** — a
real change from this page's earlier "picked once, immediately, at creation" claim, per [Border
Pregeneration § Worked example](border-pregeneration.md#worked-example-boss-placement-revised).
`BOSS_JIG`'s tick no-ops on any record with `position: null` until `BorderAPI.isPregenReady()`
returns true for its home border — the identical no-op-and-recheck-next-tick shape the
materialization check below already uses. Once ready, it scores several
`BorderMath.randomPointInDisk`-sampled chunk-center candidates against
`BossRules`/`DefaultBossRules`'s flatness/hazard functions (see "[Spawn
algorithm](#spawn-algorithm-a-pluggable-strategy-mirroring-borderrules)" below) and commits the
winner as `position`. Materialization below has nothing to act on until this step commits a
non-null `position`.

**"Does an existing record have an actual entity yet?"** — tick-driven, on `BOSS_JIG`'s own
`ScopeEvent.Tick` (not borrowed from `BORDERS_JIG` — see "Module wiring" above). Any record with a
finalized (non-null) `position`, `alive: true`, and `bossEntityId == null` hasn't been
materialized yet. Per [Border Pregeneration](border-pregeneration.md#worked-example-boss-placement-revised),
`position` by this point already names validated, real terrain, so no in-place Y-resolution or
liquid-column check happens here anymore — this check is purely `Level.isLoaded(position)` (a
direct boolean query, not a forced load); if true, spawn the mob at that already-known-good
position. If not loaded, no-op and check again next tick — same fixed position, never a new random
guess. This is a normal, expected wait, not an error: it converges the moment a player (or
anything else) gets that column loaded. The same tick also runs the defensive reconciliation check
described in "What can actually go wrong" below — cheap enough to share the cadence, logically
separate from materialization.

**"Is the boss I have on record currently present?"** — a question about live entity state, which
`BossFixture` can't answer on its own (it doesn't know or care whether anything is loaded).
`LevelJig`/`PlayerJig` have no equivalent mechanism to reuse here — `MobJig` accepts a per-consumer
interest supplier ("here are the UUIDs I care about, on this level") and runs a reconciliation
step inside `foundationLifecycle().pulse()` — the same pulse `ServerForgeIngress.onExecutionPulse`
already drives every server tick — checking
`Satchel.require().egress().getEntity(level, uuid)` for each supplied UUID (`ForgeEgress`, the
side-resolved lookup [RM_SAT_022](../../../roadmap/RM_SAT_022_roger.md) ("Roger") shipped; Boss's
own consumer stays `SERVER`-applicability, so for `BossModule` this still only ever runs through
`ServerForgeIngress`). This doesn't require the chunk to already be loaded to safely call it; it's
a lookup against whatever's currently present and simply returns empty otherwise, so this can run
unconditionally rather than waiting for some other signal to say "check now." Found-and-not-yet-
scoped fires `ScopeEvent.Loaded` (triggers `BossModule`'s attach handler); previously-scoped-and-
now-absent fires `ScopeEvent.Unloaded` (triggers the release handler) — reason-agnostic, the same
way `LevelEvent.Unload`/`PlayerLoggedOutEvent` teardown already is for the other two jig kinds.
No Forge join/leave hook of any kind is needed for this — presence is actively re-verified each
cycle rather than inferred from an event that may or may not fire for every removal reason (chunk
unload specifically is genuinely uncertain territory across Forge versions; polling sidesteps the
question rather than depending on the answer). Doesn't need to run every single tick — something
like every 20 (matching `PlayerTrackingModule`'s own cadence) is plenty, since nothing here is
latency-sensitive.

`MobScope.getFor(Mob)` (see `RM_SAT_021`) is still the right call for the one moment presence is
already guaranteed without waiting on a poll cycle: the instant a boss is freshly spawned.

## Spawn algorithm — a pluggable strategy, mirroring `BorderRules`

`BossRules`/`DefaultBossRules` (`boss/server/rules/*`) deliberately mirror [Border](border.md)'s
`BorderRules`/`DefaultBorderRules` split (a "safe baseline, replace later" strategy object, not a
hardcoded algorithm):

- **Position and Materialization — now specified in [Border
  Pregeneration](border-pregeneration.md), not here.** That page partially supersedes this
  section: Position no longer commits the instant a `BossFixture` record is created -- it waits on
  `BorderAPI.isPregenReady()` for the home border, then picks among already-generated terrain
  scored against `BossRules`/`DefaultBossRules`'s flatness/hazard functions, rather than a single
  blind, unvalidated XZ column. Materialization simplifies in turn -- once a position is only ever
  committed after passing that validation, materialization goes back to purely "wait for
  `Level.isLoaded()`, then spawn," with no in-place Y-resolution or liquid-column edge case left
  to handle here. See that page for the full mechanism, the exploit it closes, and the new bounded
  wander/leash movement model this design also introduces.
- **Mob type / stat scaling by the boss's own recorded `layer`** — `BossFixture.layer`, the
  copy-once value set at creation (see "Data model" above), **never** a live `Border.layer()`
  lookup at spawn or materialization time. This is the same guarantee [Border
  Vocabulary](border-vocabulary.md#implementation-trap-worth-flagging-now)'s "implementation trap"
  section warns against reintroducing — stated here explicitly so a reader who jumps straight to
  this section doesn't have to cross-check "Data model" to confirm it. A placeholder table, not a
  locked curve — layer 0/1 is [Progression & Frontier
  Mechanics](../design/progression.md#starting-conditions)'s own named example (a rabbit), higher
  layers step up through tougher vanilla mobs (zombie, spider, skeleton, zombified piglin,
  pillager, vindicator, ravager) with health/attack-damage scaled linearly per layer above that
  baseline. Real balance tuning is Game Designer/playtest territory once there's something to play,
  same category as `DefaultBorderRules.GROWTH_FACTOR`'s own "safe baseline" framing — this is a
  working default, not a final curve.
- **Tagging:** happens in the same step as materialization above, not a separate pass — the entity
  is guaranteed loaded at that exact instant (`isLoaded` just confirmed it), so `MobScope.getFor(mob)`
  attaches `BossMobFixture` right there, no reason to wait a full poll cycle when the reference is
  already in hand. The `BossFixture` record (now carrying a real `bossEntityId`) is the real tag; a
  lightweight visible marker (custom name, glowing) rides alongside it, so "no discovery aids"
  (Tier 1) still means "findable by looking," not "invisible until you already know."

Record creation (Position) and materialization are genuinely different mechanisms, not two callers
sharing one algorithm — see "Three questions, three different mechanisms" above for creation's
direct-call trigger, and "Defeat detection and the border-growth gap" below for exactly which call
sites make that call. Materialization is single and uniform regardless of how a record was
created: `BOSS_JIG`'s own tick checks every unmaterialized record the same way, every time.

## Aliveness checking and despawn

A boss can outlive its entity (unloaded, untracked) and can be explicitly removed (admin command).
Two separate queries address both cases, feeding Environmental Tells (which need to know if a boss
is alive before rendering) and border growth (which needs to happen when a boss dies).

### Per-record aliveness query on `BossMobFixture`

**`boolean isAlive(UUID bossId)`** — checks whether a boss record is marked alive (`alive: true`
in `BossFixture`). This is a persistence-backed query, independent of whether the entity is
currently loaded. Cheap; works across restarts.

### Public query surface on `BossAPI`

**`BossAPI.isBossAlive(Level, UUID bossId)`** — wraps the fixture query via the standard
cross-module API shape. Callers (Environmental Tells, future systems) use this, never
`BossMobFixture` directly.

Optional convenience: **`BossAPI.getAllAliveBosses(Level)`** — returns the set of all alive boss
UUIDs in the level, if Environmental Tells need to iterate (e.g., for rendering tells matched to
alive bosses).

### Despawn and record removal

**`BossAPI.removeBoss(UUID bossId)`** — explicit admin removal (e.g., `/boss delete`). This:

1. **Despawns the live entity** via `BossMobFixture`/`MobScope` if the entity is currently
   loaded — no orphaned mobs left in the world.
2. **Deletes the `BossRecord` entirely** from `BossFixture`'s persistence — not a state-change
   (e.g., `alive: false`), a full removal. Rationale: a deleted boss is done. Off-path hand-placed
   bosses (created with `/boss add`) already show deletion is the right model — you don't query
   "are there any bosses that used to exist here" as part of normal gameplay. If you need history
   later (e.g., "which bosses have ever spawned on this path"), that's a separate ledger, not a
   corrupted persistent record. Keeping a `dead` record would only confuse reconciliation
   ("is this orphaned, or deleted on purpose?") and leave stale data behind.
3. **Does not fire a border-growth event** — deletion is administrative removal, not a "boss died"
   cascade. No new boss is spawned, no border is grown. This mirrors
   [RM_FRO_022](../../../roadmap/RM_FRO_022_joyce.md) ("Joyce")'s own distinction: `/boss transform defeat` *is* a
   growth trigger (it dispatches the same cascade as Karen's `LivingDeathEvent` handler), but
   `/boss delete` is not.
4. **Marks the border `pendingAttach` if the removed record was on-path.** If the removed record's
   `borderId` (see [Boss-less path layers and attach](#boss-less-path-layers-and-attach)) pointed
   at a border still on the level's path, that border's id is added to `pendingAttach` -- the same
   sanctioned wait-state a boss-less `pathGrow()` produces, not a reconciliation false-positive. A
   hand-placed off-path boss (no `borderId`) skips this step entirely, same as it always has.

## What can actually go wrong, and what doesn't need to

Worth being precise about which failure modes this design actually has to defend against, since
not every scenario that sounds scary is a real gap:

- **Boss sits unloaded for an entire play session, or across a restart — whether or not it's even
  been materialized yet.** Not a failure case either way — this is the expected common state.
  Already-materialized: `BossFixture` says `alive: true` with a real `bossEntityId` the whole time;
  nothing tries to respawn it. Not yet materialized: the record's `position` just hasn't had
  `Level.isLoaded` come back true yet; nothing tries a different position, it just keeps checking
  the same one.
- **A tracked boss is genuinely destroyed while loaded** (`/kill`, or a `LivingDeathEvent` from
  ordinary combat). A death can only happen to something currently loaded, so once
  [RM_FRO_019](../../../roadmap/RM_FRO_019_karen.md)'s `MobDied` handler is wired, that path is
  reliable by construction, independent of the presence-poll's cadence — see [Mob Lifecycle
  Signals § MobDied](../../satchel/architecture/mob-lifecycle-signals.md#mobdied).
- **A boss is removed by something that never fires `LivingDeathEvent` at all** — external
  world-editing, a bug in an unrelated mod. `BossFixture` keeps saying `alive: true` with the
  now-gone entity's UUID; nothing here clears it to trigger a respawn. [Mob Lifecycle
  Signals § MobDied](../../satchel/architecture/mob-lifecycle-signals.md#mobdied) is the real death
  signal for the case where `LivingDeathEvent` *does* fire — this narrower remaining gap is
  specifically the removals that never fire it in the first place, which `MobDied` can't help with
  by construction. Distinguishing this residual case from an ordinary chunk unload still needs
  either a different real signal or an "expected but absent for N consecutive polls" heuristic —
  flagged here as an open design question, not a settled one, since the two are genuinely
  indistinguishable
  to `MobJig`'s own reason-agnostic teardown. [FRO_043](../../../tickets/FRO_043_boss-build.md)'s
  log records the call to leave this unmet rather than invent an unproven heuristic to close it.
- **A `Border` that entered the level's progression has no matching boss record.** Most wired
  call sites create the boss record in the same breath as the border (see "Defeat detection and the
  border-growth gap" below); `BorderCommandHandler.pathGrow()` is the one deliberate exception --
  see [Boss-less path layers and attach](#boss-less-path-layers-and-attach) below for why, and for
  `pendingAttach`, the mechanism that keeps that sanctioned gap from reading as corruption.
  `BOSS_JIG`'s own tick runs a defensive reconciliation check alongside materialization:
  `BossModule.reconcilePathAgainstBossRecords()` computes the path's set of `layer()` values minus
  `BossFixture`'s own set of `layer` values, using that value itself as the correlating key (no
  live `Border` reference needed, matching the decoupling in "Data model" above), then checks any
  gap found against `pendingAttach` before logging. **This check is one-directional only** -- it
  catches a path border with no boss record, and stops there. A gap not covered by `pendingAttach`
  is logged loudly as a real data bug (a missed call site, a crash between two paired calls, manual
  world editing), never silently self-healed; a gap that *is* covered logs at a lighter level --
  awaiting `/boss attach`, not corruption.

  **The reverse direction -- a boss record whose `layer` matches no real path border -- is
  deliberately not checked, and that's a ruling, not an oversight.**
  [FRO_058](../../../tickets/FRO_058_boss-mutation-validation-reconciliation.md) asked whether to
  add it; the answer is not yet, and not with `layer` alone. A hand-placed off-path boss
  (`/boss add <pos> <layer>`, shipped in FRO_057) can legitimately hold any `layer` value with no
  border behind it at all -- that's the whole point of the command. A naive reverse check using
  `layer` as the correlating key can't tell "this boss's border went missing" from "this boss was
  deliberately hand-placed for testing" -- every legitimate use of `/boss add` off-path would log a
  false positive. The precise version needs the still-proposal `borderId` field
  ([Boss Command Surface](boss-commands.md)'s Selector scheme) to distinguish "on-path, tracking a
  real border" from "intentionally standalone" -- until that field exists, an imprecise heuristic
  is worse than no check: it trains whoever reads the log to ignore reconciliation warnings.
  Revisit once `borderId` lands.

## Boss-less path layers and attach

A path layer can legitimately carry no boss, in exactly one sanctioned window: between
`BorderCommandHandler.pathGrow()` growing the path (deliberately boss-less, per project-owner
ruling -- see [FRO_048](../../../tickets/FRO_048_pathgrow-no-boss.md)) and a follow-up
`/boss attach` pairing one onto it. `/boss delete` removing an on-path boss produces the identical
state -- deletion and a boss-less grow are the same wait-state, not two different ones, so no
separate "detach" operation exists or is needed.

**`pendingAttach: Set<UUID>` (border ids)** is a small persisted collection on `BossFixture`,
alongside its boss-record list -- not a per-record field, since it tracks borders with *no* record
at all. Two write sites, both following the existing paired-call-at-the-call-site convention
[Defeat detection and the border-growth gap](#defeat-detection-and-the-border-growth-gap)
establishes below:

- `pathGrow()` succeeding adds the newly-grown border's id.
- `BossAPI.removeBoss()` removing a record whose `borderId` (see below) pointed at an on-path
  border adds that border's id back in -- see [Despawn and record
  removal](#despawn-and-record-removal)'s updated step list.

`/boss attach <border-selector>` is the clearing side: it creates a boss record for the selected
border the normal way (`BossCrudFacet.create(border.layer())` -- nullable position, finalized by
`BOSS_JIG`'s tick same as any other new record, see "Spawn algorithm" above), sets the new
record's `borderId` to the selected border, and removes that border's id from `pendingAttach`. No
new creation mechanism -- `attach` is `add` with a border to pair against instead of a raw
position, using the `borderId` field [Boss Command Surface](boss-commands.md)'s "Selector scheme"
section already proposed.

**Depends on `Optional<UUID> borderId` landing on `BossRecord`** -- [Boss Command
Surface](boss-commands.md)'s "Selector scheme" section already ruled on the field's shape
(`empty()` for a hand-placed off-path boss, set once at creation otherwise); this ruling is what
makes building it necessary now rather than optional.

## Defeat detection and the border-growth gap

[RM_FRO_019](../../../roadmap/RM_FRO_019_karen.md) ("Karen") owns the `MobDied` handler itself
(see [Mob Lifecycle Signals](../../satchel/architecture/mob-lifecycle-signals.md) — the raw
`LivingDeathEvent` registration lives in `ServerForgeIngress`, not here). Growing a border centered
on the defeated boss's home block, while keeping `borderPath` consistent, is Border's own job: `BordersPathFacet.grow(BlockPos center)` — an overload of the
no-arg `grow()`, not a separately-named method — takes an explicit center in place of
`DefaultBorderRules.chooseNextCenter()`'s own random pick; radius and `layer` are unaffected,
coming from the same rules either overload uses (`chooseNextRadius()`, `previous.layer() + 1`).
This keeps "create a border and keep the path consistent" atomic, the same guarantee every other
`BordersPathFacet` mutation already provides, rather than leaving two calls (`addBorder()` +
`PATH.insert()`) for a caller to remember to sequence correctly. An absent path tip is not a
special case for this overload: it bootstraps exactly like the no-arg `grow()`'s own empty-path
branch (`getInitial()`'s rules-driven radius, `layer 0`), just with the caller's center substituted
for whatever `getInitial()` would otherwise pick — the same mechanism `grow()` already needs for a
level's very first border, not a failure case unique to a caller-supplied center. Full method and
`Result` contract: [Border § Mutation surface](border.md#mutation-surface).

**Pairing boss-record creation with border creation — the actual trigger for "Should a boss record
exist at all?" above.** This isn't Border's job and isn't a Border-side hook — Border doesn't know
`BossModule` exists, and that dependency direction (Boss depends on Border, never the reverse) stays
fixed. Instead, whoever *calls* a border-creating operation also calls into `BossAPI` right after,
as a sibling step, extracting `position`/`layer` from the `Border` that call just returned:

- **`grow(center)`, post-defeat.** Belongs to [RM_FRO_019](../../../roadmap/RM_FRO_019_karen.md)
  ("Karen"): its `MobDied` handler (see [Mob Lifecycle
  Signals](../../satchel/architecture/mob-lifecycle-signals.md) — subscribed via
  `EventHandlers.on(MobDied.class, ...)`, not a raw Forge listener) resolves the dying entity's
  boss record (its `BossMobFixture` if `MobJig` already attached one, with a synchronous
  `MobScope.getFor(mob)` fallback otherwise, called on the `Mob` the event's own `LivingDeathEvent`
  payload carries — see [MobScope.getFor() Contract](../../satchel/spec/mobscope-getfor.md)),
  marks that record defeated, then -- gated per "Cascade gating" below -- calls
  `BorderAPI.grow(level, deathLocation)` and, once that succeeds, `BossAPI.createBoss` right after,
  in the same handler.
- **`BossAPI.forceDefeat(Level, UUID)`, command-triggered.** [FRO_057](../../../tickets/FRO_057_boss-control-commands-build.md)
  (`/boss transform defeat`, [RM_FRO_022](../../../roadmap/RM_FRO_022_joyce.md) "Joyce"): the
  same cascade as the combat path above -- `markDefeated`, then, gated the same way, `BorderAPI.grow`
  centered on the record's own stored `position()`, then `createBoss` for the resulting border --
  but keyed off a selector-resolved `bossId` instead of a dying `Mob`, so it works on an
  unmaterialized record with no live entity at all. Deliberately not sharing code with
  `onLivingDeath` above; that handler is Karen's own already-verified path and stays untouched.
- **`getInitial()`, a level's first border.** `getInitial()` has exactly one call site in the
  codebase -- `BordersPathFacet.grow()`'s own empty-path branch. `BossModule` -- not `BorderModule`,
  see [FRO_075](../../../tickets/FRO_075_bootstrap-ownership.md), which moved this listener to keep
  the dependency direction (Boss depends on Border, never the reverse) intact -- subscribes to
  Satchel's own `ScopeEvent.Loaded` on `BORDERS_JIG` (server + overworld only); when an unseeded
  level's borders scope loads, it calls `BorderAPI.grow(level)` and then
  `BossCrudFacet.create(border.layer())` for the resulting border, in that same handler -- see
  [Border § Runtime wiring](border.md#runtime-wiring) for the full mechanism, including why
  `BordersFixture.seeded()` rather than `PATH.isEmpty()` is what gates it. Never routed through the
  defeat cascade -- no death occurred -- so "Cascade gating" below doesn't apply to it; this is the
  call site the [FRO_064](../../../tickets/FRO_064_boss-defeat-cascade-grows-border-level-r.md)
  ruling means by "the bootstrap case needs no special case."
- **Anything else that calls `grow()`/`addBorder()` directly** (Tier 0's own description mentions
  "an admin command and a debug trigger" as existing callers) only needs the paired call if it's
  meant to produce a real progression border. If it's producing an off-path/debug border, it
  correctly gets no boss by doing nothing extra — consistent with "Data model" above's point that
  not every `Border` needs one.

### Cascade gating: on-path, and last one standing

Neither defeat-triggered call site above fires `grow`/`createBoss` unconditionally --
[FRO_064](../../../tickets/FRO_064_boss-defeat-cascade-grows-border-level-r.md) traced a live
playtest bug to exactly that gap. Both check the defeated record's `borderId` (see [Boss-less path
layers and attach](#boss-less-path-layers-and-attach)) after `markDefeated`, before doing anything
else:

- **Off-path stops here.** `borderId` empty -- a hand-placed `/boss add`-ed record never `/boss
  attach`-ed to a border -- means the cascade stops right after `markDefeated`. No `grow`, no
  `createBoss`. This is the only way an added boss avoids gating progression, and it's not a
  special case -- every record gets the same check, an off-path one just never has a `borderId` to
  match unless someone deliberately attaches it.
- **On-path requires every sibling on that `borderId` defeated too.** `BossFixture`'s n:1
  boss-to-border cardinality (a themed multi-mob encounter can share one `borderId`) means one
  defeat isn't necessarily the whole encounter. Before proceeding, the cascade checks whether any
  other record sharing that `borderId` still has `alive: true`. If yes, stop -- this defeat is
  recorded and nothing else happens yet. Only once the last living record sharing that `borderId`
  falls does `grow` + `createBoss` run. `borderId`, not `layer`, is the correlating key -- tighter
  than a layer match, which can't tell an intentionally standalone off-path boss from a real
  progression gap (see "What can actually go wrong" above).

Depends on `Optional<UUID> borderId` landing on `BossRecord`, same field [Boss-less path layers and
attach](#boss-less-path-layers-and-attach) needs.

## Known gaps

- **A tracked boss removed by something that never fires `LivingDeathEvent` at all still needs its
  own reconciliation check** -- see "What can actually go wrong" above. [Mob Lifecycle
  Signals § MobDied](../../satchel/architecture/mob-lifecycle-signals.md#mobdied) resolved this for
  the case where `LivingDeathEvent` does fire; this narrower residual case (external world-editing,
  an unrelated mod's bug) is untouched by construction. Still an open design question, not an
  assumed answer.
- **Reverse-direction reconciliation (a boss record whose `layer` matches no real path border) is
  deferred, not built** -- see "What can actually go wrong" above. Needs `borderId` (still
  proposal, [Boss Command Surface](boss-commands.md)) to be precise; not worth an imprecise interim
  version.
- **`remove()` on a materialized record leaves its live entity orphaned -- untracked, but still
  standing in the world.** Real today, not latent: `/boss delete` (FRO_057) reaches this path in
  production. See "Mutation validation boundary" above. Ruling: `/boss delete` despawns the
  entity directly (via `BossMobFixture`/`MobScope`), not through `markDefeated()`/`forceDefeat()`'s
  cascade -- deletion is an administrative removal ("get rid of this record"), not a "boss died"
  event, so it must not grow a border or queue a next boss. See FRO_061.

## Related pages

- [Border Pregeneration](border-pregeneration.md) — the placement/materialization revision that partially supersedes this page's Spawn Algorithm section
- [Border](border.md) — the data model and module pattern this extends
- [Progression & Frontier Mechanics](../design/progression.md) — the core loop this implements
- [FrontierMode Operational Tiers](../../plans/operational-tiers.md) — Tier 1's definition
- [RM_FRO_018](../../../roadmap/RM_FRO_018_shirley.md) / [RM_FRO_019](../../../roadmap/RM_FRO_019_karen.md) — roadmap trackers
- [RM_SAT_021](../../../roadmap/RM_SAT_021_frank.md) — the `MobJig` prerequisite
- [FRO_058](../../../tickets/FRO_058_boss-mutation-validation-reconciliation.md) — the mutation validation/reconciliation spec review this page's "Mutation validation boundary" section and "Known gaps" answer
- [Mob Lifecycle Signals](../../satchel/architecture/mob-lifecycle-signals.md) — `MobDied`/`MobGainedInterest`/`MobLostInterest`, the Satchel-side dispatch this page's defeat detection and attach/release wiring build against
