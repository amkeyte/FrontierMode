---
id: FRO_027
uid: FRO
number: 27
client: FrontierMode
status: open
title: Known-failed commands (running list, not being worked)
context: 'Running list of border commands confirmed broken by real playtest. Not being
  triaged or fixed until further notice -- project owner''s explicit call. First entry:
  /border delete @all throws and refuses instead of deleting.'
priority: low
opened: '2026-08-16'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

Admin/parking-lot ticket, not an active bug queue. Real command failures found during playtest
land here as a running checklist instead of each getting its own ticket — **explicitly not being
triaged or fixed** until the project owner says otherwise. Low priority by design: nothing here
crashes the server or corrupts data, it's commands refusing/erroring instead of doing their job.

## Known-failed commands

- [ ] **`/border delete @all`** — throws and refuses instead of deleting. Reported 2026-08-16.
  Likely root cause (not confirmed, not fixed): `BorderCommands.applySelector()` iterates the
  `List<Border>` returned by `BorderAPI.borders(level) -> b.CRUD.all()` with a plain for-each,
  then calls `BorderCommandHandler::delete` per border inside that loop.
  `BordersFixture.all()` returns `Collections.unmodifiableList(borders)` — an *unmodifiable view*
  of the fixture's live backing list, not a defensive copy. `delete` mutates that same backing
  list (`BorderAPI.removeBorder` → `borders.CRUD.remove(id)`) while the view is still being
  iterated. The per-border `try/catch` in `applySelector`'s loop body can't catch this — a
  for-each's iterator advancement happens outside the loop body, so a `ConcurrentModificationException`
  there aborts the whole command instead of surfacing as one failed border among many. If this
  read is right, the fix would be iterating a snapshot (e.g. `List.copyOf(borders)`) at the top of
  `applySelector` rather than the live view — but that's a real code change, intentionally not
  made here.

## Log

- 2026-08-16: Ticket opened. First entry (`/border delete @all`) logged with a root-cause read
  from static analysis only — not confirmed against a debugger/rebuild, and deliberately not
  fixed. Add further known-failed commands to the checklist above as they're found; this ticket
  stays open as the running list until the project owner asks for it to be worked.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
