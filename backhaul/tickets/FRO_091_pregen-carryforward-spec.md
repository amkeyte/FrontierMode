---
id: FRO_091
uid: FRO
number: 91
client: FrontierMode
status: done
title: 'Border Pregen carryforward: spec review'
context: RM_FRO_035's 4 carried-forward Diane items need Architect rulings before
  Lead Dev scopes them.
priority: normal
opened: '2026-09-06'
closed: '2026-09-06'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Architect spec-review ticket for [RM_FRO_035](../roadmap/RM_FRO_035_donna-03.md) ("Donna epoch
maintenance 3") -- the four items [RM_FRO_028](../roadmap/RM_FRO_028_diane.md) ("Diane," Border
Pregeneration) carried forward at mint time rather than resolving before its own done bar. Same
split as [FRO_058](FRO_058_boss-mutation-validation-reconciliation.md)/[FRO_059](FRO_059_border-proposal-center-bounds-id-display.md)
-> [FRO_060](FRO_060_boss-border-mutation-validation-build.md): this ticket is the ruling: a Lead
Dev build ticket follows once scope is settled, not opened here.

## Why this ticket, why now

RM_FRO_035 gates all five remaining Tier 2 discovery-gradient siblings --
[RM_FRO_029](../roadmap/RM_FRO_029_gloria.md) (Guardian Mobs),
[RM_FRO_031](../roadmap/RM_FRO_031_joan.md) (Beacons),
[RM_FRO_032](../roadmap/RM_FRO_032_elizabeth.md) (Tracker),
[RM_FRO_033](../roadmap/RM_FRO_033_marilyn.md) (Special Compass), and
[RM_FRO_034](../roadmap/RM_FRO_034_virginia.md) (Warps) -- none of which can open until it clears.
It does not gate [RM_FRO_030](../roadmap/RM_FRO_030_janice.md) (Environmental Tells, FRO_087),
which depends on Diane directly and is unaffected by this ticket either way.

## What to decide

Per [Border Pregeneration § Open questions](../wiki/frontiermode/architecture/border-pregeneration.md#open-questions)
and RM_FRO_035's own carried-forward list -- four items, each needs a real ruling (build shape,
or an explicit accepted-risk/defer call) before Lead Dev can scope real work against it:

1. **Retry/reroll if an entire disk fails validation.** Vanishingly unlikely per Diane's own
   note, not provably impossible -- worth a real mechanism, or an accepted-risk call?
2. **Exact throttle budget** -- chunks per allowed tick, `TickThrottler`'s own interval. A tuning
   number, not an architecture decision, but needs a real value before it can be build-verified.
3. **Stalled-trigger watchdog.** A border-creation call site that forgets to trigger
   pregeneration stalls its boss permanently and silently today, nothing surfaced anywhere.
   Diane's own page guesses the shape (a `Rules`-level scan for exactly this stuck-forever
   state) -- confirm or correct that shape and where it lives before it's buildable.
4. **Retroactive pregen vs. grandfathering.** What an already-progressed world (path borders with
   bosses long since placed the old way) does under this mechanism -- unresolved.

Write rulings directly onto [border-pregeneration.md](../wiki/frontiermode/architecture/border-pregeneration.md#open-questions),
same discipline as FRO_058/FRO_059's rulings onto boss.md/border.md -- correct/replace each open
question with the actual decision, don't leave the question text sitting alongside the answer.

## Log

- 2026-09-06: Rulings written directly onto border-pregeneration.md's Open Questions section (project owner + Architect, this ticket): (1) retry/reroll on disk-validation failure -- no mechanism, accepted risk, warn loudly if it ever fires. (2) exact throttle budget -- a Rules/DefaultBorderRules tunable coefficient, Lead Dev picks the value. (3) stalled-trigger watchdog -- no new module; reuse BorderPregenFixture's existing onJigTick()/TickThrottler with a second, longer-interval throttler and an OUT.warn, no new event for now. (4) retroactive pregen vs. grandfathering -- moot, project owner's call: no old-world compatibility requirement exists at this stage (worlds get reset for testing). A fifth item found during review -- whether BorderPregenEvent.Complete needs JigConfigValidator-style registration discipline, untracked by both Diane's own carryforward list and RM_FRO_035 -- was folded into this ticket's scope per project owner's direction and ruled no, fine as-is, following the MobDied precedent already claimed for it. All five now marked resolved on the page. Closing; Lead Dev build ticket follows for the throttle value and stalled-trigger watchdog code.
- 2026-09-06: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
