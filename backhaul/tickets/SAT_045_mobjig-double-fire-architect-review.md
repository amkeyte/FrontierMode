---
id: SAT_045
uid: SAT
number: 45
client: Satchel
status: closed
title: MobJig double-fires MobGainedInterest/MobLostInterest with multiple configs
context: '[Architect] MobJig.onLoad/onUnload class-level override fires once per MobJig
  config instance -- two configs tracking the same UUID via global MobInterestRegistry
  causes double-fire.'
priority: normal
opened: '2026-09-04'
closed: '2026-09-04'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Surfaced during SAT_044/FRO_086 playtest. `MobJig.onLoad`/`onUnload` overrides (added in
SAT_044) are class-level, so they fire once per `MobJig` config instance that tracks a given
UUID. With two configs currently registered -- `frontiermode:boss_mob_jig` and
`satchelmobtracker:mob_tracker_jig` (SatchelHealth's canary tracker) -- both see boss UUIDs
via the global `MobInterestRegistry`. Both fire `MobGainedInterest`/`MobLostInterest` for the
same mob.

Observed in logs: each boss spawn/death produced two `[MobJig] MobGainedInterest` /
`[MobJig] MobLostInterest` log lines, one per config instance.

## Impact

- Consumers subscribed to `MobGainedInterest`/`MobLostInterest` may receive the event twice
  per mob lifecycle transition if multiple `MobJig` configs are registered and both track the
  same UUID.
- `BossModule.onBossMobScopeLoaded`/`onBossMobScopeUnloaded` are currently idempotent enough
  that double-fire is benign in practice (FRO_086 closed without incident), but this is
  coincidental -- not a guarantee.

## Questions for Architect

1. Should `MobGainedInterest`/`MobLostInterest` be deduplicated at the bus/producer level
   (post only once per UUID per lifecycle transition, regardless of how many MobJig configs
   track it)?
2. Or should consumers be designed to be idempotent against duplicate signals?
3. Is the global `MobInterestRegistry` the right scope for MobJig presence checks, or should
   each config instance's interest be isolated?

## Standing constraint

No Gradle in the agent sandbox.

## Log

- 2026-09-04: Ticket opened. Surfaced during SAT_044/FRO_086 playtest. Pre-existing
  architectural gap, not a regression from those tickets. Filed for Architect review before
  additional `MobGainedInterest`/`MobLostInterest` consumers are added.
- 2026-09-04: **Closed.** Resolved consumer-side via `ScopeEvent.isJig(JigKey<?>)` —
  a new `final` method on the `ScopeEvent` base class. Identity comparison (`==`) on the
  `JigKey` singleton; no producer-side dedup needed. `BossModule.onBossMobScopeLoaded` and
  `onBossMobScopeUnloaded` now guard with `if (!event.isJig(FrontierKeys.BOSS_MOB_JIG)) return;`
  replacing the previous manual `info.jigInfo().key.equals(...)` blocks. Any future consumer of
  `MobGainedInterest`/`MobLostInterest` uses the same one-liner guard.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
