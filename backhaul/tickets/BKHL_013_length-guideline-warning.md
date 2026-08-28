---
id: BKHL_013
uid: BKHL
number: 13
client: Backhaul
status: open
title: bht open doesn't warn on oversized title/context
context: bht.md's own title<=40/context<=100 standard isn't checked on write; easy
  to blow past without noticing.
priority: low
opened: '2026-08-28'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

`bht.md`'s own "Length standard" section sets a target of title <= ~40 characters and context
<= 100 characters, explicitly because `BOARD.md` renders both as table columns that "wrap
awkwardly in a standard browser window." It also says plainly this isn't CLI-enforced -- `bht
open` won't stop a caller from going over. That's a reasonable choice for a hard rule (real
detail sometimes needs the room, and the doc already says to push it into the ticket body
instead), but there's a middle ground between enforcing and saying nothing at all.

## What this cost, concretely

Writing tonight's tickets (this one included), staying inside 40/100 meant manually counting
characters against a string I was about to pass as a CLI argument -- easy to get wrong, and
`bht open` gives no feedback either way once the command runs. A title or context that quietly
blew past the guideline would only ever surface later, by eyeballing the rendered board, not at
the moment it was actually written.

## Suggested direction, not a committed design

A soft warning only -- `bht open` still writes the ticket exactly as given, but prints something
like `warning: title is 52 chars (guideline: ~40) -- consider shortening or moving detail to the
ticket body` to stderr when either field is over. Keeps the doc's own "target, not a hard rule"
intent intact while closing the actual gap: right now nothing says anything, ever, even when a
value is dramatically over.

## Log

- 2026-08-28: Ticket opened, off FRO_047's build cycle -- same session and same reporting
  perspective as [BKHL_011](BKHL_011_log-append-command.md).
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/backhaul)
<!-- bh-header:end -->
