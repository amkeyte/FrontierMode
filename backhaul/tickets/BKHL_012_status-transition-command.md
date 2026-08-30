---
id: BKHL_012
uid: BKHL
number: 12
client: Backhaul
status: done
title: bht has no command for in-progress/blocked status
context: open/close are the only write verbs; the two middle lifecycle states can
  only be set by hand-editing frontmatter.
priority: normal
opened: '2026-08-28'
closed: '2026-08-28'
---

<!-- board:start -->
<!-- board:end -->

## Summary

[BHT — Ticket Conventions](../wiki/meta/bht.md) documents the lifecycle as
`open -> in-progress | blocked -> done`, but `bht`'s only write verbs are `open` (which always
starts a ticket at `open`) and `close` (which always ends at `done`). The two middle states --
`in-progress` and `blocked` -- have no command at all; the only way to set them is hand-editing
`status:` in a ticket's frontmatter directly.

## Why this matters beyond convenience

[BKHL_006](BKHL_006_closed-status.md) already found six tickets carrying `status: closed`, a
value outside BHT's documented vocabulary, "unrejected because nothing validates on write" --
and closed with the write-time-validation half tracked upstream as Backhaul's own BH_010. That
gap and this one are the same root cause seen from two sides: if `bht` had a real `status`
subcommand for every documented lifecycle value, hand-editing frontmatter -- the only place a
typo like `closed` instead of `done` could have been introduced -- would stop being the *normal*
path for two out of four lifecycle states, not just the accidental one for one.

Concretely this session: [FRO_045](FRO_045_karen-build.md) is sitting at `status: blocked` on the
live board right now. I never ran a command that set that -- whoever put it there did the same
hand-edit this ticket is describing, because there was no other option.

## Suggested direction, not a committed design

`bht status <id> <in-progress|blocked|open>` -- validated against BHT's own documented vocabulary
(reject anything else, closing the write-side half of
[BKHL_006](BKHL_006_closed-status.md)'s gap for these two states specifically). Whether `blocked`
should also accept a `--reason` (mirroring how `close` presumably records a closed date) is worth
deciding at implementation time, not asserted here.

## Log

- 2026-08-28: **Closed.** Tracked upstream in the Backhaul repo as BH_017 (`bht status <id>
  <in-progress|blocked|open>`), noted there as natural to build alongside BH_010's vocabulary
  validation given the shared logic.

- 2026-08-28: Ticket opened, off FRO_047's build cycle -- same session and same reporting
  perspective as [BKHL_011](BKHL_011_log-append-command.md).
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/backhaul)
<!-- bh-header:end -->
