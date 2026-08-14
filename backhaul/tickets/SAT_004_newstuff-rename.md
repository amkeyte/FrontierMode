---
id: SAT_004
uid: SAT
number: 4
client: Satchel
status: done
title: Rename common.newstuff package
context: Placeholder package name on live code; also clean up two dead, commented-out
  files.
priority: normal
opened: '2026-08-11'
closed: '2026-08-13'
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
- 2026-08-13 (Architect): Naming call: `com.arryn.satchel.common.newstuff` →
  `com.arryn.satchel.common.persistence`. Matches the vocabulary
  [persistence.md](../wiki/satchel/architecture/persistence.md) (`status: verified`) already uses
  for this package's own content — no new terminology introduced, just aligning the package with a
  name already settled elsewhere. Confirmed no collision: `common/persistence/` doesn't exist yet,
  and the old `server.persistence` package (the one this could be confused with) is gone entirely,
  not just deprecated — see below. Two of the eight classes (`ParcelEgressSink`,
  `ParcelHydrationSource`) are network-egress, not disk-persistence, but the wiki page already
  frames them as part of the same hydrate-in/egress-out plumbing rather than carving them out
  separately, so keeping them in this one package under this name is consistent with that existing
  call, not a new judgment.

  Re-checked both dead files this ticket names for deletion: `common/fixture/NbtFixtureHydrationSource.java`
  is still present, still entirely commented out — confirmed, delete as planned.
  `server/persistence/ServerPersistenceContext.java` is **no longer there at all** — the whole
  `server/persistence/` directory is gone (grepped and globbed, nothing found), most likely cleaned
  up incidentally during the SAT_008 compile-fix pass. Only one dead file left to delete, not two.

  Consolidated [SAT_007](SAT_007_newstuff-rename.md) into this ticket as a duplicate and closed it.
  Routing to PM for Lead Dev pickup: mechanical rename + import updates + delete the one remaining
  dead file. No design questions left open.
- 2026-08-13: Executed. Moved all 8 files (`BundleSavedData`, `FixtureHydrationSource`,
  `FixtureHydrator`, `NbtFixtureHydrationSource`, `ParcelEgressSink`, `ParcelHydrationSource`,
  `SavedDataEgressSink`, `SavedDataHydrationSource`) from `common/newstuff/` to
  `common/persistence/` with updated package declarations, fixed the two external importers
  (`SatchelBundle.java`, `ScopeEngine_Server.java`), deleted the one remaining dead file
  (`common/fixture/NbtFixtureHydrationSource.java`), and confirmed by grep that no `newstuff`
  references remain anywhere in either repo's source. Also updated
  [persistence.md](../wiki/satchel/architecture/persistence.md) — it explicitly cited this
  ticket as "pending mechanical execution" in two places, now corrected to describe the
  completed rename as current fact rather than leaving it stale. Confirmed by a real
  `gradlew build` on Satchel: `BUILD SUCCESSFUL`. Closing.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
