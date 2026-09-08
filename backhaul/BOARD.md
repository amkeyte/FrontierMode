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
| [FRO_061](tickets/FRO_061_boss-spawned-selector-delete-despawns-it.md) | FrontierMode | low | Boss: delete despawns its mob | [Susan_02] /boss delete should despawn its mob, not orphan it -- despawn only. See body. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_061_boss-spawned-selector-delete-despawns-it.md) |
| [FRO_068](tickets/FRO_068_ambient-difficulty-unowned.md) | FrontierMode | normal | Ambient mob difficulty scaling unowned | Design promises normal-mob difficulty scaling; nothing wires layerToDifficulty to mob stats yet. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_068_ambient-difficulty-unowned.md) |
| [FRO_097](tickets/FRO_097_exterior-design-ready.md) | FrontierMode | normal | New design ready: Exterior (Frontier Sickness, Feral) | Exterior design (Frontier Sickness, Feral) ready. 3 open Qs for Architect -- see Summary. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_097_exterior-design-ready.md) |
| [FRO_099](tickets/FRO_099_frontier-sickness-core-build.md) | FrontierMode | normal | Frontier Sickness core build | Dev(FrontierMode) build for RM_FRO_037 -- also fixes a live distanceToSurface bug. See body. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_099_frontier-sickness-core-build.md) |
| [SAT_049](tickets/SAT_049_satchelfixture-onjigtick-is-never-actual.md) | Satchel | high | SatchelFixture.onJigTick() is never actually invoked -- dead per-fixture tick dispatch | Discovered while building RM_FRO_037 (FrontierMode's Sick Wildlife). SatchelBundle.onJigTick() correctly loops fixtures.values() and calls fixture.onJigTick() (code reads right), and the per-tick driver chain (FoundationLifecycleDispatcher.pulse() -> jig.onTick(info) -> coupler().onJigTick(info) -> engine().onJigTick(info)) also reads right on paper -- but in a real play session it never actually reaches any fixture. Proof: Satchel's own TrackerFixture (com.arryn.satchel.common.newconfig.TrackerFixture) increments internalTicks only from onJigTick() and externalTicks from a separate countExternalTick() call; after a session that logged externalTicks=2708 on unload, internalTicks stayed at 0. This isn't isolated to FrontierMode's new code -- BorderPregenFixture (FrontierMode, LevelJig-scoped) relies on the exact same dispatch and has apparently never once ticked either: zero "[BorderPregen]" log lines exist anywhere across this world's whole play history despite 7 borders on record needing pregeneration. Suspect the break is somewhere between engine().onJigTick(info)'s bundle sweep and it actually being reached per real scope -- possibly bundlesFor(info) never getting populated the way onJigTick's sweep expects, a lifecycle-state check silently continuing past every bundle, or FoundationLifecycleDispatcher.pulse()'s jInfo.scopeInfos() not including the scopes onJigTick's sweep needs. FrontierMode worked around this locally for RM_FRO_037 (ExteriorTellFixture is now driven by a direct manual call from BorderModule.onPlayerScopeTick, same pattern BorderPlayerStatusFixture already used), but BorderPregenFixture has no such workaround yet and its whole pregeneration pipeline may be silently non-functional in real play. Recommend instrumenting the dispatch chain (or reproducing with TrackerFixture in isolation) to find exactly where internalTicks stops incrementing. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/SAT_049_satchelfixture-onjigtick-is-never-actual.md) |

## in-progress

_No tickets in this state._

## blocked

_No tickets in this state._
