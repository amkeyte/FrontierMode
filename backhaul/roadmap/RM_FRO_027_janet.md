---
id: RM_FRO_027
uid: RM_FRO
number: 27
kind: work
status: resolved
title: BorderCurve intensity-curve fixture
owner: Arryn
depends_on:
- RM_FRO_017
created: '2026-08-31'
superseded_by: null
ticket: FRO_066
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_FRO
<!-- bh-header:end -->

## BorderCurve intensity-curve fixture

**Second of three shared-infrastructure nodes minted together for [Boss Discovery
Systems](../wiki/frontiermode/architecture/discovery-systems.md) -- see [Donna Epoch
Nodes](../wiki/plans/donna-epoch-nodes.md) for why this trio mints as a block and in this order.
Independent of [RM_FRO_026](RM_FRO_026_dorothy.md) ("Dorothy", Navigator) at the code level --
both are sibling fixtures in `BordersBundle` but neither calls into the other -- minted together
only because both were settled in the same design pass.**

**Scope, per [Border Curve](../wiki/frontiermode/architecture/border-curve.md):**
`BorderCurveFixture`, a new sibling fixture in `BordersBundle` giving a border zero-to-many named
intensity curves (`"placement"`, `"difficulty"`, `"tell"`, ...), each evaluated through
`BorderMath` against distance from a border's center. That page is the spec to build against,
including its worked Guardian Mobs example and the `LOG`-shape curve `"tell"` purpose needs (see
[Boss Discovery Systems § Environmental Tells](../wiki/frontiermode/architecture/discovery-systems.md#environmental-tells)).

**Explicitly out of scope for this node:** Guardian Mobs and Environmental Tells themselves --
both are separate, not-yet-minted candidates on [Donna Epoch
Nodes](../wiki/plans/donna-epoch-nodes.md) that will consume `BorderCurveFixture` once it exists;
this node builds the curve infrastructure, not either consumer.

**Done bar:** `BorderCurveFixture` is registered as a sibling fixture in `BordersBundle` and
builds/loads cleanly alongside `BordersFixture`; a border can hold zero-to-many named curve
records keyed by purpose string; at least one curve evaluates correctly end-to-end against
`BorderMath` for a real distance value (exercising the worked example in the spec page is
sufficient). Exact `BorderMath` method signatures and curve parameter shape are this node's own
open question per the spec page -- Lead Dev's call within the shape described there, not a
blocker to starting.

- 2026-08-31: Node opened, alongside its two table-mates
  ([RM_FRO_026](RM_FRO_026_dorothy.md) "Dorothy", [RM_FRO_028](RM_FRO_028_diane.md) "Diane") --
  minted per [Donna Epoch Nodes](../wiki/plans/donna-epoch-nodes.md)'s decision.

- 2026-08-31: Done bar met and playtest-verified via FRO_066's full build+playtest log -- BorderCurveFixture registered as a sibling fixture, curve records evaluate end-to-end against BorderMath, confirmed clean during the Windows playtest pass across real border-growth cycles. Resolved.

## Required By

<!-- required-by:start -->
- [**RM_FRO_029**](RM_FRO_029_gloria.md) — Guardian Mobs epoch 1
- [**RM_FRO_030**](RM_FRO_030_janice.md) — Environmental Tells epoch 1
- [**RM_FRO_031**](RM_FRO_031_joan.md) — Beacons epoch 1
- [**RM_FRO_032**](RM_FRO_032_elizabeth.md) — Ender-eye-style Tracker epoch 1
- [**RM_FRO_033**](RM_FRO_033_marilyn.md) — Special Compass epoch 1
- [**RM_FRO_034**](RM_FRO_034_virginia.md) — Player-built Warps epoch 1
<!-- required-by:end -->
