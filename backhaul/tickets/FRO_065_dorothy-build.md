---
id: FRO_065
uid: FRO
number: 65
client: FrontierMode
status: done
title: Build Navigator per RM_FRO_026
context: 'Time-critical: start ASAP. Build against RM_FRO_026 (Dorothy) / discovery-systems.md
  spec.'
priority: high
opened: '2026-08-31'
closed: '2026-08-31'
---

<!-- board:start -->
<!-- board:end -->

## Summary

**For Curtis (Lead Dev) -- time-critical, start ASAP, do not wait on the rest of this cluster.**

Build against [RM_FRO_026](../roadmap/RM_FRO_026_dorothy.md) ("Dorothy" -- Navigator
target-resolution fixture). The real spec is [Boss Discovery Systems § Navigation lives in
Border](../wiki/frontiermode/architecture/discovery-systems.md#navigation-lives-in-border), not
this ticket's own prose -- read that section (and its Special Compass section, for
`TargetRef::Dynamic`'s exact shape) before writing code.

Scope in one line: a `TargetRef` tagged union, two new `BorderMath` primitives (`distanceTo`,
`direction`), a `TargetType -> resolver` registry, and `NavigatorFixture` as a new sibling fixture
in `BordersBundle`. Full done bar is on RM_FRO_026 itself.

**Execute as much of this as you can now.** Its two table-mates
([RM_FRO_027](../roadmap/RM_FRO_027_janet.md) "Janet" / Border Curve,
[RM_FRO_028](../roadmap/RM_FRO_028_diane.md) "Diane" / Border Pregeneration) are minted but not
yet ticketed -- don't wait for them. Nothing in this node's own done bar depends on either.

File a ticket back to the Architect (not a wiki edit) if the spec doesn't hold up once real code
has to do it, same as always.

## Log

- 2026-08-31: Ticket opened. Time-critical -- pushed to Curtis ahead of finishing the rest of the
  Donna epoch shared-infrastructure cluster, per project owner's explicit instruction.
- 2026-08-31: Full done bar built. `TargetRef` sealed interface (`Boss`/`Border`/`Structure`/
  `RawPos`/`Dynamic`) and `TargetType`/`TargetResolver`/`TargetResolverRegistry` added under a new
  `border.common.navigator` package. `BorderMath.distanceTo()`/`direction()` added, delegating to
  a new pure-math `BorderMathLogic` class (no Minecraft types) so they're unit-testable on this
  project's classpath -- `BorderMathLogicTest` (JUnit 5, added to `build.gradle`) exercises both
  against real point pairs. `NavigatorFixture` added and wired as a second, sibling fixture inside
  `BordersBundle` (`BorderModule.init()`), the first bundle in this codebase to actually hold two
  fixtures. `BossModule.init()` registers the one real resolver the done bar requires
  (`TargetType.BOSS`, resolving through `BossFixture`/`BossAPI.boss(level)`).
  Two places the spec's own prose didn't fully hold up once real code had to do it, both flagged
  to the Architect rather than guessed at silently -- see FRO_067: (1) the registry is written as
  `TargetType -> (UUID -> BlockPos)` with no `Level` in the signature, but every registry-backed
  target in this codebase lives in a level-scoped fixture, so `TargetResolver.resolve` takes
  `(Level, UUID)`, not bare `UUID`; (2) `Structure(...)` is left as an ellipsis on the architecture
  page -- implemented here as `Structure(UUID structureId)` to match the registry's own UUID-keyed
  shape, not exercised by this node's done bar.
  **Not yet build-verified from this session** -- this session reaches the repo only through a
  device bridge into a Linux VM on the dev machine (Java 11, no network egress to Maven Central/
  Forge's maven), which can't run this project's Java 17 + ForgeGradle/Minecraft-userdev toolchain
  or download the new JUnit dependency. `./gradlew build` and `./gradlew test` need to be run on
  the real Windows dev environment before this is trusted compiled.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
