---
id: FRO_002
uid: FRO
number: 2
client: FrontierMode
status: done
title: Strip FrontierMode to bare necessity
context: 'Remove .git/.gitattributes/.gitignore, .gradle/, .idea/, build/, run/, top-level
  docs (CREDITS/LICENSE/changelog/WIKI.md/depmods), in-source api_dump.txt, and the
  apiDumpFrontier task + maven-publish/publishing block from build.gradle. Full scope:
  backhaul/wiki/plans/strip-down.md'
priority: high
opened: '2026-08-11'
closed: '2026-08-11'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Strip FrontierMode to bare necessity

## Log

- 2026-08-11: Ticket opened.
- 2026-08-11: Started execution (PM/Walter, sanitization scope carve-out).
- 2026-08-11: Removed .git/.gitattributes/.gitignore, .gradle/, .idea/, build/, run/,
  run-data/ (empty datagen scratch dir, same category as run/), CREDITS.txt, LICENSE.txt,
  changelog.txt, WIKI.md, depmods/, and in-source api_dump.txt. Removed the maven-publish
  plugin + publishing{} block and the apiDumpFrontier task from build.gradle. Remaining tree:
  README.txt, build.gradle, gradle/, gradle.properties, gradlew, gradlew.bat, settings.gradle,
  src/. Done.
- 2026-08-11 (addendum, found during verification): gradle.properties still had a dangling
  "API dump tooling" block (apiDumpRootPackage/apiDumpOutputName/apiDumpTaskName/
  apiDumpDocletJar, the last pointing at deleted DocletProject). Removed. Re-grepped both repos
  for DocletProject/maven-publish/apiDump/bumpBuildNumber/installToFrontierMode/build-number —
  clean.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
