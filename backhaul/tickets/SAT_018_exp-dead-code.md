---
id: SAT_018
uid: SAT
number: 18
client: Satchel
status: done
title: 'common/exp/* dead code: delete or doc'
context: 'Confirmed unreferenced. PM call: delete it, or document as intentional scratch.
  See SAT_014.'
priority: normal
opened: '2026-08-13'
closed: '2026-08-13'
---

<!-- board:start -->
<!-- board:end -->

## Summary

`common/exp/*` (`BaseConfig`, `CategoryAConfig`, `HomeCategoryAConfig`, `HomeConfig`, `UseCase`)
is confirmed dead/unreferenced scratch code, but nothing currently tracks it — no ticket to
delete it, no doc marking it as intentional sandbox space, so it just looks like an oversight.

**PM call needed:** delete it outright, or document it explicitly as scratch space. Flagging both
options open pending that decision. From [SAT_014](SAT_014_status-doc-gaps-for-pm-to-ticket.md)
item 4.

## Log

- 2026-08-13 (PM call): Delete, not document. Re-confirmed zero references anywhere in either
  repo's `src/` (grepped both trees for all five class names, outside the directory itself —
  nothing). Nothing about the names (`BaseConfig`, `CategoryAConfig`, `HomeCategoryAConfig`,
  `HomeConfig`, `UseCase`) suggests an intentional, load-bearing sandbox — no comment, no doc, no
  wiki mention anywhere claiming it as deliberate scratch space, and this project's existing
  precedent (SAT_004's dead-file cleanup, the strip-down plan generally) leans toward removing
  confirmed-dead code rather than annotating it in place. Deleted all five files and the
  now-empty `common/exp/` directory.
- 2026-08-13: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
