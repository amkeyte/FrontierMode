---
id: BKHL_006
uid: BKHL
number: 6
client: Backhaul
status: done
title: 'status: closed outside BHT vocabulary'
context: 'bht.md defines open/in-progress/blocked/done. Six tickets carry status:
  closed and pass unvalidated.'
priority: low
opened: '2026-08-21'
closed: '2026-08-24'
---

<!-- board:start -->
<!-- board:end -->

## Summary

[BHT — Ticket Conventions](../wiki/meta/bht.md) defines the lifecycle as
`open -> in-progress | blocked -> done`. Six tickets in this project carry `status: closed`, which
is not in that vocabulary, and nothing rejected it on write.

Among them: [FRO_023](FRO_023_playtest-checklist-batch2.md) and
[FRO_025](FRO_025_client-crash-borders-jig-not-installed-o.md) — the two tickets
[RM_FRO_010](../roadmap/RM_FRO_010_susan.md)'s entire evidence trail leans on for build and playtest
confirmation of her six children.

## Impact today: none. Later: a wrong answer, silently.

`BOARD.md` renders correctly, because the board filters *for* the open-ish states rather than
against `done` — so `closed` falls off the board exactly like `done` does, by accident rather than
by rule.

It stops being harmless the first time anything asks "was this verified" by looking for `done`.
A `bht`-side query, a lint check, or a person grepping frontmatter all get the same wrong answer:
FRO_023 and FRO_025 don't match, so the work they confirm looks unverified.

## Suggested direction

Two independent pieces, either useful alone:

1. **Validate on write.** `bht open`/`bht close` should reject a status outside the documented set,
   the same way `bhrm` validates its own kind-dependent status vocabulary
   (`WORK_STATES` / `CONVERGENCE_STATES`). Whatever wrote `closed` was not stopped.
2. **Normalize the six existing tickets** to `done`. Mechanical, but worth confirming first that
   `closed` didn't mean something distinct from `done` to whoever wrote it — e.g. "closed without
   being completed," which BHT has no state for and which would be a real gap rather than a typo.

## Log

- 2026-08-24: **Closed.** Project owner's call: no distinct meaning intended by `closed` — normalize
  and move on. All six tickets (FRO_020, FRO_022, FRO_023, FRO_024, FRO_025, SAT_033) had
  `status: closed` changed to `status: done` directly, each with its own log entry citing this
  ticket. The write-time-validation half is tracked upstream in the Backhaul repo as BH_010 (`bht
  open`/`close` should reject an out-of-vocabulary status, not just this project fixing it by hand
  after the fact).

- 2026-08-21: Ticket opened by PM off a doc audit of Susan's subtree.

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/backhaul)
<!-- bh-header:end -->
