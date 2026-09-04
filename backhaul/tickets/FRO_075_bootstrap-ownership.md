---
id: FRO_075
uid: FRO
number: 75
client: FrontierMode
status: done
title: Move boss bootstrap into BossModule
context: '[Donna_02] Move boss creation from BorderModule into BossModule per Architect
  ruling. FRO_074#1'
priority: normal
opened: '2026-09-03'
closed: '2026-09-03'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Move boss bootstrap into BossModule

Split from [FRO_074](FRO_074_cartographer-findings.md#1-borderboss-level-bootstrap-ownership-is-inverted-relative-to-the-codebases-own-pattern)
finding 1. `BorderModule.onBordersScopeLoaded` currently creates the level's first Border *and*
its paired Boss record itself (`BorderAPI.grow(level)` then straight into
`BossAPI.createBoss(...)`) -- the only one of Border's three growth triggers where Border reaches
into Boss, rather than the reverse (`border.md`: "Boss depends on Border, never the reverse").

**Architect's ruling (FRO_074#1):** `BossModule` registers its own `ScopeEvent.Loaded` listener
on `BORDERS_JIG` (server + overworld only, same filter) and orchestrates the initial boss
creation from there:

    @EventHandler(key = FrontierKeys.BORDERS_JIG, event = ScopeEvent.Loaded.class)
    static void onBordersScopeLoaded(LevelScope scope, ScopeEvent.Loaded event) {
        Level level = scope.level();
        Border border = BorderAPI.grow(level).orElse(null);
        if (border != null) {
            BossCrudFacet.create(border.layer());
        }
    }

`BorderModule.onBordersScopeLoaded` is removed entirely as part of this change.

**Unblocks:** [FRO_080](FRO_080_grow-level-deprecation.md) (finding 7) and
[FRO_081](FRO_081_bossmodule-facet-refactor.md) (finding 8) are both sequenced after this one
lands -- see their own Summaries.

## Log
- 2026-09-03: Implemented. Moved the level-bootstrap listener from `BorderModule.onBordersScopeLoaded` into `BossModule`, registered on `BOSS_JIG`'s own `EventHandlers` (`registerBossJig()`) rather than `BORDERS_JIG`'s -- `BossModule` doesn't own `BORDERS_JIG`'s `LevelJigConfig` and can't register a second config for the same jig key (`JigConfigCompiler` throws on duplicate keys), but `ScopeEvent.Loaded` is dispatched on one shared foundation-wide bus and every handler in this codebase already filters by jig key itself (confirmed by reading `SatchelEventBus`/`JigConfigCompiler` directly), so subscribing from `BOSS_JIG`'s config and filtering for `BORDERS_JIG`'s key works cleanly. Bonus: `BOSS_JIG` is SERVER-applicability only, so this handler is never even compiled on the client -- the old CLIENT-side guard `BorderModule`'s version needed (`BORDERS_JIG` is BOTH-applicability) is gone, no longer needed.
- 2026-09-03: Deviated from the ticket's own pseudocode -- `BossCrudFacet.create(border.layer())` doesn't exist yet (that facet lands in FRO_081, sequenced after this ticket). Used the existing `BossAPI.createBoss(level, border)` call instead (the same one the old `BorderModule` version used), preserving the full original bootstrap logic verbatim (JIG-key check, overworld-only filter, `seeded()` check, `Result`-based `grow()` handling, `startPregeneration()` call) -- only the owning class and the sidedness guard changed. Updated three stale doc-comment references elsewhere (`BorderAPI.java`, `BorderPregenFixture.java`, `FrontierMode.java`, `BordersFixture.java`) that cited the old `BorderModule.onBordersScopeLoaded` as a live call site/pattern. Verified via brace/paren balance and a dotted+bare-reference grep sweep across every touched file plus the wider `frontiermode` tree (no compiler available in this sandbox -- see standing note).

- 2026-09-03: Ticket opened.
- 2026-09-03: Ticket opened. Split from FRO_074 finding 1 for scheduling; carries the Architect's ruling verbatim, ready for Lead Dev build. Parked on [RM_FRO_025](../roadmap/RM_FRO_025_donna-02.md) ("Donna_02").
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
