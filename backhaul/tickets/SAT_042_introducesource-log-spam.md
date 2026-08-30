---
id: SAT_042
uid: SAT
number: 42
client: Satchel
status: done
title: introduceSource debug-logs every idempotent poll
context: LogicalFoundation.introduceSource logs before the hasScope guard, spamming
  DEBUG every MobJig poll cycle for every already-tracked mob.
priority: normal
opened: '2026-08-28'
closed: '2026-08-29'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Confirmed via live server log (project owner) and source read (Lead Dev, FrontierMode session --
Satchel is out of this session's assigned scope, hence a ticket rather than a direct fix).

**Symptom:** DEBUG log spam, one `[JigLifecycle][introduceSource] MobScope[...]... -> JigKey[...]`
line per tracked mob per jig per poll cycle, forever -- e.g. every ~1s (MobJig's throttled
reconcile cadence) for every already-scoped bat/rabbit/etc, against both
`satchelmobtracker:mob_tracker_jig` and `frontiermode:boss_mob_jig`. Not new introductions --
the same mob, over and over, at the poll's own interval.

**Root cause:** `LogicalFoundation.introduceSource(Object source)`
(`common/jig/guts/LogicalFoundation.java`, the `for (JigInfo ji : jigInfos.values())` loop) calls
`OUT.debug(...)` unconditionally for every jig that resolves the source, *before* the
`if (!ji.hasScope(scope)) { ji.addScope(scope, source); }` guard. The guard itself is correct --
no redundant `addScope` calls happen, state mutation really is idempotent as the surrounding
comments claim (`MobJig.reconcile`'s own comment: "Idempotent via JigInfo.hasScope's existing
guard -- safe to call every cycle even for a mob already scoped"). The comment is only true for
the *mutation*; the log statement sits outside that guard and fires every call regardless, and
`MobJig.reconcile` calls `introduceSource(mob)` unconditionally for every currently-interested mob
on every throttled cycle -- so in practice this is once per tracked mob per jig per poll interval,
indefinitely, at DEBUG level.

**Suggested fix:** move the `OUT.debug(...)` call inside the `if (!ji.hasScope(scope))` block, so
it only logs on a genuine first-time introduction. No behavior change to the idempotency guarantee
itself -- purely a logging fix.

## Log

- 2026-08-28: Ticket opened, diagnosed against real source and a live server log the project owner
  shared. Root cause and suggested fix above; not fixed here since Satchel is outside this
  session's assigned scope (Lead Dev, FrontierMode).

- 2026-08-28: **Built.** Project owner authorized stepping outside this session's assigned
  FrontierMode scope to fix Satchel directly. Applied exactly the suggested fix in
  `LogicalFoundation.introduceSource` (`common/jig/guts/LogicalFoundation.java`): moved the
  `OUT.debug(...)` call inside the existing `if (!ji.hasScope(scope))` block, so it only fires on
  a genuine first-time introduction. No change to the mutation guard itself or to
  `MobJig.reconcile`'s call site -- its own comment ("Idempotent via JigInfo.hasScope's existing
  guard") already described the mutation correctly and needed no edit. Brace-balance checked
  (62/62); no javac/Gradle in this sandbox to compile-check directly -- standing constraint, same
  as every FrontierMode ticket this session. Not marking resolved: real build + a live server run
  confirming the DEBUG spam is gone (and nothing else regressed) are still owed on the project
  owner's own machine.

- 2026-08-29: **Confirmed against two separate live server runs (project owner), verified against
  real `debug.log` evidence, not self-report.** Every mob scoped across both sessions shows exactly
  2 `[JigLifecycle][introduceSource]` lines total -- one per jig it resolves to
  (`satchelmobtracker:mob_tracker_jig`, `frontiermode:boss_mob_jig`) -- never repeating on later
  `MobJig` poll cycles, confirmed by grepping per-UUID occurrence counts across a full run. This is
  exactly the intended behavior: log only on genuine first-time introduction. Closing.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
