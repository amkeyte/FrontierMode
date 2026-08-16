---
id: RM_SAT_020
uid: RM_SAT
number: 20
kind: work
status: open
title: Build PlayerJig/PlayerScope
owner: Arryn
depends_on:
- RM_SAT_017
created: '2026-08-14'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_SAT
<!-- bh-header:end -->

## Build PlayerJig/PlayerScope

- 2026-08-14: Node opened, ruled ("option A") over hosting per-player state on the existing
  world-scoped `BordersBundle` instead. Project owner's reasoning: `Scope` is meant to generalize
  — levels, blocks, players, mob bosses, anything that represents a live Forge-adapted data set a
  bundle's fixtures operate on — and Player is the long-term proof that the abstraction isn't
  just `LevelJig` with extra steps. Concretely: a player's applicable border layer can be modified
  by buffs that can't be recomputed generically — it has to follow the player, across dimensions,
  independent of which world-level bundle they're currently standing in. Same for "which border ID
  is a border-compass currently attuned to" — identity-tied state, not level-derivable state.
  Explicitly rejected hosting this on `BordersBundle` instead (cheaper short-term) for two reasons:
  it would need a manual handoff on every dimension change, and more importantly it would make
  `BordersBundle` "the go-to mechanism" every future module dumps player-state into — precisely
  the SavedData-sprawl problem Satchel was built to replace in the first place.

**Not a resurrection — a rebuild.** `PlayerJig`/`PlayerScope`/`PlayerScopeCoupler` (all in
`common/jig/player/*`) predate not just the Strap→JigConfig migration but an earlier
`jig`/`requireJig` package rename — they reference `com.arryn.satchel.common.requireJig.player`,
a package that no longer exists, and implement a `SatchelScope`/`ScopeCoupler` shape from before
the current `ASatchelScope`/`JigConfig` system. Build fresh against what `LevelJig`/`LevelScope`/
`LevelJigConfig` demonstrate today: a `PlayerScope extends ASatchelScope`, a `PlayerJigConfig`
(mirroring `LevelJigConfig`'s four-category-lens shape), a `PlayerScopeCoupler`, and ingress
wiring — `PlayerEvent.PlayerLoggedInEvent`/`PlayerLoggedOutEvent`/a per-player tick source — using
the old files only as a sketch of intent, not a starting point. See
[Forge Integration & Sidedness Contract](../wiki/satchel/spec/forge-integration.md) and
[Jig & Scope Runtime](../wiki/satchel/architecture/runtime.md) for the pattern to follow.

**Sequencing note, not a hard gate:** this doesn't depend on
[RM_SAT_018](RM_SAT_018_edward.md) (universal sidedness facade) — that vision is still
undecomposed and gating a ruled, concrete feature on it would stall real progress. If facade work
lands first, this jig's ingress can be built against whatever generic forwarding mechanism it
introduces instead of a dedicated ingress class; if this lands first, it just follows the current
per-scope-kind ingress pattern `LevelJig` already uses. Either order works.

**Consumer waiting on this:** [RM_FRO_006](RM_FRO_006_sandra.md) — cross-graph,
not a `depends_on` edge (`bhrm` graphs are UID-independent), documented in both directions.

## Required By

*(computed — nothing depends on this yet)*
