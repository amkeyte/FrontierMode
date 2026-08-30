---
id: BKHL_011
uid: BKHL
number: 11
client: Backhaul
status: done
title: bht has no command to append a ticket log entry
context: Every dated Log entry this session went through hand-edited markdown with
  fragile exact-string anchors, twice failing.
priority: normal
opened: '2026-08-28'
closed: '2026-08-28'
---

<!-- board:start -->
<!-- board:end -->

## Summary

`bht`'s write surface is `open` and `close` only (per `bht.md`'s own cheatsheet). Every dated
`## Log` entry in between -- which is most of what a ticket like
[FRO_047](FRO_047_border-interface-refactor.md) actually *is*, given BHT's own convention that a
ticket's history lives in its log, not the wiki -- has no CLI path at all. It has to happen as a
raw markdown edit against the live file.

## What this cost, concretely

This session (FRO_047's Lead Dev build, 2026-08-28) wrote five separate dated log entries by hand
-- the initial build summary, two design-refinement entries, a build+playtest review, and a
second playtest confirmation -- each one a Python script matching an exact multi-line anchor
string against the ticket's current tail and splicing new text in. Two of those five attempts
failed on the first try: one because the anchor assumed 2-space list-continuation indentation the
file actually used 4 spaces for, another because a live re-read of the file showed the anchor text
itself was subtly different from what static reasoning about the file's structure predicted
(a line-wrap point wasn't where expected). Both were recoverable by re-reading the file and
retrying, but that's real risk for zero benefit -- a `bht`-side append wouldn't need the caller to
reconstruct any surrounding context at all, just the ticket ID and the new entry text.

This is the same class of exposure [BKHL_010](BKHL_010_cli-unreachable-over-device-bridge.md)
already named for a different root cause (CLI unreachable at all) -- here the CLI *was* reachable
and correctly installed, and the gap is still real because the command it would need doesn't
exist yet.

## Suggested direction, not a committed design

`bht log <id> --entry "..."` -- appends a new `- YYYY-MM-DD: <entry>` line to that ticket's `##
Log` section (today's date, from the CLI's own clock, not a caller-supplied one -- removes the
one thing hand-edits get right by copying an existing entry's date format but could still get
wrong). Multi-paragraph entries are the common case in practice (every real entry logged this
session ran to several sentences with inline code spans and cross-links) -- worth accepting
either a `--entry-file <path>` for longer text or reading multi-line input from stdin, rather than
forcing everything into one `--entry` string argument.

## Log

- 2026-08-28: **Closed.** Tracked upstream in the Backhaul repo as BH_016 (`bht log <id> --entry
  "..."`) — not yet implemented; this ticket's diagnosis is complete, the fix lives there now.
- 2026-08-28: Ticket opened, off FRO_047's build cycle -- Lead Dev role, reporting from the
  perspective of the CLI's actual end user this session.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/backhaul)
<!-- bh-header:end -->
