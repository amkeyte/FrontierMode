<!-- bh-header:start -->
**mcRepos** — [Dashboard](../BACKHAUL.md)
<!-- bh-header:end -->

# Work Board

## open

| Ticket | Client | Pri | Title | Context | Edit |
|---|---|---|---|---|---|
| [BKHL_004](tickets/BKHL_004_deprecated-convergence-tracking.md) | Backhaul | normal | Deprecated-convergence tracking: no terminal status, no stale-reference detection |  | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/BKHL_004_deprecated-convergence-tracking.md) |
| [BKHL_005](tickets/BKHL_005_requiredby-stale.md) | Backhaul | normal | Required By blocks never regenerate | Every RM_FRO node's Required By says nothing depends on it. Ten have real dependents. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/BKHL_005_requiredby-stale.md) |
| [BKHL_006](tickets/BKHL_006_closed-status.md) | Backhaul | low | status: closed outside BHT vocabulary | bht.md defines open/in-progress/blocked/done. Six tickets carry status: closed and pass unvalidated. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/BKHL_006_closed-status.md) |
| [BKHL_007](tickets/BKHL_007_lint-routine.md) | Backhaul | normal | lint/dashboard not in any refresh routine | backhaul lint catches today's broken links for free and BACKHAUL.md was stale on all 3 counts. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/BKHL_007_lint-routine.md) |
| [BKHL_008](tickets/BKHL_008_render-output-format.md) | Backhaul | low | bhrm render --output ignores file extension | Pointing the markdown renderer at a .html path silently overwrites a generated graph with markdown. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/BKHL_008_render-output-format.md) |
| [FRO_027](tickets/FRO_027_known-failed-commands-running-list-not-b.md) | FrontierMode | low | Known-failed commands (running list, not being worked) | Running list of border commands found broken by playtest. Not being triaged or fixed until further notice -- project owner's explicit call. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_027_known-failed-commands-running-list-not-b.md) |
| [FRO_031](tickets/FRO_031_betty-donebar.md) | FrontierMode | low | Betty resolved with 2 unconfirmed items | RM_FRO_011's own log leaves an @none tab-complete check and an add-rejection retest unconfirmed. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_031_betty-donebar.md) |
| [FRO_042](tickets/FRO_042_shirley-prep.md) | FrontierMode | high | Architect prep for Shirley (RM_FRO_018) | boss.md describes a MobJig interest API that shipped differently, and is still draft. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_042_shirley-prep.md) |
| [SAT_039](tickets/SAT_039_jig-self-test-audit.md) | Satchel | high | Audit and build automatic self-test coverage for every Jig kind | Found while scoping RM_SAT_022 (Roger): nothing in Satchel exercises MobJig's CLIENT/BOTH path at all, so a real regression (client-scoped MobScopes torn down every cycle) sat completely undetected. Project owner wants this generalized: every jig kind gets its own automatic, in-repo self-test, not a manual verification pass repeated by hand each time. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/SAT_039_jig-self-test-audit.md) |
| [SAT_041](tickets/SAT_041_mobjig-side-agnostic-build.md) | Satchel | high | Build MobJig side-agnostic resolution (RM_SAT_022) | Lead Dev build for Roger. Design settled on SAT_037 (MobEntityLookup interface); this ticket is the code. Unblocked now that SAT_039/SAT_040 give it real self-test coverage to verify against instead of a promise. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/SAT_041_mobjig-side-agnostic-build.md) |

## in-progress

_No tickets in this state._

## blocked

_No tickets in this state._
