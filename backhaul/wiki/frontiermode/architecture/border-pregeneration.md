---
id: frontiermode/architecture/border-pregeneration
category: frontiermode/architecture
slug: border-pregeneration
title: Border Pregeneration
summary: Proactive, throttled terrain generation for a border's entire disk, owned
  by Border itself -- closes the "chunks are still loading" discovery exploit and
  gives Boss (and future consumers, like a placed Environmental Tell) real validated
  terrain to build on instead of a blind, unchecked coordinate. Partially supersedes
  boss.md's Spawn Algorithm.
keywords: null
status: verified
updated: '2026-08-31'
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
future consumer of "this area of the world is real and validated" (a placed [Environmental
Tell](discovery-systems.md#beacons-and-particle-trails), for instance) builds on top of real
terrain instead of a blind coordinate. **Pregeneration is an explicit action, not automatic on
border creation** -- a consumer creates the border, then separately triggers pregeneration for it;
see "`BorderPregenFixture`" below for why, and what that means for a border nobody ever triggers
it for.

**Everything on this page is proposal, not ruling** -- same status as [Border
Curve](border-curve.md) and [Boss Discovery Systems](discovery-systems.md) before it: worked out
in the wiki ahead of minting any `RM_FRO` node, because getting the shape wrong before it's in the
graph is the expensive mistake (see [BHRM — Persona names are not
reusable](../../meta/bhrm.md#persona-names-are-not-reusable)). It also **partially supersedes**
[Boss § Spawn algorithm](boss.md#spawn-algorithm-a-pluggable-strategy-mirroring-borderrules) --
see "What this changes in Boss" below for exactly which parts, since that page carries a
`verified` status this one doesn't disturb lightly.

## Why this is Border's job, not Boss's

Boss depends on Border; Border doesn't know Boss exists, and that direction is load-bearing
elsewhere in this design (see [Boss Discovery Systems § Navigation lives in
Border](discovery-systems.md#navigation-lives-in-border)'s own version of the same argument).
Pregeneration is fundamentally about a border's own footprint existing in the world, independent
of what -- if anything -- ever gets placed inside it. Boss is the first real consumer, but it
won't be the last: [Boss Discovery Systems](discovery-systems.md) already anticipates a placed,
findable Environmental Tell (a platform, or eventually a beacon/trail) that needs exactly the same
guarantee -- real, validated ground to sit on -- with no boss-specific reasoning involved at all.
Housing this on Boss would mean every future consumer either depends on Boss for something that
has nothing to do with bosses, or reinvents its own copy. Housing it on Border means Boss reads a
capability Border already offers for its own reasons, the same shape `TargetRef`/Navigator's
resolver registry already established.

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

## `BorderAPI`'s new query surface

Boss and Border are separate jigs (`BOSS_JIG`/`BORDERS_JIG`), so this is a genuine cross-module
read, not two fixtures in the same bundle checking each other. Consistent with "Boss depends on
Border, never the reverse," this surfaces as a new method on `BorderAPI` -- something like
`BorderAPI.isPregenReady(Level, UUID borderId)` -- that internally does both checks above and
hands back a single boolean. Boss's tick code (and eventually a Static Tell's own tick code) calls
this rather than reaching into `BordersBundle`'s fixtures directly, the same discipline every other
cross-module read in this design already follows.

## Worked example: Boss placement, revised

- **Position — still geometry, now gated.** The candidate-selection step still samples within the
  target `Border`'s disk via `BorderMath.randomPointInDisk`, but Boss's tick handler no-ops on a
  record whose position isn't finalized until `BorderAPI.isPregenReady()` returns true for its
  home border -- the identical no-op-and-recheck-next-tick shape the existing materialization poll
  already uses for `Level.isLoaded()`. Once ready, the whole disk is already real, generated
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

- **Retry/reroll if an entire disk somehow fails validation** -- vanishingly unlikely once
  searching across a whole generated border instead of one blind candidate, but not provably
  impossible (an extreme biome, a pathological seed). Not resolved here; flagged so it isn't
  assumed away.
- **Exact throttle budget** (chunks per allowed tick, `TickThrottler`'s own interval) -- a tuning
  number, not an architecture decision; same category as `DefaultBorderRules.GROWTH_FACTOR`'s own
  "safe baseline" framing.
- **`isReady()`'s fixture-level contract is under-documented on the Satchel side** -- `fixture.md`
  covers `onCreated`/`onLoaded` but not `isReady()` or `onJigTick()`, which this design leans on
  directly. This is a real gap in Satchel's own wiki, not a design error here -- now tracked as
  [RM_SAT_024](../../../roadmap/RM_SAT_024_raymond-01.md) ("Raymond epoch maintenance 1") on
  Satchel's own roadmap; out of scope for this page to fix.
- **Punted: a border-creation call site that forgets the pregeneration trigger stalls its boss
  permanently, silently.** The two-call design (create the border, then separately trigger
  pregeneration -- see "Starting a border's pregeneration is an explicit call" above) means a
  missed second call leaves `isReadyFor()` false forever, and Boss's position-finalization tick
  just no-ops on that record indefinitely with no error surfaced anywhere. Not fixed here --
  stricter call-site validation isn't worth designing against a hypothetical missed call right
  now. The likely eventual answer is a `Rules`-level watchdog (mirroring `BossRules`/
  `DefaultBorderRules`'s own pluggable-strategy shape) that periodically scans for exactly this
  kind of stuck-forever state -- a record stalled well past any reasonable pregen duration -- and
  surfaces it loudly to an admin instead of failing silently. Not this page's mechanism to design
  now; noted so the idea isn't lost.
- **What a level does with a border that already existed before this mechanism shipped** -- an
  already-progressed world has path borders with bosses long since placed the old way. Whether
  those get retroactively pregenerated or are simply grandfathered in is unresolved.

## Related pages

- [Boss](boss.md) -- the Spawn Algorithm section this page partially supersedes, and
  `BossRules`/`DefaultBossRules`'s existing pluggable-strategy precedent
- [Border](border.md) -- `BordersBundle`'s existing shape and `BorderMath`'s geometry primitives
  this design reuses
- [Border Curve](border-curve.md) -- the sibling fixture this page's placement precedent follows,
  and the anchor assumption this page's wander/leash mechanism keeps valid
- [Boss Discovery Systems](discovery-systems.md) -- the discovery-gradient design this page's
  exploit-prevention argument protects, and the Static Tell (beacons/platform) that will want the
  same guarantee this page provides
- [Boss Discovery](../design/boss-discovery.md) -- the design intent behind "no accidental
  discovery signals"
- [Donna Epoch Nodes](../../plans/donna-epoch-nodes.md) -- where this becomes a tracked candidate
  once it's ready to mint, alongside Navigator and Border Curve
- [RM_FRO_023](../../../roadmap/RM_FRO_023_kathleen.md) ("Kathleen") -- the convergence this
  cluster's real nodes will fold into
