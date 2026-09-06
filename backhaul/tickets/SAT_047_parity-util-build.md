---
id: SAT_047
uid: SAT
number: 47
client: Satchel
status: done
title: 'Build: simulation-parity utility'
context: Implement the parity primitive ruled on in SAT_046 -- common/util, GameTime-mod-N
  gate.
priority: normal
opened: '2026-09-06'
closed: '2026-09-06'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Build work for [SAT_046](../tickets/SAT_046_parity-util-proposal.md), now ruled and closed. This
ticket is the implementation: a stateless `common/util` helper that lets server and client
independently compute an identical value from already-synced inputs, with no packet involved. See
[Utilities § Honorable mention](../wiki/satchel/architecture/utilities.md#honorable-mention-simulation-parity-proposed)
for where this lands once built, and [SAT_046](../tickets/SAT_046_parity-util-proposal.md)'s own
ruled questions for the settled contract this implements.

## Contract to build against

- **Package:** `common/util`, alongside `TickThrottler`/`OUT`. Class name is this ticket's own
  call -- the package and shape are ruled, the identifier isn't.
- **Inputs:** a stable id (UUID or similar, already synced to both sides), `GameTime` (never
  wall-clock -- `ServerLevel.getGameTime()`/its client-synced equivalent), and a salt/purpose
  discriminator per call site so unrelated checks sharing an id/tick don't correlate.
- **Gate on `GameTime mod N`, not a raw tick-delta comparison.** `gameTime % interval == 0`
  (interval matching the calling module's own cadence), not "has N ticks passed since I last
  checked." This is the actual parity guarantee: if client and server briefly disagree on the
  exact tick (a caught-up client, network jitter), they don't need an explicit resync -- they
  agree again automatically at the next tick that's a multiple of `N`, because both are reading
  the same shared counter against the same fixed modulus rather than tracking independent
  per-side state. Contrast with `TickThrottler`'s existing `allow()`, which phases its window off
  whenever it was first polled -- correct for its own job, not this one; see
  [Utilities](../wiki/satchel/architecture/utilities.md) for that distinction written up in full.
- **Hard requirement: bit-identical output on a server JVM and a client JVM given the same
  inputs.** The specific mixing/hash function is this ticket's call, but it has to satisfy this
  or the whole primitive is pointless -- verify it, don't just assume a chosen approach clears it.
- **Stays a pure static utility -- no fixture, no bundle, no `JigConfig`.** Nothing identified
  needs state beyond the inputs above; if a real need for that surfaces later, that's a new
  ticket, not scope creep on this one.

## First consumer

[FRO_089](../tickets/FRO_089_effectsmod-proposal.md) -- `EffectsMod`'s client-derived dispatch
path is built on this. Not a hard sequencing dependency (FRO_089 is FrontierMode's own proposal,
still ahead of its own build ticket), but worth having this land first or alongside, since it's
the concrete case that motivated the primitive.

## Log

- 2026-09-06: Built `SimParity` (`Satchel/src/main/java/com/arryn/satchel/common/util/SimParity.java`):
  `isCheckpoint(long gameTime, long interval)` for the GameTime-mod-N gate, and
  `parityValue(UUID id, long gameTime, long salt)` / `parityRoll(...)` for the deterministic
  mix, built from the SplitMix64 finalizer over XOR-folded primitive longs (no `hashCode()`
  reliance). Verification: `SimParityTest`
  (`Satchel/src/test/java/test/arryn/satchel/util/SimParityTest.java`) covers the gate,
  determinism, id/gameTime/salt sensitivity, roll range, and four independently-computed
  golden vectors locking the exact mix output. Documented in
  [Utilities § SimParity](../wiki/satchel/architecture/utilities.md#simparity----simulation-parity),
  replacing its prior "Honorable mention (proposed)" placeholder.
  Could not run the real `gradlew test` this session -- no network egress from this
  device-bridge session to fetch the Gradle 8.8 distribution (not yet cached on this
  machine), and no JDK/javac present in the device shell to compile standalone. Instead
  verified `SimParity`'s logic byte-for-byte in an isolated sandbox: identical source
  compiled and run there with a plain `java` harness (no Forge/JUnit dependency needed,
  since the class has none) reproduces every assertion in `SimParityTest`, including all
  four golden vectors, 24/24 passing. A real `gradlew test` run against this module is
  still owed before the dashboard/CI can be trusted on this ticket -- flagging at handoff
  per role instructions.
- 2026-09-06: Ticket opened. Build work for the design settled and ruled on
  [SAT_046](../tickets/SAT_046_parity-util-proposal.md), now closed.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
