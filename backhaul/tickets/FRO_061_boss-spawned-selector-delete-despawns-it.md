---
id: FRO_061
uid: FRO
number: 61
client: FrontierMode
status: open
title: 'Boss: delete despawns its mob'
context: '/boss delete should despawn its mob rather than leave it orphaned -- resolves
  the entity-orphaning gap boss.md''s ''Mutation validation boundary''/''Known gaps''
  sections already flagged (found on FRO_058), project owner''s ruling: despawn only,
  not the full defeat cascade. (This ticket originally also carried a @spawned selector
  item, rolled back -- redundant with the already-in-progress @status filter work;
  see log.)'
priority: low
opened: '2026-08-29'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

Project owner's line item.

`/boss delete` should despawn its mob, not leave it orphaned. Resolves a gap already on
record, not a new finding: [boss.md](../wiki/frontiermode/architecture/boss.md)'s
"Mutation validation boundary" and "Known gaps" sections (written during
[FRO_058](FRO_058_boss-mutation-validation-reconciliation.md)'s spec review) already flag that
`/boss delete` -\> `BossFixture.remove(UUID)` leaves a materialized record's live entity behind,
untracked but still standing in the world -- called out there as "real today, not latent" but
explicitly "undecided... flagged for a follow-up pass, not resolved here."

**Project owner's ruling, this ticket:** despawn the entity, don't run it through
`markDefeated()`/`forceDefeat()`'s cascade. Deletion is an administrative removal ("get rid of
this record"), not a "boss died" event -- it must not grow a border or queue a next boss. The
despawn itself needs the live entity handle, which means going through `BossMobFixture`/
`MobScope` (the same live-interaction surface `boss.md`'s own "Data model" section says exists
for exactly this kind of touch-the-entity operation), not a bare fixture-record edit -- an
unmaterialized record (no entity yet) has nothing to despawn and should behave exactly as
`remove()` does today.

## Standing constraint

Same as every ticket this pass: no Gradle in the agent sandbox. Whatever gets built here needs
real build/playtest evidence before closing.

## Log

- 2026-08-29: Ticket opened, parked on [RM_FRO_021](../roadmap/RM_FRO_021_susan-02.md)
  ("Susan_02"), same home as boss-commands.md's other found-not-yet-ticketed items (multi-boss
  cardinality, border-move reconciliation). Two items originally: a `@spawned` selector mode, and
  `/boss delete` despawning its mob. Not yet triaged or scoped -- Architect's call on the open
  semantic question and the despawn mechanism, per this project's standing "Architect scopes,
  Lead Dev builds" split.

- 2026-08-29: **`@spawned` selector item rolled back, project owner's call** -- redundant with
  `@status` filter work already in progress/planned (the project owner had forgotten that
  overlap when this ticket was opened). [Boss Command Surface](../wiki/frontiermode/architecture/boss-commands.md)'s
  "Status filter" section already covers this exact shape (`@status
  <alive|dead|spawned|notspawned>`, composable with any identity mode) -- a separate `@spawned`
  mode would have been a second way to ask for the same thing. This ticket now carries only the
  `/boss delete` despawn item; title and context trimmed to match.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
