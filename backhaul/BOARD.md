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
| [FRO_054](tickets/FRO_054_mutation-data-security.md) | FrontierMode | low | Data security pass: mutation proposals | [Susan_02] Mutation defensive-hardening audit; findings split to FRO_058/059. Umbrella record. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_054_mutation-data-security.md) |
| [FRO_055](tickets/FRO_055_growth-particles-stale-tip.md) | FrontierMode | normal | Growth-trigger particles don't follow new boss loc | [Susan_02] GrowthTriggerRenderer particles stay at old spot after boss-defeat border growth. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_055_growth-particles-stale-tip.md) |
| [FRO_060](tickets/FRO_060_boss-border-mutation-validation-build.md) | FrontierMode | low | Boss/Border mutation validation build | Lead Dev build for FRO_058/059's finalized specs -- see ticket body for full scope. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_060_boss-border-mutation-validation-build.md) |
| [FRO_061](tickets/FRO_061_boss-spawned-selector-delete-despawns-it.md) | FrontierMode | low | Boss: delete despawns its mob | [Susan_02] /boss delete should despawn its mob, not orphan it -- despawn only. See body. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_061_boss-spawned-selector-delete-despawns-it.md) |
| [FRO_063](tickets/FRO_063_boss-can-a-path-layer-legitimately-be-bo.md) | FrontierMode | low | Boss: can a path layer legitimately be boss-less? | [Susan_02] Design Q: can a path layer legitimately be boss-less? Not yet scoped. See body. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_063_boss-can-a-path-layer-legitimately-be-bo.md) |
| [FRO_064](tickets/FRO_064_boss-defeat-cascade-grows-border-level-r.md) | FrontierMode | normal | Boss defeat cascade grows border/level regardless of path relationship | [Susan_02] Boss defeat cascade grows border regardless of path relationship -- needs scoping. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_064_boss-defeat-cascade-grows-border-level-r.md) |
| [FRO_068](tickets/FRO_068_ambient-difficulty-unowned.md) | FrontierMode | normal | Ambient mob difficulty scaling unowned | Design promises normal-mob difficulty scaling; nothing wires layerToDifficulty to mob stats yet. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_068_ambient-difficulty-unowned.md) |
| [FRO_073](tickets/FRO_073_onlivingdeath-duplicate-death.md) | FrontierMode | high | onLivingDeath: duplicate death events mint phantom borders/bosses | Corrects FRO_060's own explicit ruling that onLivingDeath was immune to the double-defeat bug ('a Mob can only die once'). Real playtest log disproved it: a burst of already-defeated markDefeated warnings each still ran the full grow+createBoss cascade, skipping the boss/border chain several layers ahead in 3ms and pregenerating a pointless radius-32 disk. Fixed directly, no Architect spec change. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_073_onlivingdeath-duplicate-death.md) |
| [FRO_074](tickets/FRO_074_cartographer-findings.md) | FrontierMode | normal | Cartographer findings (running list) | Running log of drift/design findings from Cartographer's diagramming pass, appended to over time. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_074_cartographer-findings.md) |

## in-progress

_No tickets in this state._

## blocked

_No tickets in this state._
