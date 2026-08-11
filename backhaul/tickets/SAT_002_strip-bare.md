---
id: SAT_002
uid: SAT
number: 2
client: Satchel
status: done
title: Strip Satchel to bare necessity
context: 'Remove .git/.gitattributes/.gitignore, .gradle/, .idea/, build/, run/, logs/,
  top-level docs (CREDITS/LICENSE/changelog/build-number.txt), maven-publish/publishing
  block, and gradle/apiDump.gradle + gradle/distribution.gradle + gradle/versioning.gradle
  modules (plus their apply-from lines). Add a direct version = mod_version line to
  build.gradle since versioning.gradle set it before. Preserve src/ and design/. Full
  scope: backhaul/wiki/plans/strip-down.md'
priority: high
opened: '2026-08-11'
closed: '2026-08-11'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Strip Satchel to bare necessity

## Log

- 2026-08-11: Ticket opened.
- 2026-08-11: Removed .git/.gitattributes/.gitignore, .gradle/, .idea/, build/, run/,
  run-data/, logs/, CREDITS.txt, LICENSE.txt, changelog.txt, build-number.txt. Removed the
  maven-publish plugin and the versioning/distribution/apiDump apply-from lines from
  build.gradle, added a direct `version = mod_version` line in their place. Deleted the four
  gradle/*.gradle files themselves (versioning, distribution, apiDump, and the orphaned/unused
  empty publishing.gradle, which turned out to never be applied at all). No dangling references
  to bumpBuildNumber/installToFrontierMode/releaseBuild/apiDump remained in build.gradle after
  the edit (grepped to confirm). Remaining tree: README.txt, build.gradle, design/, gradle/
  (wrapper only), gradle.properties, gradlew, gradlew.bat, settings.gradle, src/. Done.
- 2026-08-11 (addendum, found during verification): gradle.properties still had a dangling
  "API dump tooling" block pointing at deleted DocletProject. Removed. Re-grepped both repos for
  dangling references — clean.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
