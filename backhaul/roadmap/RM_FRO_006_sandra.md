---
id: RM_FRO_006
uid: RM_FRO
number: 6
kind: work
status: open
title: Per-player border evaluation
owner: Arryn
depends_on:
- RM_FRO_008
created: '2026-08-11'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_FRO
<!-- bh-header:end -->

## Per-player border evaluation

- 2026-08-11: Node opened, status **left open** — this reflects genuinely unfinished code, not
  invented forward planning.
- 2026-08-14: `depends_on` moved from RM_FRO_002 to
  [RM_FRO_008](RM_FRO_008_sharon.md) (Border prototype verified end-to-end). This node sits on
  the *new* side of that convergence, not inside it: the per-player layer is real, evidenced
  unfinished work building on top of the now-verified prototype, not part of what got verified.
  RM_FRO_002 was accurate but too loose a prerequisite — it named the feature this depends on,
  not the fact that the feature actually works.

All six files under `border/common/player/*` are commented out — but split cleanly in two.
`BorderPlayerEval`, `BorderPlayerLogic`, `BorderPlayerStatus`, `BorderPlayerStatusProposal` have no
Satchel dependency at all and would compile unchanged the moment they're uncommented.
`BorderPlayerStatusFixture` and `BorderPlayerBundle` are the real blockers — written against a
`SatchelSetting` base class and package paths that predate the current `SatchelFixture` structure
entirely, not just "unregistered." Nothing currently constructs or registers any of it —
`BorderModule.init()` only wires the world-scoped `BordersBundle` (RM_FRO_002/003). Not chained
into RM_FRO_007 (strip-down): the strip preserved this dead code as-is rather than requiring it
finished first.

**Ruled, 2026-08-14: real `PlayerJig`/`PlayerScope`, not a `LevelScope` workaround.** Full
reasoning lives on [RM_SAT_020](RM_SAT_020_jerry.md) (the Satchel-side node that
actually builds it) — short version: `Scope` is meant to generalize beyond `Level` (players, mob
bosses, anything a bundle's fixtures operate on), and per-player state here is genuinely
identity-tied, not level-derivable (a buff-modified applicable layer, a border-compass's current
attunement) — it has to follow the player across dimensions. Hosting it on `BordersBundle` instead
was explicitly rejected: it would need a manual handoff on every dimension change, and would make
`BordersBundle` the dumping ground every future module reaches for, the exact SavedData-sprawl
problem Satchel exists to prevent.

**Real, unavoidable prerequisite this node can't satisfy on its own:** `PlayerJig`/`PlayerScope`
doesn't exist yet in any usable form — [RM_SAT_020](RM_SAT_020_jerry.md) has to
land first. That's a Satchel-side node; `bhrm` graphs are UID-independent, so there's no
`depends_on` edge enforcing this the way there would be within one graph — this node will show as
graph-actionable (its actual `depends_on`, RM_FRO_008, is reached) before it's *really*
implementation-ready. Don't assign this to Lead Dev until RM_SAT_020 is actually done; at that
point this node's own work is comparatively small — rewrite `BorderPlayerStatusFixture`/
`BorderPlayerBundle` against the finished `PlayerJig` system, reusing `BorderPlayerLogic` and the
other three pure-logic files essentially as-is, then wire registration into `BorderModule.init()`
the same way `BordersBundle` already is.

## Required By

*(computed — nothing depends on this yet)*
