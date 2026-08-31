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

**The six discovery-gradient tools** (per [Boss Discovery § The discovery
gradient](../frontiermode/design/boss-discovery.md#the-discovery-gradient)), each still needing its
own scoping/naming pass:

| Tool | Scoping status |
|---|---|
| Guardian Mobs | Closest to mintable -- `border-curve.md`'s worked example already gives it a concrete shape (`"placement"` + `"difficulty"` curves); still waiting on Game Designer calls (which Border introduces them, old-territory signaling) that don't block minting the node itself |
| Environmental Tells | Resolved -- own tick handler, own `"tell"`-purpose `BorderCurve` record, matching Guardian Mobs' shape without sharing it; see [discovery-systems.md § Environmental Tells](../frontiermode/architecture/discovery-systems.md#environmental-tells). No longer gated on that question -- scoping lane relative to Guardian Mobs/Tracker not reassessed since |
| Beacons and Particle Trails | Blocked on the attunement game-rules pass -- plausibly needs its own `LevelScope`-hosted record |
| Ender-eye-style Tracker | Deferred out of this design pass entirely, alongside Player-built Warps -- see [discovery-systems.md § Ender-eye-style Tracker](../frontiermode/architecture/discovery-systems.md#ender-eye-style-tracker). Not blocked on anything technical (still stateless, no attunement); just not next in line |
| Special Compass | Confirmed distinct from Tracker (holds real attunement); blocked on the same attunement game-rules pass as Beacons |
| Player-built Warps | Open whether it's boss-specific infrastructure at all, or reuses `TargetRef::RawPos` + attunement storage -- also unchecked against Satchel for an existing waypoint fixture |

**Decision: minting the shared-infrastructure row now, in order -- Navigator, then Border
Curve, then Border Pregeneration.** Nothing else in this cluster can be scoped precisely until
this trio exists as real code, not just a wiki proposal, so waiting on their own remaining open
questions to fully close first would only stall the whole cluster behind them. Border
Pregeneration mints with its Open Questions section still carrying real unresolved items (the
missed-trigger watchdog, retry/reroll behavior, throttle budget) -- those ride on the node as
known open items for Lead Dev to build around or flag back on, not gates on starting. Guardian
Mobs is next after this trio, since it doesn't depend on the still-open attunement rules; Tells'
own tick-handler/shared-curve question has since resolved too (see its row above), though its lane
relative to Guardian Mobs hasn't been reassessed since that changed; Beacons and Compass last,
gated on the project-owner/Game-Designer attunement pass named on both architecture pages. Tracker
and Warps are deferred out of this design pass entirely (see their rows above) and sit outside this
sequencing until picked back up. **The six discovery-gradient tools are not minted this round** --
they still need their own scoping/naming pass once Navigator/Border Curve/Border Pregeneration
exist as real code to scope precisely against, per the reasoning above.

**Proposed names are proposals, not reservations.** Nothing above is spent until a node is actually
minted with `bhrm` -- writing "Dorothy" here doesn't burn it, only `bhrm new` does.

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
