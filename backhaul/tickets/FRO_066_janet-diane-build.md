---
id: FRO_066
uid: FRO
number: 66
client: FrontierMode
status: done
title: Build Border Curve + Pregen
context: RM_FRO_027 (Janet) + RM_FRO_028 (Diane). Known open items on Diane -- proceed
  anyway, flag back.
priority: high
opened: '2026-08-31'
closed: '2026-08-31'
---

<!-- board:start -->
<!-- board:end -->

## Summary

**For Curtis (Lead Dev) -- the remaining two of this cluster's three shared-infrastructure
nodes. Pushed regardless of open blockers listed below; don't wait for them to close first.**

**[RM_FRO_027](../roadmap/RM_FRO_027_janet.md) ("Janet" -- Border Curve).** Spec:
[Border Curve](../wiki/frontiermode/architecture/border-curve.md). `BorderCurveFixture`, a new
sibling fixture in `BordersBundle`: zero-to-many named intensity curves per border, evaluated
through `BorderMath`. No known blockers to starting; exact `BorderMath` signatures and curve
parameter shape are Lead Dev's call within the spec's shape, per that page's own Open Questions.

**[RM_FRO_028](../roadmap/RM_FRO_028_diane.md) ("Diane" -- Border Pregeneration).** Spec:
[Border Pregeneration](../wiki/frontiermode/architecture/border-pregeneration.md). The least
settled of the trio -- known open items, listed on the node itself and its spec page's Open
Questions, are **not blockers**: build against the spec as written, flag back (a ticket to the
Architect, not a wiki edit) on anything that doesn't hold up in practice rather than working
around it silently. Worth knowing before starting: this page **partially supersedes
[Boss](../wiki/frontiermode/architecture/boss.md)'s** Spawn algorithm/Data model/Three questions
sections (`boss.md` already reflects the superseding text) -- if Boss code already exists, this
is a real behavior change to it, not new-territory-only work.

Neither node depends on [RM_FRO_026](../roadmap/RM_FRO_026_dorothy.md) ("Dorothy" -- Navigator,
ticketed separately as [FRO_065](FRO_065_dorothy-build.md)) at the code level -- all three are
independent sibling fixtures in `BordersBundle` minted together only because they were designed
together. Work on any order that's convenient.

## Log

- 2026-08-31: Ticket opened, immediately after RM_FRO_026/FRO_065 -- second half of the Donna
  epoch shared-infrastructure cluster, pushed per project owner's explicit instruction to fill out
  and push regardless of remaining blockers.
- 2026-08-31: Full done bar built for both nodes.
  **Janet (Border Curve):** `BorderCurveFixture` added as a third sibling fixture in
  `BordersBundle`, keyed off `borderId` (`forBorder`/`create`/`removeForBorder`). `BorderCurve`
  itself is `{id, borderId, purpose, shape, steepness}` -- `Shape` is a standalone top-level enum
  (`LINEAR`/`LOG`/`SQUARE`) rather than nested, so it can be referenced from `BorderMath` without
  reaching into the fixture package. Intensity evaluation is pure-math: `BorderCurveMath
  .intensityAt(shape, steepness, normalizedDistance)`, unit-tested directly
  (`BorderCurveMathTest`), with `BorderMath.intensityAt(...)` overloads (curve-only, and
  border+curve+position together) delegating to it -- same pure-logic-class split
  `BorderMathLogic` established on FRO_065. `steepness` is a single `double`, meaningful only for
  `LOG`; the spec's own Open Questions left the parameter shape to Lead Dev. Delete-cascade added
  at the `BorderAPI` layer: `removeBorder` now also purges every `BorderCurve` for that border
  (composing `CURVE(level)` + `curveFixture.removeForBorder(id)`), matching this codebase's
  existing "cross-facet composition belongs at the API layer" precedent rather than reaching
  fixture-to-fixture.
  **Diane (Border Pregeneration):** `BorderPregenFixture` added as a fourth sibling fixture --
  explicit-trigger-only (`BorderAPI.startPregeneration`, idempotent), never automatic on border
  creation. Tick-driven work runs through the automatic per-fixture `onJigTick()` hook (no
  `EventHandlers` wiring needed), throttled via the existing `TickThrottler`
  (`THROTTLE_INTERVAL_TICKS=5`, `CHUNKS_PER_BATCH=4` -- both tuning numbers, not locked) and paced
  against a deterministic spiral chunk enumeration (`BorderPregenLogic.chunkOffsetsInDisk`,
  unit-tested) recomputed fresh from the border's live radius every batch, so a mid-pregen resize
  is picked up automatically rather than requiring extra bookkeeping. A border deleted mid-job is
  handled defensively inside the fixture's own tick loop (marks the job complete rather than
  respinning forever) -- the page makes no delete-cascade requirement here the way Border Curve's
  page does.
  **This is a real behavior change to Boss, not new-territory-only work, per this ticket's own
  note** -- `boss.md` already carried the superseding text. `BossRecord.position` is now nullable
  (starts null at `BossFixture.create(layer)`, no position param anymore); a new
  `BossFixture.unpositioned()`/`finalizePosition(bossId, pos)` pair sits ahead of the existing
  `unmaterialized()`/`materialize()` stage. `BossModule.onBossJigTick` gained a new
  `finalizeUnpositionedBosses` step (walks `unpositioned()`, resolves each record's home border by
  walking the path the same way `reconcilePathAgainstBossRecords` already does, no-ops until
  `BorderAPI.isPregenReady()` passes, then commits `BossRules.choosePosition`'s result) that runs
  immediately before `materializeUnresolvedBosses` in the same tick, so a border whose
  pregeneration finishes this tick doesn't lose a full extra cycle before its boss can
  materialize. `BossFixture.materialize` dropped its resolved-position parameter (position is
  already committed by the time materialization runs); `forceMaterialize` (`/boss mob spawn`)
  gained a null-position guard, returning the existing `DECLINED` outcome rather than a new enum
  value when a home border hasn't finished pregenerating yet. `BossRules.choosePosition` moved
  from `BossAPI.createBoss` (called instantly, blind) into this new tick-gated step, scoring
  `POSITION_CANDIDATES=5` chunk-center candidates via new `flatnessScore`/`hazardScore` methods on
  `DefaultBossRules` (both 0-1, safe-baseline placeholder formulas per the spec's own framing) and
  reading real heightmap/block data -- safe now that the disk is guaranteed already generated.
  `BossRules.materialize` dropped its in-place Y-resolution/liquid-column check accordingly (no
  longer needed against already-validated terrain). All three call sites that pair
  `BorderAPI.grow`/`BossAPI.createBoss` (`BorderModule.onBordersScopeLoaded`,
  `BossModule.onLivingDeath`, `BossAPI.forceDefeat`) now also call `BorderAPI.startPregeneration`
  for the newly grown border, so no boss is ever left permanently unpositioned for lack of a
  triggered job.
  No new Architect ticket filed for this node -- border-pregeneration.md's own listed open items
  (throttle budget, candidate scoring formulas, curve steepness shape) were pre-flagged on this
  ticket and the spec page itself as Lead Dev's call, not blockers, and nothing in the spec's own
  prose failed to hold up against real code the way FRO_065's two flags did.
  **Not yet build-verified from this session** -- same device-bridge limitation as FRO_065 (Java
  11, no network egress in this session's Linux VM; can't run this project's Java 17 +
  ForgeGradle/Minecraft-userdev toolchain or the new JUnit tests). Verified instead by careful
  manual review against the read spec pages and source, brace/paren balance checks on every
  touched/new file, and a full `git diff` review pass -- but `./gradlew build` and
  `./gradlew test` still need to run on the real Windows dev environment before this is trusted
  compiled, especially given the real signature changes to `BossFixture.create`/`materialize` and
  `BossRules.choosePosition`/the new scoring methods.
- 2026-08-31: Real playtest pass on the Windows dev machine (client + server actually launched,
  joined, played) surfaced and fixed a batch of issues the device-bridge-only review above
  couldn't catch:
  **BossAPI naming (raised by project owner, not a ticket item):** `BossAPI.boss(Level)` renamed
  to `bosses(Level)` (it returns a `BossFixture`, i.e. a collection, not one `Boss` -- the old name
  read as singular) and `levelJig()` dropped from public to private (was an unnecessary
  data/access leak of the internal jig handle). Project owner started the rename by hand; Lead Dev
  finished every remaining call site across `BossAPI`/`BossModule`/`BossCommandHandler`
  /`BossSelector`.
  **BossCommandHandler defect sweep (requested directly):** two compile breaks found and fixed --
  `addExplicit`/`addHere` were still calling the old two-arg `create(BlockPos, int)` signature
  after Diane's rewrite dropped it for `create(int layer)` + `finalizePosition(id, pos)`; both
  admin-add commands now call `create(layer)` then commit the given position immediately via
  `finalizePosition`, matching `debugGoto`'s existing "admin-forced position" precedent. Also
  swept every `record.position()` read for the new nullable-position risk and added
  `positioned()` guards with friendly failure messages to `debugGoto`, `debugDistance`, and
  `BossDisplay.fullInfo`; found one more of the same risk proactively in `BossAPI.forceDefeat`
  (would have handed a null center into `BorderAPI.grow`) and guarded it the same way before it
  could reach a real player.
  **Satchel-level crash fix:** first real client join crashed with `SatchelException.JigNotFound`
  thrown out of `LogicalFoundation.tryScopeInfo`, called via `BorderAPI.resolveFixture` off the
  client render loop during the boot race window. Root cause: `tryScopeInfo`'s own implementation
  called the throwing `requireJigInfo` instead of the already-existing non-throwing `askJigInfo`,
  breaking its own documented "non-throwing sibling of requireScopeInfo" contract -- a genuine
  pre-existing Satchel bug, not a regression from this ticket's changes. Fixed in
  `LogicalFoundation.tryScopeInfo` to delegate to `askJigInfo`.
  **Log spam cleanup (two rounds):** `BorderAPI.resolveFixture`'s three "not ready yet" branches
  were logging unconditionally every render frame via `RenderContext`'s lazy accessors --
  converted to edge-triggered logging (per-`Level` last-reported-reason map, `BorderAPI
  .logNotReadyOnce`), matching FRO_062's existing precedent. Separately, `AScopeCoupler
  .getOrCreate()`'s `BundleNotFound ignored; falling through to create` line was logging at WARN
  on every single first-ever bundle access (i.e. every mob spawn) -- confirmed as leftover dev
  instrumentation for expected, correct behavior and removed outright per project owner's call,
  not merely downgraded.
  **Pregen throttle tuning (playtest-driven, two rounds):** original `THROTTLE_INTERVAL_TICKS=1`
  /`CHUNKS_PER_BATCH=16` caused server "Can't keep up!" warnings (45 ticks behind). Round 1 halved
  batch size alone (`CHUNKS_PER_BATCH=8`, interval unchanged) on the theory of per-batch spikes;
  barely improved (41 ticks behind against a 1009-chunk disk) -- the real cause was sustained
  per-tick load, not a spike, so round 2 widened the interval too (`THROTTLE_INTERVAL_TICKS=4`,
  `CHUNKS_PER_BATCH=2`). Confirmed clean against a real 2121-chunk disk: 212.1s, zero "Can't keep
  up" warnings. Added `OUT` start/stop-with-timer logging plus a one-time disk-size log on each
  job's first batch, per project owner's request.
  **Status:** now genuinely playtest-verified, not just reviewed -- client launches and joins
  without crashing, `/boss` commands exercised, multiple real border-growth/pregen cycles
  completed cleanly at the tuned values above. `./gradlew build`/`test` still haven't run (same
  device-bridge limitation), but real runtime behavior on the actual Java 17 toolchain has now
  substituted for that on every path exercised during the playtest.

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
