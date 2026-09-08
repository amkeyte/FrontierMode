---
id: FRO_096
uid: FRO
number: 96
client: FrontierMode
status: done
title: Build Guardian Mobs per RM_FRO_029
context: Build against RM_FRO_029 (Gloria) / guardian-mobs.md spec. Not time-critical.
priority: normal
opened: '2026-09-07'
closed: '2026-09-07'
---

<!-- board:start -->
<!-- board:end -->

## Summary

**For Curtis (Lead Dev).** Build against [RM_FRO_029](../roadmap/RM_FRO_029_gloria.md) ("Gloria"
-- Guardian Mobs epoch 1). The real spec is [Guardian
Mobs](../wiki/frontiermode/architecture/guardian-mobs.md), not this ticket's own prose -- read it
before writing code. It's written directly against `BossTellFixture`
(`boss/common/fixture/BossTellFixture.java`) as the nearest real precedent -- Environmental Tells'
own sibling fixture, already shipped -- so most of this build is "the same shape as Tell, event-
triggered instead of tick-cadenced" rather than new ground.

**Dependency chain is fully clear:** Navigator, Border Curve, Border Pregeneration, and Donna_03's
carried-forward pregen items (RM_FRO_026/027/028/035) are all resolved. Nothing blocking this one.

Scope in one line: `BossGuardiansFixture` (a new, stateless third sibling in `BossBundle`
alongside `BossFixture`/`BossTellFixture`) hooks a Forge mob-spawn-finalization event, finds the
nearest alive/positioned boss to the spawning mob (same nearest-candidate shape
`BossTellFixture.runTellsForPlayers` already uses), reads that boss's border's `"placement"`/
`"difficulty"` `BorderCurve` records, and rolls a `BossRules`-tunable coefficient to decide
whether this spawn becomes a guardian. Full technical shape, including the proposed
`createGuardianCurvesIfAbsent(level, borderId)` helper and its three call sites
(`BossJigHandlers.onBordersScopeLoaded`, `BossJigHandlers.onMobDied`, `BossAPI.forceDefeat`), is
on the wiki page.

**Real judgment calls the spec page left open, flagged there and restated here so they don't get
missed** -- none of these are design-scope questions, all are yours to decide at build time, same
as `tellTickInterval()`'s default was:

1. Whether `BossGuardiansFixture` is worth being a real (if stateless) `SatchelFixture` for access
   parity with its siblings, or a plain static utility -- the wiki page's own recommendation is
   the fixture, but it's cheap to change either way.
2. The exact Forge spawn-finalization event/method -- the wiki page's best guess is
   `MobSpawnEvent.FinalizeSpawn` (Forge 47.4.10 / MC 1.20.1), **not verified against this
   project's real Forge sources** -- confirm the actual class/method shape first.
3. Registration entry point -- proposed as a new `FrontierMode.onMobSpawnFinalize(...)`
   `@SubscribeEvent` delegating to `BossModule.onMobSpawnFinalize(event)`, mirroring
   `onRegisterCommands`'s existing split. Change it if the real Forge event doesn't fit that
   shape.
4. `BossRules.guardianPlacementCoefficient()` and the tier-bucketing/stat-scaling methods'
   exact signatures -- sketched on the wiki page, not fixed. "Safe baseline, replace later," same
   as every other `BossRules` tunable in this cluster.

**Not this ticket's scope:** which Borders actually turn Guardian Mobs on, and at what curve
settings -- that's Game Designer config, named as RM_FRO_029's own one remaining open item, not a
blocker on this build.

File a ticket back to the Architect (not a wiki edit) if the spec doesn't hold up once real code
has to do it, same as always.

## Log

- 2026-09-07: Closing. Build against RM_FRO_029/guardian-mobs.md is complete, playtest-settled per this ticket's own log (final entry corrects earlier framing -- compiled and playtested locally throughout, not just sketched): BossGuardiansFixture shipped as BossBundle's third sibling, spawn-hook confirmed against real Forge sources, curve seeding wired into all three paired-creation call sites, marker+glow+stat-scaling all shipped in v1, placement sizing (guardianPlacementRadiusFraction=0.5) and spawn rate (GUARDIAN_PLACEMENT_COEFFICIENT=0.6) settled from live tuning. Wiki drift flagged in this ticket's log is now corrected (PM pass): border-curve.md's worked example updated to describe the radius-fraction scaling, and guardian-mobs.md's Open Questions section updated to mark the fixture/hook/registration/tunable-value questions resolved against what actually shipped; page flipped draft -> verified. Only remaining open item is RM_FRO_029's own -- which Borders turn Guardian Mobs on, Game Designer's call -- explicitly not a blocker on this ticket's own scope.
- 2026-09-07: Ticket opened. Dependency chain (RM_FRO_026/027/028/035) fully resolved as of
  today's FRO_092 close -- Gloria is genuinely actionable now, not just proposal-stage.
- 2026-09-07: Build done against the spec page as written. `BossGuardiansFixture` added as
  BossBundle's third sibling (`boss/common/fixture/`, no persisted state); curve seeding wired into
  all three existing paired-creation call sites; `BossRules`/`DefaultBossRules` gained
  `guardianPlacementCoefficient()`/`guardianTier()`/`tagGuardian()`/`applyGuardianStatScaling()`;
  `EffectsAPI` gained `ensureTeam()`/`assignToTeam()`; `FrontierMode.onMobSpawnFinalize` added
  (single new raw Forge subscription, mirrors `onRegisterCommands`'s shape). The two open judgment
  calls the spec flagged are resolved: `MobSpawnEvent.FinalizeSpawn`
  (`net.minecraftforge.event.entity.living`, `MinecraftForge.EVENT_BUS`, fires *before* the mob's
  own vanilla `finalizeSpawn()`) verified directly against real Forge 1.20.x sources; fixture vs.
  static utility decided as fixture, per the page's own recommendation. Two real bugs caught and
  fixed in the wiki page's own illustrative code, also verified against real sources: `Entity` has
  no `getScoreboard()` (`EffectsAPI.assignToTeam` now takes the `Scoreboard` explicitly); otherwise
  the `ensureTeam`/`tagGuardian`/`applyGuardianStatScaling` sketches were accurate as written.
  **Not yet verified: a real compile.** This session has no path to Forge/Gradle's own dependency
  cache (device-bridge shell has no network or cached Gradle distro) -- needs `gradlew compileJava`
  (or an IDE build) run directly on the workstation before this is trustworthy, and a playtest spawn
  near a boss to confirm the guardian roll/marker/scaling actually fires. One runtime edge case
  worth watching in that playtest, not a compile concern: `BossGuardiansFixture.onMobSpawnFinalize`
  calls `Satchel.requireServer()`, and `MobSpawnEvent.FinalizeSpawn` fires wherever vanilla would
  have called `Mob#finalizeSpawn` -- if that ever happens on a thread Satchel's own
  `LogicalSideContext` wasn't bound on (satchel.md's own "fragile if [a call] doesn't [happen on an
  already-bound thread]" caveat), this throws instead of degrading. Ordinary per-tick natural
  spawning shares the main server thread with everything else Satchel already binds, so this is
  expected to be fine, but it's untested.
- 2026-09-07: Playtest correction, same day. Guardians were clustering tight around the boss with
  none appearing far out on the border -- expected behavior, not a bug: every existing `Shape`
  (`LINEAR`/`LOG`/`SQUARE`) decays from center outward by construction (`BorderCurveMath`'s own
  contract), and the `"placement"` curve was seeded `LINEAR` per border-curve.md's own worked
  example. Added `Shape.FLAT` (constant intensity, no falloff) to the shared Border Curve system
  and switched Guardian's `"placement"` purpose to it -- the roll is now uniform across the whole
  border instead of gradient-shaped. Also added `BorderCurveFixture.replace()` (upsert) and
  upgraded the old `createGuardianCurvesIfAbsent` into `ensureGuardianCurves`, which now reconciles
  an already-seeded curve to the current shape/steepness constants rather than only creating when
  absent -- needed because a live world that already grew a border has a persisted `LINEAR`
  `"placement"` record that a pure create-if-absent helper would never touch again. **Flag for the
  Architect:** border-curve.md's "Worked example: Guardian Mobs' two curves" section still
  documents `"placement"` as `LINEAR` -- now stale against the shipped shape, not updated here
  (wiki isn't Dev's lane).
- 2026-09-07: Second playtest correction, same day, after live feedback that `Shape.FLAT` (previous
  entry) overcorrected -- "doesn't act as a tell at all," uniform-everywhere gave no gradient to
  notice, and roaming confirmed the goal was never "no gradient," it was "a gradient compressed
  into a smaller, actually-noticeable ring around the boss." Reverted `Shape.FLAT` entirely --
  `Shape`/`BorderCurveMath` are back to exactly `LINEAR`/`LOG`/`SQUARE`, no trace left. Guardian's
  `"placement"` purpose is back on `Shape.LINEAR` (border-curve.md's original worked-example shape
  was right all along; only the reference radius was wrong). New tunable:
  `BossRules#guardianPlacementRadiusFraction()` (`DefaultBossRules`: 0.5, i.e. half the border's
  radius) -- the placement roll's normalized distance now divides by `border.radius() *
  guardianPlacementRadiusFraction()` instead of the bare full radius, so the same LINEAR ramp is
  concentric with the border but compressed into a smaller reference circle. Split
  `tryGuardianize`'s single shared `normalizedDistance` into two independent values:
  `placementNormalizedDistance` (uses the new fraction) and `difficultyNormalizedDistance` (still
  the full, unchanged `border.radius()` -- stat scaling/tiering was never the complaint, so it stays
  keyed off the whole border, only how far out guardians *appear* got compressed). `replace()`
  upsert from the previous entry is what let this reconcile a live world's already-seeded curves
  without a reset -- unchanged, still doing its job. **Flag for the Architect still stands and now
  has one more layer:** border-curve.md's worked example needs to additionally note that Guardian's
  `"placement"` purpose is evaluated against a *fraction* of the border radius, not the raw radius
  itself (still not edited here -- wiki isn't Dev's lane). **Still not verified: a real compile.**
  Same standing limitation as the original build entry above -- this device-bridge session has no
  network/cached Gradle distro, so `gradlew compileJava` (or an IDE build) needs to run directly on
  the workstation before any of this is trustworthy, followed by a playtest confirming guardians now
  show up at a noticeably wider, but still bounded, radius around the boss.
- 2026-09-07: Third playtest report, same day: zero guardians spawning at all after the previous
  entry's rebuild -- not "rare," none. Root cause found by code review (not yet confirmed against
  the live world's actual save data, which this session has no NBT-reading path to): the
  `"placement"` curve this same world already had persisted was seeded under `Shape.FLAT` two
  entries ago (it was live and spawning, uniformly, when the "doesn't act as a tell at all"
  feedback came in). Reverting `Shape.FLAT` from the enum means `BorderCurve.load`'s
  `Shape.valueOf(tag.getString("shape"))` now throws on that persisted record;
  `BorderCurveFixture.loadCurves` already catches malformed records per-entry (same discipline
  `BossFixture`/`BordersFixture` use) and drops just that one -- leaving this border with no
  `"placement"` curve at all, silently, no crash. `tryGuardianize` already early-returns when
  either curve is missing, so every single spawn attempt was bailing before the RNG roll even
  happened. `ensureGuardianCurves` (which would recreate it) only runs from three lifecycle events
  (border scope load, boss death, `forceDefeat`) -- none of which necessarily fire again for an
  already-running boss that predates a rebuild, so the gap doesn't self-heal on its own timeline.
  Fix: `tryGuardianize` now calls `ensureGuardianCurves` itself and retries the lookup once, right
  at the point it would otherwise give up on a missing curve -- turns a permanent per-border gap
  into a one-spawn-attempt hiccup, and is a correct safety net regardless of whether this exact
  enum-removal was the actual cause on Arryn's live world (any other future reason a curve record
  fails to load hits the same self-heal path). **Still not verified: a real compile or an actual
  read of the live save data confirming the dropped `FLAT` record theory** -- same standing
  network/Gradle-cache limitation as every entry above. Next playtest should confirm guardians
  spawn again at all; if they do but still feel too rare, the next knob is
  `GUARDIAN_PLACEMENT_COEFFICIENT` (currently 0.15, i.e. an 85% miss chance even standing on top of
  the boss), not the curve/radius shape.
- 2026-09-07: Fourth playtest report, same day: self-heal fix confirmed working (guardians
  spawning again) and the 0.5-radius-fraction sizing from two entries ago confirmed right --
  no further complaint about *where* guardians appear. Remaining ask: raise the spawn chance.
  Bumped `GUARDIAN_PLACEMENT_COEFFICIENT` 0.15 -> 0.4 in `DefaultBossRules` (max chance per spawn
  attempt right on top of the boss goes from 15% to 40%, still decaying with `placementIntensity`
  same as before, still capped under 1.0 so ordinary vanilla spawns keep happening nearby too).
  Safe baseline, playtest territory -- next move if still too rare is further up this same
  constant (ceiling 1.0, guaranteed-guardian at the exact center), not a curve/shape change.
- 2026-09-07: Fifth and, per Arryn, final tuning pass for this round: `GUARDIAN_PLACEMENT_COEFFICIENT`
  0.4 -> 0.6 (max 60% chance per spawn attempt right on top of the boss, still decaying with
  `placementIntensity`, still a roll rather than a guarantee). Called "good for now" -- Guardian
  Mobs placement sizing (0.5 radius fraction) and spawn rate (0.6 coefficient) are both settled
  from playtest as of this entry. **Still outstanding, unchanged from every entry above: no real
  compile has been run from this session** (device-bridge shell has no network/cached Gradle
  distro) -- `gradlew compileJava` or an IDE build on the workstation is the one remaining
  verification step before this ticket can move past playtest-tuning. Difficulty/stat-scaling
  (`DIFFICULTY_SHAPE = Shape.LOG`, full border radius, `GUARDIAN_TIER_COUNT`/`GUARDIAN_HEALTH_SCALE`)
  hasn't drawn any feedback this round and is unchanged since the original build.
- 2026-09-07: Correction to every entry above: the recurring "no real compile has been verified"
  caveat was wrong framing on my part. Arryn has been compiling and playtesting locally on his own
  machine throughout this whole session -- that's literally how each round of feedback in this log
  reached me (clustering, `FLAT` reading as no gradient, the zero-spawn regression, "still too
  rare" at 0.4). What I actually can't do is compile *from this session* (the device-bridge shell
  has no network/cached Gradle distro), which is a real, standing limitation on my own
  verification path -- but it never meant the feature was unbuilt or untested, and I should have
  said it that way instead of implying an open risk that wasn't real. Dropping this caveat from
  future entries; FRO_096's Guardian Mobs placement sizing and spawn rate are settled, compiled,
  and playtested as of the previous entry.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
