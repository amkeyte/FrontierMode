<!-- bh-header:start -->
**mcRepos** — [Dashboard](../BACKHAUL.md)
<!-- bh-header:end -->

# Work Board

## open

| Ticket | Client | Pri | Title | Context | Edit |
|---|---|---|---|---|---|
| [BKHL_011](tickets/BKHL_011_log-append-command.md) | Backhaul | normal | bht has no command to append a ticket log entry | Every dated Log entry this session went through hand-edited markdown with fragile exact-string anchors, twice failing. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/BKHL_011_log-append-command.md) |
| [BKHL_012](tickets/BKHL_012_status-transition-command.md) | Backhaul | normal | bht has no command for in-progress/blocked status | open/close are the only write verbs; the two middle lifecycle states can only be set by hand-editing frontmatter. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/BKHL_012_status-transition-command.md) |
| [BKHL_013](tickets/BKHL_013_length-guideline-warning.md) | Backhaul | low | bht open doesn't warn on oversized title/context | bht.md's own title<=40/context<=100 standard isn't checked on write; easy to blow past without noticing. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/BKHL_013_length-guideline-warning.md) |
| [BKHL_014](tickets/BKHL_014_default-config-resolution-bug.md) | Backhaul | normal | Default config resolution ignores checkout, contradicts bht.md | bht.md says omit --config for the checkout's own default; bare bht commands fail with ConfigError instead. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/BKHL_014_default-config-resolution-bug.md) |
| [FRO_027](tickets/FRO_027_known-failed-commands-running-list-not-b.md) | FrontierMode | low | Known-failed commands (running list, not being worked) | Running list of border commands found broken by playtest. Not being triaged or fixed until further notice -- project owner's explicit call. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_027_known-failed-commands-running-list-not-b.md) |
| [FRO_031](tickets/FRO_031_betty-donebar.md) | FrontierMode | low | Betty resolved with 2 unconfirmed items | RM_FRO_011's own log leaves an @none tab-complete check and an add-rejection retest unconfirmed. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_031_betty-donebar.md) |
| [FRO_045](tickets/FRO_045_karen-build.md) | FrontierMode | high | Build Boss defeat border-growth caller (RM_FRO_019) | Lead Dev build for Karen. growCenteredOn() two-layer addition, LivingDeathEvent defeat handler with MobScope.getFor() race fallback, BossAPI.createBoss() pairing. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_045_karen-build.md) |
| [FRO_048](tickets/FRO_048_pathgrow-no-boss.md) | FrontierMode | low | Path grow doesn't create paired boss | Manual path grow doesn't create a paired boss record, unlike the automatic bootstrap grow. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_048_pathgrow-no-boss.md) |

## in-progress

_No tickets in this state._

## blocked

_No tickets in this state._
