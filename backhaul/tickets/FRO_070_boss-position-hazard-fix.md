---
id: FRO_070
uid: FRO
number: 70
client: FrontierMode
status: done
title: 'Boss choosePosition: void/ravine candidates scored as valid (y=0 spawn)'
context: 'RM_FRO_028 (Diane) follow-on: boss materialized at y=0, void/ravine scored
  valid. Fix authorized.'
priority: high
opened: '2026-09-01'
closed: '2026-09-01'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Boss choosePosition: void/ravine candidates scored as valid (y=0 spawn)

## Log

- 2026-09-01: Ticket opened.
- 2026-09-01: Diagnosed and fixed, from a real playtest report (boss non-materializing after
  Diane's pregen-gated position-finalize; project owner manually teleported to a defeated boss's
  stored position and found y=0).
  **Root cause:** `DefaultBossRules.choosePosition` samples `POSITION_CANDIDATES=5` random points
  and always returns whichever scored highest, with no floor -- a column with no real ground
  under it (a ravine/cave mouth breaching the surface, or open space generally) reads its
  `MOTION_BLOCKING_NO_LEAVES` heightmap back at the "nothing found" sentinel. Every one of
  `flatnessScore`'s own four neighbor samples lands on that identical sentinel too, so the delta
  is zero and it scores as *perfectly flat* (1.0) -- and the old `hazardScore` only ever checked
  fluid state and literal `getMinBuildHeight()+1`, which never fires for a mid-air/ravine sentinel
  nowhere near the true world floor. Combined score: the best possible value, guaranteed to beat
  real terrain, for a candidate that's actually open space.
  **Fix:** `hazardScore` gained a third, general check -- `!getBlockState(candidate.below())
  .isFaceSturdy(level, below, Direction.UP)` -- disqualifying anything without real solid footing
  underneath, independent of the world's min-build-height (catches the ravine/cave-mouth case the
  floor-distance check structurally can't). `choosePosition` now tracks a hazard-disqualified
  candidate separately from the overall best -- a disqualified (hazard==1.0) candidate can never
  win outright regardless of its artificially-perfect flatness anymore, only ever chosen as an
  explicit last resort if literally all `POSITION_CANDIDATES` are disqualified, and that branch
  logs an `OUT.warn` naming the border and the coordinates so a repeat doesn't require another
  manual teleport-hunt to notice.
  **Also added (explicitly requested):** `BossModule.finalizeUnpositionedBosses` now logs an
  `OUT.info` the instant a position commits -- boss id, layer, home border, and the exact x/y/z --
  rather than that information only being inferable later (if at all) from whether the entity
  visibly turns up. This alone would have shown the y=0 immediately instead of requiring a
  manual chase.
  **Status: not yet build-verified from this session** -- same device-bridge limitation as
  FRO_065/066/069 (Java 11, no network egress in this session's Linux VM). Brace/paren balance
  checked on both touched files. Needs a real playtest confirming (a) the new position-finalized
  log line appears with sane, non-hazardous coordinates on the next several boss kills, and (b)
  the `OUT.warn` "all candidates hazardous" branch does *not* fire under normal play -- if it
  does fire repeatedly for a given border, that's a signal this border's disk is genuinely short
  on safe ground (small plot, mostly ravine/water) rather than a residual bug in this fix.
- 2026-09-01: Real playtest hit exactly the case flagged above, same evening -- a radius-32
  (3209-chunk) border scored all 5 candidates hazardous, logged the `OUT.warn` correctly (working
  as designed), and placed the boss at the world floor (y=-64) as last resort. That boss then fell
  indefinitely and died "fell out of the world" far below build height (y=-5423) -- and
  `BossModule.onLivingDeath` was growing the *next* border centered on that literal death
  location, which `BorderAPI.grow`/`BordersCrudFacet.applyProposal` correctly rejects for being
  outside the level's build height range. Until now that rejection was final: nothing retried,
  so the whole chase-the-next-boss chain stalled dead there, permanently, for the level -- a more
  serious failure than the one misplaced boss itself.
  **Two fixes, addressing both layers:**
  1. `POSITION_CANDIDATES` 5 -> 20 in `DefaultBossRules` -- 5 independent random draws wasn't
     enough coverage once a disk reaches that size; a heightmap + block-state lookup is cheap
     and this only runs once per boss, so quadrupling it is effectively free insurance against
     hitting the all-hazardous branch at all.
  2. `BossModule.onLivingDeath` now retries `BorderAPI.grow` from the boss's own recorded
     position if growing from the literal death location fails -- the recorded position is
     always within build height by construction (`choosePosition()` only ever returns a real
     heightmap-resolved Y), so this retry can't fail the same way. RM_FRO_019 ("Karen")'s
     "center on the death location" behavior is unchanged for every normal kill; this is a
     fallback path only, triggered exclusively by the first `grow()` call actually failing.
  **Not yet build-verified from this session** -- same device-bridge limitation as the rest of
  tonight's work. Brace/paren balance checked on both touched files. Needs a real playtest
  confirming a boss dying far outside build height (however that happens) now successfully
  grows the next border from its recorded position instead of stalling the chain.
- 2026-09-01: After the above two fixes, project owner reports the boss mob itself is only
  actually present at its finalized location around 75% of the time -- a third, independent cause
  of the same "boss went missing" family. `DefaultBossRules.materialize()` never called
  `mob.setPersistenceRequired()` anywhere -- a plain vanilla `Mob` spawned via `finalizeSpawn` is
  still subject to natural despawn (instant if far from every player, otherwise a random roll on
  a timer) unless persistence is explicitly required. Border Pregeneration alone can now run
  multiple real minutes before a boss even materializes, plus travel time after that -- easily
  enough idle time for a despawn roll to claim it before anyone arrives, matching a ~25% loss
  rate. Fixed by adding `mob.setPersistenceRequired();` right after `finalizeSpawn` in
  `materialize()` -- the same fix Satchel's own health-check canary already uses
  (`SatchelHealth`'s `setPersistenceRequired()` call) for the identical "deliberately never
  wandering off" reason; this codebase already knew the pattern, it just hadn't been applied to
  boss mobs.
  **Not yet build-verified from this session** -- same device-bridge limitation as the rest of
  tonight's work. Brace/paren balance checked. Needs a real playtest across several boss
  kills/materializations to confirm the disappearance rate actually drops to (near) zero.
- 2026-09-01: Closed -- project owner confirms across all three rounds of fixes (hazard scoring, death-location grow retry, and the persistence-required despawn fix).

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
