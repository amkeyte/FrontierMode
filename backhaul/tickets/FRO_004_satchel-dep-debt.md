---
id: FRO_004
uid: FRO
number: 4
client: FrontierMode
status: done
title: Document FrontierMode-to-Satchel hardcoded path as tech debt
context: 'FrontierMode''s build.gradle depends on Satchel via a hardcoded absolute
  path (C:/_local/mcRepos/Satchel/build/libs/) and a flatDir repo pointing at the
  same. Dependency itself stays (functionally required per mods.toml AFTER Satchel)
  -- just document the fragile wiring as known debt for Lead Dev to fix properly later
  (real project/module reference). Not fixed in this pass. Full scope: backhaul/wiki/plans/strip-down.md'
priority: normal
opened: '2026-08-11'
closed: '2026-08-11'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Document FrontierMode-to-Satchel hardcoded path as tech debt

## Log

- 2026-08-11: Ticket opened.
- 2026-08-11: Added TODO(debt, FRO_004) comments in build.gradle at the flatDir repo and
  satchelLibDir definition, pointing to backhaul/wiki/plans/strip-down.md for the full writeup.
  Dependency mechanism not changed -- documentation only, per scope. Done.
- 2026-08-11 (addendum): considered fixing this properly by unifying FrontierMode + Satchel
  into one multi-module Gradle build (would resolve the hardcoded path cleanly). You recalled
  there are ForgeGradle-specific reasons not to do that -- each ForgeGradle subproject wants to
  own its own userdev/patched-Minecraft environment, so combining multiple Forge mods in one
  multi-project build is a known source of friction, not just style. Decision: stay separate.
  The hardcoded-path debt documented above remains open/unresolved by design. IDE-side
  unification (viewing both in one IntelliJ window) is unrelated and handled via IntelliJ's
  "Link Gradle Project" instead, no build changes needed.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
