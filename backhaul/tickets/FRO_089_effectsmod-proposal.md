---
id: FRO_089
uid: FRO
number: 89
client: FrontierMode
status: done
title: 'Propose EffectsMod: cross-cutting effects module'
context: BossTellFixture owns particle/sound calls that are presentation logic, not
  fixture state.
priority: normal
opened: '2026-09-06'
closed: '2026-09-06'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Came up during the `BossTellFixture` server-code cleanup: `BossTellFixture` currently owns
`spawnTellParticle` and an inline sound broadcast in `runTellsForPlayers`. Both are server-side
presentation logic -- no state, no persistence -- and don't belong in a fixture at all.

## Proposal

A dedicated `EffectsMod` that owns effect dispatch: targeted particle sends, area sound
broadcasts, possibly screen/title effects later. Other modules (Boss, Border, Discovery, future
systems) call into it rather than rolling their own dispatch.

**Structured the same as any Satchel-registered module (Border, Boss), not a bare utility class.**
`client/` / `common/` / `server/` packages under `effects/`, an `EffectsAPI` facade in `common/`
mirroring `BorderAPI`/`BossAPI` as the one sanctioned entry point other modules call through, and
an `EffectsMod.init()` matching `BorderModule.init()`/`BossModule.init()`'s shape and call
convention. This holds even though there's nothing to wire yet -- no fixture, no bundle, no
`JigConfig` registration, since effect dispatch has no state or persistence need identified so
far. `EffectsMod`/`EffectsAPI` start as empty or near-empty classes that establish the package
shape and entry point, not a fully wired module from day one; the module gets its real body once
there's an actual event/tick/registration need for it (a per-effect-type throttle/cooldown is the
likeliest future trigger, and that would be `PlayerJig`-scoped state, not `LevelScope`).

## Two dispatch paths, chosen per effect -- not one mechanism

Nothing here is fixed as "how effects work now." Two shapes both live as static calls, side by
side, and which one a given effect uses is a per-call-site choice, not a global rule:

- **Server-broadcast** -- the server makes a real decision (a probability roll, any check that
  depends on server-only state) once and pushes the result to nearby players. Required whenever
  the occurrence itself is non-deterministic or depends on something not synced to every client --
  otherwise different players see different outcomes for what's supposed to be one shared event.
- **Client-derived (deterministic/"parity")** -- no roll, no broadcast. Every client computes the
  same answer independently off state it already has synced, and decides locally whether to fire.
  Only valid when the effect's occurrence can be expressed as a pure function of already-synced
  inputs (a stable id, the world's own game time) -- see
  [SAT_046](../tickets/SAT_046_parity-util-proposal.md) for the actual primitive this leans on.

The split is "does this need a real roll/gate, or can it be derived" -- decided effect by effect
as we build them, not resolved as a single policy here. `EffectsAPI` (and its client-side
counterpart) expose both paths as ordinary static methods so picking one over the other is a
one-line call-site choice, never a structural one.

## Migrating existing client-side effects

Border's own client renderers (`border/client/render/level/*`) are two different drivers, not
one, and they land on opposite sides of the "generic effect vs. domain rendering" line:

- **`GrowthTriggerRenderer`'s particle spawn** -- a good, natural `EffectsMod` consumer. "Spawn a
  named particle in response to a trigger" is the same shape `BossTellFixture`'s particle already
  is, just currently client-only (spawned per-client off `ScopeEvent.Tick`, not a server
  broadcast) rather than server-broadcast. That's exactly the deterministic/parity path above,
  not the server-broadcast one -- no roll involved, just "the tip moved, render here." Worth
  noting before this moves: it currently has an open, unconfirmed bug against it
  ([FRO_055](../tickets/FRO_055_growth-particles-stale-tip.md), stale particle position after a
  boss-defeat growth) -- decide whether the migration happens before or after that lands, so the
  bug doesn't just follow the code to a new home unexplained.
- **`WorldBordersRenderer`'s ring geometry -- stays with Border, not a candidate.** This isn't a
  discrete triggered effect; it's continuous per-frame rendering of Border's own domain data,
  reading `RenderContext`'s `BorderAPI`-backed cache directly. Moving it would mean `EffectsMod`
  needs intimate knowledge of border radius/center/layer to draw a ring -- the same
  reach-back-into-a-specific-module's-data problem [FRO_085](../tickets/FRO_085_border-boss-dependency-inversion.md)
  already flagged one layer over, just inverted. `EffectsMod` stays a generic utility other
  modules call into with plain parameters; it doesn't own anyone's domain rendering.

## Sound

Joins particles in the same first pass, not a later phase -- `BossTellFixture`'s inline sound
broadcast is one of the two concrete things already blocked on this ticket, alongside
`spawnTellParticle`.

Worth being explicit about the mechanics, since "sound isn't server-synced" sounds riskier than it
is: vanilla's own area sound broadcast (`Level.playSound(null, pos, ...)`) is a pure
distance-from-source check against connected players, not chunk-gated -- and even in that
broadcast form, the actual falloff/direction a player hears is computed by their own client audio
engine off the position in the packet. So a client-derived sound (no broadcast, client checks its
own distance to an already-synced source position and plays it locally) produces the same
listener experience as the broadcast version, provided the source position is fresh -- same
staleness caveat as `GrowthTriggerRenderer` above, likely far less perceptible for audio than for
a rendered particle.

**One real exception, not yet needed but worth naming so it doesn't get missed later:** a sound
gated on server-only state (attunement, team membership, anything a client isn't told) can't be
derived client-side at all -- that one stays server-broadcast regardless of how "pretty vs.
correct" the rest of this ticket's effects sort out. Doesn't apply to anything Tell does today
(explicitly "closest, right now," no attunement per discovery-systems.md), but the API shape
should make that exception easy to reach for, not something that requires fighting the
client-derived path's assumptions.

## Motivation

- Particles and sounds are cross-cutting -- every game system that gives the player
  environmental feedback will want this.
- One place to tune coefficients, add throttling, swap particle types, hook a debug visualizer.
- Some existing inline effect calls across Boss and Border are candidates to migrate in.
- Keeps fixtures state-only; presentation goes here.
- Building toward Satchel's client/server simulation-parity goal: the less this relies on packet
  triggering, the more reliably both sides stay in sync on their own.

## Dependency

Leans on [SAT_046](../tickets/SAT_046_parity-util-proposal.md) (the Satchel-level
deterministic/parity primitive) for the client-derived path's actual "should this fire" check --
not a hard roadmap block, since both are still proposals, but `EffectsAPI`'s client-derived
methods are the primitive's first real consumer.

## Questions for the Architect

1. ~~Module registration shape~~ -- **settled 2026-09-06 (project owner's call, see Log): full
   Satchel module shape (`client`/`common`/`server`, `EffectsAPI`, `EffectsMod.init()`), even
   though `init()` has no hooks to wire yet.**
2. ~~API shape~~ -- **settled 2026-09-06: both server-broadcast and client-derived paths live as
   ordinary static methods on `EffectsAPI`/its client counterpart, chosen per call site. Not a
   registered effect-descriptor system -- "safe baseline, replace later," same convention
   Guardian Mobs' variant selection already follows.**
3. ~~Should sound dispatch move here too~~ -- **settled 2026-09-06: yes, same first pass as
   particles, same dual-path treatment. Server-only-gated sound is the one carve-out that always
   stays server-broadcast (see "Sound" above); nothing today needs that carve-out, but the API
   shouldn't fight it if something later does.**
4. ~~Any existing patterns in Satchel or FrontierMode we should mirror for the module
   boundary?~~ -- **settled 2026-09-06: `BorderMath` (pure static math, no fixture) is the
   closer precedent than `BorderModule` itself for the dispatch layer's shape.**

## Immediate unblock

Once there's a home for it, `BossTellFixture`'s remaining server methods move there and the
fixture can relocate to `boss/server/fixture/` cleanly.

## Log

- 2026-09-06: Closing. EffectsMod/EffectsAPI's server-broadcast path (sendParticleToPlayer, broadcastSound) is
  now live-verified through extensive playtest this session: BossTellFixture routes all of its
  particle and sound dispatch through this facade, and both particles and sound have been
  confirmed actually appearing/playing in real games across several rounds of tuning (positioning,
  scatter shape, coefficients). The compile-error fix this ticket folded in (spawnTellParticle's
  bad Level/Player targeted-send call) is proven by the fact the project builds and runs at all.

  Not yet exercised: ClientEffectsAPI's client-derived/parity dispatch path (no consumer wired --
  GrowthTriggerRenderer is the likely first caller once FRO_055 clears) and specifically the
  playSoundOnParity signature flagged as unverified against any existing call site. Leaving that as
  a known, low-risk unexercised piece rather than a blocking issue -- it's inert until a real caller
  exists, and FRO_055 is the ticket that would surface it.

  Not committed (git managed by project owner this session).
- 2026-09-06: Built the EffectsMod proposal as scoped, plus the client-derived/parity dispatch path built for real now (project owner: "let's just build the client side bit so we don't have to burn cycles on it later"), rather than left as a documented stub.

  New module, mirroring the client/common/server shape from wiki/frontiermode/architecture/effects.md:
  - effects/EffectsMod.java -- init hook, wired into FrontierMode.java's constructor after BossModule.init() (no fixture/init-order dependency on Border or Boss either direction).
  - effects/common/EffectsAPI.java -- server-broadcast facade. sendParticleToPlayer(ServerPlayer, ...) (targeted, via player.serverLevel().sendParticles(...)) and broadcastSound(Level, ...) (via Level#playSound(null, ...), broadcasts to all nearby players).
  - effects/client/ClientEffectsAPI.java -- client-derived/parity dispatch, built on SAT_047's SimParity. spawnParticleOnParity(...) and playSoundOnParity(...), both gated by SimParity.isCheckpoint(gameTime, interval) then SimParity.parityRoll(sourceId, gameTime, salt) against a caller-supplied probability, dispatching via ClientLevel#addParticle / ClientLevel#playLocalSound on pass. No consumer wired to this yet -- GrowthTriggerRenderer (currently blocked on FRO_055) is the likely first real caller once that clears; built now per explicit instruction so the shape exists before a second consumer forces a rework.
  - effects/server/ -- empty placeholder package, matching the client/common/server module shape; no server-only dispatch code needed yet.

  Bug fix, folded into this same pass (this is the "known issue in BossTellFixture spawnTellParticle" the project owner flagged as blocking their build): BossTellFixture.spawnTellParticle was calling a Level.sendParticles(Player, ...) targeted overload that doesn't exist on the base Level/Player types -- only on ServerLevel/ServerPlayer. This was a real compile error, not a runtime issue. Fixed by retyping spawnTellParticle's player param to ServerPlayer and routing the call through EffectsAPI.sendParticleToPlayer. runTellsForPlayers retyped to take ServerLevel (guarded in tick() with an instanceof ServerLevel check, warn-and-return on failure -- same defensive style as this fixture's other guards), its player loop retyped to ServerPlayer, and its inline level.playSound(null, ...) call routed through EffectsAPI.broadcastSound. Also updated two now-stale comments/Javadoc in this file that referenced BorderAPI.MATH as broken (fixed under FRO_088 in this same session) and moved the effects-routing note into the code itself.

  Verification note (standing constraint, same as this session's prior work): no real compiler available in this environment (Java 11 present, project targets 17; no network path to the Gradle 8.8 distribution). All of the above was cross-checked manually against already-compiling call sites in this exact codebase: ServerPlayer.serverLevel() (used elsewhere), ClientLevel#addParticle's 7-arg form (matches GrowthTriggerRenderer's existing usage), ServerLevel#players() returning List<ServerPlayer> (corroborated by a commented-out but otherwise-correct usage in the dead PlayerTickHandler.java), and ServerLevel#sendParticles's 11-arg targeted form. One signature I could not cross-verify against any existing call site in this codebase: Level#playLocalSound's exact parameter order/types used in ClientEffectsAPI.playSoundOnParity -- flagging this specifically for the real build to catch if it doesn't compile as written.

  Not committed (git managed by project owner this session). Real build/playtest still owed before this can close.

  Stale ticket note, not acted on: this ticket's own "Immediate unblock" text suggests relocating BossTellFixture to boss/server/fixture/. That package-split was proposed separately and was rejected by the project owner earlier this session -- flagging here rather than acting on it or editing the wiki. No files were moved.
- 2026-09-06: Ticket opened.
- 2026-09-06: Scope expanded, project owner's call: `EffectsMod` is a full client/common/server
  module from the start, matching Border/Boss's shape, with an `EffectsAPI` facade -- not a
  minimal utility class -- even though the module has no Forge/jig hookup yet and
  `EffectsMod`/`EffectsAPI` start effectively empty. Question 1 above settled by this; questions
  2-4 still open.
- 2026-09-06: Design conversation with project owner settled questions 2, 3, and 4, worked out
  the server-broadcast-vs-client-derived split, decided `GrowthTriggerRenderer`'s particle joins
  this module while `WorldBordersRenderer` doesn't, and identified a dependency on a new
  Satchel-level parity primitive ([SAT_046](../tickets/SAT_046_parity-util-proposal.md)) for the
  client-derived path. See that ticket for the primitive itself. All four questions now settled --
  this ticket is ready to move from proposal to scoped build whenever it's picked up.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
