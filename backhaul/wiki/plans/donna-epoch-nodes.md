---
id: plans/donna-epoch-nodes
category: plans
slug: donna-epoch-nodes
title: Donna Epoch Nodes
summary: Candidate RM_FRO nodes proposed for the Donna epoch, staged here before minting
  -- starting with the Tier 2 discovery-gradient cluster (Navigator, Border Curve,
  and the six discovery tools) feeding Kathleen's convergence.
keywords: null
status: draft
updated: '2026-08-30'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../BACKHAUL.md) · [Wiki Index](../../WIKI_INDEX.md) · plans
<!-- bh-header:end -->

# Donna Epoch Nodes

Tracks candidate `RM_FRO` nodes proposed for the Donna epoch -- the span opened by
[RM_FRO_017](../../roadmap/RM_FRO_017_donna.md) ("Donna") reaching -- before any of them are
minted. Minting a node burns a persona name permanently (see [BHRM — Persona names are not
reusable](../meta/bhrm.md#persona-names-are-not-reusable)), and this cluster took several rounds
of real design discussion to settle its shape; this page is the staging area so that discussion
doesn't only live in chat history, and so scoping decisions get made before a name is spent, not
after.

**Naming this cluster's counterpart, not a duplicate of it:** [RM_FRO_024](../../roadmap/RM_FRO_024_donna-01.md)
("Donna epoch maintenance 1") and [RM_FRO_025](../../roadmap/RM_FRO_025_donna-02.md) ("Donna epoch
maintenance 2") gather *unplanned* rework surfaced during the epoch -- things a design pass turned
up that don't belong on a persona-named node. This page is the other half: *planned* new work,
proposed here ahead of minting, that will get its own persona-named nodes once real. Once minted, a
node's entry here should link to it rather than duplicate its status -- this page tracks proposals,
`bhrm` tracks reality.

**This is a bounded plan, not a standing practice** -- same relationship [Bare-Necessity Strip-Down
Plan](strip-down.md) has to its own scope: it's done when every candidate below either has a real
`RM_FRO` node or has been explicitly dropped, not an ongoing thing that grows forever attached to
the epoch's name.

## Tier 2 discovery-gradient cluster

Feeds [RM_FRO_023](../../roadmap/RM_FRO_023_kathleen.md) ("Kathleen," Tier 2: Guided loop
operational). Design work lives in [Boss Discovery
Systems](../frontiermode/architecture/discovery-systems.md) and [Border
Curve](../frontiermode/architecture/border-curve.md) -- this section only tracks which pieces are
close enough to mint, and in what order, not the technical shape itself.

**Shared infrastructure -- proposed to mint first, since every feature below depends on one or both:**

| Candidate | Proposed name | Scoping status |
|---|---|---|
| Navigator (target-ref + resolver registry, sibling fixture in `BordersBundle`) | Dorothy (rank 20) | Settled enough to mint -- see [discovery-systems.md § Navigation lives in Border](../frontiermode/architecture/discovery-systems.md#navigation-lives-in-border) |
| Border Curve (`BorderCurveFixture`, distance-keyed intensity curves) | Janet (rank 21) | Settled enough to mint -- see [border-curve.md](../frontiermode/architecture/border-curve.md) |

**The six discovery-gradient tools** (per [Boss Discovery § The discovery
gradient](../frontiermode/design/boss-discovery.md#the-discovery-gradient)), each still needing its
own scoping/naming pass:

| Tool | Scoping status |
|---|---|
| Guardian Mobs | Closest to mintable -- `border-curve.md`'s worked example already gives it a concrete shape (`"placement"` + `"difficulty"` curves); still waiting on Game Designer calls (which Border introduces them, old-territory signaling) that don't block minting the node itself |
| Environmental Tells | Open whether it shares a tick handler and `BorderCurve` records with Guardian Mobs, or is a fully separate node -- see [discovery-systems.md § Open questions](../frontiermode/architecture/discovery-systems.md#open-questions) |
| Beacons and Particle Trails | Blocked on the attunement game-rules pass -- plausibly needs its own `LevelScope`-hosted record |
| Ender-eye-style Tracker | Stateless, no attunement -- likely mintable alongside Guardian Mobs |
| Special Compass | Confirmed distinct from Tracker (holds real attunement); blocked on the same attunement game-rules pass as Beacons |
| Player-built Warps | Open whether it's boss-specific infrastructure at all, or reuses `TargetRef::RawPos` + attunement storage -- also unchecked against Satchel for an existing waypoint fixture |

**Proposed sequencing:** Navigator and Border Curve first (nothing else can be scoped precisely
until the shared math/storage exists as real code, not just a wiki proposal); Guardian Mobs and
Tracker next, since neither depends on the still-open attunement rules; Tells once the
tick-handler/shared-curve question resolves; Beacons, Compass, and Warps last, gated on the
project-owner/Game-Designer attunement pass named on both architecture pages.

**Proposed names are proposals, not reservations.** Nothing above is spent until a node is actually
minted with `bhrm` -- writing "Dorothy" here doesn't burn it, only `bhrm new` does.

## Related pages

- [Boss Discovery Systems](../frontiermode/architecture/discovery-systems.md) -- technical shape
  for Navigator and the six discovery tools
- [Border Curve](../frontiermode/architecture/border-curve.md) -- technical shape for the
  intensity-curve infrastructure
- [Boss Discovery](../frontiermode/design/boss-discovery.md) -- the design intent this cluster
  implements
- [RM_FRO_017](../../roadmap/RM_FRO_017_donna.md) ("Donna") -- the Tier 1 convergence that opened
  this epoch
- [RM_FRO_023](../../roadmap/RM_FRO_023_kathleen.md) ("Kathleen") -- the Tier 2 convergence this
  cluster's real nodes will fold into
- [RM_FRO_024](../../roadmap/RM_FRO_024_donna-01.md) / [RM_FRO_025](../../roadmap/RM_FRO_025_donna-02.md)
  -- this epoch's unplanned-rework counterpart to this page's planned-work tracking
- [BHRM — Roadmap Conventions](../meta/bhrm.md) -- persona-name and epoch-maintenance conventions
- [FrontierMode Operational Tiers](operational-tiers.md) -- the tier framework Kathleen belongs to
