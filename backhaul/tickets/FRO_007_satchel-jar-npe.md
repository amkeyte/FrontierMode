---
id: FRO_007
uid: FRO
number: 7
client: FrontierMode
status: done
title: Fix FrontierMode build NPE on missing Satchel jar
context: Line 148 NPE'd (listFiles() on nonexistent Satchel/build/libs) because a
  fresh checkout has never built Satchel, so no jar exists yet. Hardened build.gradle
  to check existence and throw a clear GradleException telling you to build Satchel
  first, instead of NPE'ing. Underlying FRO_004 hardcoded-path debt (flatDir pointing
  at C:/_local/mcRepos/Satchel/build/libs) is still open by design -- this only fixes
  the failure mode, not the mechanism.
priority: high
opened: '2026-08-11'
closed: '2026-08-11'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Fix FrontierMode build NPE on missing Satchel jar

## Log

- 2026-08-11: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
