---
id: frontiermode/architecture/discovery-systems
category: frontiermode/architecture
slug: discovery-systems
title: Boss Discovery Systems
summary: Technical shape for Tier 2's discovery-gradient tools (guardian mobs, tells,
  beacons, tracker, compass, warps) and the navigation/attunement mechanism, hosted
  in Border, that most of them build on -- proposal stage, drafted ahead of minting
  RM_FRO_023's real intermediate nodes.
keywords: null
status: draft
updated: '2026-08-31'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · frontiermode / architecture
<!-- bh-header:end -->

# Boss Discovery Systems

The architecture counterpart to [Boss Discovery](../design/boss-discovery.md) and [Guardian
Mobs](../design/guardian-mobs.md) -- what those two design pages describe as player experience,
this page works out as data model and module shape, for [FrontierMode Operational
Tiers](../../plans/operational-tiers.md) Tier 2 ("Guided loop operational"). Feeds
[RM_FRO_023](../../../roadmap/RM_FRO_023_kathleen.md) ("Kathleen").

**Everything on this page is proposal, not ruling.** No `RM_FRO` nodes exist for any of it yet --
this is deliberately being worked out in the wiki first, precisely because minting a roadmap node
burns a persona name permanently (see [BHRM — Roadmap Conventions § Persona names are not
reusable](../../meta/bhrm.md#persona-names-are-not-reusable)) and this cluster has enough open
questions that getting the shape wrong before committing it to the graph is the expensive mistake
to avoid. Treat every section below as a working draft.

## Navigation lives in Border

Five of [Boss Discovery](../design/boss-discovery.md#the-discovery-gradient)'s six tools --
guardian mobs, environmental tells, beacons/trails, the ender-eye-style tracker, and the compass --
all reduce to some form of the same question: *how far is a given point from a target that matters
for discovery, and which direction is it?* **Settled: this capability is built inside the Border
module, not a standalone module.** Two reasons: standing up a genuinely new Satchel module carries
real fixed overhead (see [New Module Checklist](../../satchel/architecture/new-module-checklist.md)'s
own list of footguns every new module consumer hits), and the underlying point-to-point geometry
this needs -- nearest-of, direction-between -- is plausibly the same math Border's own services
already want (`chooseNextCenter()`'s angle/distance work, `BorderMath.randomPointInDisk`,
`BorderMath.isInside`). Housing both in one place means one math surface, not two.

**This does not require Border to know Boss exists.** A Navigator only needs to carry an opaque
target reference -- it never has to interpret what that reference means. Resolution happens
through a registry, the same shape `MobInterestRegistry.register(FrontierKeys.BOSS_MOB_JIG, () ->
INTERESTS)` already establishes in [Boss § Module wiring](boss.md#module-wiring): whatever module
owns a target type registers its own resolver at init time (`BossModule.init()` registering a
resolver for `TargetType.BOSS`, say), and Navigator code looks the resolver up by tag without ever
importing Boss types. This is the same "reference by ID, resolve on demand" discipline
`BossFixture` already uses for its own decoupling from Border -- applied here so that Border can
host a generic pointing mechanism without absorbing a compile-time dependency on everything it
might ever point at.

**Proposed shape:**

- **`TargetRef`** -- a small tagged union: `Boss(UUID) | Border(UUID) | Structure(...) |
  RawPos(BlockPos) | Dynamic(Supplier<BlockPos>)`. `RawPos` is the escape hatch (and plausibly what
  [Player-built Warps](#player-built-warps) turns out to want -- a warp doesn't need a *live* target
  at all, just a remembered position, which is `RawPos` with nothing else added). `Dynamic` is a
  different kind of escape hatch, added for [Special Compass](#special-compass)'s tip-pointing case:
  it bypasses the resolver registry entirely (Navigator calls the embedded supplier directly rather
  than dispatching through `TargetType -> resolver`), and by construction cannot be serialized --
  see "Special Compass" below for why that pushes persistence onto the consumer instead of onto
  `TargetRef` itself.
- **`BorderMath` gains exactly two new methods** -- `distanceTo(BlockPos a, BlockPos b)` and
  `direction(BlockPos a, BlockPos b)`. Plain two-point primitives; no player, target-type, or
  attunement awareness at all. **Deliberately no `nearest()` method:** "nearest of N candidates" is
  just `candidates.stream().min(Comparator.comparingDouble(c -> distanceTo(from, c)))`, and the
  candidate list is always pre-filtered by consumer-specific "relevant" logic first (Guardian Mobs'
  notion of a relevant boss isn't the Tracker's) -- that filter has to live at the call site
  regardless, since `BorderMath` has no business knowing what "relevant" means for a given consumer.
  Folding a one-line `min` into a filter loop that already has to exist costs nothing; a shared
  `nearest()` would only paper over call sites that still need their own logic anyway.
- **A target-resolver registry**, `TargetType -> (UUID -> BlockPos)`, populated by whichever module
  owns that type, mirroring `MobInterestRegistry`'s existing registration shape.

**Proposed placement: a `NavigatorFixture`, registered as a sibling fixture inside the existing
`BordersBundle` -- not merged into `BordersFixture`'s own schema, and not a wholly separate
bundle.** [Boss § Module wiring](boss.md#module-wiring)'s `JigConfigValidator` already requires a
`FixtureDecl -> BundleDecl -> Schema` chain "regardless of how many fixtures a bundle ends up
holding" -- a bundle hosting more than one fixture is an already-precedented shape, not a new one:
this cluster's own designs now put four sibling fixtures in `BordersBundle` and three in
`BossBundle`.

**Correction to this page's own earlier framing:** an earlier draft treated "one bundle per
concern" as if it meant "one fixture per bundle," and cited an open [RM_FRO_024](../../../roadmap/RM_FRO_024_donna-01.md)
review as something Navigator's placement needed to survive. **There never was a
one-fixture-per-bundle rule.** `BossFixture` and `BordersFixture` each starting out alone in their
own bundles was coincidental -- a fact about when each was built, not a constraint on how many
fixtures a bundle may hold -- and nothing about that history bears on Navigator sharing a bundle
with Border's. The only thing genuinely still open on RM_FRO_024 is a separate question: whether
`BossFixture` and `BossMobFixture` are one concern or two, i.e. whether *they* should share a
bundle -- unrelated to Navigator's placement here.

Riding inside `BordersBundle` means Navigator inherits `BORDERS_JIG`'s already-correct tick/pulse
wiring for free, instead of standing up a second `JigConfig` and re-earning the `withTick(true)` +
`policies().persistence(...)` pairing [Boss](boss.md) already paid for once (see [Boss § What can go
wrong](boss.md#what-can-go-wrong)) -- while keeping Navigator's own schema separate from Border's
core save format, since it's a sibling fixture, not a field bolted onto `BordersFixture`.

**Other infrastructure this can reuse rather than reinvent:** `BorderSelector`/
`BorderSelectorArgumentType` plus the `Commands`/`CommandHandler` registration split (see [Boss
Commands](boss-commands.md)) for whatever admin commands Navigator eventually needs; Boss's lighter
boolean/`Optional`-return validation idiom (reject -> `OUT.warn` -> a "nothing happened" value,
rather than throwing) for attunement mutation validation; and the `BorderDisplay`/`BossDisplay`
presentation-only formatting pattern for however Navigator state gets shown to a player. One real
gap, not a reuse: there's no existing item-NBT-wrapper infrastructure to build the attunement
wrapper from (see "Attunement" below) -- it would be the first concrete built instance of [Universal
Sidedness Facade](../../satchel/architecture/facade-vision.md)'s argument, not an application of
something already proven. **No longer purely hypothetical:** [Special Compass](#special-compass)
below now designs this wrapper concretely, against a real existing consumer (`BorderPathCompass`)
rather than in the abstract.

**Worth noting: this whole mechanism has no dependency on `PlayerJig`/`PlayerScope`
([RM_SAT_020](../../../roadmap/RM_SAT_020_jerry.md), not yet rebuilt).** Navigator's
target-resolver registry and `LevelScope`-hosted attunement records don't need per-player
identity-tied storage the way Border's own official compass-attunement concept does -- so this
cluster could ship independently of that Satchel work landing, unlike
[RM_FRO_006](../../../roadmap/RM_FRO_006_sandra.md)'s own compass.

## Attunement: item state vs. world state

**Not one mechanism -- two, with different lifecycles, and this is still genuinely in flux; the
project owner is planning a further pass with Game Designer before any of it is game-rule-final.**
What follows is the structural split, not the attunement rules themselves.

- **Item-carried attunement** (a compass) -- lives in the item's own `ItemStack` NBT, not a Satchel
  fixture. Two compasses are two stacks with two independent tags -- this is what actually makes
  "two compasses, two locations" work, the same way vanilla's lodestone compass already carries its
  target in its own tag. No Scope, no registration needed for the storage itself. **Still worth a
  thin managed wrapper around that NBT access, not raw `ItemStack.getOrCreateTag()` calls scattered
  through item-use handlers** -- this is precisely the kind of touch point [Universal Sidedness
  Facade](../../satchel/architecture/facade-vision.md) already argues should be guarded rather than
  left as unmanaged ingress into Forge/MC APIs, even where the underlying persistence is just NBT
  the wrapper doesn't own.
- **Process/world-anchored attunement** (something with no item carrying it, e.g. a placed trail
  effect) -- no `ItemStack` to hang a tag off, so this needs a real Satchel-backed record. Proposed:
  a `LevelScope`-hosted fixture (the one Scope kind that's actually mature today -- `PlayerScope`
  isn't built yet per [RM_SAT_020](../../../roadmap/RM_SAT_020_jerry.md), and `MobScope` doesn't
  fit something that isn't a tracked entity), holding a flat collection of `{instanceId, ownerRef,
  target: TargetRef}` records.

**Worth naming, not yet reconciled:** Border already has its own, differently-shaped attunement
concept -- [RM_FRO_006](../../../roadmap/RM_FRO_006_sandra.md)/[RM_SAT_020](../../../roadmap/RM_SAT_020_jerry.md)
describe "which border ID a border-compass is currently attuned to" as per-player,
`PlayerScope`-hosted identity-tied state (one canonical attunement the player carries), not
per-item. That's already-decided Border-side infrastructure, not something this page overturns --
but it means Border may end up with two attunement mechanisms shaped differently for genuinely
different reasons (one canonical per-player lock vs. N independent per-item locks), and a player
will eventually hold both kinds of compass. Flagged for the same project-owner/Game-Designer pass
mentioned above, not resolved here. Also worth noting the border-compass as currently coded is an
admin tool, not the player-facing version either of these mechanisms would actually serve --
another reason this whole area counts as game rules still in flux rather than settled ground to
build straight from.

## Guardian Mobs

Technical counterpart to [Guardian Mobs](../design/guardian-mobs.md), and the first of `BossBundle`'s
three sibling fixtures to get a concrete home: **`BossGuardiansFixture`**, alongside `BossFixture`
and [`BossTellFixture`](#environmental-tells) below -- riding `BossBundle`'s own tick/pulse wiring
the same way every sibling fixture in this design does, though this one barely needs it.

A spawn-time modifier, not a periodic one: it hooks Forge's own mob-spawn-finalization event
(exact hook is Lead Dev's call) rather than rolling its own independent check, the same way
[Boss](boss.md)'s defeat detection hooks `LivingDeathEvent` alongside its own tick-driven
materialization -- intercepting before a hostile mob fully materializes, not replacing one after
the fact. Never attuned -- always "closest, right now": filters `BossFixture` down to whatever
counts as relevant to find the nearest boss, then reads that boss's home border's [`BorderCurve`
record(s)](border-curve.md) -- a `"placement"`-purpose curve for spawn density/replacement rate,
and separately a `"difficulty"`-purpose curve for the spawned variant's stat scaling -- each
evaluated through `BorderMath.intensityAt()`. The `"placement"` intensity gates a roll (against a
`BossRules`-tunable coefficient) for whether this particular spawn becomes a guardian at all; if it
does, the tier-bucketing/mob-table lookup that picks the variant and the `"difficulty"` intensity
that scales its stats is a `BorderRules`/`BossRules`-style pluggable strategy (see [Boss § Spawn
algorithm](boss.md#spawn-algorithm-a-pluggable-strategy-mirroring-borderrules)) -- "safe baseline,
replace later," matching this codebase's convention -- not settled here, just the likely shape.

**`BossGuardiansFixture` holds no persisted state at all** -- the leanest of `BossBundle`'s three
siblings. A guardian isn't tracked anywhere once it spawns; its guardian-ness lives entirely in its
own stats and a lightweight visible marker (custom name, glow), the same non-tracking precedent
[Boss](boss.md) itself uses for the "findable by looking, not invisible" guarantee. That's a
deliberate choice, not an oversight -- it means no density cap beyond whatever vanilla's own
spawn-attempt/mob-cap system already provides, no way to despawn a border's guardians specifically
when its boss is defeated, and no way to attribute a kill-reward to "that was a guardian"
afterward. None of those are required yet; if any becomes a real requirement later, that's the
point tracking would need to come back, not before.

**Two open design questions belong to Game Designer, not this page:** which Border first
introduces guardian mobs, and how they signal a boss that landed in old, already-passed territory
(see [Guardian Mobs § Open question](../design/guardian-mobs.md#open-question-guardians-in-old-territory)).

## Environmental Tells

Technical counterpart to [Boss Discovery](../design/boss-discovery.md#the-discovery-gradient)'s
"environmental tells" entry, worked out down to a concrete mechanism for two of its three
suggested signal types. Owned by **`BossTellFixture`**, a second sibling in `BossBundle` alongside
`BossFixture` and [`BossGuardiansFixture`](#guardian-mobs).

**Two samples drove this, chosen to stress opposite ends of the design space:** an ambient
particle effect (infrequent pops, so as not to be annoying) and an ambient sound (an existing
vanilla `SoundEvent`, no new asset needed for this pass) -- both tick-time, no attunement, "closest
right now" the same as Guardian Mobs. The dedicated-biome idea from [Boss
Discovery](../design/boss-discovery.md#the-discovery-gradient)'s own list is explicitly punted, not
built -- it's generation-time, not tick-time, a different category of problem entirely, and parked
at the bottom of the list rather than dropped. **Player-built Warps is not part of this cluster at
all**, despite living under the same "discovery gradient" umbrella in the design doc -- it's
player-placed, not boss-anchored, with nothing pointing back at the boss the way every tool above
it does; see its own section below.

**Mechanism, resolved:** this settles the standing open question of whether Tells shares a tick
handler with Guardian Mobs or reuses its `BorderCurve` records -- it does neither. `BossTellFixture`
gets its own fixed-cadence tick handler (a `BossRules`-tunable interval, not architecture's number
to pick -- something like every 20 ticks as a starting default, matching
`PlayerTrackingModule`'s existing cadence) and its own `"tell"`-purpose `BorderCurve` (a `LOG`
shape reads right for "climbs near, never spammy"), separate from Guardian's `"placement"`/
`"difficulty"` pair -- they're conceptually different signals with different tuning needs, even
though the resolution pattern (nearest relevant boss, `BorderMath.intensityAt()`) is identical.
Each check rolls **two independent probabilities off that one shared intensity** -- one for
particle, one for sound, each gated by its own `BossRules` coefficient -- which is how "particles
fire much more than sounds" is expressed without needing two curves: one curve describes the
gradient's shape, two coefficients separately control each effect's volume of occurrence on top of
it.

Sound uses vanilla's own area broadcast (`Level.playSound` with no specific target reaches every
player in range) -- nothing new needed there. The particle spawns at a randomized position near the
triggering player, found via a short local ground check -- deliberately *not*
[Border Pregeneration](border-pregeneration.md)'s heavier, generation-correct validation, since a
particle is cosmetic and transient and the player triggering it is by definition already standing
in loaded, already-generated terrain. Same underlying question ("where's the ground here") as
boss placement asks, a much cheaper answer for this case.

**The platform is not, technically, a tell.** The 3x3 bedrock platform from this cluster's Static
sample doesn't fit the "repeating ambient signal" shape the rest of this section describes -- it's
built once, tied to the boss's own existence, closer to placement infrastructure than a discovery
signal. Rather than a separate persisted-record fixture for it, `BossTellFixture` just builds it as
part of its own per-boss record's creation. That record is created once boss placement actually
succeeds (see [Border Pregeneration § Worked example: Boss placement,
revised](border-pregeneration.md#worked-example-boss-placement-revised)) -- an intra-bundle call
from whatever code commits the boss's final position, since `BossFixture` and `BossTellFixture`
share `BossBundle`. Gated only on the border-size threshold that decides whether one is warranted
at all (a Game-Designer/balance number, not resolved here).

**Tell creation must be resilient to chunk loading -- but that's a question Tell answers by
reading a signal, not by checking chunks itself.** An earlier version of this section had
`BossTellFixture` calling `Level.isLoaded(...)` directly before building the platform. That puts
geometry logic in the wrong place: whether a position is currently loaded, and whether it's
legally inside a given border at all, is exactly the kind of spatial question
[`BorderMath`](#navigation-lives-in-border) already owns -- Border is the module that describes
spatial aspects of the world; Tell shouldn't hold its own copy of that logic any more than Boss's
own materialization check should. **All a Tell needs to know is off or on.** Whatever build logic
a Tell runs -- the platform today, a much larger placed effect later -- gates purely on that one
signal, and does nothing when it reads off. Flipping the signal is external handling logic's job,
built on a `BorderMath`/`BorderAPI` geometry check ("is this position legal, is its chunk
currently loaded") rather than duplicated inside `BossTellFixture` -- the exact mechanics of that
handling (a poll `BossTellFixture`'s own tick makes against the geometry check, versus a push from
whatever else is already watching border state) are Lead Dev's call, not settled here. This
matters beyond the 3x3 platform: a Tell's placed effects could eventually expand to span thousands
of blocks (a large beacon or trail, later in this cluster's life), at which point checking chunk
state is unavoidably a multi-chunk question -- all the more reason it belongs in Border's own
geometry surface rather than something Tell reimplements per effect. Worth building the on/off
discipline in now, even though today's single-chunk platform is small enough that skipping it would
rarely be caught in practice.

## Beacons and Particle Trails

Technical counterpart to [Boss Discovery](../design/boss-discovery.md#the-discovery-gradient)'s
"beacons and particle trails" entry -- a placed, findable signal *near, not on top of,* a boss.
**Distinct from the platform now built by [Environmental Tells](#environmental-tells)'s
`BossTellFixture`** -- that's boss-placement infrastructure, built once as a byproduct of a boss
being placed at all; a beacon or trail here would be its own deliberate, later-game player-facing
tool, still fully unbuilt. Unlike Guardian Mobs/Tells, this plausibly *does* want attunement -- a trail meant to stay locked
onto one boss over time, rather than always re-resolving to "nearest right now," is exactly the
process/world-anchored case above: no item carries it, so it needs its own `LevelScope`-hosted
record pointing a `TargetRef` at the boss it's leading to. Not settled whether it needs this or can
get away with always-recompute-nearest instead -- a game-feel question, not a structural one.

## Ender-eye-style Tracker

**Deferred out of this design pass entirely** -- punted alongside [Player-built
Warps](#player-built-warps), for the same reason: not wrong, just not next in line. Kept here for
whenever it does get picked up.

Technical counterpart to [Boss Discovery](../design/boss-discovery.md#the-discovery-gradient)'s
ender-eye-style tracking item -- a consumable or thrown item giving a one-shot directional signal.
Never attuned: filters to relevant candidates, folds `distanceTo()` to find the closest, then
resolves `direction()` once, at the moment of use, keeping no state afterward. No new data model
beyond the item itself.

## Special Compass

Technical counterpart to [Boss Discovery](../design/boss-discovery.md#the-discovery-gradient)'s
special compass -- persistent, reusable directional tracking. **Genuinely different from the
Tracker, not the same consumer with a different shell (a correction from an earlier draft of this
page):** the Tracker is stateless and one-shot (and now deferred, above); the Compass holds a real
attunement -- the item-carried case above, `ItemStack`-NBT-backed, resolved through the same
`TargetRef` + resolver-registry mechanism. That state-shape difference is a real argument for two
roadmap nodes rather than one, though nothing is minted yet. Exact attunement rules (how it's set,
whether it can be re-attuned, cost) are part of the still-open Game-Designer pass above, not decided
here.

**Not hypothetical -- there's an existing coded compass to formalize.** `BorderPathCompass` (a
server-only static utility, hacked in ahead of this whole design) already builds a vanilla
`Items.COMPASS` stack tagged `FrontierCompass=true` and repoints it by rewriting the vanilla
lodestone NBT fields (`LodestonePos`/`LodestoneDimension`, with `LodestoneTracked=false` so vanilla
doesn't try to manage it) whenever the border tip moves -- an externally-triggered snapshot rebind,
not a self-refreshing item. **Proposed formalization: fold it into a real, self-refreshing custom
`Item` subclass**, reusing vanilla's own compass-needle rendering entirely for free (the needle
already points at whatever `LodestonePos` says; nothing about the rendering changes). On a
throttled `inventoryTick()`, the item reads a small persisted NBT discriminator off its own stack --
e.g. `AttunedTo: "PATH_TIP"`, or `AttunedTo: "BOSS", TargetUuid: <uuid>` -- reconstructs the
matching `TargetRef` fresh, resolves it, and rewrites the lodestone fields. This unifies
tip-pointing and boss-pointing (today's placeholder "server-op-only compass simply points straight
at the boss," see [Boss Discovery](../design/boss-discovery.md)) into one generic item class,
distinguished only by which discriminator a given stack carries.

**New `TargetRef` variant needed for the tip case: `Dynamic(Supplier<BlockPos>)`.** The tip isn't
owned by any registered `TargetType` the way a boss or a border is -- it's a live value read
straight off Border's own path-tip tracking, so there's nothing to look up in the resolver registry
by UUID. Navigator special-cases this one variant: if a `TargetRef` is `Dynamic`, call the embedded
supplier directly (e.g. `() -> BordersPathFacet.getTipCenter()`); otherwise dispatch through the
`TargetType -> resolver` registry as before. This is one new branch in Navigator's resolution
logic, not a new registration kind -- `Dynamic` never goes through the registry at all.

**Corollary, and the reason the discriminator above exists in the first place: a
`Supplier<BlockPos>` cannot be serialized.** It's a closure, not data, so `Dynamic` is a pure
runtime-only convenience -- it is never itself persisted, and Navigator's `TargetRef` machinery
never needs a persistence story for it, because structurally it can't have one. **The consumer
manages itself:** anything that needs a live reference to survive a save/reload -- the compass is
the only consumer so far -- is responsible for its own lightweight persisted discriminator (the
`AttunedTo` tag above) and for reconstructing the right `TargetRef` fresh from that discriminator
every time it's needed, rather than expecting Navigator to remember a `Dynamic` reference across a
restart. This is the concrete case the "still worth a thin managed wrapper" line in Attunement
above was gesturing at in the abstract -- the item-NBT wrapper's first real built instance is this
discriminator-read-and-rebind cycle, not a hypothetical one anymore.

## Player-built Warps

**Deferred out of this design pass entirely** -- explicitly later-epoch ("2.0 type stuff") rather
than part of the Tier 2 discovery-gradient cluster the rest of this page works through, despite
sharing the same design-doc list. The reasoning below is kept for whenever it does get picked up,
not because it's next in line.

Technical counterpart to [Boss Discovery](../design/boss-discovery.md#the-discovery-gradient)'s
player-built warps -- once a boss's general location is known, let the player mark a return point.
**Possibly not boss-specific infrastructure at all:** a warp doesn't need a *live*, resolvable
target the way the other five tools do -- once set, it's just a remembered position. That's exactly
`TargetRef::RawPos(BlockPos)` with nothing else attached, which raises the question of whether Warps
reuses the same Navigator/attunement storage (item- or record-based) rather than being fully
separate infrastructure, as an earlier draft of this page assumed. Not settled -- worth checking
whether Satchel already has anything waypoint-adjacent before either path gets built.

## Boss variety and tells, reward loot -- explicitly out of scope here

[Boss Discovery](../design/boss-discovery.md#boss-variety-and-tells-tbd)'s second discovery axis
(boss-type tells) and its reward-loot scaling both belong to [FrontierMode Operational
Tiers](../../plans/operational-tiers.md#the-tiers) Tier 3, not Tier 2 -- named here only so a
reader doesn't wonder why this page skips them.

## Open questions

- **Attunement's actual game rules** -- how it's granted, whether/how it can change, cost, and
  whether Border's existing per-player compass-attunement concept
  ([RM_FRO_006](../../../roadmap/RM_FRO_006_sandra.md)/[RM_SAT_020](../../../roadmap/RM_SAT_020_jerry.md))
  and this page's item-carried version reconcile or knowingly stay two different mechanisms --
  pending a project-owner/Game-Designer pass, not this page's call.
- **Compass vs. Tracker: confirmed as two roadmap nodes**, not one -- see "Special Compass"
  above. (The Tracker itself is now deferred, but the two-node distinction still stands for
  whenever it's picked back up.)
- **The compass's NBT discriminator schema is a Lead Dev call, not decided here** -- `AttunedTo:
  "PATH_TIP"` / `AttunedTo: "BOSS", TargetUuid: <uuid>` above is illustrative shape, not a
  ratified tag format.
- **Pinned, not resolved: `TargetRef::Dynamic` has no structural guard against ending up
  somewhere it can't survive.** The Attunement section's persisted `{instanceId, ownerRef,
  target: TargetRef}` record (for process/world-anchored attunement) has no described mechanism
  stopping a future implementation from writing a `Dynamic` value into that `target` field -- and
  a `Dynamic`'s embedded `Supplier<BlockPos>` can't be serialized (see "Special Compass" above),
  so that would silently fail or lose state on the next save/reload. The discipline today is
  purely conventional ("the consumer manages itself"), not enforced by any type restriction or
  write-time check. Flagged here deliberately rather than fixed.
- **`Ender-eye-style Tracker` is deferred out of this epoch entirely**, alongside Player-built
  Warps -- see its own section above.
- **Tuning numbers throughout this page are placeholders, not decisions** -- the Tells tick
  interval, the particle/sound `BossRules` coefficients, and the border-size threshold that gates
  the platform are all "safe baseline, replace later," matching every other tunable in this
  codebase.
- **Whether Guardian Mobs ever needs tracking** (a density cap beyond vanilla's own mob-cap
  system, despawn-on-boss-defeat, or reward attribution) -- deliberately not built now; revisit
  only if one becomes a real requirement.
- **Player-built Warps is deferred out of this epoch entirely** -- its own open questions (whether
  it reuses `TargetRef::RawPos`/Navigator storage, whether Satchel already has a waypoint-shaped
  fixture) stay recorded on its own section above for whenever it is picked up.
- **The cost-curve risk [Boss Discovery](../design/boss-discovery.md#the-discovery-gradient) flags
  itself** ("if the cost curve on these tools outpaces what a player can realistically earn...")
  is a balance question, not a structural one -- noted so it isn't lost, playtest-territory once
  something exists to test.

## Related pages

- [Boss Discovery](../design/boss-discovery.md) -- the design intent this page gives technical
  shape to
- [Guardian Mobs](../design/guardian-mobs.md) -- design intent for the first gradient tool
- [Boss](boss.md) -- `BossFixture`'s data model, the `MobInterestRegistry` registration precedent,
  and the `BossRules`/`DefaultBossRules` pluggable-strategy precedent
- [Border](border.md) -- `BorderMath`, and `BordersBundle`'s existing shape
- [Border Curve](border-curve.md) -- the distance-keyed intensity-curve mechanism both Guardian
  Mobs and Environmental Tells read from, each through their own purpose-keyed records
- [Border Pregeneration](border-pregeneration.md) -- the placement-readiness guarantee both Boss
  and the Environmental Tells platform depend on
- [Universal Sidedness Facade](../../satchel/architecture/facade-vision.md) -- the managed-touch-point
  argument behind wrapping item-NBT attunement access instead of reaching into Forge APIs directly
- [RM_FRO_006](../../../roadmap/RM_FRO_006_sandra.md) / [RM_SAT_020](../../../roadmap/RM_SAT_020_jerry.md)
  -- Border's own, differently-shaped compass-attunement concept
- [RM_FRO_024](../../../roadmap/RM_FRO_024_donna-01.md) ("Donna epoch maintenance 1") -- the
  open question of whether `BossFixture`/`BossMobFixture` should merge into one bundle, flagged
  from this page (not, per the correction in "Navigation lives in Border" above, a review of any
  one-fixture-per-bundle rule -- there never was one)
- [FrontierMode Operational Tiers](../../plans/operational-tiers.md) -- Tier 2's definition, and
  why boss variety/loot scaling sit in Tier 3 instead
- [RM_FRO_023](../../../roadmap/RM_FRO_023_kathleen.md) ("Kathleen") -- the convergence this
  cluster's real nodes will fold into, once minted
