---
id: RM_SAT_023
uid: RM_SAT
number: 23
kind: convergence
status: reached
title: Scope generalizes beyond Level
owner: Arryn
depends_on:
- RM_SAT_020
- RM_SAT_021
- RM_SAT_022
created: '2026-08-23'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_SAT
<!-- bh-header:end -->

## Scope generalizes beyond Level

Marks the point where `SatchelScope` is demonstrably a general abstraction rather than a
`Level`-shaped one with aspirations. Three real jig kinds exist, each with a live consumer, each
built against a genuinely different ingress model — and the third one works identically on both
sides.

This is the milestone [RM_SAT_020](RM_SAT_020_jerry.md) ("Jerry") argued for when it opened:
*"`Scope` is meant to generalize beyond `Level` — players, mob bosses, anything a bundle's
fixtures operate on."* Jerry proved it once. This node is where that claim stops being a single
data point.

## What it gathers

- **[RM_SAT_020](RM_SAT_020_jerry.md) ("Jerry") — `PlayerJig`/`PlayerScope`**, `resolved`.
  Identity-tied rather than level-derived, surviving a dimension change intact. Consumed by
  FrontierMode's `BorderModule`/`BorderAPI`.
- **[RM_SAT_021](RM_SAT_021_frank.md) ("Frank") — `MobJig`/`MobScope`**, `resolved`. The first
  poll-driven ingress: presence is actively re-verified rather than announced by a Forge
  join/leave event, because a tracked mob spends most of its life in an unloaded chunk.
  Dedicated-server verified.
- **[RM_SAT_022](RM_SAT_022_roger.md) ("Roger") — side-agnostic `MobJig`**, `resolved`. The last
  piece, and the one that makes the claim honest: a jig kind whose mechanism does not assume a
  side.

## Why this is a convergence and the facade vision isn't

[Universal Sidedness Facade](../wiki/satchel/architecture/facade-vision.md) describes a direction
with no decomposition and, by its own heading, exists to hold open questions rather than resolve
them. A convergence in this project marks a state that is *reached* — prototype hardened,
persistence model landed, foundation runtime verified end-to-end. A direction has no such moment.

That mismatch is why the previous node in this slot (RM_SAT_018, "Edward") was retired rather than
repointed: it was a roadmap-side bookmark for a wiki page, opened 2026-08-14 ahead of any work
existing to gather, and it never gathered any. Its one inserted piece, RM_SAT_019, was moved out
the following day by a convergence-gate correction, leaving a convergence whose only dependency
was another convergence and which nothing depended on. Same shape, same cause, and the same
outcome as RM_FRO_014/RM_FRO_016 on the other graph — see
[RM_FRO_010](RM_FRO_010_susan.md)'s own account of that, and
[BKHL_004](../tickets/BKHL_004_deprecated-convergence-tracking.md) for why deletion rather than
deprecation is the available move.

The facade vision keeps its page. When it decomposes into two or three concretely scoped pieces, a
convergence to gather *those* is the right time to open one — the sequencing
[FrontierMode Operational Tiers](../wiki/plans/operational-tiers.md) already prescribes for its own
Tier 2-3.

## Reaching this node

Reached 2026-08-24. All three children resolved, and the thing worth confirming before flipping —
Roger's side-agnostic mechanism verified on both sides, not only argued — is confirmed:
[SAT_041](../tickets/SAT_041_mobjig-side-agnostic-build.md)'s second, longer verification run
showed a client-scoped `MobScope` surviving thirteen consecutive reconcile cycles over a full
minute, then unloading cleanly with no violation on a real teardown, against a real dedicated
server plus a connected client. Same standard [RM_SAT_017](RM_SAT_017_paul.md) ("Paul") used.

- 2026-08-24: **Reached — flipped by PM.** All three children (`RM_SAT_020`/`RM_SAT_021`/
  `RM_SAT_022`) confirmed `resolved`; Roger's side-agnostic mechanism is live-verified per
  [SAT_041](../tickets/SAT_041_mobjig-side-agnostic-build.md), not just argued. Flagged as ready by
  [FRO_042](../tickets/FRO_042_shirley-prep.md)'s own closing note, which found this node's prose
  stale while closing out unrelated work and correctly left the flip itself to PM.
- 2026-08-23: Node opened by PM, replacing the retired RM_SAT_018 as this graph's forward
  convergence. Project owner's call.

## Required By

<!-- required-by:start -->
- [**RM_SAT_024**](RM_SAT_024_raymond-01.md) — Raymond epoch maintenance 1
<!-- required-by:end -->
