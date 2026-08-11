---
id: SAT_003
uid: SAT
number: 3
client: Satchel
status: done
title: Migrate in-source architecture notes to wiki
context: 'Migrate the five in-source .md files (bundle/notes.md, fixture/notes.md,
  fixture/SatchelFacet.md, net/notes.md, server/persistence/BundlePersistence.md)
  into the wiki under satchel/architecture/*. Merge the two fixture files per the
  include directive, strip the leftover editorial artifact in bundle/notes.md, flag
  the BundlePersistence.md package/location drift (BundleSavedData actually lives
  in common/newstuff, not server/persistence) for Architect review. Remove the .md
  files from src/ once migrated. Suggest routing the validity review to the Architect
  role. Full scope: backhaul/wiki/plans/strip-down.md'
priority: normal
opened: '2026-08-11'
closed: '2026-08-11'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Migrate in-source architecture notes to wiki

## Log

- 2026-08-11: Ticket opened.
- 2026-08-11: Migrated all five in-source .md files into backhaul/wiki/satchel/architecture/
  (bundle.md, fixture.md, net.md, persistence.md). bundle.md and net.md marked verified (no
  issues found). fixture.md and persistence.md marked draft, each carrying a flagged
  drift/inconsistency for Architect review (facet/fixture terminology mismatch; and
  BundleSavedData's real location vs. what BundlePersistence.md claimed). Removed all five
  original .md files from Satchel/src/. Updated mods/satchel.md and mods/frontiermode.md wiki
  pages to reflect the sanitization pass and link to the new architecture pages. Done.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
