---
id: FRO_035
uid: FRO
number: 35
client: FrontierMode
status: done
title: FRO_027 delete @all entry is outdated
context: FRO_027 says it refuses to delete. RM_FRO_015 later found it deletes fine,
  error is cosmetic.
priority: normal
opened: '2026-08-21'
closed: '2026-08-21'
---

<!-- board:start -->
<!-- board:end -->

## Summary

[FRO_027](FRO_027_known-failed-commands-running-list-not-b.md) — currently the only open
FrontierMode ticket — records `/border delete @all` as "throws and refuses instead of deleting,"
with a detailed and plausible `ConcurrentModificationException` root cause: `applySelector` iterates
the live unmodifiable view returned by `BordersFixture.all()` while `delete` mutates the same
backing list, and a for-each's iterator advancement happens outside the loop body, so the per-border
`try/catch` can't catch it.

[RM_FRO_015](../roadmap/RM_FRO_015_margaret.md), on 2026-08-20, records something different: the
delete **succeeded** (`[Border] Removed border <uuid>` in the server log), the client separately
displayed Brigadier's generic "unexpected error," and an investigation across both
`run-server/logs/latest.log` and the full DEBUG-level `debug.log` found **zero** corroborating
evidence — no `Command exception:` line, which vanilla's `Commands.performCommand` logs
unconditionally at ERROR whenever a raw exception escapes a command's `.executes()`. Root cause
undetermined. Project owner's call: cosmetic, deprioritized.

## These are probably the same bug seen from two world states

Margaret's test world had exactly one border, so `applySelector`'s per-border loop ran once and no
concurrent modification was possible — which is consistent with FRO_027's CME hypothesis being
correct for the multi-border case and Margaret observing a different, unexplained single-border
symptom. That reconciliation is PM's inference and is not written down anywhere; nobody has actually
checked it.

What is not in dispute: FRO_027's checkbox still reads "refuses to delete," and that phrasing is
what shows in the `context` column on [BOARD.md](../BOARD.md) — the single most-read summary of this
ticket. It is now known to be wrong at least in the single-border case.

## Scope

1. Add Margaret's 2026-08-20 evidence to
   [FRO_027](FRO_027_known-failed-commands-running-list-not-b.md)'s own entry for this command.
2. Correct FRO_027's `context` line so the board stops asserting the command refuses to delete.
3. Decide whether the two observations are one bug or two. A cheap discriminator: run
   `/border delete @all` against a world with several borders and one with exactly one, and compare.
   That is a playtest, not a code change, and FRO_027's "not being triaged or fixed" standing
   instruction doesn't obviously forbid it — but confirm with the project owner first, since that
   ticket's whole point is that it isn't being worked.

## Log

- 2026-08-21: **Done for the documentation half; the bug stays parked where it belongs.**
  [FRO_027](FRO_027_known-failed-commands-running-list-not-b.md)'s checklist entry now records both
  sightings side by side — 2026-08-16's multi-border "refuses to delete" with its
  `ConcurrentModificationException` read, and 2026-08-20's single-border "deletes correctly, error
  is cosmetic" from [RM_FRO_015](../roadmap/RM_FRO_015_margaret.md)'s playtest thread, including
  the absence of vanilla's `Command exception:` line that makes the second sighting genuinely
  unexplained rather than merely benign.

  The reconciliation between them is written down **as inference, explicitly labelled as such** —
  sighting 2's world had one border, so no concurrent modification was possible, which is
  consistent with both reports being true of different world states. Nobody has run the
  discriminator, and the entry says so rather than quietly presenting the inference as settled.

  `context` corrected: it no longer asserts the command refuses to delete, so
  [BOARD.md](../BOARD.md) stops carrying a claim the project's own logs contradict.

  **Item 3 of this ticket's scope — actually running the discriminator — deliberately not done.**
  FRO_027's standing instruction is that nothing on that list gets triaged or fixed without the
  project owner asking, and a playtest is triage. It's recorded on FRO_027's own entry as the cheap
  next step whenever that changes. Closing this ticket rather than holding it open for a playtest
  that isn't mine to schedule: the documentation contradiction this ticket was filed against is
  resolved, and FRO_027 remains the home for the bug itself.
- 2026-08-21: Ticket opened by PM off a doc audit of Susan's subtree. Filed against the
  documentation contradiction, not against the command — FRO_027 remains the home for the bug
  itself, and its parked status is unchanged by this.

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
