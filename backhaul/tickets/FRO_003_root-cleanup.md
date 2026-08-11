---
id: FRO_003
uid: FRO
number: 3
client: FrontierMode
status: done
title: Delete DocletProject and clean mcRepos root clutter
context: 'Delete DocletProject/ entirely (only feeds apiDump tooling being removed).
  Remove forge-1.20.1-47.4.10-mdk.zip and .vs/ at the mcRepos root -- neither belongs
  to either mod. General housekeeping tracked under FRO per convention. Full scope:
  backhaul/wiki/plans/strip-down.md'
priority: normal
opened: '2026-08-11'
closed: '2026-08-11'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Delete DocletProject and clean mcRepos root clutter

## Log

- 2026-08-11: Ticket opened.
- 2026-08-11: Deleted DocletProject/, forge-1.20.1-47.4.10-mdk.zip, and .vs/. Also removed a
  stray, empty root-level WIKI_INDEX.md left over from earlier CLI testing before
  BACKHAUL_LOCAL_ROOT was fixed (not the real one — that's backhaul/WIKI_INDEX.md). mcRepos root
  now contains only BACKHAUL.md, FrontierMode/, Satchel/, backhaul/. Done.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
