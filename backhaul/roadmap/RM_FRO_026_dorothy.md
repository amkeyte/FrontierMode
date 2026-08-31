---
id: RM_FRO_026
uid: RM_FRO
number: 26
kind: work
status: open
title: Navigator target-resolution fixture
owner: Arryn
depends_on:
- RM_FRO_017
created: '2026-08-31'
superseded_by: null
ticket: FRO_065
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_FRO
<!-- bh-header:end -->

## Navigator target-resolution fixture

**First of three shared-infrastructure nodes minted together for [Boss Discovery
Systems](../wiki/frontiermode/architecture/discovery-systems.md) -- see [Donna Epoch
Nodes](../wiki/plans/donna-epoch-nodes.md) for why this trio mints as a block and in this order.
Minted and ticketed first, ahead of its two table-mates, since nothing else in the Tier 2
discovery-gradient cluster can start without it.**

**Scope, per [Boss Discovery Systems § Navigation lives in
Border](../wiki/frontiermode/architecture/discovery-systems.md#navigation-lives-in-border):**
a `TargetRef` tagged union (`Boss(UUID) | Border(UUID) | Structure(...) | RawPos(BlockPos) |
Dynamic(Supplier<BlockPos>)`), two new `BorderMath` primitives (`distanceTo`, `direction`), a
`TargetType -> resolver` registry mirroring `MobInterestRegistry`'s existing shape, and
`NavigatorFixture` itself as a new sibling fixture riding inside the existing `BordersBundle`.
That page is the spec to build against, not this node's own prose -- including its Special
Compass section's concrete formalization of `TargetRef::Dynamic` and the registry-bypass it
needs in Navigator's own resolution logic.

**Explicitly out of scope for this node:** the Special Compass item itself (a separate,
not-yet-minted candidate on [Donna Epoch Nodes](../wiki/plans/donna-epoch-nodes.md)) -- this node
builds the `TargetRef`/registry/`NavigatorFixture` machinery the compass will eventually consume,
not the compass item.

**Done bar:** `TargetRef` and its resolver registry exist and are exercised by at least one real
registered resolver (`TargetType.BOSS`, resolving through `BossFixture`, is the natural first
case since Boss already exists); `NavigatorFixture` is registered as a sibling fixture in
`BordersBundle` and builds/loads cleanly alongside `BordersFixture`; `BorderMath.distanceTo()`/
`direction()` exist and are unit-exercised against at least one real pair of points. Anything
touching `TargetRef::Dynamic`'s consumer side (the compass) is not required for this node's own
done bar -- only that the variant exists and Navigator's resolution logic special-cases it
correctly, per spec.

- 2026-08-31: Node opened, alongside its two table-mates
  ([RM_FRO_027](RM_FRO_027_janet.md) "Janet", [RM_FRO_028](RM_FRO_028_diane.md) "Diane") --
  minted first per [Donna Epoch Nodes](../wiki/plans/donna-epoch-nodes.md)'s decision. Ticketed
  to Lead Dev the same day; see this node's `ticket:` field once opened.

## Required By

*(computed — nothing depends on this yet)*
