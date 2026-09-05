---
id: RM_FRO_035
uid: RM_FRO
number: 35
kind: work
status: open
title: Donna epoch maintenance 3
owner: Arryn
depends_on:
- RM_FRO_028
created: '2026-09-05'
superseded_by: null
ticket: null
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

## Required By

*(computed — nothing depends on this yet)*
