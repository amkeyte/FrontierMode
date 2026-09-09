---
id: SAT_051
uid: SAT
number: 51
client: Satchel
status: open
title: '[Architect] satchel-health.md stale -- canary/coordinator removed (SAT_050)'
context: '[Architect] backhaul/wiki/satchel/architecture/satchel-health.md documents
  SAT_039''s MobJig canary (a tagged vanilla Bat + BOTH-applicability LevelJig coordinator,
  spawned/discovered via ScopeEvent.Tick) as "Confirmed live on a real client+server
  run" and describes it in the same detail as SatchelHealth.java''s own class javadoc
  did. SAT_050 removed that mechanism from the code entirely (COORDINATOR_* keys,
  registerCoordinator(), onCoordinatorTick, isCanary, scanForCanaryOnClient, findOrSpawnCanary,
  anchorPos, searchBoxAround, CANARY_TYPE/CANARY_NAME/CANARY_SEARCH_RADIUS/CLIENT_SCAN_EVERY_N_TICKS)
  after it caused a NoSuchFieldError: BAT class-load crash on a second dev machine
  -- the CANARY_TYPE = EntityType.BAT field was evaluated eagerly at class-init, so
  no runtime flag could have neutralized it; the reference had to be deleted. MobJig''s
  own BOTH-applicability widening and onMobScopeUnloaded''s RM_SAT_022 violation check
  are unchanged and still fire for any real MobScope reaching teardown with its backing
  mob present -- there is simply no longer a synthetic entity guaranteeing that path
  gets exercised on every run; coverage now depends on real gameplay putting a mob
  under MobJig (FrontierMode/Border consumers, chat-command watch()). This is a real
  regression from ''continuously self-verified'' to ''verified only when something
  happens to scope a mob'' for MobJig''s CLIENT/BOTH path -- worth an explicit call
  on whether that coverage gap needs a replacement mechanism (a non-vanilla-entity-typed
  canary? a dev-only debug command that spawns one on demand? accepting FrontierMode''s
  Border mob traffic as sufficient real-world coverage?) or whether it''s fine as-is
  now that MobJig''s BOTH widening itself is settled, mature code rather than the
  freshly-built thing SAT_039 needed to prove. Not edited directly per role boundaries
  (wiki is the Architect''s); SatchelHealth.java''s own class javadoc has already
  been updated to describe current state, so that half is stitch-ready. See SAT_050''s
  log for the full before/after and the crash''s root-cause writeup (not fully diagnosed
  -- traced to the other machine''s build/runtime, not this repo''s config, which
  is identical across both mods and unchanged in git).'
priority: normal
opened: '2026-09-09'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

[Architect] satchel-health.md stale -- canary/coordinator removed (SAT_050)

## Log

- 2026-09-09: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
