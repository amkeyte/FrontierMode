---
id: FRO_022
uid: FRO
number: 22
client: FrontierMode
status: in-progress
title: Hand off next batch to Lead Dev
context: FRO_009/011/012/013 + RM_SAT_020 ready for Curtis. RM_SAT_018 needs Douglas
  first. RM_FRO_006 still blocked.
priority: normal
opened: '2026-08-16'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

Admin ticket, not a bug — same shape as [FRO_020](FRO_020_handoff-hardening.md). PM (Walter)
assessing readiness after the RM_SAT_017 ("Paul") hardening batch landed and a new source-level
resilience pass produced three FrontierMode-side siblings under RM_FRO_010 ("Susan"). Full
reasoning lives on the roadmap nodes themselves, not restated here.

**Ready for Curtis now, no blockers:**
- [RM_FRO_009](../roadmap/RM_FRO_009_judith.md) — Clean up dead BorderView code
- [RM_FRO_011](../roadmap/RM_FRO_011_betty.md) — Border mutation validation hardening (3 bugs,
  one a real NPE crash on 3 of 5 `/border transform` forms)
- [RM_FRO_012](../roadmap/RM_FRO_012_carolyn.md) — Client render lifecycle cleanup (live
  unconditional particle effect + unbounded per-world cache leak)
- [RM_FRO_013](../roadmap/RM_FRO_013_judy.md) — Border fixture & compass robustness (includes a
  live item-duplication exploit via offhand slot)
- [RM_SAT_020](../roadmap/RM_SAT_020_jerry.md) — Build PlayerJig/PlayerScope (concrete rebuild
  shape already specified: `PlayerScope`/`PlayerJigConfig`/`PlayerScopeCoupler`, mirroring
  `LevelJig`'s pattern)

All five carry the same discipline the RM_SAT_012-019 batch did: specific root cause, stated fix
direction, explicit real-play/real-build done-bar — not just "add robustness."

**Sequencing:** no hard constraint between any of these five, same as last batch — all depend
only on already-reached convergences (RM_FRO_008, RM_SAT_017). The four RM_FRO ones share a
natural grouping (they're what's gating RM_FRO_010 from reaching), RM_SAT_020 is fully
independent. Pick order freely, or run in parallel.

**Not ready — hold back from assignment:**
- [RM_SAT_018](../roadmap/RM_SAT_018_edward.md) — its own body says it outright: "still mostly
  undecomposed" beyond RM_SAT_019. Needs Architect (Douglas) scoping before this is
  ticket-shaped work for Lead Dev, not a Lead Dev judgment call to fill in the gaps himself.
- [RM_FRO_006](../roadmap/RM_FRO_006_sandra.md) — unchanged from FRO_020: graph-actionable but
  genuinely blocked on RM_SAT_020 actually landing (built, not just started).

## Log

- 2026-08-16: **All five items implemented by Lead Dev (Curtis), unverified.** RM_SAT_020,
  RM_FRO_009, RM_FRO_011, RM_FRO_012, RM_FRO_013 — see each node's own log for what changed.
  One deviation from the letter of a node's fix direction, flagged rather than silently decided:
  RM_FRO_011 item 3 (`fixLayers`) took the "honest message instead of a false positive" branch
  the node's own text explicitly sanctioned as an acceptable alternative, not the full
  layerIndex-reorder implementation — the reorder turned out to interact badly with this same
  pass's new collision validation (item 2), and designing around that safely isn't a call to make
  without a real build to verify against. Not filed as its own ticket — a design question for
  Architect/PM to pick up if it's worth prioritizing, not blocking. Real build + the
  [FRO_023](FRO_023_playtest-checklist-batch2.md) checklist still owed on all five before any of
  this counts as resolved.
- 2026-08-16: Lead Dev (Curtis) confirmed this session's sandbox has no Forge/Mojang maven access
  (curl to maven.minecraftforge.net/files.minecraftforge.net/libraries.minecraft.net all 403 from
  the proxy) — can't run a real `gradlew build` or playtest here, which RM_FRO_011/012/013's
  stated done-bars require. Project owner's call: implement here, project owner runs real
  build/playtest against a checklist — see [FRO_023](FRO_023_playtest-checklist-batch2.md).
  Also, RM_SAT_020 had no stated done-bar (unlike its siblings here) — added directly to the node
  per project owner's instruction, before implementation started; flags a real dedicated-server
  test specifically (not just singleplayer), given the new client/server JVM split and PlayerJig
  being the first per-connection-driven jig in either repo. Order confirmed: Satchel
  (RM_SAT_020) first, then the four RM_FRO nodes, since FrontierMode depends on Satchel.
- 2026-08-16: Moving to in-progress — this ticket is the handoff. None of the five need a
  separate BHT ticket; Lead Dev works from the roadmap nodes directly, same pattern established
  in FRO_020.
- 2026-08-16: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
