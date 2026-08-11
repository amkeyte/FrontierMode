---
id: FRO_005
uid: FRO
number: 5
client: FrontierMode
status: done
title: Prepare unified root .gitignore for single mcRepos repo
context: 'Decision: run one unified git repo at the mcRepos root (covering FrontierMode
  + Satchel + backhaul/) for now, rather than per-mod repos -- can split later if
  useful. Prepared a merged root-level .gitignore combining both mods'' original patterns
  (now applies at any depth so it covers both subdirs without duplication). Actual
  git init + identity + first commit intentionally left to the human to run themselves.
  Full scope: backhaul/wiki/plans/strip-down.md'
priority: normal
opened: '2026-08-11'
closed: '2026-08-11'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Prepare unified root .gitignore for single mcRepos repo

## Log

- 2026-08-11: Ticket opened.
- 2026-08-11: Wrote mcRepos/.gitignore (unified, covers both mods' build/IDE/run artifacts
  since patterns match at any depth). Asked about git identity for the eventual init commit;
  you said you'll create the repo yourself, so no `git init`/`add`/`commit` was run here. This
  ticket closes on the .gitignore prep only -- the actual init is on you. Done (my part).
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
