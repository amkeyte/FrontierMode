---
id: FRO_027
uid: FRO
number: 27
client: FrontierMode
status: open
title: Known-failed commands (running list, not being worked)
context: Running list of border commands found broken by playtest; not being triaged,
  per owner's call.
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

- [ ] **`/border delete @all`** — displays a generic error. Whether it also fails to delete
  depends on the world, and the two sightings below disagree; see "Which of these is it" underneath.
  Reported 2026-08-16.

  **Sighting 1 (2026-08-16, multi-border world): refuses to delete.** Likely root cause (not
  confirmed, not fixed): `BorderCommands.applySelector()` iterates the
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

  **Sighting 2 (2026-08-20, single-border world): deletes correctly, error is cosmetic.** Logged
  during [RM_FRO_015](../roadmap/RM_FRO_015_margaret.md)'s playtest thread. The border was removed
  (`[Border] Removed border <uuid>` in the server log) and the client *also* showed Brigadier's
  generic "An unexpected error occurred trying to execute that command." Investigated at length and
  found **zero** corroborating evidence in either `run-server/logs/latest.log` or the full
  DEBUG-level `debug.log` — in particular no `Command exception:` line, which vanilla's
  `Commands.performCommand` logs unconditionally at ERROR whenever a raw exception escapes a
  command's `.executes()`. `applySelector`'s own per-border `catch` would have produced its distinct
  `"[Border] Operation failed for border <id>"` message rather than vanilla's generic one, ruling
  out the obvious "a second selector match failed" explanation. Root cause undetermined; project
  owner's call at the time was cosmetic and deprioritized.

  **Which of these is it.** The two are consistent rather than contradictory: sighting 2's world had
  exactly one border, so `applySelector`'s per-border loop ran once and no concurrent modification
  was possible. That fits sighting 1's `ConcurrentModificationException` read holding for the
  multi-border case while something *else*, still unexplained, produces the generic message even
  when the delete succeeds. **That reconciliation is inference, not evidence — nobody has run the
  discriminator.** Cheap when someone wants it: run the command against a world with several borders
  and one with exactly one, and compare. Not run here, since this ticket's standing instruction is
  that nothing on the list gets triaged without the project owner asking. Tracked as
  [FRO_035](FRO_035_delete-all-verdict.md).

- [ ] **`/border ... @coord <x> <y> <z>`** — suspect only, not confirmed. Surfaced during
  [FRO_047](FRO_047_border-interface-refactor.md)'s build+playtest review, 2026-08-28: project
  owner reported "@coord isn't working" while everything else that session (including
  `@containing`) worked fine. Checked both fresh `run/logs/latest.log` and
  `run-server/logs/latest.log` from that session end to end -- zero trace of the attempt: no
  exception, no stack trace, no `Command exception:` line, not even a failed-parse log entry, only
  the player's own chat message about it not working. `BorderSelector.parseBlockPos()` /
  `resolveCoord()` are untouched by FRO_047's diff (confirmed against `git show` on the refactor
  commit) and `resolveCoord` calls the identical `BorderAPI.bordersContaining()` that
  `@containing`'s `resolveInside` calls, just with an explicit position -- so the underlying
  plumbing is the same code that worked minutes earlier in the same session. Two live
  explanations, neither confirmed: (1) `parseBlockPos()` only ever does three plain
  `reader.readInt()` calls -- it does not understand Minecraft's `~` relative-coordinate notation,
  only literal integers (its own usage string is `@coord <x> <y> <z>`, examples show
  `@coord 100 64 100`) -- a `~ ~ ~` attempt would fail client-side as a normal Brigadier parse
  error and never touch the server log at all, which matches what's (not) in the log; (2) the
  literal coordinates given, if they were literal integers, simply weren't inside any border, so
  "no borders matched" would be the correct, non-buggy result. What exact string was typed was
  never captured. Not triaged or fixed here per this ticket's standing instruction -- reported
  2026-08-28.

## Log
- [Arryn] next one to read this one, ask me what maintenance node to tie it to.
- 2026-08-28: Added `@coord` to the checklist as a suspect, not a confirmed failure -- flagged during FRO_047's build+playtest review, no corroborating log evidence either way. See the checklist entry itself for the full writeup. No triage performed, per this ticket's standing instruction.
- 2026-08-21: **First entry corrected — it was asserting more than the evidence supported.** The
  checklist and this ticket's `context` line both said `/border delete @all` "throws and refuses
  instead of deleting," which [RM_FRO_015](../roadmap/RM_FRO_015_margaret.md) had already
  contradicted on 2026-08-20 with a real log showing a successful delete. That newer evidence never
  reached this ticket, so [BOARD.md](../BOARD.md) has carried a summary contradicted by the
  project's own logs since that day. Both
  sightings are now recorded side by side with the open question named rather than resolved by
  assumption. No triage or fix performed — this ticket's parked status is unchanged. Per
  [FRO_035](FRO_035_delete-all-verdict.md).
- 2026-08-16: Ticket opened. First entry (`/border delete @all`) logged with a root-cause read
  from static analysis only — not confirmed against a debugger/rebuild, and deliberately not
  fixed. Add further known-failed commands to the checklist above as they're found; this ticket
  stays open as the running list until the project owner asks for it to be worked.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
