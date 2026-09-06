---
id: RM_FRO_006
uid: RM_FRO
number: 6
kind: work
status: resolved
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

- 2026-08-20: **Resolved — project owner's call.** All three done-bar items now have real positive
  evidence (see the two entries below): build/playtest confirmed item 2 directly, the project
  owner's `@relevant` test confirmed item 3 directly and item 1 indirectly (the same
  `BorderPlayerLogic.evaluate()` chain `@relevant` runs through). [FRO_026](../tickets/FRO_026_sandra-implementation.md)
  closed alongside this node.
- 2026-08-20: **First real build + playtest pass, read from `run/logs/latest.log` and
  `run-server/logs/latest.log`.** Real `gradlew build` confirmed `BUILD SUCCESSFUL` (12s, 7
  actionable tasks). A login/dimension-change/logout cycle ran: `Dev` logged into the overworld
  (20:35:31), `frontiermode:border_player_bundle` wasn't present yet for that `PlayerScope` and
  fell through to create — the normal first-touch path, same shape every other bundle in this
  server log takes, not an error. Player then traveled overworld → the_nether (20:35:51) → back to
  overworld (20:35:56), then logged out cleanly (20:36:07). No error, exception, or
  `AccessFailed` was logged for `border_player_bundle` at any point in that cycle — created once,
  survived the dimension round-trip and logout without incident. Read against this node's own
  three-part done bar:
  1. **Four pure-logic files behave unchanged** — not directly evidenced either way by this log;
     they're not independently logged, so a clean run is consistent with correctness but doesn't
     positively confirm it.
  2. **`BorderPlayerStatusFixture`/`BorderPlayerBundle` construct correctly across a
     login/dimension-change/logout cycle** — reasonably well supported. Construction succeeded at
     login, no bundle-related error surfaced during the nether round-trip or at logout. Not as
     strong as it could be: unlike Satchel's own `PlayerTracking` verification aid (which logs an
     explicit `TICK`/`LOADED`/`UNLOADED` line every cycle), `BorderPlayerStatusFixture` has no
     dedicated log output of its own, so this is "no evidence of failure" rather than a positive
     per-tick confirmation.
  3. **`@relevant` resolves to a real border, not the old empty-list stub** — **not exercised this
     session.** No `/border` command or `@relevant` selector was run in either log; this pass was a
     pure login/travel/logout cycle, no command testing. Still open.

  **Separately observed, not part of this node's own scope:** the client log shows a repeating
  `[engine] CLIENT bundle became dirty (read-only violation)` WARN for `BordersBundle` — once per
  dimension load (overworld, the_nether, overworld again). That's the world-scoped borders bundle,
  not this node's player-scoped one, so it doesn't bear on Sandra's own done bar — flagging only so
  it isn't lost; worth a look wherever `BordersBundle`'s client-side read-only handling is next
  touched.

  **[Answered 2026-08-21: not a fault.** It is the client's normal first-creation path, once per new
  scope — hence the per-dimension cadence. Now documented on [Jig & Scope
  Runtime](../wiki/satchel/architecture/runtime.md), so the next log reader lands on the explanation
  instead of re-flagging it; see [FRO_040](../tickets/FRO_040_bordersbundle-warn.md). This entry is
  left as originally written.]**

  **Net: items 2 (partial, positive) and update on 1 (no evidence either way); item 3 still
  needs a real `/border`/`@relevant` command exercised in a future pass before this node's own
  done bar is actually met.** Left `open`.
- 2026-08-20: **Item 3 confirmed — project owner reports `/border info @relevant` was tested
  separately and resolves correctly.** Not captured in the logs read above (that pass never
  exercised a command); this is the project owner's own direct report, same category as other
  owner-reported confirmations logged elsewhere in this project. Worth noting this also bears on
  item 1, not just item 3: `@relevant` resolving correctly exercises the same chain item 1 asks
  about — `BorderAPI.getRelevant(ServerPlayer)`/`@relevant` read off `BorderPlayerStatusFixture`
  (see [Border § Known gaps](../wiki/frontiermode/architecture/border.md#known-gaps)), which is
  populated each tick by `BorderPlayerLogic.evaluate()` — one of the four "pure-logic" files item 1
  names, and the specific one this ticket's own 2026-08-16 fix touched. A correct `@relevant`
  result is real evidence that file behaves correctly, not just consistent-with-correctness the
  way "no crash" was. `BorderPlayerEval`/`BorderPlayerStatus` are exercised the same way, as the
  data this evaluation reads/writes; `BorderPlayerStatusProposal` is the one file this doesn't
  directly speak to.

  **All three done-bar items now have real, positive evidence behind them** (item 1 indirectly via
  `@relevant`'s own correctness, items 2 and 3 directly). Whether that's enough to call this node
  `resolved` is the project owner's own call, not assumed here — flagging that the bar looks met,
  not closing it unilaterally.
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

<!-- required-by:start -->
- [**RM_FRO_010**](RM_FRO_010_susan.md) — Prototype hardening
<!-- required-by:end -->
