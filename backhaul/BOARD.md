<!-- bh-header:start -->
**mcRepos** — [Dashboard](../BACKHAUL.md)
<!-- bh-header:end -->

# Work Board

## open

| Ticket | Client | Pri | Title | Context | Edit |
|---|---|---|---|---|---|
| [FRO_023](tickets/FRO_023_playtest-checklist-batch2.md) | FrontierMode | normal | Build+playtest checklist: Sat020/Fro009-013 | Sandbox has no Forge/Mojang maven access; owner runs real gradlew build + playtest per checklist in ticket body. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_023_playtest-checklist-batch2.md) |
| [FRO_024](tickets/FRO_024_rendering-eager-static-crash.md) | FrontierMode | high | Rendering eager static crashes dedicated server | WorldBordersRenderer built in Rendering's <clinit>; RuntimeDistCleaner blocks MultiBufferSource on DEDICATED_SERVER. First real runServer crash. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_024_rendering-eager-static-crash.md) |
| [SAT_033](tickets/SAT_033_bundle-sync-parcels-sent-unconditionally.md) | Satchel | normal | Bundle sync parcels sent unconditionally, ignoring dirty state and connected players | Observed in a real server log with no client connected: pulseSync() fires scheduleSync() every syncIntervalTicks regardless of bundle.isDirty() and regardless of whether any player is in the dimension. Pinned from project owner-supplied log, not yet actioned. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/SAT_033_bundle-sync-parcels-sent-unconditionally.md) |

## in-progress

| Ticket | Client | Pri | Title | Context | Edit |
|---|---|---|---|---|---|
| [FRO_020](tickets/FRO_020_handoff-hardening.md) | FrontierMode | normal | Hand off hardening batch to Lead Dev | Satchel-side hardening ready for Curtis; FrontierMode follows once it proves out. See RM_SAT_012-015, RM_FRO_009/006. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_020_handoff-hardening.md) |
| [FRO_022](tickets/FRO_022_handoff-batch2.md) | FrontierMode | normal | Hand off next batch to Lead Dev | FRO_009/011/012/013 + RM_SAT_020 ready for Curtis. RM_SAT_018 needs Douglas first. RM_FRO_006 still blocked. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_022_handoff-batch2.md) |

## blocked

_No tickets in this state._
