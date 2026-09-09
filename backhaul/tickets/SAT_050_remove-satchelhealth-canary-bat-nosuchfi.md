---
id: SAT_050
uid: SAT
number: 50
client: Satchel
status: done
title: 'Remove SatchelHealth canary (Bat) -- NoSuchFieldError: BAT crash on another
  machine'
context: 'User report: game crashes with NoSuchFieldError: BAT on a second computer.
  Traced to SatchelHealth.java''s SAT_039 canary mechanism, whose private static final
  CANARY_TYPE = EntityType.BAT field is evaluated eagerly at class-init time (JLS
  clinit semantics) the instant SatchelHealth.init() -- or any other static member
  -- is first touched, regardless of whether the coordinator/canary registration methods
  are actually called. So the field reference to EntityType.BAT cannot be neutralized
  by a runtime flag alone; it has to stop being an eager static field. In-repo mapping
  config (mapping_channel=official, mapping_version=1.20.1, forge_version=47.4.10)
  is identical between Satchel and FrontierMode and unchanged in git, so the mismatch
  causing the missing field is specific to the other machine''s build/runtime environment
  (stale Forge/mappings cache, mismatched jar, etc.) -- not diagnosed further since
  that machine isn''t reachable from this session. Fix: removed the entire canary/coordinator
  mechanism from SatchelHealth.java (COORDINATOR_* keys, registerCoordinator(), onCoordinatorTick,
  isCanary, scanForCanaryOnClient, findOrSpawnCanary, anchorPos, searchBoxAround,
  CANARY_TYPE/CANARY_NAME/CANARY_SEARCH_RADIUS/CLIENT_SCAN_EVERY_N_TICKS, and the
  now-unused BlockPos/Component/EntityType/AABB imports), which removes every reference
  to EntityType.BAT from the class. Left untouched: MobJig''s own BOTH-applicability
  widening and onMobScopeUnloaded''s violation check (registerMobHealthCheck and its
  handlers), plus the fully independent LevelJig/PlayerJig health checks -- none of
  those reference EntityType.BAT. Net effect: MobJig''s CLIENT/BOTH gap is no longer
  continuously self-exercised by a guaranteed-present synthetic mob; it now only gets
  exercised when real gameplay puts a mob under MobJig (FrontierMode/Border consumers,
  chat-command watch()). Class javadoc updated to describe current state, no dated
  narration. satchel-health.md (Architect-owned wiki page) documents the removed mechanism
  as ''confirmed live'' and is now stale -- flagged to the Architect separately rather
  than edited directly, per role boundaries. gradlew build/test unreachable from this
  session (no network egress, no JDK) -- verified by careful manual line-accounting
  of the edit only; a real build/test run is still owed.'
priority: high
opened: '2026-09-08'
closed: '2026-09-09'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Remove SatchelHealth canary (Bat) -- NoSuchFieldError: BAT crash on another machine

## Log

- 2026-09-09: Confirmed resolved on both affected machines (Mac, Windows via official launcher). Closing.

  Note for the record: this ticket's own fix (removing the SatchelHealth canary) correctly
  resolved the specific NoSuchFieldError: BAT crash it was opened for. But the user kept
  hitting a *different*, superficially similar-looking crash afterward on the same two
  machines -- NoSuchMethodError on FriendlyByteBuf.readUUID(), then Commands.literal(),
  kicking on join or crashing on world/command-tree creation. That turned out to be a wholly
  separate root cause, unrelated to the canary: Satchel/build.gradle had no `reobf { jar {} }`
  block, so ForgeGradle never created a reobfJar task and the shipped jar kept calling vanilla
  methods by their official/MCP name (`literal`, `readUUID`) instead of the SRG names Forge's
  actual runtime jar uses internally (`m_82127_`, etc.) -- worked fine in the ForgeGradle dev
  environment (which deobfuscates for you), broke on every real install. Fixed and verified
  separately as SAT_052; see that ticket for the full root-cause writeup and the build.gradle
  diff. Filing this note here since both problems arrived in the same conversation and could
  otherwise read as one bug with two fixes.
- 2026-09-09: Removed the canary/coordinator mechanism from SatchelHealth.java: COORDINATOR_BUNDLE/COORDINATOR_TRACKER/COORDINATOR_JIG keys, registerCoordinator(), onCoordinatorTick, isCanary, scanForCanaryOnClient, findOrSpawnCanary, anchorPos, searchBoxAround, clientScanCounters, CANARY_TYPE/CANARY_NAME/CANARY_SEARCH_RADIUS/CLIENT_SCAN_EVERY_N_TICKS, and the now-unused BlockPos/Component/EntityType/AABB imports. This removes every reference to EntityType.BAT from the class, which is the fix: that field was being evaluated eagerly at class-init (JLS <clinit> semantics) the moment SatchelHealth.init() (or any static member) was first touched, regardless of whether the canary registration methods actually ran -- so a runtime toggle alone could not have stopped the NoSuchFieldError: BAT crash; the reference itself had to go.

  Left untouched: MobJig's own BOTH-applicability widening (registerMobHealthCheck) and onMobScopeUnloaded's RM_SAT_022 violation check -- neither references EntityType.BAT, and both still fire for any real MobScope reaching teardown while its backing mob is present. Also untouched: the fully independent LevelJig/PlayerJig health checks. Net effect: MobJig's CLIENT/BOTH gap is no longer continuously self-exercised by a guaranteed-present synthetic entity; coverage now depends on real gameplay putting a mob under MobJig (FrontierMode/Border consumers, chat-command watch()).

  Updated the class javadoc to describe current state (removed "The canary"/"Mechanism"/canary-tracking-range-limitation paragraphs, added one paragraph noting the removal and why) and one stale doc-comment reference on onMobScopeUnloaded. Verified: brace/paren counts balanced pre/post edit, no other file in Satchel or FrontierMode referenced the removed symbols (grep), git diff reviewed end-to-end and matches intended scope exactly.

  Root cause on the other machine not fully diagnosed -- in-repo mapping config (mapping_channel=official, mapping_version=1.20.1, forge_version=47.4.10) is identical and unchanged between Satchel and FrontierMode in git, so whatever produced the missing EntityType.BAT field is specific to that machine's build/runtime (stale Forge/mappings cache, mismatched jar, etc.), not reachable from this session to confirm further.

  Owed: a real ./gradlew build/test run -- gradlew has no network egress and no JDK in this session's device shell, so this edit was verified by manual line-accounting (documented sanity-check script) and a full diff review only, not a compile. satchel-health.md (Architect-owned wiki page, currently describes the canary as "confirmed live") is now stale -- flagged to the Architect in a separate ticket rather than edited directly, per role boundaries.
- 2026-09-08: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
