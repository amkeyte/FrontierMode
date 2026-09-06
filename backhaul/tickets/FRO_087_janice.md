---
id: FRO_087
uid: FRO
number: 87
client: FrontierMode
status: open
title: Environmental Tells epoch 1
context: Already built (uncommitted, unverified) -- Curtis's job is build+playtest,
  not new work.
priority: normal
opened: '2026-09-05'
closed: null
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
- 2026-09-06: Found BossTellFixture (+ BossTellRecord) already implemented on disk, uncommitted -- registered in BossBundle/FrontierKeys, wired into both boss-creation call sites in BossJigHandlers, BossRules/DefaultBossRules already carry the tell coefficients/interval. Appears to satisfy this ticket's What-to-build and done bar against the discovery-systems spec. Never built or playtested -- ticket's standing constraint not yet met, and the compiled .class predates the current source so there's no compile evidence either. Curtis's actual task: real build + playtest verification of the existing code, not greenfield implementation. Context field updated to match.
- 2026-09-05: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
