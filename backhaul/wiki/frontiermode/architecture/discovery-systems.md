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
updated: '2026-08-30'
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
  RawPos(BlockPos)`. `RawPos` is the escape hatch (and plausibly what [Player-built
  Warps](#player-built-warps) turns out to want -- a warp doesn't need a *live* target at all, just
  a remembered position, which is `RawPos` with nothing else added).
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
holding" -- a bundle hosting more than one fixture is an already-precedented shape, not a new one.
Riding inside `BordersBundle` means Navigator inherits `BORDERS_JIG`'s already-correct tick/pulse
wiring for free, instead of standing up a second `JigConfig` and re-earning the `withTick(true)` +
`policies().persistence(...)` pairing [Boss](boss.md) already paid for once (see [Boss § What can go
wrong](boss.md#what-can-go-wrong)) -- while keeping Navigator's own schema separate from Border's
core save format, since it's a sibling fixture, not a field bolted onto `BordersFixture`. This
supersedes the "still open, no longer settled-by-precedent" framing this page carried before: the
"one bundle per concern" review still flagged on
[RM_FRO_024](../../../roadmap/RM_FRO_024_donna-01.md) ("Donna epoch maintenance 1") is about whether
*Boss's own* two fixtures should merge, not about whether Navigator can share a bundle with Border's
-- those are independent questions, and this page's placement choice doesn't need that review to
land first.

**Other infrastructure this can reuse rather than reinvent:** `BorderSelector`/
`BorderSelectorArgumentType` plus the `Commands`/`CommandHandler` registration split (see [Boss
Commands](boss-commands.md)) for whatever admin commands Navigator eventually needs; Boss's lighter
boolean/`Optional`-return validation idiom (reject -> `OUT.warn` -> a "nothing happened" value,
rather than throwing) for attunement mutation validation; and the `BorderDisplay`/`BossDisplay`
presentation-only formatting pattern for however Navigator state gets shown to a player. One real
gap, not a reuse: there's no existing item-NBT-wrapper infrastructure to build the attunement
wrapper from (see "Attunement" below) -- it would be the first concrete built instance of [Universal
Sidedness Facade](../../satchel/architecture/facade-vision.md)'s argument, not an application of
something already proven.

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

Technical counterpart to [Guardian Mobs](../design/guardian-mobs.md). A spawn-time or
periodic-tick modifier: near a target of interest, hostile mob spawns get replaced or supplemented
by a stronger, visually distinct variant, with density/strength climbing as distance shrinks.
Never attuned -- always "closest, right now": filters `BossFixture` down to whatever counts as
relevant to find the nearest boss, then reads that boss's home border's [`BorderCurve`
record(s)](border-curve.md) -- a `"placement"`-purpose curve for spawn density/replacement rate,
and separately a `"difficulty"`-purpose curve for the spawned variant's stat scaling -- each
evaluated through `BorderMath.intensityAt()`. How the two curves' outputs actually combine into a
tier and a mob-table lookup is a `BorderRules`/`BossRules`-style pluggable strategy (see [Boss §
Spawn algorithm](boss.md#spawn-algorithm-a-pluggable-strategy-mirroring-borderrules)) -- "safe
baseline, replace later," matching this codebase's convention -- not settled here, just the likely
shape.

**Two open design questions belong to Game Designer, not this page:** which Border first
introduces guardian mobs, and how they signal a boss that landed in old, already-passed territory
(see [Guardian Mobs § Open question](../design/guardian-mobs.md#open-question-guardians-in-old-territory)).

## Environmental Tells

Technical counterpart to [Boss Discovery](../design/boss-discovery.md#the-discovery-gradient)'s
"environmental tells" entry -- ambient sound/particle/biome-adjacent signals near a boss, one step
more specific than guardian density alone. Same shape as Guardian Mobs, at least provisionally --
never attuned, likely reading the same nearest-boss resolution and, possibly, the same
[`BorderCurve`](border-curve.md) record(s) rather than a curve of its own. Open question whether it
shares one tick handler with Guardian Mobs (both key off the same distance bands), and whether it
reuses Guardian Mobs' `BorderCurve` records at all -- see [Border Curve §
Open questions](border-curve.md#open-questions).

## Beacons and Particle Trails

Technical counterpart to [Boss Discovery](../design/boss-discovery.md#the-discovery-gradient)'s
"beacons and particle trails" entry -- a placed, findable signal *near, not on top of,* a boss.
Unlike Guardian Mobs/Tells, this plausibly *does* want attunement -- a trail meant to stay locked
onto one boss over time, rather than always re-resolving to "nearest right now," is exactly the
process/world-anchored case above: no item carries it, so it needs its own `LevelScope`-hosted
record pointing a `TargetRef` at the boss it's leading to. Not settled whether it needs this or can
get away with always-recompute-nearest instead -- a game-feel question, not a structural one.

## Ender-eye-style Tracker

Technical counterpart to [Boss Discovery](../design/boss-discovery.md#the-discovery-gradient)'s
ender-eye-style tracking item -- a consumable or thrown item giving a one-shot directional signal.
Never attuned: filters to relevant candidates, folds `distanceTo()` to find the closest, then
resolves `direction()` once, at the moment of use, keeping no state afterward. No new data model
beyond the item itself.

## Special Compass

Technical counterpart to [Boss Discovery](../design/boss-discovery.md#the-discovery-gradient)'s
special compass -- persistent, reusable directional tracking. **Genuinely different from the
Tracker, not the same consumer with a different shell (a correction from an earlier draft of this
page):** the Tracker is stateless and one-shot; the Compass holds a real attunement -- the
item-carried case above, `ItemStack`-NBT-backed, resolved through the same `TargetRef` +
resolver-registry mechanism. That state-shape difference is a real argument for two roadmap nodes
rather than one, though nothing is minted yet. Exact attunement rules (how it's set, whether it can
be re-attuned, cost) are part of the still-open Game-Designer pass above, not decided here.

## Player-built Warps

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
- **Compass vs. Tracker: confirmed as two roadmap nodes**, not one -- see "Special Compass" above.
- **Guardian Mobs and Environmental Tells: one tick handler or two, and does Tells reuse the same
  `BorderCurve` records as Guardian Mobs or want its own?** -- unresolved (also flagged on [Border
  Curve](border-curve.md#open-questions)).
- **Do Player-built Warps reuse `TargetRef::RawPos` and the same attunement storage**, or stay
  fully separate infrastructure? -- unresolved, and changes how many nodes this cluster mints.
- **Does Satchel already have a waypoint-shaped fixture Warps could sit on regardless?** -- not
  checked yet.
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
- [Border Curve](border-curve.md) -- the distance-keyed intensity-curve mechanism Guardian Mobs (and
  possibly Environmental Tells) read from
- [Universal Sidedness Facade](../../satchel/architecture/facade-vision.md) -- the managed-touch-point
  argument behind wrapping item-NBT attunement access instead of reaching into Forge APIs directly
- [RM_FRO_006](../../../roadmap/RM_FRO_006_sandra.md) / [RM_SAT_020](../../../roadmap/RM_SAT_020_jerry.md)
  -- Border's own, differently-shaped compass-attunement concept
- [RM_FRO_024](../../../roadmap/RM_FRO_024_donna-01.md) ("Donna epoch maintenance 1") -- the
  "one bundle per concern" review flagged from this page
- [FrontierMode Operational Tiers](../../plans/operational-tiers.md) -- Tier 2's definition, and
  why boss variety/loot scaling sit in Tier 3 instead
- [RM_FRO_023](../../../roadmap/RM_FRO_023_kathleen.md) ("Kathleen") -- the convergence this
  cluster's real nodes will fold into, once minted
