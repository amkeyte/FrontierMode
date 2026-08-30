---
id: FRO_058
uid: FRO
number: 58
client: FrontierMode
status: done
title: 'Boss mutation validation + reconciliation: spec review'
context: FRO_054's data-security QA pass found BossFixture has no validation boundary
  at all -- create()/materialize()/markDefeated()/remove() accept whatever they're
  given, unlike BordersCrudFacet's radius/layer checks. Also found BossModule.reconcilePathAgainstBossRecords()
  only checks one direction (a path layer missing a boss record), never the reverse
  (a boss record whose layer matches no real border). Architect to verify boss.md's
  documented behavior against the actual source and correct/extend the spec (validation
  boundary shape, bidirectional reconciliation) before Lead Dev implements against
  it.
priority: low
opened: '2026-08-29'
closed: '2026-08-29'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Boss mutation validation + reconciliation: spec review

## Log

- 2026-08-29: Ticket opened.
- 2026-08-29: Spec review done. Read `BordersCrudFacet.failureReason()` (Border's own
  precedent), `BossFixture.create/materialize/markDefeated/remove`, `BossAPI.forceDefeat`, and
  `BossModule.reconcilePathAgainstBossRecords` against boss.md's existing text, then corrected/
  extended the spec directly on [boss.md](../wiki/frontiermode/architecture/boss.md) rather than
  just answering here. Summary of the rulings:
  - **New "## Mutation validation boundary" section.** Shape: stay with Boss's own existing
    boolean/`Optional` idiom (already used by `remove()`/`get()`, and by FRO_057's own
    `MaterializeOutcome`/`DefeatOutcome`) rather than adopting Border's heavier `Result` --
    Boss's mutations each fail on one thing, not several independent fields the way a
    `BorderProposal` does, so `Result`'s message-carrying doesn't earn its keep here.
    `create()` should reject `layer < 0` (mirrors `layerIndex < 0` exactly). `materialize()`
    needs a fixture-level already-materialized guard, not just the caller-level one
    `BossModule.forceMaterialize` already has.
  - **Found a real bug, not just a gap: `markDefeated()`/`forceDefeat()` have no
    already-defeated guard.** `/boss transform defeat <selector>`, shipped and
    playtest-verified in FRO_057, re-triggers the full grow-and-spawn cascade if run twice
    against the same already-dead boss -- a duplicate progression step with no real defeat
    behind it. This is live in the shipped command, not latent. Documented as the headline
    item in the new section, with the fix shape (`markDefeated()` returns `boolean`,
    `forceDefeat()` checks it before proceeding).
  - **`remove()`'s entity-orphaning is real today, not hypothetical.** `/boss delete` (FRO_057)
    already calls straight through to `remove()`, so a materialized boss deleted this way
    already leaves its live entity behind in production. Left as an open item in "Known gaps"
    -- whether `/boss delete` should also despawn the entity is a separate call, not folded
    into this validation-boundary ruling.
  - **Corrected the one-directional reconciliation paragraph** ("What can actually go wrong")
    -- the old wording read as a two-way set comparison; it's actually
    `pathLayers - fixtureLayers` only, now stated precisely.
  - **Ruled on the reverse direction: deferred, not built, and not with `layer` alone.** A
    hand-placed off-path boss (`/boss add <pos> <layer>`, also shipped in FRO_057) can
    legitimately hold any `layer` with no border behind it, so a naive reverse check using
    `layer` as the correlating key would false-positive on every legitimate use of that
    command. The precise version needs the still-proposal `borderId` field
    (boss-commands.md's Selector scheme) to distinguish "on-path, orphaned" from
    "intentionally standalone." Recorded as a "Known gaps" item, gated on `borderId` landing,
    rather than shipping an imprecise interim heuristic.
  - `boss.md`'s "Known gaps" now has three items (was one): the pre-existing
    non-`LivingDeathEvent` removal gap, the reverse-reconciliation deferral, and the
    `remove()` entity-orphaning question.
  Closing this ticket -- its own scope was the spec review, now done and on the wiki page.
  Implementation against the corrected spec is Lead Dev's next step, same shape as
  FRO_056 -> FRO_057: expect a follow-up build ticket when that work is picked up, not
  opened here.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
