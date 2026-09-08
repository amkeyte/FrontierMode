---
id: frontiermode/architecture/border-curve
category: frontiermode/architecture
slug: border-curve
title: Border Curve
summary: Technical shape for BorderCurveFixture -- a sibling fixture in BordersBundle
  giving borders zero-to-many named intensity curves (placement, difficulty, ...),
  evaluated through BorderMath. First concrete consumer is Guardian Mobs -- proposal
  stage.
keywords: null
status: draft
updated: '2026-08-31'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · frontiermode / architecture
<!-- bh-header:end -->

# Border Curve

The architecture counterpart to the "gradient" need first raised in [Boss Discovery
Systems](discovery-systems.md#guardian-mobs) -- Guardian Mobs' "density/strength climbing as
distance shrinks" needed a real shape, and working that out surfaced a concept general enough that
it doesn't belong to Guardian Mobs, or even to Boss: any border can carry one or more named,
distance-keyed intensity curves. Feeds [RM_FRO_023](../../../roadmap/RM_FRO_023_kathleen.md)
("Kathleen"), same as `discovery-systems.md`.

**Everything on this page is proposal, not ruling.** Same caveat as `discovery-systems.md`: no
`RM_FRO` node exists for this yet, and this cluster's persona names stay unspent until the shape
here settles further (see [BHRM — Persona names are not
reusable](../../meta/bhrm.md#persona-names-are-not-reusable)).

## Why a sibling fixture, not a field on Border

The first draft of this idea was a single value object attached directly to the Border record. It
broke on a concrete case: Guardian Mobs actually need *two* independent curves on the same border
-- a placement curve (spawn density/replacement rate, climbing as distance to center shrinks) and a
difficulty curve (guardian stat scaling, its own distance-based climb). These aren't variants of
one thing; they're two different questions, each with its own shape. A single embedded object can't
hold two of those, and a bare collection field would just reinvent per-entry identity and CRUD
inside a nested list.

**Settled: `BorderCurveFixture`, a sibling fixture inside the existing `BordersBundle`**, joining
`BordersFixture` and [Navigator's own sibling
fixture](discovery-systems.md#navigation-lives-in-border) -- each `BorderCurve` record references
its border by UUID rather than being embedded in it, the same "reference by ID, resolve on demand"
discipline `BossFixture` already uses for its own decoupling from Border. Unlike Boss's decoupling,
though, this one doesn't cross a module boundary: Boss and Border are separate modules, which is why
Boss's fixtures live in their own bundle entirely. `BorderCurve` is still a Border-owned concept --
it describes what the border itself is for -- just implemented by reference instead of embedding,
for CRUD/lifecycle flexibility. `BordersFixture`'s own schema needs zero changes as a result --
tighter than the original nested-field plan, which would have needed at least one new optional
field.

This is the second time in this same design pass that "starts as an embedded property, gets
promoted to a sibling fixture once real multiplicity or independent-identity needs show up" has
happened -- Navigator went through the same move. Worth naming as a recognized shape for this
codebase, not a one-off.

**Naming note:** this page was drafted as "`BorderIntensity`" and renamed to `BorderCurve` once
written up -- the record is structurally a curve descriptor (shape + parameters); what it means
(placement, difficulty, ...) lives entirely in `purpose`, not in the type name. Keeping the type
neutral matches the same "Border doesn't interpret what it's given" discipline `TargetRef` already
follows.

## Data model

- **`BorderCurve`** record: `{id: UUID, borderId: UUID, purpose: String, shape: Shape, params:
  <TBD>}`.
- **`Shape`** -- an enum, `LINEAR | LOG | SQUARE` to start, extensible.
- **`purpose`** is a plain string, opaque to Border -- stored, never interpreted by
  `BorderCurveFixture` itself. Consumers agree on keys by convention (`"placement"`,
  `"difficulty"`, ...), the same way a `TargetRef`'s tag means nothing to Navigator itself.
- **No tick wiring.** These are static descriptors, set once and read on demand -- unlike
  `NavigatorFixture`, there's no periodic recomputation happening here at all, so this fixture
  skips the `withTick`/persistence-pairing question entirely rather than just handling it
  correctly. Simpler than Navigator, not just consistent with it.

## Query surface

- **`forBorder(UUID borderId) -> List<BorderCurve>`** -- every curve on a border.
- **`forBorder(UUID borderId, String purpose) -> Optional<BorderCurve>`** -- the one named curve,
  for a consumer that knows what it wants.
- **`borderOf(BorderCurve) -> Border`** -- the reverse lookup: resolves a held record back to the
  border geometry (center, radius) it applies to, so a consumer doesn't have to handle the raw
  `borderId` itself once it already has the record in hand.

## Deletion: explicit, not orphaned

**Settled: deleting a Border explicitly deletes its `BorderCurve` records.** No orphaned references
left pointing at a dead border.

**Not yet nailed down: how the cascade is actually implemented.** Either `BordersCrudFacet`'s
border-delete path gets bespoke logic to purge matching `BorderCurveFixture` records, or Satchel
already offers some general referential-integrity/cascade mechanism for this shape of problem and
it should be used instead. That's a real open question for Lead Dev, not something this page
resolves -- flagged here so the "explicit, not orphaned" requirement doesn't get lost between
architecture and implementation.

## Evaluation: `BorderMath`, not a service class

`BorderCurve` is data; the evaluation is pure math, living on `BorderMath` alongside
`isInside`/`randomPointInDisk`, which already take border geometry as input:

- **`BorderMath.intensityAt(BorderCurve descriptor, double normalizedDistance) -> double`** -- the
  shape function core. `normalizedDistance = distanceTo(center, point) / radius`.
- **`BorderMath.intensityAt(Border border, BorderCurve descriptor, BlockPos point) -> double`** --
  the convenience wrapper doing the resolve-distance-then-normalize-then-evaluate steps in one call,
  so consumers don't re-derive that boilerplate each time.

Still zero domain knowledge either way -- `BorderMath` doesn't know what a "tier" or "guardian" is,
only how to turn a shape and a distance into a number.

## Tier-bucketing and mob tables stay outside `BorderCurve`

`BorderCurve` carries only the curve's shape and geometry parameters. How a consumer turns that
continuous number into tiers, and what happens at each tier (which mob variant spawns, at what
rate), stays a separate, global, `BossRules`/`DefaultBossRules`-style pluggable strategy -- "safe
baseline, replace later," the same convention already expected for Guardian Mobs' spawn algorithm.
Two independently tunable layers: the curve's shape (per-border data) and how a consumer reacts to
it (global code).

## Worked example: Guardian Mobs' two curves

A boss's home border carries two `BorderCurve` records:

- `purpose: "placement"`, `shape: LINEAR` -- feeds spawn density/replacement probability, climbing
  linearly as distance to center shrinks. Evaluated against a fraction of the border's radius
  (`BossRules.guardianPlacementRadiusFraction()`, 0.5 by default) rather than the bare radius, so
  the ramp is concentric with the border but compressed into a smaller reference circle around the
  boss -- keeps guardians appearing in a noticeably bounded ring instead of spread across the
  entire border.
- `purpose: "difficulty"`, `shape: LOG` -- feeds the guardian variant's stat scaling, climbing
  logarithmically (a sharper spike close to the boss, flattening further out) -- independent of
  `BorderRules.layerToDifficulty` (see "Relationship to Difficulty" below).

Both are fetched via `forBorder(borderId, purpose)` and evaluated independently through
`BorderMath`. How placement-tier and difficulty-tier actually combine into a spawn decision is
Boss-domain pluggable-strategy territory, not something this page prescribes -- the point here is
only that they're two independent records answering two independent questions, which is exactly the
case that ruled out a single embedded value object.

## Relationship to Difficulty (border.md / difficulty.md)

Deliberately orthogonal. [`layerToDifficulty`](difficulty.md#two-new-methods-on-borderrules----not-a-separate-interface)
is Layer-keyed -- a discrete ambient/boss difficulty baseline, independent of literal distance. The
`"difficulty"`-purpose `BorderCurve` above is a *different*, distance-based curve scoped
specifically to guardian stat scaling -- not an input to `layerToDifficulty`, not a replacement for
it. For this page's purposes, `layerToDifficulty` is treated as a flat function of relevant layer,
with no composition between the two mechanisms. Two different gradients that both happen to use the
word loosely -- kept named apart deliberately (`BorderCurve` vs. Difficulty) so they don't get
conflated later.

## Anchor: border-centered, not boss-position

`BorderCurve` is evaluated against the border's own geometry (center, radius) -- not a boss's
literal block position. This works cleanly because the current design has no moving bosses. If boss
movement is ever introduced, a minimum border radius for guardian-carrying borders, plus a maximum
leash radius before a wandering boss gets teleported back home, would keep this anchor valid without
redesigning it. Flagged here as a future constraint, not built now -- nothing today needs it.

## Deferred: inverted (donut) orientation

Noodled on, then explicitly kicked down the road -- the only known use case (setting difficulty
*outside* a border) is several epochs out and nothing on the roadmap needs it yet. v1 uses a plain
non-negative distance-from-center model: no orientation flag, no signed-distance representation
reserved in the schema ahead of time. Revisit if/when a concrete need appears -- adding it later is
a schema addition, not a rework.

## Open questions

- **Exact `BorderMath` method signatures and what `params` holds beyond the `shape` tag**
  (steepness, an exponent?) -- the sketch above is a starting shape, not final; likely settles
  alongside Navigator's own math, and the exact curve parameters are probably Game
  Designer/playtest territory once something is buildable.
- **How the delete-cascade is actually implemented** -- bespoke `BordersCrudFacet` logic, or an
  existing Satchel referential-integrity mechanism this page doesn't know about. Lead Dev's call.

## Related pages

- [Boss Discovery Systems](discovery-systems.md) -- Guardian Mobs, the first consumer of this
  page's concept, Environmental Tells, the second (its own `"tell"`-purpose record, not shared with
  Guardian Mobs'), and Navigator, the other sibling fixture riding in `BordersBundle`
- [Difficulty](difficulty.md) -- the Layer-keyed gradient this page's "difficulty" purpose is
  deliberately distinct from
- [Boss](boss.md) -- the reference-by-ID/resolve-on-demand precedent this page reuses
- [Border](border.md) -- `BorderMath`, and `BordersBundle`'s existing (and growing) shape
- [RM_FRO_023](../../../roadmap/RM_FRO_023_kathleen.md) ("Kathleen") -- the convergence this
  cluster's real nodes will fold into, once minted
