<!-- bh-header:start -->
**mcRepos** — [Dashboard](../BACKHAUL.md)
<!-- bh-header:end -->

# Work Board

## open

| Ticket | Client | Pri | Title | Context | Edit |
|---|---|---|---|---|---|
| [FRO_027](tickets/FRO_027_known-failed-commands-running-list-not-b.md) | FrontierMode | low | Known-failed commands (running list, not being worked) | Running list of border commands found broken by playtest. Not being triaged or fixed until further notice -- project owner's explicit call. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_027_known-failed-commands-running-list-not-b.md) |
| [FRO_031](tickets/FRO_031_betty-donebar.md) | FrontierMode | low | Betty resolved with 2 unconfirmed items | RM_FRO_011's own log leaves an @none tab-complete check and an add-rejection retest unconfirmed. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_031_betty-donebar.md) |
| [FRO_047](tickets/FRO_047_border-interface-refactor.md) | FrontierMode | high | Build Border external interface refactor | Lead Dev build for FRO_046's ruling: eliminate BorderLogic, Result type across every applyProposal-reachable operation, BordersFixture package-private behind per-facet BorderAPI resolvers, remove BorderAuthority. Not roadmap-tracked -- foundation Karen's build depends on, not a roadmap node itself. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_047_border-interface-refactor.md) |

## in-progress

_No tickets in this state._

## blocked

| Ticket | Client | Pri | Title | Context | Edit |
|---|---|---|---|---|---|
| [FRO_045](tickets/FRO_045_karen-build.md) | FrontierMode | high | Build Boss defeat border-growth caller (RM_FRO_019) | Lead Dev build for Karen. growCenteredOn() two-layer addition, LivingDeathEvent defeat handler with MobScope.getFor() race fallback, BossAPI.createBoss() pairing. Blocked on FRO_047 (general Border-interface refactor -- facet resolvers, Result type -- split out and not roadmap-tracked). | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_045_karen-build.md) |
