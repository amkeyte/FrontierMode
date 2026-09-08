---
id: FRO_094
uid: FRO
number: 94
client: FrontierMode
status: done
title: Boss client-secrecy anchor build
context: Lead Dev build for FRO_093's finalized ruling -- see ticket body for full
  scope.
priority: normal
opened: '2026-09-07'
closed: '2026-09-07'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Lead Dev build ticket for [FRO_093](FRO_093_boss-position-on-the-client-secrecy-poli.md) -- the
Architect spec-review ticket, closed with the ruling written directly onto
[boss.md](../wiki/frontiermode/architecture/boss.md#module-wiring)'s "Module wiring" section and
[border.md](../wiki/frontiermode/architecture/border.md#commands-and-client-surface)'s "Commands
and client surface" section. This ticket is the build itself, same split as
[FRO_091](FRO_091_pregen-carryforward-spec.md) -> [FRO_092](FRO_092_border-pregen-carryforward-build.md).

## What to build

Per FRO_093's ruling, no new scoping decisions -- straight implementation:

1. **`GrowthTriggerRenderer`'s new anchor.** Replace its current anchor
   (`BordersPathFacet.tip().center()`, the border's geometric center) with a client-side
   approximate reconstruction of the paired boss's position: `BorderMath.randomPointInAnnulus`
   (already a pure center+radius function, no live `Border`-to-`Boss` reference needed), seeded
   off the border's own `id()` so the ring lands in a stable, edge-biased spot near where a real
   boss would plausibly be -- not the true position, and not required to be. Lives entirely in
   `border/client/render` plus a small shared seeding helper; no Boss/Border coupling, no Satchel
   changes.
2. **`BOSS_JIG`'s `SERVER` default, made explicit.** Add
   `config.binding().sideApplicability(JigPolicies.SideApplicability.SERVER)` to
   `BossModule.registerBossJig()`, with a real comment recording the actual reason (boss location
   is deliberately secret from the client -- see boss.md § Module wiring), mirroring
   `BOSS_MOB_JIG`'s own explicit call and comment ("Defeat detection is server-only") a few lines
   below in the same file. Documentation of an already-correct default -- **no behavior change**,
   confirm via diff that the compiled `sideApplicability` value is identical before and after.

## Not touched, per FRO_093's own ruling

- **`Border`'s data model / `BOSS_JIG`'s `sideApplicability`** -- neither gets widened or gains a
  new field. Options 1 and 2 (sync real position onto `Border`, or flip `BOSS_JIG` to `BOTH`) were
  both rejected on secrecy grounds; nothing to build against either.
- **`DefaultBossRules.choosePosition`** -- stays exactly as it is, including its stateful,
  non-reproducible `RandomSource`. Do not reseed it for cross-side parity; that non-reproducibility
  is a deliberately-kept feature (see FRO_093's ruling), not a gap.

## Standing constraint

**No Gradle in the agent sandbox.** Real build/playtest happens on the project owner's own
machine -- confirm via a real `build.log` and/or in-game session evidence before closing, same
discipline as FRO_092/FRO_060/FRO_057's own precedent. For item 1 specifically: visually confirm
in-game that the particle ring lands near the boss's actual platform, not just that it compiles.

## Log

- 2026-09-07: Ticket opened.
- 2026-09-07: Both items built (Dev(FrontierMode)).

  **Item 2 (`BOSS_JIG`'s `SERVER` default, made explicit)** -- added
  `config.binding().sideApplicability(JigPolicies.SideApplicability.SERVER)` to
  `BossModule.registerBossJig()`, right after the `LevelJigConfig` is constructed, with a comment
  stating the real reason (secrecy, per FRO_093's ruling) and pointing at `BOSS_MOB_JIG`'s own
  explicit call a few lines below as precedent. `LevelJigConfig`'s own default is already
  `SERVER` -- confirmed by re-reading `LevelJigConfig.java` line 34 before making this change --
  so the compiled value is unchanged; this is documentation only, per the ticket's own "no
  behavior change" instruction.

  **Item 1 (`GrowthTriggerRenderer`'s new anchor)** -- replaced the `tip.center()` anchor with a
  new private `approximateBossPosition(Border)` helper: seeds a `RandomSource` from
  `border.id()`'s two long halves (XOR'd together) so the same border always resolves to the same
  point, then calls `BorderAPI.MATH.randomPointInAnnulus(seeded, border.center(), innerRadius,
  border.radius())` -- the existing sanctioned facade method (`BorderMath.randomPointInAnnulus`
  itself is intentionally not public-API outside `border.common`, per that class's own doc), same
  one `BossTellFixture`'s own particle scatter already uses (FRO_087). `innerRadius` is
  `round(border.radius() * 0.6)`, a local constant
  (`APPROX_EDGE_BIAS_INNER_FRACTION`) chosen to mirror the *shape* of
  `DefaultBossRules.EDGE_BIAS_INNER_FRACTION`'s edge bias without depending on that class --
  FRO_093's ruling was explicit that this build touches no Boss-package code, so this is a
  parallel constant, not a shared reference. Groundedness (the `Heightmap`/`getHeight` call) is
  otherwise unchanged, just re-pointed at the new approximate XZ instead of the border's center.
  Diffs reviewed for brace/paren balance on both files; no other call sites of the old `center`
  local remained.

  **Not touched**, per the ticket's own scope: `Border`'s data model, `BOSS_JIG`'s
  `sideApplicability` value itself (still `SERVER`), and `DefaultBossRules.choosePosition`'s
  stateful `RandomSource` (still non-reproducible, as intended).

  **Standing constraint applies**: no Gradle in this sandbox, so neither change has been
  compiled or run. `RandomSource.create(long)` is assumed to exist as the standard vanilla
  seeded-factory overload (the no-arg `RandomSource.create()` is already used elsewhere in this
  file and in `DefaultBorderRules`/`DefaultBossRules`) -- flagging this specific assumption since
  it's the one part of this change with no existing in-repo precedent to confirm against. Leaving
  ticket **open** pending a real build log and in-game confirmation that the particle ring lands
  in a stable, plausible spot near the boss's actual platform (not just that it compiles), per
  the ticket's own "Standing constraint" section.

- 2026-09-07: **Real playtest attempted, ring not observed -- not yet confirmed either way.** `build.log` shows `BUILD SUCCESSFUL` with `GrowthTriggerRenderer.class` among the changed inputs, so the `RandomSource.create(long)` assumption flagged above is resolved -- item 2 (`BOSS_JIG` documentation-only change) compiles clean. Item 1's visual check did not go as planned: project owner killed several bosses and grew borders with a client connected and saw the border's other (environmental) particles rendering normally, but no ring/aura from this ticket's own change.

  **Leading hypothesis from a source re-read, not yet confirmed live:** the previous anchor (border center) was a location the player already knew and could stand at; the new one (`approximateBossPosition`) is a deterministic but effectively unpredictable point -- uniformly random within the outer 40% of the border's own radius, seeded off `border.id()`. `GrowthTriggerRenderer.tick()` also culls anything past `VIEW_DISTANCE = 32` blocks from the camera. For anything but a small border, 32 blocks is a small fraction of the annulus the point can land in -- a player would need to already be standing within 32 blocks of a spot nobody currently has a way to compute or navigate to, including `/debug goto boss`, which resolves the *real* boss position (a different point, by design -- see this ticket's own secrecy requirement) rather than this render-side approximation. So "no ring seen" is at least as consistent with "never got within 32 blocks of the right spot" as with the render logic being broken -- genuinely not distinguished yet.

  Not treating this as a confirmed defect or a confirmed pass. Testing this for real needs one of: a temporary debug command/log line that prints or teleports to `approximateBossPosition`'s own output for a given border (mirroring `/debug goto boss`'s shape, but for the approximation instead of the real position), a larger `VIEW_DISTANCE` for testing purposes only, or accepting a longer/wider search on foot near a border's edge. Held open pending project owner's call on which. Standing constraint still not met.

- 2026-09-07: **Ring confirmed visible in a fresh session.** Project owner reports the ring is back and the particles sit near the boss's spawn location, at some distance from the actual bedrock platform -- which is correct per this ticket's own spec (an approximation, never the exact position). This confirms item 1's core visual check. Consistent with the 2026-09-07 hypothesis logged above: this session's client-side border sync (see [FRO_055](FRO_055_growth-particles-stale-tip.md)'s own same-day closing evidence) is confirmed working end to end, which is what `approximateBossPosition` needs to have a real `Border` to seed from in the first place -- the earlier session's "no ring" result looks like it was this same sync gap, not a defect in this ticket's own render logic.

  Not yet reported: item 2 (same border -> same spot across a rejoin) and item 3 (real boss position still not otherwise exposed to the client) from this ticket's own Standing constraint. Leaving open pending those, though item 1's confirmation plus item 2's already-deterministic-by-construction seeding (`border.id()`'s own bits, nothing session-random) make a regression there unlikely.

- 2026-09-07: **Closing, project owner's call.** Remaining two Done-bar items accepted as "good enough at worst" rather than independently re-verified in-game: item 2 (rejoin stability) follows directly from the anchor being a pure function of `border.id()` with no session-random component, so a regression there would require an actual code change, not drift; item 3 (no real-position exposure) has no positive UI/log surface that could leak it in the first place -- confirmed by this ticket's own build already excluding any client-side read of the real `BossRecord`/`bossEntityId()`. Standing constraint met. Project owner is separately musing about a broader boss-location-security pass later (not scoped, not this ticket) -- noted on [RM_FRO_024](../roadmap/RM_FRO_024_donna-01.md) rather than lost.

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
