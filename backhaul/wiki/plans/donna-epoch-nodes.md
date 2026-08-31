---
id: plans/donna-epoch-nodes
category: plans
slug: donna-epoch-nodes
title: Donna Epoch Nodes
summary: Candidate RM_FRO nodes for the Donna epoch's Tier 2 discovery-gradient cluster.
  Its shared-infrastructure trio (Navigator, Border Curve, Border Pregeneration) is
  minted -- RM_FRO_026/027/028; the six discovery tools remain staged here, pending
  their own scoping pass against that trio's real code.
keywords: null
status: draft
updated: '2026-08-31'
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

**Shared infrastructure -- proposed to mint first, since every feature below depends on one or more:**

| Candidate | Proposed name | Status |
|---|---|---|
| Navigator (target-ref + resolver registry, sibling fixture in `BordersBundle`) | Dorothy (rank 20) | **Minted: [RM_FRO_026](../../roadmap/RM_FRO_026_dorothy.md).** Ticketed to Lead Dev as [FRO_065](../../tickets/FRO_065_dorothy-build.md), time-critical -- pushed ahead of its two table-mates below |
| Border Curve (`BorderCurveFixture`, distance-keyed intensity curves) | Janet (rank 21) | **Minted: [RM_FRO_027](../../roadmap/RM_FRO_027_janet.md).** |
| Border Pregeneration (`BorderPregenFixture`, proactive throttled terrain generation; partially supersedes `boss.md`'s Spawn Algorithm) | Diane (rank 22) | **Minted: [RM_FRO_028](../../roadmap/RM_FRO_028_diane.md),** with its own Open Questions carried onto the node rather than closed first -- see that node for the list |

**The six discovery-gradient tools -- all now minted** (2026-08-31), scoped against completed infrastructure:

| Tool | Status | Node |
|---|---|---|
| Guardian Mobs | Open, ready for implementation | [RM_FRO_029](../../roadmap/RM_FRO_029_gloria.md) (Gloria, rank 23) |
| Environmental Tells | Open, ready for implementation | [RM_FRO_030](../../roadmap/RM_FRO_030_janice.md) (Janice, rank 24) |
| Beacons | Blocked on attunement game-rules pass | [RM_FRO_031](../../roadmap/RM_FRO_031_joan.md) (Joan, rank 25) |
| Ender-eye-style Tracker | Deferred, no blockers | [RM_FRO_032](../../roadmap/RM_FRO_032_elizabeth.md) (Elizabeth, rank 26) |
| Special Compass | Blocked on attunement game-rules pass | [RM_FRO_033](../../roadmap/RM_FRO_033_marilyn.md) (Marilyn, rank 27) |
| Player-built Warps | Open, needs Satchel audit | [RM_FRO_034](../../roadmap/RM_FRO_034_virginia.md) (Virginia, rank 28) |

**Sequencing:** Guardian Mobs and Environmental Tells can proceed to implementation immediately (no
blockers). Beacons and Compass are held pending the attunement game-rules pass. Tracker and Warps
are deferred to a future round.

**This staging plan is complete.** All six nodes are minted; the next phase is implementation as
blockers clear.

**Decision: all three infrastructure nodes completed; all six discovery-gradient nodes now minted.**
Navigator, Border Curve, and Border Pregeneration are built and provide the code baseline for the
six discovery tools. All six nodes are minted (2026-08-31) with appropriate status markers: Guardian
Mobs and Environmental Tells are open and ready for implementation; Beacons and Compass are marked
blocked pending the attunement game-rules pass; Tracker is deferred (no blockers, but lower priority);
Warps is open but flagged for a quick Satchel audit before proceeding.

This plan is complete. Further work is implementation of Guardian Mobs and Environmental Tells, and
coordination with the Game Designer on the attunement pass that unblocks Beacons and Compass.

## Related pages

- [Boss Discovery Systems](../frontiermode/architecture/discovery-systems.md) -- technical shape
  for Navigator and the six discovery tools
- [Border Curve](../frontiermode/architecture/border-curve.md) -- technical shape for the
  intensity-curve infrastructure
- [Border Pregeneration](../frontiermode/architecture/border-pregeneration.md) -- technical
  shape for the proactive terrain-generation infrastructure
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
