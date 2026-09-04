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
| [FRO_084](tickets/FRO_084_border-vocab-growth-removed.md) | FrontierMode | normal | border-vocabulary.md: update gold-block growth as removed, not aspirational | FRO_076 removed the gold-block path-growth trigger entirely. border-vocabulary.md's 'Aspirational vs. actual' paragraph still describes it as a debug-shaped stand-in pending boss-defeat growth landing -- that condition is now met and the trigger is gone. Wiki/architecture is Architect-owned; Lead Dev flagging rather than editing. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_084_border-vocab-growth-removed.md) |
| [FRO_085](tickets/FRO_085_border-boss-dependency-inversion.md) | FrontierMode | normal | BorderCommandHandler now imports BossAPI -- pendingAttach write inverts Boss-never-reaches-back-into-Border rule | FRO_082's own literal spec (pathGrow marks the new border pendingAttach on BossFixture) requires BorderCommandHandler to import and call BossAPI.CRUD(level).addPendingAttach(...) -- built exactly as specified, flagged here rather than silently reworked. This is the one place Border now depends on Boss, inverting the border.md/FRO_075 rule ('Boss depends on Border, never the reverse'). Architect call: accept as a named, documented exception, or redesign (e.g. an event Boss listens for) so pendingAttach bookkeeping doesn't require Border to know about Boss. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_085_border-boss-dependency-inversion.md) |

## in-progress

_No tickets in this state._

## blocked

_No tickets in this state._
