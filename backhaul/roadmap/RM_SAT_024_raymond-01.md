---
id: RM_SAT_024
uid: RM_SAT
number: 24
kind: work
status: open
title: Raymond epoch maintenance 1
owner: Arryn
depends_on:
- RM_SAT_023
created: '2026-08-31'
superseded_by: null
ticket: SAT_043
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_SAT
<!-- bh-header:end -->

## Raymond epoch maintenance 1

**Maintenance container, not a feature deliverable.** Gathers unplanned rework/health work found
during the "Raymond epoch" -- the span opened by [RM_SAT_023](RM_SAT_023_raymond.md) ("Raymond")
reaching -- that does not belong on a persona-named node because it is not building toward a
design goal, it is paying down something a ruling or a build turned up along the way. See
[BHRM -- Roadmap Conventions § Epoch maintenance nodes](../wiki/meta/bhrm.md#epoch-maintenance-nodes-containers)
for the full convention. No `ticket:` field on purpose -- a container can gather more than one
ticket over its life; what actually landed here is tracked in this log, not a single frontmatter
pointer. Unlike the Donna epoch's own containers, this one was not opened empty at epoch start --
nothing had surfaced needing one until this design pass, so it opens now, alongside its first item.

- 2026-08-31: Node opened, alongside its first item below.

- 2026-08-31: **SatchelJig's per-fixture extensible overload/hook system needs a proper API-style
  doc, not scattered mentions.** Surfaced on the FrontierMode side during the [Border
  Pregeneration](../wiki/frontiermode/architecture/border-pregeneration.md) design pass --
  `fixture.md` documents `onCreated()`/`onLoaded()` but not `isReady()` or `onJigTick()`, both
  real, already-relied-on-elsewhere per-fixture-instance hooks (see that page's own Open Questions,
  and [RM_FRO_024](RM_FRO_024_donna-01.md)'s 2026-08-31 log entry, for where this was first
  flagged and why it matters -- a same-name collision with the unrelated, already-documented
  foundation-level `Satchel.isReady()`/`LogicalFoundation.isReady()` in `runtime.md` made it easy
  to miss). Scope for the doc: the full per-fixture-instance lifecycle surface `SatchelFixture`
  exposes for overriding -- `onCreated()`, `onLoaded()`, `onRemoved()`, `onJigTick()`, `isReady()`
  -- written up the way an API reference documents an extension point (signature, when it fires,
  what state is guaranteed at that point, what overriding it is and is not for), not narrated in
  `runtime.md`'s prose the way it is today. Not actioned yet, just parked here.

## Required By

*(computed — nothing depends on this yet)*
