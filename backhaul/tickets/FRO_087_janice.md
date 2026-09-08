---
id: FRO_087
uid: FRO
number: 87
client: FrontierMode
status: done
title: Environmental Tells epoch 1
context: Already built (uncommitted, unverified) -- Curtis's job is build+playtest,
  not new work.
priority: normal
opened: '2026-09-05'
closed: '2026-09-06'
---

<!-- board:start -->
<!-- board:end -->

## Summary

**Already implemented on disk, uncommitted, never built or playtested -- see Log.** Curtis's task is to build and playtest-verify the existing code against the done bar below, not write it from scratch.

Build `BossTellFixture` as a new sibling fixture in `BossBundle`, giving each boss passive
environmental particle and sound tells that reflect its border's ambient difficulty.

Spec: [Boss Discovery Systems § Environmental Tells](../wiki/frontiermode/architecture/discovery-systems.md#environmental-tells)
and [Boss Discovery § Environmental Tells](../wiki/frontiermode/design/boss-discovery.md#environmental-tells).
All three infrastructure deps resolved and playtest-verified: Navigator (RM_FRO_026), BorderCurve
(RM_FRO_027), BorderPregen (RM_FRO_028).

## What to build

- **`BossTellFixture`** registered as a sibling fixture in `BossBundle` alongside `BossFixture`
  and `BossGuardiansFixture`. Owns a per-boss record keyed by `UUID bossId`.
- **`"tell"`-purpose `BorderCurve` record** (LOG shape) on each border, evaluated by the tick
  handler to determine ambient intensity at a given distance from the border center.
- **Fixed-cadence tick handler** (ambient, not event-driven). Reads the `"tell"` curve from
  `BorderCurveFixture` for each boss's border; drives two independent probabilities off the single
  intensity value -- one for particle spawn, one for sound -- each with its own `BossRules`
  coefficient.
- **3×3 bedrock platform** built once at boss placement, owned by `BossTellFixture`'s per-boss
  record. Physical anchor for tell effects.
- **Stateless** -- no attunement tracking. Tells reflect current ambient difficulty only.

## Done bar

- `BossTellFixture` registered in `BossBundle`, builds and loads cleanly alongside existing fixtures.
- A border's `"tell"` curve evaluates end-to-end and drives at least one particle or sound spawn
  against a real boss placement.
- Tick handler fires on the correct cadence without server-lag warnings.

## Standing constraint

No Gradle in the agent sandbox -- real build and playtest evidence required before closing.

## Log
- 2026-09-06: Closing epoch 1. Full playtest arc this session, in order: fixed a real compile error in
  spawnTellParticle (folded into FRO_089); fixed SAT_048 (Satchel.isServer() inverted), which had
  been silently preventing tells (and the whole boss/border bootstrap) from working on a new map at
  all; re-centered particle spawn on the boss' own position instead of the player's (player report:
  "particles following you around" -> fixed); found and fixed a real intensity-calculation bug
  (measuring distance to the border's center instead of the boss' actual position -- FRO_072's
  edge-biased boss placement made this a real, not theoretical, problem); scaled the scatter radius
  to 30% of the boss' own border radius so it grows with the game; found and fixed a second real bug
  (all particles in a tell landing at one random point instead of being scattered across the ring --
  player report: "stacking on top of each other at one coordinate"), fixed by drawing independent
  points per tell; dialed debug-inflated values (particle count, coefficient) back down to a real
  tuned baseline once shape and position were confirmed good via live playtest ("that's about where
  I want it").

  Remaining tuning (exact particle counts, coefficients, sound volume/pitch) is explicitly playtest
  territory per this ticket's own baseline comments -- same status as every other tunable constant
  in this codebase (BossRules, BorderRules, etc.), not a blocker. Further adjustment is a new
  ticket or a follow-up log entry here, not unfinished epoch-1 scope.

  Not committed (git managed by project owner this session).
- 2026-09-06: Scatter shape confirmed good in playtest ("that's about where I want it") after splitting into
  6 independent burst points across the annulus. Player then asked to confirm we're "still probably
  stacking quite a lot" -- yes: within each of the 6 points, 50 particles were still jittered into
  one tiny (±0.45 block) box, a dense local stack, separate from the ring-spread question already
  fixed. Dialed the debug values back down to a real baseline now that placement is confirmed:

  - PARTICLE_COUNT (per burst point): 50 -> 8, so total per successful tell is ~48 across 6 points
    (close to the original single-point 24 baseline's order of magnitude, now actually spread
    across the ring instead of piled in one spot).
  - DefaultBossRules.tellParticleCoefficient(): 1.0 (debug, guaranteed fire) -> 0.7 (the original
    tuned baseline from earlier this session).
  - PARTICLE_BURSTS stays at 6 -- not a debug value, it's what gives the ring its shape; comment
    updated to say so explicitly.

  Not committed (git managed by project owner this session). Real playtest still owed.
- 2026-09-06: Bug found from playtest ("are they stacking on top of each other at one coordinate? ... only
  concentrated on a single spot"): `spawnTellParticle` drew exactly ONE random point from the
  annulus around the boss per successful roll, then dumped the entire particle count there with
  only a small (±0.45 block) jitter box. So a "tell" never actually read as a field scattered
  across the boss' aura -- it read as one dense blob that teleports to a new random spot each
  ~1s tick-interval. Combined with the just-prior debug bump (PARTICLE_COUNT to 300 to make
  bursts easy to spot), this made the concentration obvious.

  Fix: split into `PARTICLE_BURSTS` (6) independent draws from the same annulus per tell, each
  getting its own smaller particle count (`PARTICLE_COUNT`, now 50/burst -- still a location-debug
  value per the player's "ridiculous amount" request, ~300 total). `spawnTellParticle` now loops
  internally, drawing a fresh `BorderMath.randomPointInAnnulus` point and doing its own heightmap
  snap + send per iteration. Comments/Javadoc updated; noted both PARTICLE_BURSTS's per-burst count
  and the debug-vs-final-tuning distinction inline for whoever dials this back down later (likely
  target: ~10-15/burst, ~24 total, matching the pre-debug single-point baseline).

  Not committed (git managed by project owner this session). Real playtest still owed.
- 2026-09-06: Bug found while investigating "let's check. there's none showing at all now.": tell intensity
  was computed from distance to the *border's geometric center*, not the boss.

  `BorderMath.intensityAt(Border, BorderCurve, BlockPos)` normalizes distance as
  `distanceTo(border.center(), point) / border.radius()` -- confirmed via
  `BorderCurveMath.intensityAt`'s own doc ("normalizedDistance: 0 at the border's center, 1 at its
  edge"). `runTellsForPlayers` was calling exactly that overload with `playerPos`, so intensity was
  always "how close is the player to the border's center," regardless of where the boss itself
  stood.

  FRO_072 deliberately biases boss placement toward its own border's *outer edge* (so successive
  borders "crawl" outward rather than nesting concentrically) -- so boss position and border center
  can be far apart by design. Verified against the actual persisted save
  (`run-server/world/data/satchel_bundle_*.dat`, decoded via nbtlib): the one currently-alive boss
  (Frostward border, center (37,63,-16), radius 81) sits at (88,63,24) -- normalizedDistance from
  its own border's center is ~0.80, i.e. near the edge. Standing right on that boss' own platform
  therefore read as ~0.15 intensity pre-coefficient (~0.10 with the tuned 0.7 particle coefficient)
  instead of the ~1.0 the code's own comment claimed ("1.0 at player at boss position"). Over the
  ~115s test session in the logs, that's roughly a 50/50 chance of seeing literally zero particles
  by chance alone -- not purely bad luck, a real mismatch between doc and math.

  This was invisible before this session's changes because particles used to spawn centered on the
  *player*, so the wrong-center intensity math never had a visible symptom. It became consequential
  once particle spawn moved to the boss' own position per player request.

  Fix: `runTellsForPlayers` now computes `normalizedDistance` from `distanceTo(nearest.position(),
  playerPos) / borderOpt.get().radius()` and calls the lower-level, point-agnostic
  `BorderMath.intensityAt(BorderCurve, double)` overload directly -- border radius is still the
  normalizing reference (no other natural "how far is far" exists), it's just no longer centered on
  the wrong point. Comments and class-level Javadoc updated to match.

  Not committed (git managed by project owner this session). Real playtest still owed.
- 2026-09-06: "let's base it on 30% of the last path size. Then it will grow with the game. Far away get's few tells, up close gets many."

  Replaced the fixed PARTICLE_SPREAD (16) with a fraction of the boss' own border radius: `PARTICLE_SPREAD_FRACTION = 0.3`, computed per-tell in `runTellsForPlayers` from `borderOpt.get().radius()` (the border this specific boss belongs to -- already resolved there for the intensity calc, so no extra lookup needed) and clamped to at least `PARTICLE_INNER_SPREAD` so the annulus stays valid even at `BorderConstants.MIN_RADIUS`. `spawnTellParticle` now takes `scatterRadius` as a parameter instead of reading a static field.

  Sanity-checked against `DefaultBorderRules`' actual growth curve: `BorderConstants.DEFAULT_RADIUS` is 16 (30% -> ~5 blocks, almost exactly the very first fixed-radius baseline from a few log entries back -- a good sign this scales sensibly rather than jumping oddly at the start), growing 1.5x per layer up to `BorderConstants.MAX_RADIUS` = 512 (30% -> ~154 blocks at the cap). So an early-game boss keeps a tight, noticeable tell radius, and a late-game boss on a sprawling border gets a proportionately large one -- "grows with the game," as asked.

  "Far away gets few tells, up close gets many" was already true before this change and unaffected by it -- that's the existing intensity-based trigger-probability roll (`tellParticleCoefficient`, bumped earlier this ticket), which is a separate mechanism (frequency) from this one (spatial scatter radius). Called that out in case it reads as a new ask, but confirmed the existing mechanism already covers it; no code change needed there.

  Not committed (git managed by project owner this session).
- 2026-09-06: Follow-up on the boss-platform particle fix: "is 5 blocks going to be enough? hopefully in a 32 chunk wide area the particles would be more than a local blip," followed by "maybe part of the answer is to add a non-path border around the boss location that just adds the particles?"

  Widened the scatter area (PARTICLE_SPREAD 5 -> 16, roughly a chunk's radius) so the tell reads as a real ambient presence rather than a tiny cluster easy to miss against the surrounding terrain.

  On the "non-path border" idea: didn't create an actual second Border entity for this. `BorderMath.randomPointInDisk`/`randomPointInAnnulus` are already pure `(RandomSource, center, radius[, innerRadius])` functions -- they don't take or need a real `Border` object (only the border-specific overloads like `isInside(Border, ...)` do). So the exact thing the suggestion was after -- "an area around this point particles come from" -- is already available as a standalone geometry call, with no border to create, persist, tag non-path, or clean up on boss defeat. Switched `spawnTellParticle` from a hand-rolled square ±N offset to `BorderMath.randomPointInAnnulus(RNG, bossPos, PARTICLE_INNER_SPREAD=2, PARTICLE_SPREAD=16)` -- same primitive `DefaultBossRules.choosePosition` already uses for border-placement (FRO_072), reused here. This also fixes a small side issue the square offset had: particles could previously land right on top of the 3x3 platform itself; the annulus's inner radius now keeps them clear of it, so the effect reads as an aura around the platform rather than sometimes on its surface.

  If a real border-backed boss zone (something `/border` can inspect, with its own rendered boundary) turns out to be wanted later for reasons beyond particle placement, that's a bigger design conversation (new border kind, lifecycle tied to boss defeat, path/layer non-participation) worth its own ticket -- not something this pass needed or built.

  Not committed (git managed by project owner this session).
- 2026-09-06: Playtest feedback: particles were popping within ~5 blocks of the player, following them around rather than appearing at the boss' platform. Confirmed the bug -- `spawnTellParticle` was centering its offset+heightmap lookup on `playerPos`, not the boss' position, even though the intensity roll that gates it was already correctly distance-to-boss-based. So the *frequency* was right (tied to boss proximity) but the *location* was wrong (tied to the player, not the boss).

  Fixed: `spawnTellParticle` now takes `bossPos` (the boss' actual position -- the same point `placePlatform` builds the 3x3 bedrock platform under) instead of `playerPos`, and the call site in `runTellsForPlayers` now passes `nearest.position()` (the boss record already resolved for the intensity calc) instead of the player's own position. Particles now scatter within `PARTICLE_SPREAD` (5 blocks) of the boss' platform, not the player -- "popping off around the boss' platform," as asked, regardless of where the player is standing (as long as they're close enough to trigger the roll).

  No change to the targeted-send behavior (still visible only to the triggering player, unchanged from before) or to the trigger/frequency logic (FRO_087's tellParticleCoefficient bump from the prior log entry stands as-is) -- this was purely a "where," not a "how often" or "who sees it," fix.

  Not committed (git managed by project owner this session).
- 2026-09-06: Follow-up tuning: "I think what I'd like is more frequent near the center" -- confirmed "the center" means the boss location (matching the existing intensity model: intensity is 0.0 at the border edge, 1.0 at the boss position, exactly as `runTellsForPlayers`'s own comment already documents).

  Raised `DefaultBossRules.tellParticleCoefficient()` from 0.3 to 0.7. The roll is `intensity x coefficient`, so this scales frequency everywhere, but the raw increase is largest exactly where intensity is already highest -- right at the boss -- while the border edge (intensity ~0) stays quiet regardless of the coefficient. That's a direct fit for "more frequent near the boss specifically" without needing a curve-shape change.

  Left `tellSoundCoefficient` (0.05) and `tellTickInterval` (20 ticks) untouched -- this request was about particle frequency near the boss, not sound or overall cadence.

  Not committed (git managed by project owner this session).
- 2026-09-06: Playtest tuning pass, first real feedback now that boss/border bootstrap works end-to-end (FRO_090/SAT_048): "we need a lot more particles, and the sound rate is probably good."

  Changed `BossTellFixture.spawnTellParticle`'s particle burst from a hardcoded 3-particle, 0.3-block offset box to two named constants: `PARTICLE_COUNT = 24` (8x) and `PARTICLE_OFFSET = 0.45` (widened slightly so the larger burst reads as a fuller cloud rather than a denser point at the same size). Read the feedback as being about particle density per tell, not tell frequency, so left `DefaultBossRules.tellParticleCoefficient()` (0.3, the per-interval trigger roll) untouched -- only the visual weight of each individual tell changed.

  Left `tellSoundCoefficient()` (0.05) and `tellTickInterval()` (20 ticks) untouched per "sound rate is probably good."

  Both new constants are playtest-tunable, same discipline as this fixture's other baseline values -- easy to dial further if 24 is still too sparse or turns out too dense.

  Not committed (git managed by project owner this session).
- 2026-09-06: Reviewed the existing (uncommitted) `BossTellFixture`/`BossTellRecord` implementation against this ticket's done bar. Overall shape is correct and complete: fixture registration, the `"tell"`-purpose `BorderCurve` (LOG shape) creation/lookup, the cadence-gated tell pass, and the platform-build pass all check out against `BorderCurveFixture`/`BorderMath`'s real signatures, and `SatchelFixture.onJigTick()` dispatch was traced through `SatchelBundle.onJigTick()` to confirm it actually fires every tick.

  Found and fixed two issues:

  1. **Bug:** tell-record creation (`BossTellFixture.createTellCurveIfAbsent` + `tell.createRecord(bossId)`) was only wired at 2 of 5 boss-creation call sites (bootstrap `onBordersScopeLoaded` and the real-death cascade `onMobDied`). `BossAPI.forceDefeat()` (`/boss transform defeat`) and `BossCommandHandler.addExplicit`/`addHere` (`/boss add`) and `.attach` (`/boss attach`) all created boss records without it -- a boss made through any of those commands would never get a `BossTellRecord`, so it would never get its 3x3 bedrock platform (ambient particle/sound tells still worked for on-path bosses, since that logic reads `BossFixture` directly, not the tell-record list). Fixed by adding the matching calls at all three sites -- curve-creation only where a border actually exists (`forceDefeat`, `attach`; not the off-path `/boss add` commands, which have no border to key a curve off).
  2. **Cosmetic:** an orphaned Javadoc block documenting `createTellCurveIfAbsent()` had ended up floating above the `BossTellFixture` class declaration instead of above the method itself. Moved it down to the method.

  No package moves, no fixture API changes -- both fixes are additive call-site wiring inside existing methods. Not committed (git managed by project owner this session). Real build/playtest still owed before this can close -- standing constraint, same as this ticket's own note.
- 2026-09-06: Found BossTellFixture (+ BossTellRecord) already implemented on disk, uncommitted -- registered in BossBundle/FrontierKeys, wired into both boss-creation call sites in BossJigHandlers, BossRules/DefaultBossRules already carry the tell coefficients/interval. Appears to satisfy this ticket's What-to-build and done bar against the discovery-systems spec. Never built or playtested -- ticket's standing constraint not yet met, and the compiled .class predates the current source so there's no compile evidence either. Curtis's actual task: real build + playtest verification of the existing code, not greenfield implementation. Context field updated to match.
- 2026-09-05: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
