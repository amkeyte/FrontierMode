---
id: FRO_075
uid: FRO
number: 75
client: FrontierMode
status: open
title: Move boss bootstrap into BossModule
context: '[Donna_02] Move boss creation from BorderModule into BossModule per Architect
  ruling. FRO_074#1'
priority: normal
opened: '2026-09-03'
closed: null
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

- 2026-09-03: Ticket opened.
- 2026-09-03: Ticket opened. Split from FRO_074 finding 1 for scheduling; carries the Architect's ruling verbatim, ready for Lead Dev build. Parked on [RM_FRO_025](../roadmap/RM_FRO_025_donna-02.md) ("Donna_02").
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
