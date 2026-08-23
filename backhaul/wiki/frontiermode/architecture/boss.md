---
id: frontiermode/architecture/boss
category: frontiermode/architecture
slug: boss
title: Boss
summary: Boss entity/spawn system design for Tier 1 -- data model, spawn algorithm,
  and the defeat-detection caller into BorderAPI. RM_FRO_018/019 build against this.
keywords: null
status: verified
updated: '2026-08-23'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · frontiermode / architecture
<!-- bh-header:end -->

# Boss

*Design pass for [RM_FRO_018](../../../roadmap/RM_FRO_018_shirley.md)/[RM_FRO_019](../../../roadmap/RM_FRO_019_karen.md)
(FrontierMode Tier 1 — "Core loop operational") — this is what Lead Dev builds against, not a
description of shipped behavior. Depends on Satchel's
[RM_SAT_021](../../../roadmap/RM_SAT_021_frank.md) ("Frank," `MobJig`/`MobScope`).*

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
state on boot, independent of anything else. That's the default state to design around, not a
corner case to harden against after the fact — the data model below is built on that footing from
the start.

## Data model — a persisted record and a live view, not two peers

**`BossFixture`** — level-scoped (`LevelScope`), but living in its **own `BossBundle`, not folded
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
chosen home for this boss, picked once, immediately, at creation — see "Position" below for why
this doesn't wait on anything). `layer` is a plain copied number (whatever the originating
`Border`'s `layer()` was at the moment of creation, renamed from `layerIndex()` 2026-08-20), not a
live reference — once copied, this record never looks at a `Border` again. Named `layer`, not `level` — per [Border
Vocabulary](border-vocabulary.md), "level" is reserved for player-facing text only; an internal
data-model field is exactly the kind of place it's supposed to have retired from. `bossEntityId` is
**nullable** (null until the entity has
actually been placed in the world; a record can legitimately exist with no entity yet). `alive` is
false once defeated.

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

**`BossMobFixture`** — `MobJig`-scoped (via `RM_SAT_021`'s new jig kind), one per *currently live*
boss entity, living in its own **`BossMobBundle`** (`MobScope`). Needed for the same structural
reason `BossBundle` is, not the same footgun `BossBundle` was fixed for: `JigConfigValidator`
requires the two-level `FixtureDecl` → `BundleDecl` → `Schema` shape regardless of how many
fixtures a bundle ends up holding — `TrackingModule`'s own `TRACKER` fixture gets a dedicated
`BUNDLE` wrapper the same way despite being the only fixture in it. Unlike `BossFixture`'s case,
there's no pre-existing bundle on `MobScope` to be tempted to dump into — `MobJig` is new
infrastructure with nothing registered against it yet — so this is just the ordinary registration
shape, not a second instance of checklist item 7's mistake. Unlike `BossFixture`, this is **not
persisted** — it's fed from `BossFixture`'s
record whenever a boss's entity happens to be present, the same "cheap to rebuild, don't bother
saving it" shape `BorderPlayerStatusFixture` already established in this codebase for exactly this
reason. It exists only while the entity is confirmed live, and disappears cleanly when it isn't —
no data is ever lost by that, because it was never the authoritative copy of anything. This is
the live-interaction surface: whatever needs to actually touch the entity (defeat correlation now,
a health-bar or diegetic danger-marker render in a later tier) reads through this, not
`BossFixture`.

Concretely: `BossFixture` is what answers "does level N have a boss, and is it still alive" at any
moment, restart or not. `BossMobFixture` is what answers "here's the live handle, right now,
because the entity happens to be loaded this tick" — and it's fine for it to hold the actual `Mob`
reference directly, since its whole lifecycle is bounded by confirmed presence (see "[Three
questions, three different mechanisms](#three-questions-three-different-mechanisms)" below), not by
an assumption that might quietly go stale.

## Module wiring

A new `BossModule.init()`, following the same shape `BorderModule.init()` already establishes
(see [New Module Checklist](../../satchel/architecture/new-module-checklist.md)):

1. `FixtureDecl`/`BundleDecl`/`Schema` for `BossFixture`, wrapped in a new `BossBundle`
   (`LevelScope`) — its own bundle, not folded into `BordersBundle` (see "Data model" above, and
   checklist item 7) — schema registration only, same footgun class as checklist item 1.
2. A `BossJigConfig` (`BOSS_JIG`, its own `LevelJigConfig`, distinct from Border's `BORDERS_JIG`)
   with `executionPulse`/`.withTick(true)` set — `BossBundle` is fully decoupled from
   `BordersBundle` (see "Data model" above), so it needs its own tick capability for
   materialization below, not a borrowed ride on Border's.
3. `FixtureDecl`/`BundleDecl`/`Schema` for `BossMobFixture`, wrapped in a new `BossMobBundle`
   (`MobScope`) — see "Data model" above for why this still needs its own bundle wrapper even
   though `MobScope` has no pre-existing bundle to fold into. A `MobJigConfig`,
   `sideApplicability = SERVER` — Boss's own explicit choice (defeat-detection is server-only), not
   a default `MobJig` itself imposes; `MobJigConfig` ships with no opinionated default, per
   [RM_SAT_021](../../../roadmap/RM_SAT_021_frank.md)'s own design.

   **Registering interest is a separate call, not part of the `MobJigConfig` above.**
   `MobJigConfig`/`Presets`/`CompiledJigConfig` have no generic slot for it, and `JigInfo` — what
   `MobJig.reconcile` actually receives each pulse — never holds a reference back to the
   originating config instance, so Frank shipped a standalone registry instead:
   `MobInterestRegistry.register(key, supplier)`, keyed by the same `JigKey` `reconcile` already
   has in hand as `info.key`. `MobTrackingModule` is the shipped worked example — its own `init()`
   calls `MobInterestRegistry.register(JIG, () -> INTERESTS)` right alongside
   `Satchel.registerJigConfig(config)`; `BossModule.init()` follows the identical shape. `init()`
   itself must actually be invoked from `SatchelMod`'s constructor —
   `MobTrackingModule` compiled clean but was never installed the first time because that call was
   missing, so its jig was never registered at all
   ([SAT_035](../../../tickets/SAT_035_mobjig-build.md)); worth checking for explicitly rather than
   assuming the wiring is done because it compiles. See "Three questions, three different
   mechanisms" below for what the registered supplier itself answers.
4. `EventHandlers` subscribing materialization + the defensive reconciliation check (see "What can
   actually go wrong" below) to `BOSS_JIG`'s own `ScopeEvent.Tick`, guarded by jig key per checklist
   item 3, plus handlers on `MobJigConfig`'s own `ScopeEvent.Loaded`/`Unloaded` that attach/release
   `BossMobFixture` when `MobJig` confirms a tracked boss becomes present or stops being present.
5. The `LivingDeathEvent` listener from `RM_FRO_019` is registered the same way
   `BorderModule.onBlockPlaced` is — `MinecraftForge.EVENT_BUS.addListener(...)`, not folded into
   the `EventHandlers` builder above (that's for `ScopeEvent`, not raw Forge events — same
   distinction Border's own module already draws). This remains the **only** raw Forge registration
   `BossModule` needs; everything else rides Satchel's own tick machinery, or the direct
   creation-time call described in "Defeat detection and the border-growth gap" below.

## Three questions, three different mechanisms

Easy to conflate; worth keeping visibly separate, since each is answered a different way:

**"Should a boss record exist at all?"** — **not tick-driven, not a scan.** A direct call, paired
with whatever code just created a new `Border` in the level's progression — see "Defeat detection
and the border-growth gap" below for exactly where those calls live. Creating the record is
instant and unconditional: pick a random XZ column within the new `Border`'s disk (see "Position"
below — pure geometry, no chunk state involved), copy its `layer()` value as `layer`, and write
`{position, layer, bossEntityId: null, alive: true}`. No periodic check ever asks "does the
path-tip have a boss" — `BossFixture` doesn't know what the path even is.

**"Does an existing record have an actual entity yet?"** — tick-driven, on `BOSS_JIG`'s own
`ScopeEvent.Tick` (not borrowed from `BORDERS_JIG` — see "Module wiring" above). Any record with
`alive: true` and `bossEntityId == null` hasn't been materialized yet. Per "[the central
fact](#the-central-fact-that-shapes-this-whole-design)" above, its `position` usually isn't loaded
the moment the record is created — so this check asks `Level.isLoaded(position)` (a direct boolean
query, not a forced load) and, if true, resolves a ground Y at that exact XZ (can't be done any
earlier — Y needs real block data, which needs the chunk loaded) and spawns the mob there. If not
loaded, no-op and check again next tick — same fixed position, never a new random guess. This is a
normal, expected wait, not an error: it converges the moment a player (or anything else) gets that
column loaded. The same tick also runs the defensive reconciliation check described in "What can
actually go wrong" below — cheap enough to share the cadence, logically separate from
materialization.

**"Is the boss I have on record currently present?"** — a question about live entity state, which
`BossFixture` can't answer on its own (it doesn't know or care whether anything is loaded).
`LevelJig`/`PlayerJig` have no equivalent mechanism to reuse here — `MobJig` accepts a per-consumer
interest supplier ("here are the UUIDs I care about, on this level") and runs a reconciliation
step inside `foundationLifecycle().pulse()` — the same pulse `ServerForgeIngress.onExecutionPulse`
already drives every server tick — checking `Level.getEntity(UUID)` for each supplied UUID.
`Level.getEntity` doesn't require the chunk to already be loaded to safely call it; it's a lookup
against whatever's currently present and simply returns null otherwise, so this can run
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

A `BossRules` interface + `DefaultBossRules` implementation, deliberately mirroring
[Border](border.md)'s existing `BorderRules`/`DefaultBorderRules` split (a "safe baseline, replace
later" strategy object, not a hardcoded algorithm):

- **Position — picked once, immediately, has nothing to do with chunk loading.** A uniform random
  XZ column within the target `Border`'s current `center()`/`radius()` disk (new helper needed —
  `BorderMath` doesn't have one; the same shape `chooseNextCenter()`'s own angle/distance math
  already uses). That's the whole step: ordinary geometry, computed the instant a `BossFixture`
  record is created, no retries, no loaded-check, no dependency on where any player happens to be.
  Y is deliberately *not* resolved here — a chunk's block data isn't readable until the chunk is
  loaded, so there's nothing to resolve yet.
- **Materialization — the one part that has to wait on chunk state, and only this part.** Once
  `Level.isLoaded(position)` (see "Three questions, three different mechanisms" above) comes back
  true for
  a record's stored `position`, resolve a valid ground Y there (surface height, not inside a solid
  block or a liquid — same category of check vanilla natural mob spawning already does), spawn the
  vanilla entity at that point, and write `bossEntityId` onto the existing record. Nothing about
  *where* was ever in question by this point — the XZ was fixed back at "Position" — this step only
  answers *when*.
- **Mob type / stat scaling by the boss's own recorded `layer`** — `BossFixture.layer`, the
  copy-once value set at creation (see "Data model" above), **never** a live `Border.layer()`
  lookup at spawn or materialization time. This is the same guarantee [Border
  Vocabulary](border-vocabulary.md#implementation-trap-worth-flagging-now)'s "implementation trap"
  section warns against reintroducing — stated here explicitly so a reader who jumps straight to
  this section doesn't have to cross-check "Data model" to confirm it. A placeholder table, not a
  locked curve — layer 1 should be [Progression & Frontier
  Mechanics](../design/progression.md#starting-conditions)'s own named example (a rabbit), higher
  layers tougher vanilla mobs with scaled health/damage attributes. Real balance tuning is Game
  Designer/playtest territory once there's something to play, same category as
  `DefaultBorderRules.GROWTH_FACTOR`'s own "safe baseline" framing — this node ships a working
  default, not a final curve.
- **Tagging:** happens in the same step as materialization above, not a separate pass — the entity
  is guaranteed loaded at that exact instant (`isLoaded` just confirmed it), so call
  `MobScope.getFor(mob)` to attach `BossMobFixture` right there, no reason to wait a full poll
  cycle when the reference is already in hand. The `BossFixture` record (now carrying a real
  `bossEntityId`) is the real tag; a lightweight visible marker (custom name, glowing) is worth
  adding too, so "no discovery aids" (Tier 1) still means "findable by looking," not "invisible
  until you already know."

Record creation (Position) and materialization are genuinely different mechanisms now, not two
callers sharing one algorithm — see "Three questions, three different mechanisms" above for
creation's direct-call trigger, and "Defeat detection and the border-growth gap" below for exactly
which call sites need to make that call. Materialization is single and uniform regardless of how a
record was created: `BOSS_JIG`'s own tick checks every unmaterialized record the same way, every
time.

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
  ordinary combat). Handled by [RM_FRO_019](../../../roadmap/RM_FRO_019_karen.md) directly — a
  death can only happen to something currently loaded, so this path is reliable by construction,
  independent of the presence-poll's cadence.
- **A boss is removed by something that *doesn't* fire `LivingDeathEvent`** (external
  world-editing, a datapack/command discard, a bug in an unrelated mod). `BossFixture` would still
  say `alive: true` with a real `bossEntityId` until something notices otherwise. Since the
  presence-poll only tracks entities while a level is actively checking for them, this specific
  case — record says alive, no entity anywhere, and no death event ever fired — needs its own
  explicit reconciliation (an "expected but absent for N consecutive polls" check), not just the
  two mechanisms above by themselves. An open design question, not an assumed answer.
- **A `Border` that entered the level's progression has no matching boss record.** Given creation
  is a direct, paired call at the moment a border is created (see "Defeat detection and the
  border-growth gap" below), this should never legitimately happen — every wired call site creates
  its boss record in the same breath as the border. If it ever does, that's a real data bug (a
  missed call site, a crash between the two calls, manual world editing) — not a normal transient
  state to tolerate the way an unmaterialized record is. A defensive reconciliation check can catch
  it: compare the path's set of `layer()` values against `BossFixture`'s set of `layer` values,
  using that value itself as the correlating key (no live `Border` reference needed, matching
  the decoupling in "Data model" above). Runs on `BOSS_JIG`'s tick alongside materialization, but
  it's a periodic integrity check, not a primary creation mechanism — if it ever finds a mismatch,
  that's worth logging loudly, not silently self-healing without a trace.

## Defeat detection and the border-growth gap

[RM_FRO_019](../../../roadmap/RM_FRO_019_karen.md) owns the `LivingDeathEvent` handler itself; the
one thing worth documenting here is a real gap it found in Border's own surface. Border's public
mutation surface is already trigger-agnostic — `BorderAPI.addBorder()` accepts an arbitrary
center from any caller, not just commands — but that alone isn't sufficient: it doesn't touch
`borderPath`; `BordersPathFacet.grow()` maintains the path but always
computes its own random center via `DefaultBorderRules.chooseNextCenter()`, with no parameter for
"center here instead." Neither alone satisfies
[Progression & Frontier Mechanics](../design/progression.md#the-core-loop)'s "centered on the
defeated boss's home block" requirement while keeping the path consistent.

**Recommended fix, small and targeted:** a new `BordersPathFacet.growCenteredOn(BlockPos center)`
— same two-step shape `grow()` already has (rules-driven proposal, then path append), but taking
an explicit center instead of deferring to `chooseNextCenter()`. Radius and `layer` still come
from the existing rules (`chooseNextRadius()`, `previous.layer() + 1`) — only the center
differs. This keeps "create a border and keep the path consistent" atomic, the same guarantee every
other `BordersPathFacet` mutation already provides, rather than leaving two calls
(`addBorder()` + `PATH.insert()`) for every future "grow to a specific point" caller to remember to
sequence correctly.

**Pairing boss-record creation with border creation — the actual trigger for "Should a boss record
exist at all?" above.** This isn't Border's job and isn't a Border-side hook — Border doesn't know
`BossModule` exists, and that dependency direction (Boss depends on Border, never the reverse) stays
fixed. Instead, whoever *calls* a border-creating operation also calls into `BossModule` right
after, as a sibling step, extracting `position`/`layer` from the `Border` that call just returned:

- **`growCenteredOn(center)`, post-defeat.** The caller is [RM_FRO_019](../../../roadmap/RM_FRO_019_karen.md)'s
  own `LivingDeathEvent` handler — it already calls growth; it just also calls `BossModule`'s
  "create a boss record for this border" right after, in the same handler.
- **`getInitial()`, a level's first border.** Traced: `getInitial()` has exactly one call site in
  the codebase — `BordersPathFacet.grow()`'s own empty-path branch, which already falls through to
  it correctly. The real gap wasn't a hidden call site; it's that nothing currently calls `grow()`
  automatically when a level first loads. See [Border's Known
  gaps](border.md#known-gaps) for the settled fix — a persisted `seeded` flag on `BordersFixture`
  plus a `BorderModule` subscription to Satchel's own `ScopeEvent.Loaded` that calls
  `BorderAPI.grow(level)` for an unseeded level. This record's paired boss-creation call belongs at
  that same hook, right after `grow()` succeeds for a level's very first border.
- **Anything else that calls `grow()`/`addBorder()` directly** (Tier 0's own description mentions
  "an admin command and a debug trigger" as existing callers) only needs the paired call if it's
  meant to produce a real progression border. If it's producing an off-path/debug border, it
  correctly gets no boss by doing nothing extra — consistent with "Data model" above's point that
  not every `Border` needs one.

## Known gaps

- **`getInitial()`'s real call site is now traced, and the bootstrap gap has a settled design** —
  see "Defeat detection and the border-growth gap" above and [Border's Known
  gaps](border.md#known-gaps). Not yet built; when it is, this record's paired boss-creation call
  goes at the same `ScopeEvent.Loaded` hook.
- **`MobInterestSupplier.interestedMobs()`'s exact map-key type is provisional.** Frank shipped it
  typed against `ServerLevel`. [RM_SAT_022](../../../roadmap/RM_SAT_022_roger.md) ("Roger") has
  since settled a side-neutral resolution design (a new `ForgeEgress`, `Level`-keyed) that reverses
  this, not yet built. `BossModule` implements this interface, so write it against whatever Roger
  ships, not against `ServerLevel` — Boss's own `sideApplicability` stays `SERVER` either way;
  defeat detection is server-only regardless of the map's key type.
- **A tracked boss removed by something that never fires `LivingDeathEvent` still needs its own
  reconciliation check** — see "What can actually go wrong" above. An open design question, not an
  assumed answer.

## Related pages

- [Border](border.md) — the data model and module pattern this extends
- [Progression & Frontier Mechanics](../design/progression.md) — the core loop this implements
- [FrontierMode Operational Tiers](../../plans/operational-tiers.md) — Tier 1's definition
- [RM_FRO_018](../../../roadmap/RM_FRO_018_shirley.md) / [RM_FRO_019](../../../roadmap/RM_FRO_019_karen.md) — roadmap trackers
- [RM_SAT_021](../../../roadmap/RM_SAT_021_frank.md) — the `MobJig` prerequisite
