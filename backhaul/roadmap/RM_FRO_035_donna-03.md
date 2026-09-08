---
id: RM_FRO_035
uid: RM_FRO
number: 35
kind: work
status: resolved
title: Donna epoch maintenance 3
owner: Arryn
depends_on:
- RM_FRO_028
created: '2026-09-05'
superseded_by: null
ticket: FRO_092
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_FRO
<!-- bh-header:end -->

## Donna epoch maintenance 3

**Maintenance container, not a feature deliverable.** Gathers the still-outstanding work that
[RM_FRO_028](RM_FRO_028_diane.md) ("Diane," Border Pregeneration) carried forward at mint time
rather than resolving before its own done bar -- see [Border Pregeneration § Open
questions](../wiki/frontiermode/architecture/border-pregeneration.md#open-questions) for the full
text of each. Diane's own status is `resolved` (done bar met, playtest-verified), but these four
items were explicitly *carried forward*, not closed, when that node minted -- they don't belong on
a persona-named node because they aren't a design goal in their own right, they're loose ends off
one. See [BHRM -- Roadmap Conventions § Epoch maintenance nodes](../wiki/meta/bhrm.md#epoch-maintenance-nodes-containers)
for the full convention; a middle-of-epoch container, not a start/end one, opened now because this
batch is big enough to warrant its own marker rather than sitting as prose on a resolved node. No
`ticket:` field on purpose -- none of the four below has been scoped into real work yet; what
actually gets picked up is tracked in this log as it happens.

**Why this gates every Tier 2 discovery-gradient sibling, not just one:**
[RM_FRO_029](RM_FRO_029_gloria.md) (Gloria) through [RM_FRO_034](RM_FRO_034_virginia.md)
(Virginia) -- all six discovery tools -- depend on [RM_FRO_028](RM_FRO_028_diane.md) directly, the
same shared-infrastructure edge [RM_FRO_030](RM_FRO_030_janice.md) ("Janice") does. None of the
four items below are Environmental-Tells-specific; each is a real gap in Border Pregeneration's
own contract that any consumer placing something against a border's disk inherits equally. Wired
onto all six rather than just Janice for that reason -- see each sibling's own `depends_on` for the
added edge.

**Carried forward, still open:**

- **Retry/reroll if an entire disk somehow fails validation** -- vanishingly unlikely, not
  provably impossible.
- **Exact throttle budget** (chunks per allowed tick, `TickThrottler`'s own interval) -- a tuning
  number, not an architecture decision.
- **Stalled-trigger watchdog** -- a border-creation call site that forgets the pregeneration
  trigger stalls its boss permanently and silently today, with nothing surfaced anywhere. Likely
  eventual answer is a `Rules`-level watchdog scanning for exactly this stuck-forever state.
- **Retroactive pregen vs. grandfathering** -- what an already-progressed world (path borders with
  bosses long since placed the old way) does under this mechanism is unresolved.

**Not carried here:** the fifth item on Diane's own list -- `isReady()`/`onJigTick()`'s
under-documentation on the Satchel side -- is already closed via
[RM_SAT_024](RM_SAT_024_raymond-01.md) ("Raymond epoch maintenance 1")/SAT_043, which added a full
Per-Fixture Lifecycle API Reference to `fixture.md`. Border Pregeneration's own Open Questions
section hasn't been updated to reflect that yet -- noted here so it doesn't get re-carried by
mistake; the page's own wording still needs a pass.

- 2026-09-05: Node opened. Four items pulled forward from [RM_FRO_028](RM_FRO_028_diane.md)'s own
  "Known open items" list, none previously ticketed. Wired as a `depends_on` edge onto
  [RM_FRO_029](RM_FRO_029_gloria.md) through [RM_FRO_034](RM_FRO_034_virginia.md) (all six Tier 2
  discovery-gradient siblings), project owner's explicit call -- these were previously listed as
  unblocked ("ready for implementation" / "no blockers") on [Donna Epoch
  Nodes](../wiki/plans/donna-epoch-nodes.md); that page's sequencing note is being corrected in
  the same pass.

- 2026-09-06: [FRO_091](../tickets/FRO_091_pregen-carryforward-spec.md) opened -- Architect spec-review ticket for all four carried-forward items above, project owner's call. Douglas rules directly on border-pregeneration.md's Open Questions section; a Lead Dev build ticket follows once scope is settled.

- 2026-09-06: FRO_091 closed -- all four carried-forward items ruled, plus a fifth
  (`BorderPregenEvent.Complete` registration discipline) caught untracked during review and folded
  in per project owner's direction. Rulings written onto
  [border-pregeneration.md](../wiki/frontiermode/architecture/border-pregeneration.md#open-questions)'s
  Open Questions section. [FRO_092](../tickets/FRO_092_border-pregen-carryforward-build.md) opened
  as the Lead Dev build ticket for the two items needing real implementation (throttle value,
  stalled-trigger watchdog); `ticket:` field above updated to point at it. This node stays open
  until FRO_092's build lands.

- 2026-09-07: Done bar met and playtest-verified via FRO_092's real build.log plus an extensive live session -- normal pregeneration confirmed across a wide size range (13 to 3209 chunks) under the new `BorderRules.pregenChunksPerBatch()`/`pregenThrottleIntervalTicks()` tunables with no behavior change, and the new 200-tick stalled-trigger liveness check produced zero false positives across multiple full jobs, including one that ran to completion unattended. The disk-validation-failure crash path went unexercised (no real failure occurred; project owner couldn't reliably induce one live) but was independently verified by source read-through -- accepted, not a blocker, per the ticket's own reasoning. Wiki/spec drift between FRO_091's original log-only ruling and the crash-based behavior actually built was caught and corrected on border-pregeneration.md in the same close. Resolved -- this clears the last shared-infrastructure gate on RM_FRO_029/031/032/033/034 (Guardian Mobs, Beacons, Tracker, Compass, Warps), all of which now have every `depends_on` edge satisfied.

## Required By

<!-- required-by:start -->
- [**RM_FRO_029**](RM_FRO_029_gloria.md) — Guardian Mobs epoch 1
- [**RM_FRO_031**](RM_FRO_031_joan.md) — Beacons epoch 1
- [**RM_FRO_032**](RM_FRO_032_elizabeth.md) — Ender-eye-style Tracker epoch 1
- [**RM_FRO_033**](RM_FRO_033_marilyn.md) — Special Compass epoch 1
- [**RM_FRO_034**](RM_FRO_034_virginia.md) — Player-built Warps epoch 1
<!-- required-by:end -->
