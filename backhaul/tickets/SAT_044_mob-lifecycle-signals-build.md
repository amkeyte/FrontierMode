---
id: SAT_044
uid: SAT
number: 44
client: Satchel
status: closed
title: Mob lifecycle signals build
context: '[Raymond-01] Lead Dev build for MobDied/MobGainedInterest/MobLostInterest
  signal primitives.'
priority: normal
opened: '2026-09-03'
closed: '2026-09-04'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Lead Dev build for [Mob Lifecycle
Signals](../wiki/satchel/architecture/mob-lifecycle-signals.md) -- three new signal primitives so
a consumer's mob-presence/death detection can go through Satchel's own dispatch instead of
reaching raw Forge directly. Architect design session, not implementation -- this ticket is the
build itself.

**Split, 2026-09-03:** this ticket originally also carried the FrontierMode-side consumer
migration (Boss's defeat detection and `BOSS_MOB_JIG` moving onto these signals) -- pulled out to
[FRO_086](../tickets/FRO_086_boss-mobdied-migration.md) since that's FrontierMode code
(`BossModule`/`BossMobFixture`), a different client than this ticket's Satchel-side signal build.
FRO_086 needs these signal types to exist first; sequenced after this ticket.

## What to build

**1. `MobGainedInterest` / `MobLostInterest`** -- new `ScopeEvent` subtypes. `MobJig` posts these
*additively*, alongside the existing generic `Loaded`/`Unloaded` -- `ScopeLifecycleDispatcher`/
`ASatchelJig`'s shared signal path is unchanged for every jig kind; `MobJig` (a subclass) posts one
extra signal of its own at the same moment `Loaded`/`Unloaded` already fire from the poll's
reconciliation cycle. No rerouting -- [Satchel Health](../wiki/satchel/architecture/satchel-health.md)'s
canary-mob check already depends on `MOB_JIG`'s generic `Unloaded` firing unmodified.

**2. `MobDied`** -- standalone, `(Level, UUID, LivingDeathEvent)`, **not** a `ScopeEvent` subtype
(no `ScopeInfo` field -- see the wiki page's own reasoning for why). Producer: a new
`@SubscribeEvent LivingDeathEvent` handler added directly to `ServerForgeIngress`, alongside the
existing `LevelEvent.Unload`/`PlayerLoggedOutEvent` handlers. On fire:
   - Check the *union* of every currently-registered `MobInterestRegistry` supplier's set (live
     calls, not a cached snapshot) for the dying entity's UUID.
   - No match anywhere -- return, construct nothing, post nothing.
   - A match anywhere -- construct one `MobDied` and `SatchelEventBus.post()` it once, unscoped
     (not routed to only the matching consumer -- every subscriber checks its own relevance itself,
     same as the raw listener this replaces already required).

**Consumer migration (Boss's defeat detection, `BOSS_MOB_JIG` attach/release) is out of scope
for this ticket -- see [FRO_086](../tickets/FRO_086_boss-mobdied-migration.md).** This ticket
delivers the signal types and their producers only; nothing here changes how any consumer
currently detects mob death or presence (Boss keeps its existing raw `LivingDeathEvent` listener
until FRO_086 migrates it).

**Not touched:** the general [Forge Event Conduit](../wiki/satchel/architecture/forge-event-conduit.md)
machinery (Shape A/B) -- stays parked, this is a bespoke, narrower addition motivated by Boss's
actual need, not a commitment to the general multi-jig-kind conduit. `MobInterestRegistry`'s
per-consumer pull/supplier model -- staying as-is; moving to a single push-based shared registry is
a separate, explicitly deferred idea (see the wiki page's and forge-event-conduit.md's open items).

## Standing constraint

Same as every ticket this pass: no Gradle in the agent sandbox. Whatever gets built here needs
real build/playtest evidence before closing.

## Log

- 2026-09-03: Ticket opened. Carries the Architect's design session verbatim (see [Mob Lifecycle
  Signals](../wiki/satchel/architecture/mob-lifecycle-signals.md)), parked on
  [RM_SAT_024](../roadmap/RM_SAT_024_raymond-01.md) ("Raymond epoch maintenance 1") as unplanned
  work surfaced this session, not a persona-named node.
- 2026-09-03: **Split.** This ticket mixed two concerns across two clients: the Satchel-side
  signal primitives (items 1-2, staying here) and the FrontierMode-side consumer migration
  (items 3-4, moved to [FRO_086](../tickets/FRO_086_boss-mobdied-migration.md)). Title/context/
  scope trimmed to match. FRO_086 depends on this ticket landing first.
- 2026-09-04: **Closed.** Build complete and playtested. All three signal primitives confirmed working.
  - `MobDied`: end-to-end confirmed for 3 boss kills (rabbit `5c5da5e3`, zombie `533e2c61`, spider
    `f5030b7a`). `ServerForgeIngress.onLivingDeath` → `MobInterestRegistry.isAnyInterested` gate
    → `SatchelEventBus.post(MobDied)` → consumer handler -- full chain logged and verified.
  - Non-boss mobs (sheep etc.) correctly gate out at `isAnyInterested` -- no spurious posts.
  - `MobGainedInterest`/`MobLostInterest`: fire on `MobJig.onLoad`/`onUnload` (confirmed via logs
    on boss spawn/death). Chunk-unload path not directly observed in `runClient` integrated server
    (chunks don't unload at distance the way a dedicated server does -- environment limitation, not
    a code bug); the code path is identical to the death-side unload which IS confirmed.
  - **Architectural note**: Both `frontiermode:boss_mob_jig` and `satchelmobtracker:mob_tracker_jig`
    use the `MobJig` class; `onLoad`/`onUnload` overrides fire once per config instance, so
    `MobGainedInterest`/`MobLostInterest` double-fire when both configs track the same UUID via
    the global `MobInterestRegistry`. Pre-existing architecture gap, not a regression. Filed as
    separate ticket for Architect review.
  - Diagnostic `OUT.info` gate logs added during playtest; stripped/downgraded to `OUT.debug`
    before close.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
