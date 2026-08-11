---
id: SAT_004
uid: SAT
number: 4
client: Satchel
status: open
title: Rename common.newstuff package
context: Placeholder package name on live code; also clean up two dead, commented-out
  files.
priority: normal
opened: '2026-08-11'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

Rename common.newstuff package

### Context

`common/newstuff/` holds live, current persistence/hydration code (`BundleSavedData`,
`FixtureHydrator`, and related classes) — not provisional despite the placeholder-sounding
name. Architect has already confirmed this is the correct package, so this is a rename, not a
redesign. While in there, also delete the two dead, fully-commented-out predecessor files:
`common/fixture/NbtFixtureHydrationSource.java` and
`server/persistence/ServerPersistenceContext.java`. Full background:
`backhaul/wiki/satchel/architecture/persistence.md` and `fixture.md`.

### Suggested fix

Pick a real package name for `common/newstuff/`, move its classes there, update imports, and
delete the two dead files above in the same pass.

### Required By

*(none)*

## Log

- 2026-08-11: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
