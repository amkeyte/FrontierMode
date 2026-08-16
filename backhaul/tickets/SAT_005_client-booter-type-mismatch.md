---
id: SAT_005
uid: SAT
number: 5
client: Satchel
status: done
title: 'ClientFoundationBooter: type mismatch on compileForSide'
context: 'ClientFoundationBooter.java:51-52 declares CompiledJigConfig config = JigConfigCompiler.compileForSide(...),
  but compileForSide returns List<CompiledJigConfig> (JigConfigCompiler.java:66) --
  javac error, does not compile. ServerFoundationBooter.java:43-46 has the correct
  pattern (List<CompiledJigConfig> configs, passed straight to installConfigs). installConfigs
  itself takes List<CompiledJigConfig> (LogicalFoundation.java:32), so Server''s version
  is right and Client''s is the stale one. Fix: match Server''s pattern in Client
  -- declare List<CompiledJigConfig> config, drop the (assumed) single-config usage.
  Check for any other config-typed reads in ClientFoundationBooter.java that assumed
  a single object, in case there''s more than a one-line fix once the type is corrected.'
priority: high
opened: '2026-08-11'
closed: '2026-08-13'
---

<!-- board:start -->
<!-- board:end -->

## Summary

ClientFoundationBooter: type mismatch on compileForSide

## Log

- 2026-08-11: Ticket opened.
- 2026-08-13 (Architect): Duplicate of item 2 in
  [SAT_008](SAT_008_fix-4-satchel-compile-blocking-errors.md), which bundled this same finding
  (independently rediscovered via a real compile) alongside three other Satchel-side errors and
  fixed all four together. Verified directly: `ClientFoundationBooter.java` now declares
  `List<CompiledJigConfig> configs = JigConfigCompiler.compileForSide(...)`, matching
  `ServerFoundationBooter`'s pattern as this ticket recommended. Confirmed by a real
  `gradlew build`: `BUILD SUCCESSFUL`. Closing as resolved via SAT_008.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
