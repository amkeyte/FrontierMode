---
id: FRO_081
uid: FRO
number: 81
client: FrontierMode
status: done
title: BossModule facet refactor
context: '[Donna_02] Extract BossModule into Crud/Rules/Info facets, mirroring BordersFixture.
  FRO_074#8'
priority: normal
opened: '2026-09-03'
closed: '2026-09-03'
---

<!-- board:start -->
<!-- board:end -->

## Summary

BossModule facet refactor

Split from [FRO_074](FRO_074_cartographer-findings.md#8-bossmodule-has-become-a-grab-bag----find-real-homes-for-most-of-what-s-hanging-off-it)
finding 8. `BossModule` has grown to 707 lines / 18 methods spanning at least seven separate
concerns as static methods on one class (two independent jig lifecycles, the `BOSS_JIG` tick
pipeline, defensive reconciliation, interest-registry bookkeeping, mob-scope attach handling, the
defeat-detection cascade, and an admin escape hatch) -- the same shape of concern the original
`BorderModule` signal-flow diagram raised, which Boss has now grown into as well.

**Architect's ruling (FRO_074#8):** extract state-management logic into `BossFixture` facets,
mirroring `BordersFixture`'s `BordersCrudFacet`/`BordersRulesFacet`/`BordersInfoFacet` pattern:

- **`BossCrudFacet`** -- record lifecycle/mutations: `create(layer)`, `get(bossId)`,
  `materialize(bossId, entityId)`, `finalizePosition(bossId, pos)`, `markDefeated(bossId)`,
  `remove(bossId)`.
- **`BossRulesFacet`** -- pluggable strategy: `BossRules`/`DefaultBossRules`, exposed as an
  explicit facet for consistency; position validation (flatness, hazard scoring).
- **`BossInfoFacet`** -- queries/diagnostics: `isAlive(bossId)`, `getAllAlive()`, reconciliation
  diagnostics (`reconcilePathAgainstBossRecords()`), interest-registry bookkeeping, log-friendly
  debug accessors.

`BossModule` becomes a thin orchestration layer: jig registration stays as wiring (not
orchestration), and event handlers (`onBordersScopeLoaded`, `onBossJigTick`,
`onBossMobScopeLoaded`/`Unloaded`, `onLivingDeath`) delegate their actual work to the new facets
rather than doing it inline.

**Sequencing: do not action before [FRO_075](FRO_075_bootstrap-ownership.md) (finding 1) lands.**
Once FRO_075 moves the bootstrap listener into `BossModule`, that call site feeds directly into
the new `BossCrudFacet`, making the new structure immediately useful rather than adding one more
static method to the class this ticket is shrinking.

## Log
- 2026-09-03: Implemented. Split BossFixture's state-management into three facets in
  `boss/common/fixture/` -- `BossCrudFacet` (create/get/finalizePosition/materialize/
  markDefeated/remove/all), `BossRulesFacet` (wraps the BossRules/DefaultBossRules strategy,
  moved from BossModule's own private static RULES field -- kept as a static field *on the
  facet class* rather than an instance field, so every level's BossRulesFacet still shares the
  exact one DefaultBossRules/RandomSource instance BossModule.RULES always was, not a fresh one
  per level), and `BossInfoFacet` (unmaterialized/unpositioned/layers queries, plus
  reportMismatch() -- the edge-triggered compare-and-log half of the old
  reconcilePathAgainstBossRecords(), FRO_062). BossFixture gained CRUD/RULES/INFO public final
  fields wired in its constructor, mirroring BordersFixture's PATH/CRUD/RULES/INFO field
  pattern and doc-comment style exactly. BossAPI gained CRUD(Level)/RULES(Level)/INFO(Level)
  resolvers mirroring BorderAPI's own, and its createBoss()/forceDefeat() now route through
  BossCrudFacet internally instead of touching the raw fixture. BossModule's onBossJigTick
  trio (finalizeUnpositionedBosses/materializeUnresolvedBosses/reconcilePathAgainstBossRecords),
  forceMaterialize, onBordersScopeLoaded, onBossMobScopeLoaded, and onLivingDeath now delegate
  their state-management calls to fixture.CRUD/RULES/INFO instead of doing it inline or via the
  old static RULES field; each loop's control flow (which records to consider, when to skip,
  what to log) stayed in BossModule as orchestration, per the ticket's own "thin orchestration
  layer" framing. Removed BossModule's now-dead private static RULES field and
  LAST_RECONCILIATION_MISMATCH map (state moved onto BossRulesFacet/BossInfoFacet respectively)
  and the now-unused BossRules/DefaultBossRules imports. Updated three stale doc-comment
  references this made stale (BorderAPI.java's LAST_NOT_READY_REASON comment, and two
  javadoc {@link BossRules#...} references in BossModule that no longer resolve without that
  import -- switched to {@code}).
- 2026-09-03: Deviations from the ticket's literal wording, judgment calls made and not stopped
  on (Lead Dev instruction: log and proceed):
  1. **BossFixture's own methods stay public, not narrowed to package-private.** Unlike
     BordersFixture (whose get/remove/accept are package-private, reachable only via its
     facets), BossFixture.create/get/finalizePosition/materialize/markDefeated/remove/all/
     unmaterialized/unpositioned/layers all stay public exactly as before. Reason: unlike
     Border, `BossCommandHandler` and `BossSelector` already hold direct `BossFixture`
     references from `BossAPI.bosses(Level)` predating this ticket, calling these methods
     directly -- narrowing the surface would mean rewriting call sites in files this ticket
     never asked me to touch, on zero compiler to verify the result. The three new facets are
     thin, additive pass-throughs onto the still-public fixture methods, not a replacement
     surface -- BossModule/BossAPI now go through them, BossCommandHandler/BossSelector are
     untouched and still compile against the exact same public methods they always called. A
     real BordersFixture-style encapsulation lockdown for Boss (narrowing BossFixture itself,
     updating BossCommandHandler/BossSelector to route through CRUD) is a separate ticket, the
     shape Border's own FRO_047 was for Border.
  2. **`onBordersScopeLoaded`'s `BossAPI.createBoss(...)` call site was left unchanged**, rather
     than rewritten to call `BossCrudFacet.create(...)` directly as the ticket's Summary
     ("that call site feeds directly into the new BossCrudFacet") and FRO_075's own log
     anticipated. Calling the facet directly there would mean duplicating createBoss()'s own
     "fixture not available" warn-and-bail handling inline in BossModule instead of reusing it.
     Instead, `BossAPI.createBoss()` itself now delegates to `BossCrudFacet.create()`
     underneath -- so the call site's outcome flows through the new facet either way, just
     reached through Boss's sanctioned facade (matching this ticket's own "BossAPI stays the
     sanctioned external facade" framing) rather than around it. Logged explicitly since it's a
     literal-wording deviation, not silently substituted.
  3. **Interest-registry bookkeeping (`INTERESTS` map, `addInterest()`, and the
     `MobInterestRegistry.register(...)` call) stayed in BossModule**, not moved into
     BossInfoFacet despite the ticket's Summary listing "interest-registry bookkeeping" under
     BossInfoFacet's job. Reason: `MobInterestRegistry`'s supplier contract needs a single
     `Map<Level, Set<UUID>>` spanning *every* level at once (registered once, globally, at
     `registerBossMobJig()` time) -- it is not per-level data a level-scoped `BossFixture`/
     facet instance could hold without rewriting `MobInterestRegistry`'s own registration
     contract to aggregate across every live `BossFixture`, which is a functional rewrite, not
     a move, and out of this ticket's "preserve every existing behavior verbatim" constraint.
     This is the same "jig registration stays as wiring, not orchestration" principle the
     ticket's own Summary states for `registerBossJig()`/`registerBossMobJig()` -- treated
     `INTERESTS` as part of that same wiring, not fixture-owned state.
  4. **`reconcilePathAgainstBossRecords()` was split, not moved wholesale into BossInfoFacet.**
     The ticket lists it under BossInfoFacet's job in full; I kept the `BorderAPI.PATH`/`CRUD`
     resolution and the loop building `pathLayers` in `BossModule` (cross-module composition
     into a sibling module, Border -- the same orchestration-layer precedent
     `BossModule.resolveHomeBorder()` already set in the pre-existing code), and moved only the
     edge-triggered compare-and-log diagnostic (the FRO_062 half) into
     `BossInfoFacet.reportMismatch(Level, Set<Integer>)`. This also let the old
     `Map<Level, Set<Integer>>` (LAST_RECONCILIATION_MISMATCH) collapse into a plain instance
     field on BossInfoFacet, since the facet is already one-per-level (one per BossFixture) --
     the map's key becomes redundant once the state lives on the already-level-scoped facet.
  5. **BossRulesFacet's wrapped `BossRules` instance is a `static` field on the facet class**,
     not an instance field constructed per-facet/per-level. A per-instance field would have
     quietly given every level its own independent `DefaultBossRules`/`RandomSource` instead of
     the one shared instance `BossModule.RULES` always was -- a behavior change the ticket's
     "move, don't rewrite" constraint rules out, so the shared-instance semantics were preserved
     deliberately rather than defaulting to the more "natural"-looking per-facet field.
- 2026-09-03: Verification performed (no JDK/network in this sandbox -- manual checks only, per
  this project's standing note): brace/paren balance (Python `s.count('{')==s.count('}')`, same
  for parens) on every touched/created file (BossModule.java, BossAPI.java, BossFixture.java,
  BossCrudFacet.java, BossRulesFacet.java, BossInfoFacet.java, BorderAPI.java) -- all balanced.
  A brace/paren sweep across every other `.java` file in the `frontiermode` tree turned up 4
  pre-existing mismatches (RenderContext.java, BorderDisplay.java, BordersCrudFacet.java,
  BordersFixture.java) in files this ticket never touched -- confirmed these are stray parens
  inside comments/strings tripping the naive counter, not something this change introduced.
  Dotted-and-bare grep sweep across the whole `frontiermode` tree for every symbol removed,
  renamed, or relocated (`BossModule.RULES`/bare `RULES.`, `LAST_RECONCILIATION_MISMATCH`,
  `DefaultBossRules(`, `BossFixture.create/get/finalizePosition/materialize/markDefeated/
  remove/all/unmaterialized/unpositioned/layers`, `BossModule`, `BossAPI`) -- one stale doc
  comment found and fixed (BorderAPI.java's `LAST_NOT_READY_REASON` comment, referencing the
  now-relocated `BossModule.LAST_RECONCILIATION_MISMATCH` by name); every other hit was either
  inside a file this ticket intentionally touched or a benign, still-accurate reference (e.g.
  `BossCommandHandler`/`BossSelector`'s direct `BossFixture` method calls, which remain valid
  per deviation 1 above).
- 2026-09-03: Closed.
- 2026-09-03: Ticket opened.
- 2026-09-03: Ticket opened. Split from FRO_074 finding 8 for scheduling; carries the Architect's facet-design ruling verbatim. Sequencing dependency on FRO_075 landing first noted above -- not yet actionable. Parked on [RM_FRO_025](../roadmap/RM_FRO_025_donna-02.md) ("Donna_02").
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
