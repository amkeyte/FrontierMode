---
id: FRO_048
uid: FRO
number: 48
client: FrontierMode
status: open
title: Path grow doesn't create paired boss
context: Manual path grow doesn't create a paired boss record, unlike the automatic
  bootstrap grow.
priority: low
opened: '2026-08-28'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

Found during [FRO_047](FRO_047_border-interface-refactor.md)'s build+playtest review,
2026-08-28 -- not a regression from that refactor, a pre-existing gap its playtest happened to
surface.

Two different code paths grow the border path, and only one of them pairs the new border with a
boss record:

- **Automatic bootstrap** (`BorderModule.onBordersScopeLoaded()`) -- fires once per world on
  first load if not yet seeded. Calls `BorderAPI.grow(level)`, and on success immediately calls
  `BossAPI.createBoss(level, result.border())` right after.
- **Manual command** (`BorderCommandHandler.pathGrow()`, `/border path grow`) -- calls
  `BorderAPI.grow(level)` and reports success/failure to the player. **Never calls
  `BossAPI.createBoss()`.**

Symptom: growing the path via the in-game command leaves a border on the path with no matching
`BossFixture` record. `BossModule.reconcilePathAgainstBossRecords()` -- a defensive tick-driven
check comparing the path's border layers against `BossFixture`'s own layer set -- correctly
detects this every tick and logs it loudly, exactly per its documented contract ("A layer present
on the path but missing here is a real data bug ... logged loudly, never silently self-healed").
Confirmed in FRO_047's second playtest session: after a manual `/border path grow`,
`run-server/logs/latest.log` shows

```
[Boss] Reconciliation: path has border(s) at layer(s) [N] with no matching BossFixture record
in level minecraft:overworld -- real data bug (missed call site, crash between paired calls, or
manual world editing), not a normal transient state.
```

repeating every tick indefinitely (no self-heal, matching the doc's own promise).

Nothing here crashes the server or corrupts persisted data -- it's a real behavior gap (the
command path and the bootstrap path aren't symmetric) surfacing as a warning log message, not an
in-game failure the player sees directly. Not triaged or fixed yet; opened as its own ticket
rather than folded into [FRO_027](FRO_027_known-failed-commands-running-list-not-b.md) since it's
a design/parity gap between two code paths, not a command erroring out.

**Likely fix shape (not implemented, not confirmed):** either have
`BorderCommandHandler.pathGrow()` call `BossAPI.createBoss()` on success, same as
`onBordersScopeLoaded()` does -- or, if boss-pairing is meant to be automatic/bootstrap-only by
design, have the reconciliation warning (or a separate mechanism) actually create the missing
boss record instead of just logging the gap. Which of these is correct depends on whether manual
path growth is supposed to spawn a boss at all -- project owner's call, not inferred here.

## Log

- 2026-08-28: Ticket opened. Found via FRO_047's second playtest session; write-up above is from
  static analysis of `BorderCommandHandler.pathGrow()` vs. `BorderModule.onBordersScopeLoaded()`
  plus the corroborating `[Boss] Reconciliation:` log lines -- not yet triaged or fixed.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
