---
id: FRO_044
uid: FRO
number: 44
client: FrontierMode
status: done
title: Architect prep for Karen (RM_FRO_019)
context: 'Two open design calls before Lead Dev starts: growCenteredOn() vs a two-call
  sequence, and the defeat-detection race fallback choice. Also reconcile boss.md''s
  defeat-growth-gap section against what FRO_043 actually built.'
priority: high
opened: '2026-08-24'
closed: '2026-08-24'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Architect-lane work standing between now and Lead Dev opening
[RM_FRO_019](../roadmap/RM_FRO_019_karen.md) ("Karen," boss defeat → border-growth caller) — the
last node in FrontierMode's Tier 1 core loop. Now genuinely startable:
[RM_FRO_018](../roadmap/RM_FRO_018_shirley.md) ("Shirley") is `resolved`, real-playtest-verified,
via [FRO_043](FRO_043_boss-build.md).

Three items. Item 1 is the node's own long-flagged open design call.

## 1. `growCenteredOn(BlockPos center)` vs. a two-call sequence — settle it

[RM_FRO_019](../roadmap/RM_FRO_019_karen.md)'s own log has flagged this since 2026-08-16 as
"Architect's call before Lead Dev starts" and never closed it. `BorderAPI.addBorder()` accepts an
arbitrary center but only touches the border list, not the canonical `borderPath` — only
`BordersPathFacet.grow()` appends to the path, and `grow()` always computes its own center via
`DefaultBorderRules.chooseNextCenter()`. Two ways to satisfy "centered on the defeated boss's home
block" while keeping path-append atomic:

1. **New method, `BordersPathFacet.growCenteredOn(BlockPos center)`** — same two-step shape
   `grow()` already has internally, but taking an explicit center. Small, targeted addition to
   Border's own surface. **Recommended** by the node's own log — keeps "create + path-append"
   atomic the way every other path-mutating call already is.
2. **Two-call sequence from Karen's own handler** — no Border-surface change, but pushes the
   atomicity requirement onto the caller instead of Border guaranteeing it.

Pick one and record the reasoning on [Border](../wiki/frontiermode/architecture/border.md) and
[RM_FRO_019](../roadmap/RM_FRO_019_karen.md) directly — Lead Dev shouldn't be the one deciding this
mid-build.

## 2. Defeat-detection race — choose the fallback mechanism

A `LivingDeathEvent` on an entity carrying no `BossMobFixture` yet (a fast load-then-death sequence
beating `MobJig`'s ~20-tick poll cadence) needs a fallback before concluding "not a tracked boss."
The node's own 2026-08-17 log recommends calling `MobScope.getFor(mob)` synchronously in the
handler (the entity is loaded by definition — it just died) over a direct `BossFixture` scan, but
explicitly "leaves the actual choice to whoever implements this." That's this ticket's job, not
Lead Dev's — pick one and record it, same as item 1.

## 3. Reconcile `boss.md`'s defeat/growth-gap section against what FRO_043 actually built

[Boss § Defeat detection and the border-growth gap](../wiki/frontiermode/architecture/boss.md#defeat-detection-and-the-border-growth-gap)
was written before FRO_043 landed. Check it still matches the shipped shape — in particular,
`BorderModule.onBordersScopeLoaded()`'s built bootstrap hook calls `BossAPI.createBoss(level,
border)` (confirmed against source), not the more generic "calls into `BossModule`'s
record-creation directly" phrasing [RM_FRO_019](../roadmap/RM_FRO_019_karen.md)'s own log and
`boss.md` both currently use — Karen's own post-defeat call should almost certainly be the same
`BossAPI.createBoss(level, border)` entry point for consistency, but confirm `BossAPI`'s actual
shape (checked 2026-08-24: `boss/BossAPI.java` exists, 3570 bytes) rather than assume it before
writing Karen's own handler spec.

## Known gap, not blocking

[RM_FRO_018](../roadmap/RM_FRO_018_shirley.md)'s own done bar still promises `/kill`-triggered
bootstrap self-heal — a mechanism `boss.md` walked back to the one-shot `seeded`-flag bootstrap
FRO_043 actually built, punting real self-heal to this node. Karen's own design already covers the
death-detection half; whether that alone satisfies Shirley's stale done-bar wording, or whether a
dedicated self-heal path is still owed on top, is worth a one-line ruling in this pass rather than
left implicit.

## Log

- 2026-08-24: Ticket opened. Prerequisite ([RM_FRO_018](../roadmap/RM_FRO_018_shirley.md)
  "Shirley") resolved and real-playtest-verified via [FRO_043](FRO_043_boss-build.md).

- 2026-08-24: **All three items settled, ticket closed (Architect).** Full ruling recorded on
  [RM_FRO_019](../roadmap/RM_FRO_019_karen.md)'s own 2026-08-24 log entry and reflected in its
  body prose, not restated here — summary:

  1. **`growCenteredOn` (option 1) ruled**, three mirrored additions verified against real source
     shapes (`BorderLogic.grow(Border)`/`BordersPathFacet.grow()`/`BorderAPI.grow(Level)`):
     `BorderLogic.growCenteredOn(Border, BlockPos)`, `BordersPathFacet.growCenteredOn(BlockPos)`,
     `BorderAPI.growCenteredOn(Level, BlockPos)`. Flagged one case `grow()` doesn't have to handle
     that this does — an absent path tip is a corruption case here, not a bootstrap case, and
     should log loudly rather than silently falling back to `getInitial()`.
  2. **Defeat-detection race fallback (option b) ruled** — `MobScope.getFor(mob)` synchronous
     call, matching what the node's own handler prose already assumed; this closes the formal gap
     between that assumption and the 2026-08-17 log entry's "leaving the actual choice to whoever
     implements this."
  3. **`boss.md` already matches shipped source** — checked `BossAPI.java` directly:
     `createBoss(Level level, Border border)` is real, and
     [Boss § Defeat detection and the border-growth gap](../wiki/frontiermode/architecture/boss.md#defeat-detection-and-the-border-growth-gap)
     already names it explicitly (updated during FRO_043's own build). Only
     [RM_FRO_019](../roadmap/RM_FRO_019_karen.md)'s own older prose still used the generic
     "`BossModule`'s record-creation" phrasing — corrected in its body text, with a naming-note
     bracket added to the one historical log entry that also used it, per this project's
     don't-rewrite-history convention.

  **Known gap ruling:** `/kill` fires `LivingDeathEvent` for a `LivingEntity` in vanilla/Forge
  (standard behavior, not verified against this project's own decompiled source — no Gradle access
  in the agent sandbox, per this project's standing constraint) — if that holds, Karen's own
  defeat handler (with the race fallback above) already catches a `/kill`ed boss identically to a
  combat-killed one, which would satisfy Shirley's stale `/kill`-self-heal done-bar wording without
  a separate mechanism. Not asserted as settled fact: added an explicit `/kill`-the-boss case to
  Karen's own done bar's real-play pass so this gets confirmed live rather than assumed. The
  genuinely distinct case — a boss removed by something that never fires `LivingDeathEvent` at all
  — is unaffected either way and stays tracked on `boss.md`'s own "Known gaps," out of this
  ticket's scope.

  Karen ([RM_FRO_019](../roadmap/RM_FRO_019_karen.md)) is now unblocked for Lead Dev — `ticket:`
  field there set to this ticket (FRO_044) for now, same as [RM_FRO_018](../roadmap/RM_FRO_018_shirley.md)
  pointed at [FRO_042](FRO_042_shirley-prep.md) before [FRO_043](FRO_043_boss-build.md) opened; it
  moves to a build ticket once Lead Dev actually opens one.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
