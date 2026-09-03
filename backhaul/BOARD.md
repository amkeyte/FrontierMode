<!-- bh-header:start -->
**mcRepos** — [Dashboard](../BACKHAUL.md)
<!-- bh-header:end -->

# Work Board

## open

| Ticket | Client | Pri | Title | Context | Edit |
|---|---|---|---|---|---|
| [BKHL_018](tickets/BKHL_018_role-new-missing-template.md) | Backhaul | normal | bhrole new: missing role.md.tmpl in package | bhrole new crashes: FileNotFoundError, roles/templates/role.md.tmpl not shipped in the installed package. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/BKHL_018_role-new-missing-template.md) |
| [FRO_027](tickets/FRO_027_known-failed-commands-running-list-not-b.md) | FrontierMode | low | Known-failed commands (running list, not being worked) | Running list of border commands found broken by playtest; not being triaged, per owner's call. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_027_known-failed-commands-running-list-not-b.md) |
| [FRO_031](tickets/FRO_031_betty-donebar.md) | FrontierMode | low | Betty resolved with 2 unconfirmed items | RM_FRO_011's own log leaves an @none tab-complete check and an add-rejection retest unconfirmed. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_031_betty-donebar.md) |
| [FRO_048](tickets/FRO_048_pathgrow-no-boss.md) | FrontierMode | low | Path grow doesn't create paired boss | [Susan_02] Manual path grow doesn't create a paired boss record, unlike automatic bootstrap grow. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_048_pathgrow-no-boss.md) |
| [FRO_051](tickets/FRO_051_border-load-count-mismatch.md) | FrontierMode | normal | Border load count: client vs server | [Susan_02] Client BordersFixture load count (1) mismatches server's (0) on same fresh world. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_051_border-load-count-mismatch.md) |
| [FRO_055](tickets/FRO_055_growth-particles-stale-tip.md) | FrontierMode | normal | Growth-trigger particles don't follow new boss loc | [Susan_02] GrowthTriggerRenderer particles stay at old spot after boss-defeat border growth. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_055_growth-particles-stale-tip.md) |
| [FRO_060](tickets/FRO_060_boss-border-mutation-validation-build.md) | FrontierMode | low | Boss/Border mutation validation build | Lead Dev build for FRO_058/059's finalized specs -- see ticket body for full scope. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_060_boss-border-mutation-validation-build.md) |
| [FRO_061](tickets/FRO_061_boss-spawned-selector-delete-despawns-it.md) | FrontierMode | low | Boss: delete despawns its mob | [Susan_02] /boss delete should despawn its mob, not orphan it -- despawn only. See body. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_061_boss-spawned-selector-delete-despawns-it.md) |
| [FRO_068](tickets/FRO_068_ambient-difficulty-unowned.md) | FrontierMode | normal | Ambient mob difficulty scaling unowned | Design promises normal-mob difficulty scaling; nothing wires layerToDifficulty to mob stats yet. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_068_ambient-difficulty-unowned.md) |
| [FRO_073](tickets/FRO_073_onlivingdeath-duplicate-death.md) | FrontierMode | high | onLivingDeath: duplicate death events mint phantom borders/bosses | Corrects FRO_060's own explicit ruling that onLivingDeath was immune to the double-defeat bug ('a Mob can only die once'). Real playtest log disproved it: a burst of already-defeated markDefeated warnings each still ran the full grow+createBoss cascade, skipping the boss/border chain several layers ahead in 3ms and pregenerating a pointless radius-32 disk. Fixed directly, no Architect spec change. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_073_onlivingdeath-duplicate-death.md) |
| [FRO_075](tickets/FRO_075_bootstrap-ownership.md) | FrontierMode | normal | Move boss bootstrap into BossModule | [Donna_02] Move boss creation from BorderModule into BossModule per Architect ruling. FRO_074#1 | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_075_bootstrap-ownership.md) |
| [FRO_076](tickets/FRO_076_gold-block-growth-removal.md) | FrontierMode | normal | Remove gold-block path growth | [Donna_02] Remove gold-block path-growth trigger, superseded by boss-defeat growth. FRO_074#3 | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_076_gold-block-growth-removal.md) |
| [FRO_077](tickets/FRO_077_pregen-sibling-access.md) | FrontierMode | low | BorderPregenFixture sibling access | [Donna_02] Switch BorderPregenFixture to direct sibling access, not the facade. FRO_074#4 | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_077_pregen-sibling-access.md) |
| [FRO_078](tickets/FRO_078_bordermath-to-api.md) | FrontierMode | normal | Add BorderMath to BorderAPI surface | [Donna_02] Route BorderMath's geometry ops through BorderAPI's surface. FRO_074#5 | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_078_bordermath-to-api.md) |
| [FRO_079](tickets/FRO_079_debug-create-deprecation.md) | FrontierMode | low | Deprecate /border debug create | [Donna_02] Deprecate BorderCommandHandler.debugCreate() and its escape-hatch accessors. FRO_074#6 | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_079_debug-create-deprecation.md) |
| [FRO_080](tickets/FRO_080_grow-level-deprecation.md) | FrontierMode | normal | Deprecate BorderAPI.grow(Level) | [Donna_02] pathGrow gets explicit center arg; sequenced after findings 1 & 3. FRO_074#7 | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_080_grow-level-deprecation.md) |
| [FRO_081](tickets/FRO_081_bossmodule-facet-refactor.md) | FrontierMode | normal | BossModule facet refactor | [Donna_02] Extract BossModule into Crud/Rules/Info facets, mirroring BordersFixture. FRO_074#8 | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_081_bossmodule-facet-refactor.md) |
| [FRO_082](tickets/FRO_082_boss-attach-build.md) | FrontierMode | normal | Boss-less path layers: pendingAttach + /boss attach build | [Susan_02] Lead Dev build for FRO_063's ruling -- borderId, pendingAttach, /boss attach, reconciliation update. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_082_boss-attach-build.md) |
| [FRO_083](tickets/FRO_083_boss-defeat-cascade-gating-build.md) | FrontierMode | normal | Boss defeat cascade gating build | [Susan_02] Lead Dev build for FRO_064's ruling -- borderId-gated cascade, on-path + last-sibling check. Depends on FRO_082. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_083_boss-defeat-cascade-gating-build.md) |

## in-progress

_No tickets in this state._

## blocked

_No tickets in this state._
