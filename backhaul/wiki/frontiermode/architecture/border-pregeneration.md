---
id: frontiermode/architecture/border-pregeneration
category: frontiermode/architecture
slug: border-pregeneration
title: Border Pregeneration
summary: Proactive, throttled terrain generation for a border's entire disk, owned
  by Border itself -- closes the "chunks are still loading" discovery exploit and
  gives Boss (and future consumers, like a placed Environmental Tell) a real, generated
  footprint to validate against instead of a blind, unchecked coordinate. Partially
  supersedes boss.md's Spawn Algorithm.
keywords: null
status: verified
updated: '2026-09-07'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · frontiermode / architecture
<!-- bh-header:end -->

# Border Pregeneration

Bosses today are placed against a coordinate nobody has checked. [Boss § Spawn
algorithm](boss.md#spawn-algorithm-a-pluggable-strategy-mirroring-borderrules) picks a random XZ
column instantly, resolves a ground Y only once a player happens to load that chunk, and validates
nothing beyond "not solid, not liquid." That's loose enough to spawn a boss in a treetop or on a
cliff edge, gives nothing to anchor a wander radius to, and -- more importantly than either --
means a boss's real location is entangled with which chunks happen to be loaded, which a player
can read as a signal no discovery mechanic ever intended to give them.

This page describes the fix: **Border proactively, throttled-generates a border's entire disk
once triggered to do so**, and everything downstream -- boss placement, boss movement, and any
future consumer needing "this area of the world is real, generated ground" (a placed [Environmental
Tell](discovery-systems.md#beacons-and-particle-trails), for instance) builds on top of real
terrain instead of a blind coordinate. That guarantee stops at generation -- whether the terrain is
*good* ground (flat, hazard-free, whatever a given consumer cares about) is entirely that
consumer's own call, not something Border decides or promises; see "Why this is Border's job, not
Boss's" below. **Pregeneration is an explicit action, not automatic on border creation** -- a
consumer creates the border, then separately triggers pregeneration for it; see
"`BorderPregenFixture`" below for why, and what that means for a border nobody ever triggers it
for.

**Minted and built as [RM_FRO_028](../../../roadmap/RM_FRO_028_diane.md) ("Diane") -- resolved,
done bar met, playtest-verified.** Started life the same way [Border
Curve](border-curve.md) and [Boss Discovery Systems](discovery-systems.md) did: worked out in the
wiki ahead of minting any `RM_FRO` node, because getting the shape wrong before it's in the graph
is the expensive mistake (see [BHRM — Persona names are not
reusable](../../meta/bhrm.md#persona-names-are-not-reusable)). It also **partially supersedes**
[Boss § Spawn algorithm](boss.md#spawn-algorithm-a-pluggable-strategy-mirroring-borderrules) --
see "What this changes in Boss" below for exactly which parts, since that page carries a
`verified` status this one doesn't disturb lightly. Four items from this page's own Open
Questions were carried forward rather than resolved before minting -- now tracked as
[RM_FRO_035](../../../roadmap/RM_FRO_035_donna-03.md) ("Donna_03"); see the Open Questions
section below for the current, corrected status of each.

## Why this is Border's job, not Boss's

Boss depends on Border; Border doesn't know Boss exists, and that direction is load-bearing
elsewhere in this design (see [Boss Discovery Systems § Navigation lives in
Border](discovery-systems.md#navigation-lives-in-border)'s own version of the same argument).
Pregeneration is fundamentally about a border's own footprint existing in the world, independent
of what -- if anything -- ever gets placed inside it. Boss is the first real consumer, but it
won't be the last: [Boss Discovery Systems](discovery-systems.md) already anticipates a placed,
findable Environmental Tell (a platform, or eventually a beacon/trail) that needs exactly the same
guarantee -- real, generated ground to build its own validation against -- with no boss-specific
reasoning involved at all. **That's a guarantee about generation only, not suitability, and
deliberately so:** what counts as "good enough" ground is consumer-specific (Boss's own flatness/
hazard scoring may not be what an Environmental Tell needs), so Border can't own that judgment
without either baking Boss's own rules into a module that doesn't know Boss exists, or inventing a
generic check that might not fit the next consumer either. Housing this on Boss would mean every
future consumer either depends on Boss for something that has nothing to do with bosses, or
reinvents its own copy. Housing it on Border means Boss reads a capability Border already offers
for its own reasons, the same shape `TargetRef`/Navigator's resolver registry already
established.

## The exploit this closes

This isn't only a performance choice. The entire premise of [Boss
Discovery](../design/boss-discovery.md) is that finding a boss should only ever leak information
through designed signals -- guardian density, environmental tells, and so on. A differential
chunk-load pattern ("the game hitches when I walk this direction") is an accidental, engine-level
tell that defeats that premise for free, with no guardian mobs or tells required. Generating a
border's entire disk uniformly, ahead of any player approaching it, means there is no "which
direction is still loading" signal to read in the first place. That's worth paying for even where
most of a large, late-game border's disk goes unwalked -- it isn't wasted work the way one rejected
placement candidate would be (see the "chunk waste" discussion this page's design conversation
worked through); it's terrain the player was always going to generate by walking there eventually,
just generated on Border's schedule instead of the player's.

## `BorderPregenFixture`

A fourth sibling fixture in `BordersBundle`, alongside `BordersFixture`, `NavigatorFixture`, and
`BorderCurveFixture` -- riding the bundle's already-correct tick wiring for free, the same
reasoning [Navigator](discovery-systems.md#navigation-lives-in-border) and [Border
Curve](border-curve.md#why-a-sibling-fixture-not-a-field-on-border) both used to justify their own
placement. Worth naming the difference in *why* it graduates to a fixture, though: `BorderCurve`
needed one because of real multiplicity (0-to-many curves per border). This one doesn't have a
multiplicity problem -- one pregeneration job per border -- but it has the other legitimate reason
to leave `BordersFixture`'s own schema alone: the state is job-shaped (an in-progress frontier, a
throttle position), not a simple fact about the border, and doesn't belong bolted onto Border's
core save format.

### Starting a border's pregeneration is an explicit call, not automatic

Border creation and pregeneration are two separate actions. Something like
`BorderAPI.startPregeneration(Level, UUID borderId)` (name illustrative, Lead Dev's call) is
invoked by whatever consumer code wants a border's disk pregenerated -- the same "direct call,
paired with whatever code just created it" shape
[Boss](boss.md#three-questions-three-different-mechanisms) already establishes for its own record
creation. The path-growth code that creates a new path border and its matching boss record makes
this a third call alongside those two, at the same call site.

**This settles what happens to a border nobody ever calls it for:** an off-path or debug border
(created some other way -- a debug trigger, a directly issued admin command) simply never gets
pregenerated, because nothing pairs the trigger with those call sites -- the identical,
already-accepted shape [Boss § Data
model](boss.md#data-model-a-persisted-record-and-a-live-view-not-two-peers) uses for why an
off-path border correctly gets no boss record either. No record in `BorderPregenFixture` at all
means "never triggered" -- a third state, distinct from "triggered, still in progress" and
"complete." `isReadyFor(UUID borderId)` returns false for all three; a caller can't distinguish
"not started" from "in progress" through that query alone, and doesn't need to for Boss's own
purposes -- its tick either way just no-ops and rechecks next tick.

### Persisted state stays small

Chunk generation is idempotent -- Minecraft already knows whether a given chunk has been
generated, so that isn't state this fixture needs to duplicate. Per border, the durable record is
plausibly just `{borderId: UUID, complete: boolean, cursor: int}` -- `cursor` an index into some
deterministic enumeration of the disk's chunk coordinates (spiraling out from center is one
option), so a throttled pass resumes exactly where it left off, including across a server restart.
Once `complete` flips true, `cursor` stops mattering and is never revisited. A restart mid-pregen
is a normal state, not a failure case, by the same logic Boss's own materialization poll already
leans on for unloaded chunks.

### Reused, not reinvented: `TickThrottler`

`com.arryn.satchel.common.util.throttle.TickThrottler` already exists in Satchel and already does
exactly the pacing this needs -- an elapsed-based gate ("allow this action only once every N
ticks"), currently used for `SatchelFixture`'s own trace-log rate-limiting but general-purpose,
not log-specific. `BorderPregenFixture.onJigTick()` wraps one instance and, each time it's
allowed, generates a small, fixed batch of the current in-progress border's remaining chunks
before yielding back -- the same "spread real work across many ticks instead of blocking" pattern
server-side world-pregeneration tools already use safely today. No new pacing mechanism needed,
just an application of one that was already sitting in the framework, evidently built ahead of
having a caller.

### Signaling completion: a `BorderPregenEvent.Complete`, alongside the poll

The poll (`isReadyFor`/`BorderAPI.isPregenReady()`, below) is the durable, re-checkable ground
truth -- any consumer, at any time, gets a correct answer from it, including one that starts
existing after a border's disk already finished generating. But it forces every consumer onto
Boss's own "no-op and recheck next tick" cadence even when nothing else about that consumer needs
to poll at all. Satchel already has a real, closed precedent for exactly this shape: [Mob
Lifecycle Signals](../../satchel/architecture/mob-lifecycle-signals.md)'s `MobDied` -- a
standalone event, not a `ScopeEvent` subtype, posted to `SatchelEventBus` and subscribed to via
the ordinary `EventHandlers.on(EventClass.class, handler)` shape. `BorderPregenEvent.Complete`
follows the same pattern for the same underlying reason `MobDied` isn't a `ScopeEvent`: pregen
completion is a per-border-record fact, not a per-jig-scope one, and `BorderPregenFixture` holds
one record per border in the path, not one fixture instance per border -- the same multiplicity
mismatch "`isReady()`: the base contract" below works through for the poll side of this same
question.

`BorderPregenFixture.onJigTick()` posts `BorderPregenEvent.Complete(Level, UUID borderId)`
(name/fields illustrative, Lead Dev's call) exactly once per border, in the same tick its
persisted `complete` flag flips true -- flip state, then signal, the same order
`ScopeLifecycleDispatcher` already uses for `ScopeEvent.Loaded` (see [Satchel: Forge Event to
ScopeEvent](../../reference/diagrams/satchel-forge-to-scopeevent.md)). Boss (and eventually an
Environmental Tell) subscribes with `EventHandlers.on(BorderPregenEvent.Complete.class,
handler)`; the handler checks `event.borderId()` against its own record's `borderId` and no-ops
if it doesn't match -- the same self-check discipline `MobDied`'s bare, unscoped post already
established as correct, not sloppy, for this codebase.

**Unlike `MobDied`, no interest-registry gate is needed before posting.** `MobDied` gates on a
registry because the event it wraps -- `LivingDeathEvent` -- fires for every mob death in the
world, the overwhelming majority irrelevant; gating avoids constructing and posting noise.
`BorderPregenEvent.Complete` has no such volume problem: a border's disk finishes pregenerating
once, a rare and already-deliberate occurrence, so posting unconditionally on every real
completion is cheap and correct as-is.

This is deliberately *not* a replacement for the poll, only a faster path to it: a subscriber
that's already listening reacts the instant generation finishes instead of waiting for its own
next tick to notice, while the poll stays correct as the fallback for a subscriber that starts
listening late, misses the event, or never subscribes at all -- the same "signal is a
notification, state is the ground truth" split this design already leans on for
`isReady()`/`isReadyFor()` vs. `ScopeEvent`. The worked example below still describes the poll as
the mechanism Boss depends on for correctness; the event is the optimization once subscribed, not
a second source of truth.

### `isReady()`: the base contract, unmodified — and a new query on top

Worth being precise here, correcting something said too loosely earlier in this same design
conversation. `SatchelFixture.isReady()` is a no-argument, whole-fixture question -- "has this
fixture instance hydrated and is it safe to use at all" -- identical for every fixture in the
codebase, `BorderPregenFixture` included, and it needs no override: the base `onLoaded()`
implementation already flips it true once hydration completes, which is all "safe to query at
all" ever meant. It can't answer a per-border question on its own, since this fixture holds one
record per border in the path, not one fixture instance per border -- same shape `BorderCurve`
already has for its own multiplicity. So the actual business question -- "has *this* border's disk
finished generating" -- is a new, fixture-specific query method: `isReadyFor(UUID borderId)`,
reading the per-border record described above. A caller checks the base `isReady()` first (is the
fixture itself safe to query at all) and then `isReadyFor(borderId)` (is this specific border
done) -- two different questions at two different granularities, not one overridden method
answering both.

The fixture-level `isReady()`/`onJigTick()` documentation gap this design surfaced elsewhere in
Satchel's own wiki is now closed -- see `fixture.md`'s [Per-Fixture Lifecycle API
Reference](../../satchel/architecture/fixture.md#boolean-isready), added via
[RM_SAT_024](../../../roadmap/RM_SAT_024_raymond-01.md)/SAT_043.

## `BorderAPI`'s new query surface

Boss and Border are separate jigs (`BOSS_JIG`/`BORDERS_JIG`), so this is a genuine cross-module
read, not two fixtures in the same bundle checking each other. Consistent with "Boss depends on
Border, never the reverse," this surfaces as a new method on `BorderAPI` -- something like
`BorderAPI.isPregenReady(Level, UUID borderId)` -- that internally does both checks above and
hands back a single boolean. Boss's tick code (and eventually an Environmental Tell's own tick
code) calls this rather than reaching into `BordersBundle`'s fixtures directly, the same discipline
every other cross-module read in this design already follows. A `BorderPregenEvent.Complete`
signal exists alongside this query for a subscriber that wants to react immediately rather than
poll -- see "Signaling completion" above; the query stays the correctness fallback either way.

## Worked example: Boss placement, revised

- **Position — still geometry, now gated.** The candidate-selection step still samples within the
  target `Border`'s disk via `BorderAPI.MATH.randomPointInDisk` (routed through `BorderAPI`'s
  delegating math surface per [FRO_078](../../../tickets/FRO_078_bordermath-to-api.md), closed
  2026-09-03 -- `BorderMath` itself stays the real implementation and stays public, but every
  cross-module caller now goes through `BorderAPI.MATH`; this page's wording just hadn't caught up
  until now), but Boss's tick handler no-ops on a
  record whose position isn't finalized until `BorderAPI.isPregenReady()` returns true for its
  home border (or it reacts immediately via `BorderPregenEvent.Complete` if already subscribed --
  see "Signaling completion" above) -- the identical no-op-and-recheck-next-tick shape the existing
  materialization poll already uses for `Level.isLoaded()`. Once ready, the whole disk is already real, generated
  terrain, so checking several chunk-center candidates against `BossRules`/`DefaultBossRules`'s new
  flatness and hazard scores (each a 0-1 tunable, "safe baseline, replace later" matching every
  other pluggable strategy in this codebase) costs nothing extra per candidate and leaves nothing
  wasted behind a rejected one -- unlike generating a chunk purely to test it, this data already
  existed for other reasons. A footprint wider than the exact spawn point -- matching the wander
  radius below -- gets checked too, so a boss doesn't spawn on validated ground and immediately
  wander into unchecked terrain.
- **Materialization — simplifies back down.** Since flatness and hazard are settled before a
  position is ever committed, materialization returns to being purely "wait for a player to load
  this already-known-good chunk, then spawn the entity." The in-place Y-resolution step and the
  liquid-column no-op case both disappear -- neither is possible once nothing gets committed as a
  position unless it already passed validation against real terrain.
- **Movement — bounded wander, with a leash home.** Genuinely new: a bounded wander AI (a small
  random-stroll radius around the validated spawn point) replaces the current "spawns, then
  whatever vanilla AI does" behavior. This is exactly the future constraint [Border Curve §
  Anchor: border-centered, not boss-position](border-curve.md#anchor-border-centered-not-boss-position)
  flagged and deferred -- `BorderCurve` evaluates against the border's own center/radius specifically
  *because* bosses don't move today. A max leash radius that teleports the boss home if exceeded is
  what keeps that anchor assumption valid once movement is real, rather than letting it quietly go
  stale the first time a boss wanders off-center.

## What this changes in Boss

[Boss § Spawn algorithm](boss.md#spawn-algorithm-a-pluggable-strategy-mirroring-borderrules)'s
**Position** and **Materialization** bullets get trimmed to a short pointer here rather than fully
restating the above -- the "Mob type / stat scaling" and "Tagging" bullets are untouched, since
neither depends on any of this. The section's framing changes from "Position is instant and has
nothing to do with chunk loading" to "Position waits on this page's pregeneration guarantee before
finalizing" -- a real change to a page marked `verified`, made deliberately rather than by drift,
which is why it's called out here explicitly rather than silently landing as an edit.

## Open questions

All items below are now resolved via
[FRO_091](../../../tickets/FRO_091_pregen-carryforward-spec.md), the Architect spec-review ticket
for [RM_FRO_035](../../../roadmap/RM_FRO_035_donna-03.md) ("Donna_03"). Four were formally carried
forward at RM_FRO_035's own mint time, gating all six Tier 2 discovery-gradient siblings (Guardian
Mobs through Player-built Warps); `isReady()`'s documentation gap had already resolved separately
(RM_SAT_024/SAT_043, noted below); and the `BorderPregenEvent.Complete` registration-discipline
question below had gone untracked by both lists entirely -- caught during FRO_091's review and
folded into its scope.
[FRO_092](../../../tickets/FRO_092_border-pregen-carryforward-build.md) is the Lead Dev build
ticket covering the throttle value and the stalled-trigger watchdog. Two of its built items (the
stalled-trigger check and the disk-validation-failure case, both below) landed as a hard crash
rather than the log-only warning originally ruled here -- simplified with the project owner
mid-build; see FRO_092's own log for the full detail.

- **Retry/reroll if an entire disk somehow fails validation -- resolved: no mechanism,
  accepted risk.** [FRO_091](../../../tickets/FRO_091_pregen-carryforward-spec.md) ruled this an
  accepted-risk case rather than a build item: vanishingly unlikely once searching across a whole
  generated border instead of one blind candidate, and not worth a retry/reroll mechanism against
  a failure mode nobody's actually hit. **Built ([FRO_092](../../../tickets/FRO_092_border-pregen-carryforward-build.md))
  as a hard crash, not a log-only warning** -- simplified with the project owner mid-build from
  this page's original log-and-continue ruling: `runBatch()` re-verifies `FULL` chunk status
  immediately after each forced `Level.getChunk()` call and throws, naming the border, chunk
  coordinates, and cursor, if it didn't land. A pathological seed or extreme biome now surfaces as
  an immediate, uncaught crash rather than a warning line to notice later.
- **Whether `BorderPregenEvent.Complete` needs `JigConfigValidator`-style registration
  discipline -- resolved: no, fine as-is.** Folded into
  [FRO_091](../../../tickets/FRO_091_pregen-carryforward-spec.md)'s scope after being caught
  untracked by both Diane's own carryforward list and RM_FRO_035. The ruling follows the precedent
  this event already claims for itself: `BorderPregenEvent.Complete` is explicitly modeled on
  `MobDied` (see [Mob Lifecycle Signals](../../satchel/architecture/mob-lifecycle-signals.md)),
  whose own producer sits outside `JigConfigValidator`'s compile-time validation path entirely and
  is accepted as correct, not sloppy. An ad hoc bus post from inside `onJigTick()` gets the same
  treatment -- no additional registration discipline needed.
- **Exact throttle budget -- resolved: a `Rules`-level tunable, not a hardcoded
  constant.** [FRO_091](../../../tickets/FRO_091_pregen-carryforward-spec.md) rules this the same
  category as `DefaultBorderRules.GROWTH_FACTOR`'s own "safe baseline, replace later" framing --
  chunks-per-allowed-tick and `TickThrottler`'s own interval live as a `BorderRules`/
  `DefaultBorderRules` coefficient, tunable without a code change. The actual number is Lead Dev's
  call at build time, not an architecture decision.
- **`isReady()`'s fixture-level contract is under-documented on the Satchel side -- resolved.**
  `fixture.md` now has a full [Per-Fixture Lifecycle API
  Reference](../../satchel/architecture/fixture.md#boolean-isready) covering `onCreated`,
  `onLoaded`, `onRemoved`, `onJigTick()`, and `isReady()`, added via
  [RM_SAT_024](../../../roadmap/RM_SAT_024_raymond-01.md) ("Raymond epoch maintenance 1")/SAT_043.
  No longer an open item; kept here as a marker so it isn't re-carried by mistake.
- **A border-creation call site that forgets the pregeneration trigger stalls its boss
  permanently, silently -- resolved: reuse Satchel mechanisms, not a new watchdog module.**
  [FRO_091](../../../tickets/FRO_091_pregen-carryforward-spec.md) corrects this page's own earlier
  guess (a new `Rules`-level watchdog module): no new module is needed. `BorderPregenFixture`
  already ticks via its own `onJigTick()` and already owns a `TickThrottler` instance for pacing
  chunk generation (see "Reused, not reinvented" above); the fix is a second, longer-interval
  `TickThrottler` in that same fixture. **Built ([FRO_092](../../../tickets/FRO_092_border-pregen-carryforward-build.md))
  as a liveness check, not a predicted-duration estimate, and as a hard crash, not a log-only
  warning** -- both simplified with the project owner mid-build from this page's original ruling:
  at a 200-tick interval, the second throttler compares an in-progress job's `cursor` against its
  own last-checked value and throws, naming the border and cursor, if it hasn't moved --
  deliberately disk-size-agnostic, no `BorderRules` involvement, no elapsed-duration estimate to
  get wrong. No new event (no `BorderPregenEvent.Stalled` sibling to `Complete`) -- a thrown
  exception surfaces the failure directly, so there's no separate signal left to design.
- **What a level does with a border that already existed before this mechanism shipped --
  resolved: moot, not a real scenario right now.** [FRO_091](../../../tickets/FRO_091_pregen-carryforward-spec.md)
  rules there's no old-world compatibility requirement to design against at this stage of the
  project -- worlds get reset for testing, and FrontierMode isn't playably compatible with old
  worlds regardless of this mechanism. Neither retroactive pregeneration nor an explicit
  grandfathering policy is worth building or codifying now; revisit if and when old-world
  compatibility becomes a real requirement.

## Related pages

- [Boss](boss.md) -- the Spawn Algorithm section this page partially supersedes, and
  `BossRules`/`DefaultBossRules`'s existing pluggable-strategy precedent
- [Border](border.md) -- `BordersBundle`'s existing shape and `BorderMath`'s geometry primitives
  this design reuses
- [Border Curve](border-curve.md) -- the sibling fixture this page's placement precedent follows,
  and the anchor assumption this page's wander/leash mechanism keeps valid
- [Boss Discovery Systems](discovery-systems.md) -- the discovery-gradient design this page's
  exploit-prevention argument protects, and the Environmental Tells platform (or eventually a
  beacon/trail) that will want the same guarantee this page provides
- [Mob Lifecycle Signals](../../satchel/architecture/mob-lifecycle-signals.md) -- `MobDied`, the
  real, closed precedent `BorderPregenEvent.Complete` follows for being a standalone event rather
  than a `ScopeEvent`
- [Boss Discovery](../design/boss-discovery.md) -- the design intent behind "no accidental
  discovery signals"
- [Donna Epoch Nodes](../../plans/donna-epoch-nodes.md) -- this cluster's staging history
- [RM_FRO_028](../../../roadmap/RM_FRO_028_diane.md) ("Diane") -- this page's own real, resolved
  roadmap node
- [RM_FRO_035](../../../roadmap/RM_FRO_035_donna-03.md) ("Donna_03") -- where this page's carried-
  forward Open Questions are formally tracked
- [RM_FRO_023](../../../roadmap/RM_FRO_023_kathleen.md) ("Kathleen") -- the convergence this
  cluster's real nodes fold into
