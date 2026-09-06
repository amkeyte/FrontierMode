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
ticket: null
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
  `runtime.md`'s prose the way it is today.

- 2026-08-31: **Resolved same day** — [SAT_043](../tickets/SAT_043_fixture-lifecycle-api.md) added
  a "Per-Fixture Lifecycle API Reference" section to `fixture.md` covering all five hooks
  (`onCreated()`, `onLoaded()`, `onRemoved()`, `onJigTick()`, `isReady()`) with signatures, firing
  guarantees, state contracts, and the `Satchel.isReady()` disambiguation this item called for.

- 2026-09-03: **`MobDied`/`MobGainedInterest`/`MobLostInterest`.** Surfaced designing FrontierMode's
  Boss defeat-cascade gating (FRO_064): Boss's defeat detection reaches raw Forge directly
  (`LivingDeathEvent`), the one inconsistency in an otherwise `ScopeEvent`-mediated design.
  Architect ruling written onto [Mob Lifecycle
  Signals](../wiki/satchel/architecture/mob-lifecycle-signals.md) -- three new signals routing
  Boss's defeat detection and presence bookkeeping through Satchel's own dispatch instead. Build
  routed to [SAT_044](../tickets/SAT_044_mob-lifecycle-signals-build.md).

- 2026-09-03: **SAT_044 split.** It mixed the Satchel-side signal-primitive build with a
  FrontierMode-side consumer migration (Boss's defeat detection, `BOSS_MOB_JIG`) -- two different
  clients' code in one ticket. SAT_044 keeps the signal primitives; the consumer migration moved
  to [FRO_086](../tickets/FRO_086_boss-mobdied-migration.md) ("Donna_02", FrontierMode's own
  epoch container), which depends on SAT_044 landing first.

## Required By

<!-- required-by:start -->
*(computed — nothing depends on this yet)*
<!-- required-by:end -->
