---
id: FRO_064
uid: FRO
number: 64
client: FrontierMode
status: done
title: Boss defeat cascade grows border/level regardless of path relationship
context: '[Susan_02] Boss defeat cascade grows border regardless of path relationship
  -- needs scoping.'
priority: normal
opened: '2026-08-30'
closed: '2026-09-03'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Boss defeat cascade grows border/level regardless of path relationship

**Reported live during playtest:** defeating an `/boss add`-ed boss that was not on the path
produced an unexpected level (border) expansion. Confirmed real, not a misread -- traced to
source, not yet fixed.

### Reproduction

1. Place a boss off the progression path with `/boss add <pos> <layer>` (or `add here`).
2. Defeat it, either in combat (`LivingDeathEvent`) or via `/boss transform defeat <selector>`.
3. The world border grows by one layer and a new "next" boss gets queued -- exactly the
   cascade that's supposed to fire only when a real progression boss falls.

### Root cause

Both defeat paths funnel into the same unconditional cascade:

- `BossModule.onLivingDeath()` -- any `Mob` death that resolves to a tracked `bossId` calls
  `fixture.markDefeated(bossId)` then `BorderAPI.grow(level, deathLocation)` unconditionally.
- `BossAPI.forceDefeat()` -- same shape, deliberately mirrored per boss-commands.md: after
  FRO_058's already-defeated guard passes, it calls `BorderAPI.grow(level, record.position())`
  unconditionally.
- `BordersPathFacet.grow(BlockPos center)` has no awareness of *which* boss triggered it or
  whether `center` has anything to do with the current path tip -- it always appends a brand-new
  border to the canonical path (`layerIndex = previous.layer() + 1`) using whatever `center` it's
  handed, tip-present or not.

So the cascade cannot currently distinguish "the boss that just died is the one progression
is waiting on" from "some other tracked boss, anywhere, died." Two ways that surfaces:

1. A boss created off-path via `/boss add <pos> <layer>` still grows the path by one layer when
   it dies, even though it was never the path tip's boss.
2. boss-commands.md's own **n:1 boss-to-border cardinality** note (multiple `BossRecord`s can
   share one layer for a themed multi-mob encounter) means even an on-path boss can trigger this
   early: defeating just one of several bosses on the current tip layer already grows the path,
   with the layer's other bosses still alive. That note already flagged this exact class of gap
   as "found but not yet ticketed" on RM_FRO_021 -- this ticket is that ticketing.

### Open questions for Architect

Not a build ticket yet -- no fix applied, per this session's Architect-scopes-before-Lead-Dev-
builds discipline:

- Should the grow+`createBoss` cascade require the defeated boss to actually be the current path
  tip's boss (via `layer` match, or eventually `borderId` once that lands per boss.md's "Known
  gaps")?
- Should it additionally require *every* boss on that layer to be defeated first, given n:1
  cardinality allows more than one?
- What does `/boss add <pos|here> <layer>` mean for progression at all -- is an added boss ever
  meant to gate/trigger growth, or is it purely a standalone encounter that should never reach
  `BorderAPI.grow` regardless of its `layer` value?

Parked on [RM_FRO_021](../roadmap/RM_FRO_021_susan-02.md) ("Susan_02") pending Architect ruling.

## Log

- 2026-08-30: Ticket opened. Live playtest report (defeating an off-path `/boss add`-ed boss
  grew the level) traced to source: `onLivingDeath`/`forceDefeat`'s grow cascade has no concept
  of path-tip relationship or per-layer boss cardinality. No fix applied -- parked for Architect
  scoping.
- 2026-08-30: Project owner drafting the actual game rule (session discussion, not yet a formal
  spec): growth should happen only when (a) `/border path grow` is invoked directly -- no new
  boss paired -- or (b) every boss mob on the path tip's layer is defeated -- new boss paired.
  Not by deleting, not by killing an off-path boss, not merely by layer. All five real
  `BorderAPI.grow(...)` call sites audited against it: the world-bootstrap grow
  (`BorderModule.onBordersScopeLoaded`, first-ever border+boss, not covered by either bullet
  above -- needs its own third case) and the gold-block trigger (`BordersTriggers.growPath`,
  live today, no boss pairing, project owner intends to cut it) are both confirmed real,
  in-code, not dead paths. `/border path grow` itself (`BorderCommandHandler.pathGrow`,
  [FRO_048](FRO_048_pathgrow-no-boss.md)) confirmed staying boss-less by design -- see FRO_048's
  own 2026-08-30 log entry and [FRO_063](FRO_063_boss-can-a-path-layer-legitimately-be-bo.md)'s
  matching entry. Rule still being drafted; not yet finalized or handed to Architect.
- 2026-09-03: **Ruled on by the Architect (Douglas)**, tightening the project owner's draft with
  `borderId` (landing via [FRO_082](FRO_082_boss-attach-build.md), the same field
  [FRO_063](FRO_063_boss-can-a-path-layer-legitimately-be-bo.md) ruled on).

  Both defeat-triggered call sites (`onLivingDeath`, `forceDefeat`) now check the defeated record's
  `borderId` after `markDefeated`, before `grow`/`createBoss`: empty `borderId` (an off-path
  `/boss add`-ed record never `/boss attach`-ed) stops the cascade right there -- this is what makes
  an added boss "purely a standalone encounter" by construction, answering the third open question.
  A non-empty `borderId` additionally requires every other record sharing that `borderId` to be
  `alive: false` first -- `borderId`, not `layer`, is the correlating key, since `layer` alone can't
  tell a deliberately standalone off-path boss from a real progression gap. This answers both the
  first question (must be the path tip's boss -- via `borderId`, not a bare `layer` match) and the
  second (n:1 cardinality -- yes, every boss sharing that border must fall first).

  The "needs its own third case" wrinkle for the world-bootstrap grow turned out not to need one:
  [FRO_075](FRO_075_bootstrap-ownership.md) (already ruled, from today's Cartographer-findings pass)
  moves that call site into `BossModule`'s own `ScopeEvent.Loaded` handler, calling
  `BorderAPI.grow(level)` + `BossCrudFacet.create(border.layer())` directly -- it was never routed
  through a defeat to begin with, so it was never in scope for this gating rule at all. The gold-block
  trigger (`BordersTriggers.growPath`) is being removed entirely per FRO_076, so it needs no ruling
  here either.

  While in this section of boss.md, also removed a stale "Event wiring for death-driven border
  growth" subsection that described death firing a `BossDeathEvent` for `BordersTriggers` to listen
  to -- this ticket's own root-cause trace confirms the real code (`onLivingDeath`/`forceDefeat`
  calling `BorderAPI.grow` directly) never worked that way; no event of that kind exists. Fixed the
  `getInitial()` bullet to name `BossModule`, not `BorderModule`, per FRO_075.

  Full ruling written onto
  [boss.md](../wiki/frontiermode/architecture/boss.md#cascade-gating-on-path-and-last-one-standing)'s
  new "Cascade gating: on-path, and last one standing" section. Build routed to
  [FRO_083](FRO_083_boss-defeat-cascade-gating-build.md), sequenced after FRO_082 since both need
  `borderId`. Closing this ticket -- the design question it held is answered.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
