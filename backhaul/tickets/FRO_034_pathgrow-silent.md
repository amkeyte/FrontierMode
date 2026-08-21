---
id: FRO_034
uid: FRO
number: 34
client: FrontierMode
status: done
title: /border path grow announces nothing
context: Creates a border but never says which. Flagged on RM_FRO_015 as a real finding,
  never filed.
priority: normal
opened: '2026-08-21'
closed: '2026-08-21'
---

<!-- board:start -->
<!-- board:end -->

## Summary

`/border path grow` creates a border and reports only "Advanced border progression." It never says
what it created or where. Every other border-creating command — `addExplicit`, `addHere` — announces
the new UUID.

[RM_FRO_015](../roadmap/RM_FRO_015_margaret.md) identified this as the actual root cause of a
reported bug that turned out not to be one. The report was "when border in path deleted, then border
grows (from empty) but adds at layer 1." Reconstructing the full command sequence from
`run/logs/latest.log` showed nothing was wrong: an earlier `path grow` had already put a second real
border on the path before the delete, so the path was never empty. `BordersFixture.remove()`'s
referential-integrity contract behaved correctly throughout. The confusion came from `pathGrow`
silently creating a border several commands earlier.

Margaret's own words: **"the command surface should make its own state changes visible."** She filed
it as a real finding rather than dismissing it as user error, then closed with the fix unmade.

## Why this needs a ticket

Margaret's closing entry set an explicit rule: any further `/border` command-surface work gets a
**new sibling node** rather than reopening her, since her scope item 2 was always "the standing home
for *this pass's* findings," not an open-ended promise. No such node was created, so the finding
currently survives only inside the log of a `resolved` node.

PM's read, for the record: this is ticket-shaped, not node-shaped. It's a one-command message
change with no dependency structure and no design call attached — `bhrm` models
dependency-graph-shaped forward work, and this isn't that. If the project owner disagrees and wants
it as a roadmap sibling under a future command-surface node, that call overrides this one.

## Scope

`BorderCommandHandler.pathGrow`'s success message should name what it created, matching
`addExplicit`/`addHere`'s existing "Created border \<uuid\>." shape. Worth considering whether it
should also report the new `LEV`/`LAY` values, given the 2026-08-20 `/border info` display change
made that distinction visible everywhere else.

## Done bar

Real build, then `/border path grow` on a level with an existing path: the message names the
created border. No other behaviour changes.

## Log

- 2026-08-21: **Closed on the project owner's call — satisfied with `/border path grow`'s response
  message as it stands. No change requested through this ticket.**

  Recording the tradeoff rather than just the decision, since this ticket exists because of a
  specific episode: [RM_FRO_015](../roadmap/RM_FRO_015_margaret.md) traced the "when border in path
  deleted, then border grows (from empty) but adds at layer 1" report to this message, and closed
  that report as not-a-bug — the path was never empty, an earlier `path grow` had already appended a
  real border, and everything was accounted for in `/border info @all`. The silent success message
  was named as what made an earlier grow easy to lose track of a few commands later. Declining the
  change means that particular confusion can recur; it also means no code moves for a cosmetic
  message, which is a reasonable trade on a command surface that is admin/dev-facing rather than
  player-facing.

  **If it does recur, this is the ticket to reopen** — the diagnosis is already done and the fix
  shape is in the Scope section above (mirror `addExplicit`/`addHere`'s "Created border \<uuid\>."),
  so nobody needs to re-derive it. Per [RM_FRO_015](../roadmap/RM_FRO_015_margaret.md)'s standing
  rule, any *new* `/border` command-surface finding gets a new sibling rather than reopening
  Margaret — this ticket is the exception, being about this one message specifically.
- 2026-08-21: Ticket opened by PM off a doc audit of Susan's subtree, per Margaret's own flag.

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
