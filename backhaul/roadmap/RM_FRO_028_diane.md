---
id: RM_FRO_028
uid: RM_FRO
number: 28
kind: work
status: resolved
title: BorderPregen terrain pregeneration
owner: Arryn
depends_on:
- RM_FRO_017
created: '2026-08-31'
superseded_by: null
ticket: FRO_066
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_FRO
<!-- bh-header:end -->

## BorderPregen terrain pregeneration

**Third of three shared-infrastructure nodes minted together for [Boss Discovery
Systems](../wiki/frontiermode/architecture/discovery-systems.md) -- see [Donna Epoch
Nodes](../wiki/plans/donna-epoch-nodes.md) for why this trio mints as a block and in this order.
The least settled of the three at mint time -- minted anyway per that page's decision, with its
own open questions carried forward below rather than closed first.**

**Scope, per [Border Pregeneration](../wiki/frontiermode/architecture/border-pregeneration.md):**
`BorderPregenFixture`, a fourth sibling fixture in `BordersBundle`, proactively and
throttle-paced generating a border's entire disk once a consumer explicitly triggers it (not
automatic on border creation -- see that page's "Starting a border's pregeneration is an explicit
call" section). Reuses Satchel's existing `TickThrottler`. Exposes `isReady()` (whole-fixture) and
`isReadyFor(UUID borderId)` (per-border) queries, surfaced cross-module via a new
`BorderAPI.isPregenReady(Level, UUID borderId)`. That page is the spec to build against, including
its **partial supersession of [Boss](../wiki/frontiermode/architecture/boss.md)'s Spawn
algorithm/Data model/Three questions sections** (position finalization is now tick-gated on this
fixture's readiness, not instant at record creation -- `boss.md` already reflects this).

**Known open items, carried forward rather than resolved before minting (see [Border
Pregeneration § Open questions](../wiki/frontiermode/architecture/border-pregeneration.md#open-questions)
for full detail on each):**
- Retry/reroll behavior if an entire disk somehow fails validation.
- Exact throttle budget (chunks per allowed tick).
- A border-creation call site that forgets to trigger pregeneration stalls its boss silently,
  forever -- punted for now; the likely eventual fix is a `Rules`-level watchdog scanning for
  exactly this stuck-forever state and surfacing it loudly, not something this node needs to
  build.
- What happens to a border that already existed before this mechanism shipped (retroactive
  pregen vs. grandfathered in) -- unresolved.
- `isReady()`/`onJigTick()`'s own Satchel-side documentation gap is tracked separately as
  [RM_SAT_024](RM_SAT_024_raymond-01.md) ("Raymond epoch maintenance 1") -- not this node's to fix,
  but worth reading if the per-fixture lifecycle contract is unclear while building against it.

**Explicitly out of scope for this node:** Boss's own bounded-wander/leash movement change (a
consumer of this page's guarantee, described on the same spec page under "Worked example: Boss
placement, revised") -- covered by whichever node picks up Boss movement work, not this one, which
is Border-side infrastructure only.

**Done bar:** `BorderPregenFixture` is registered as a fourth sibling fixture in `BordersBundle`
and builds/loads cleanly; a border's disk pregenerates on an explicit trigger call, throttled
across multiple ticks rather than blocking; `isReady()`/`isReadyFor(UUID)` and
`BorderAPI.isPregenReady()` all work end-to-end against a real border; `boss.md`'s already-updated
Position/Materialization description is real and build-verified, not just spec'd.

- 2026-08-31: Node opened, alongside its two table-mates
  ([RM_FRO_026](RM_FRO_026_dorothy.md) "Dorothy", [RM_FRO_027](RM_FRO_027_janet.md) "Janet") --
  minted per [Donna Epoch Nodes](../wiki/plans/donna-epoch-nodes.md)'s decision, despite its own
  still-open items above.

- 2026-08-31: Done bar met and playtest-verified via FRO_066's full build+playtest log -- BorderPregenFixture registered, disk pregeneration throttled and tuned against a real 2121-chunk disk with zero server-lag warnings, boss materialization now genuinely tick-gated on pregen readiness. Retry/reroll, retroactive-pregen, and stalled-trigger-watchdog items remain carried forward as noted above -- not part of this node's own done bar. Resolved.

## Required By

<!-- required-by:start -->
- [**RM_FRO_030**](RM_FRO_030_janice.md) — Environmental Tells epoch 1
- [**RM_FRO_035**](RM_FRO_035_donna-03.md) — Donna epoch maintenance 3
<!-- required-by:end -->
