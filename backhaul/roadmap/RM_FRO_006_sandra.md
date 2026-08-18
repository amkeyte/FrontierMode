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

- 2026-08-16: **Implemented by Lead Dev (Curtis) — pending real build/playtest.** All six
  `border/common/player/*` files uncommented/rewritten per this node's own plan: the four
  pure-logic files (`BorderPlayerEval`, `BorderPlayerLogic`, `BorderPlayerStatus`,
  `BorderPlayerStatusProposal`) uncommented verbatim from git history, only fixing
  `BorderPlayerLogic`'s stale `border.common.level.Border` import
  (→ `border.common.fixture.Border`) — everything else, including the no-`equals()`
  `BorderPlayerStatus` (so `BorderPlayerStatusFixture.accept()`'s `Objects.equals(current, next)`
  check marks dirty on every real eval, not just on value changes) reused exactly as originally
  written, per the "essentially as-is" plan. `BorderPlayerStatusFixture` rewritten against
  `SatchelFixture` (not `SatchelSetting`) — deliberately not persisted, since
  `BorderPlayerStatus`/`BorderPlayerEval`'s own docs already call this data "not persisted or
  synced": it's live-recomputed from world state each tick, not source-of-truth state.
  `BorderPlayerBundle` rewritten against current `SatchelBundle`/`BundleKey`/`SatchelScope` import
  paths, dead `getOrCreateFacet(Class)` stub replaced with the modern `get(FixtureKey)` accessor
  pattern (`BordersBundle.borders()`'s own shape), renamed to `status()`. New
  `FrontierKeys.BORDER_PLAYER_BUNDLE`/`BORDER_PLAYER_STATUS`/`BORDER_PLAYER_JIG` keys. Registration
  wired into `BorderModule.init()` as a second, independent `PlayerJigConfig` alongside the
  existing `LevelJigConfig` — schema/bundle/tick-handler wiring follows `PlayerTrackingModule`'s
  already-proven pattern exactly (same `ScopeEvent.Tick` shared-bus key-check discipline). No
  `capabilities()`/`persistence()` override needed (unlike Border's own `LevelJigConfig`), since
  this data isn't persisted. `BorderSelector.resolveRelevant()` now calls the real
  `BorderAPI.getRelevant(ServerPlayer)` (signature changed from the placeholder's abstract
  `Player` to `ServerPlayer`, since `PlayerScope`/`PlayerJig` are server-only by design — the old
  signature was never a real constraint, it had exactly one caller and that caller was commented
  out). Also cleaned up `BorderCommands.applySelector`'s now-stale "`@relevant` isn't implemented
  yet" special-cased failure message.

  **Not verified against a real build** — no Forge/Mojang maven access in this sandbox (see
  [FRO_023](../tickets/FRO_023_playtest-checklist-batch2.md) for the standing reason). Per this
  node's own done-bar: still need real confirmation that (1) the four pure-logic files behave
  unchanged, (2) `BorderPlayerStatusFixture`/`BorderPlayerBundle` construct correctly against
  `PlayerJig`/`PlayerScope` across a login/dimension-change/logout cycle (mirroring RM_SAT_020's
  own done-bar — no persistence claim to verify here, since this data deliberately doesn't
  persist), and (3) `@relevant` resolves to a real border instead of the old empty-list stub. Left
  `open`, not `resolved` — see [FRO_026](../tickets/FRO_026_sandra-implementation.md) for the
  playtest ask.
- 2026-08-16: **Blocker cleared — genuinely workable now, not just graph-actionable.**
  [RM_SAT_020](RM_SAT_020_jerry.md) is `resolved`, confirmed against a real dedicated-server
  login/dimension-change/logout cycle (see that node's own log and
  [FRO_023](../tickets/FRO_023_playtest-checklist-batch2.md)), not just implemented. The stale
  "don't assign until RM_SAT_020 is actually done" line below no longer applies — it's done. This
  node's own remaining work is a consumer sitting on top of already-proven `PlayerJig` plumbing,
  the same relationship Border has to `LevelJig` — no dedicated-server requirement carries forward
  to verifying this node specifically; ordinary singleplayer/integrated testing (already sufficient
  for RM_FRO_009/011/012/013) should cover it. Ready to hand to Lead Dev.
- 2026-08-16: **Feeds Tier 0 via [RM_FRO_010](RM_FRO_010_susan.md) ("Susan") directly — no
  separate Tier 0 node.** Folded into Susan's own `depends_on` after review caught this node
  skipping Susan's gate to feed a separate Tier 0 convergence node in parallel — same
  convergence-bypass shape RM_SAT_007/009 already ruled out. That separate Tier 0 node (first
  RM_FRO_014 "Shirley," then rebuilt as RM_FRO_016 "Karen") was deleted the same day — both were
  functionally just a pointer back to Susan, so Susan now carries the Tier 0 designation directly
  (see her own node). This node's own `depends_on` (RM_FRO_008) is unchanged; only the downstream
  routing moved, twice, landing on Susan for good.
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
