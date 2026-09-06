---
id: SAT_046
uid: SAT
number: 46
client: Satchel
status: done
title: 'Propose Satchel.util: simulation parity'
context: Client/server simulation parity via GameTime-mod-N checkpoints, no packet
  needed.
priority: normal
opened: '2026-09-06'
closed: '2026-09-06'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Came up while scoping [FrontierMode's EffectsMod proposal](../tickets/FRO_089_effectsmod-proposal.md):
some effects (an ambient particle/sound check) don't need a server roll broadcast to every nearby
client at all -- they need a way for client and server to independently compute the *same* answer
from state both already have, with no packet in the loop. That's a distinct, reusable primitive,
not an `EffectsMod`-specific trick, and it doesn't belong in FrontierMode -- it's infrastructure,
not game content, which is exactly the boundary [Satchel mod
summary](../wiki/satchel/satchel.md) already draws for what belongs at this layer.

## Concept: a third axis alongside sidedness and readiness

Satchel already has two established axes for "did you write this module correctly":
**sidedness** (which side am I on -- [Universal Sidedness
Facade](../wiki/satchel/architecture/facade-vision.md), declared and resolved centrally rather
than re-derived per call site) and **readiness** (is the state I need actually here yet --
`isReady()`, `SatchelException.NotReady`, the world-identity token). This proposes a third:
**parity** -- do both sides agree, without needing a packet to make them agree. Same underlying
instinct facade-vision.md already names for the world-identity token ("declared centrally,
resolved safely... generalizes beyond the one place it started"), applied to a different question.

## Proposed contract

A static, stateless utility (no fixture, no bundle -- `BorderMath` is the closer precedent than
any registered module) that takes:

- **A stable identity** -- a UUID or similar, already synced to both sides (a boss id, a border
  id, whatever the calling module is keyed on).
- **`GameTime`, never wall-clock time.** `now()` is not reliably synced between server and client
  (latency, clock skew) and using it would reintroduce exactly the divergence this primitive
  exists to prevent. `ServerLevel.getGameTime()`/its client-synced equivalent is the only valid
  time input -- already kept in sync by vanilla's own tick/day-time packets, no new networking
  needed.
- **A salt/purpose discriminator per call site** -- so two unrelated checks sharing the same id
  and tick don't accidentally correlate.

**Refinement from today's conversation, and the sharpest part of this proposal: checkpoint on
`GameTime mod N`, not on a raw tick comparison.** Gate the check on `gameTime % interval == 0`
(interval matching whatever cadence the calling module already uses, e.g. Tell's ~20-tick
cadence) rather than tracking "has N ticks passed since last check" as independent per-side
state. This is what actually delivers the parity guarantee under real-world jitter: if client and
server briefly disagree on the exact tick (a caught-up client after a stall, a laggy connection),
they don't need to resync explicitly -- they'll agree again automatically at the next tick that's
a multiple of `N`, because both sides are checking the same shared counter against the same fixed
modulus, not running two independently-paced clocks that need to be told to re-align. Self
-correcting by construction, not by convention.

**The one hard requirement this rests on:** whatever mixing/hash function turns
`(id, gameTime, salt)` into a decision has to produce bit-identical output on a server JVM and a
client JVM given the same inputs. Worth settling explicitly at implementation time rather than
assuming any given approach satisfies it by default.

## First consumer

[FRO_089](../tickets/FRO_089_effectsmod-proposal.md) -- `EffectsMod`'s client-derived dispatch
path (as opposed to its server-broadcast path) is built on this primitive; it's the concrete case
that surfaced the need, not a hypothetical one.

## Open questions -- ruled

1. ~~Exact package/class home~~ -- **ruled: `common/util`, alongside `TickThrottler`/`OUT`
   (see [Utilities](../wiki/satchel/architecture/utilities.md)). Exact class name is Lead Dev's
   call at implementation time, same as boss.md leaves exact vanilla API surface to Lead Dev
   elsewhere -- the ruling is the package and the contract, not the identifier.**
2. ~~Exact mixing/hash approach~~ -- **ruled: Lead Dev's call, bound by one hard constraint --
   bit-identical output on a server JVM and a client JVM given the same inputs. That constraint is
   the actual ruling; the specific mixing function isn't.**
3. ~~Does this stay a pure math utility permanently~~ -- **ruled: yes, for now. Nothing
   identified needs a stateful wrapper; revisit only if a real consumer surfaces that need.**
4. ~~Does this get its own architecture page~~ -- **ruled: folds into
   [Utilities](../wiki/satchel/architecture/utilities.md)'s "Honorable mention" section rather
   than a standalone vision page -- small enough in scope not to need `facade-vision.md`'s
   treatment.**

All four settled. Closing this proposal; build work tracked on
[SAT_047](../tickets/SAT_047_parity-util-build.md).

## Log

- 2026-09-06: Ticket opened, full proposal written up from the design conversation that produced
  it (see [FRO_089](../tickets/FRO_089_effectsmod-proposal.md)'s Log for the FrontierMode side of
  the same conversation).
- 2026-09-06: [Utilities](../wiki/satchel/architecture/utilities.md) wiki page written, documenting
  the existing `common/util`/`server/util` space (`OUT`/`Tracer`, `TickThrottler`/
  `ThrottleClockSource`, `SideToken`) with this proposal folded in as an honorable mention. All
  four open questions ruled on above. Closed; build tracked on
  [SAT_047](../tickets/SAT_047_parity-util-build.md).
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
