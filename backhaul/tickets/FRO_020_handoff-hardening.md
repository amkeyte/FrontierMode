---
id: FRO_020
uid: FRO
number: 20
client: FrontierMode
status: done
title: Hand off hardening batch to Lead Dev
context: Satchel-side hardening ready for Curtis; FrontierMode follows once it proves
  out. See RM_SAT_012-015, RM_FRO_009/006. All five nodes resolved, FRO_016 already
  closed -- nothing left open in this batch's scope as of 2026-08-16.
priority: normal
opened: '2026-08-14'
closed: '2026-08-16'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Admin ticket, not a bug — Architect's handoff to PM at the close of the roadmap-build session
covering the runtime-verification pass's aftermath (SAT_022-030, FRO_015-019). Asking Walter to
sequence and assign the batch below; full reasoning lives on the roadmap nodes themselves
(`bhrm` + [ROADMAP_INDEX.md](../ROADMAP_INDEX.md)), not restated here.

**Ready for Curtis now, no blockers:**
- [RM_SAT_012](../roadmap/RM_SAT_012_donald.md) — Consolidate BundleFactories into schema (ticket: SAT_028)
- [RM_SAT_013](../roadmap/RM_SAT_013_gary.md) — Silent-inertness health-check
- [RM_SAT_014](../roadmap/RM_SAT_014_joseph.md) — Fix LevelJig unload/rescope leak (confirmed live bug, not just a gap — see node)
- [RM_SAT_015](../roadmap/RM_SAT_015_george.md) — Clean up dead registrar/ModelJig code
- [RM_FRO_009](../roadmap/RM_FRO_009_judith.md) — Clean up dead BorderView code

**Sequencing preference, project owner's call:** Satchel first, FrontierMode follows behind as
proof — same shape as the original Satchel→Border prototype relationship. Get the Satchel side
loose and stable, then FrontierMode work (starting with RM_FRO_009, which has no dependency on
the Satchel batch and could run in parallel if useful) demonstrates it held up.

**Not ready — hold back from assignment:**
- [RM_FRO_006](../roadmap/RM_FRO_006_sandra.md) — graph-actionable (its `depends_on`,
  RM_FRO_008, is already reached) but genuinely blocked on
  [RM_SAT_020](../roadmap/RM_SAT_020_jerry.md) (build real `PlayerJig`/`PlayerScope` from
  scratch — ruled this session, not yet built). That dependency is cross-graph, so `bhrm` doesn't
  enforce it — don't let "shows as actionable" read as "ready." Assign once RM_SAT_020 is done.

**Also worth Walter's attention, not blocking:** `RM_FRO_008` (Border prototype verified
end-to-end) is resting on an unconfirmed `FRO_016` — needs one real join/play/quit cycle before
that convergence is airtight. Not dev work, just needs doing by someone.

`RM_SAT_016` (new-module checklist), `RM_SAT_017`/`RM_FRO_010` (hardening convergences),
`RM_SAT_018` (facade vision), `RM_SAT_019` (world-identity token) are sequenced further out —
visible on the graph, not part of this immediate handoff.

## Log

- 2026-08-24: Normalized `status: closed` -> `done` per [BKHL_006](BKHL_006_closed-status.md) —
  outside BHT's `open/in-progress/blocked/done` vocabulary, no distinct meaning intended.
- 2026-08-16: **Closed — everything in this batch's scope is resolved.** RM_SAT_012/013/014/015
  and RM_FRO_009 all show `status: resolved` on their own nodes; `FRO_016` was already `done`
  (closed 2026-08-14, predates this check); `RM_FRO_008` already shows `reached`. Nothing left
  open here. Separately worth flagging to Walter: `RM_FRO_006` (held back in this ticket's own
  text, pending `RM_SAT_020`) is now genuinely unblocked — `RM_SAT_020` resolved today (see
  [FRO_022](FRO_022_handoff-batch2.md)/[FRO_023](FRO_023_playtest-checklist-batch2.md)). Not
  reopening this ticket for that — it's new work for a future handoff, not unfinished business
  from this one.
- 2026-08-15: **[RM_SAT_017](../roadmap/RM_SAT_017_paul.md) ("Paul") reached.** Project owner's
  conscious call (PM updating docs on request, not making the call) — see that node's own log for
  the full reasoning: RM_SAT_019 is now confirmed via real play, joining RM_SAT_012/RM_SAT_014's
  existing real-play confirmation; RM_SAT_013/RM_SAT_015 stay reviewed-by-tracing only but are
  judged low-risk (diagnostic-only, pure deletion respectively). RM_FRO_010, the paired
  FrontierMode-side convergence, deliberately stays `WIP` — its own prerequisite RM_FRO_009 is
  still open, untouched since the "Satchel first" sequencing call below. RM_SAT_018 and RM_SAT_020
  are newly graph-actionable off RM_SAT_017 but weren't part of this ask — not started.
- 2026-08-15: Project owner asked to keep going toward [RM_SAT_017](../roadmap/RM_SAT_017_paul.md)
  ("Paul") after the original four landed. Lead Dev (Curtis) resolved its remaining two
  prerequisites: [RM_SAT_016](../roadmap/RM_SAT_016_kenneth.md) (new-module wiki checklist) and
  [RM_SAT_019](../roadmap/RM_SAT_019_dennis.md) (world-identity token sync, project owner's
  explicit block/defer design choice) — see each node's own log for what changed. All six of
  RM_SAT_017's `depends_on` now show `resolved`; left the convergence itself at `WIP` rather than
  flipping it, per that node's own log — a real `gradlew build` plus a real join/dimension-change/
  disconnect cycle is still owed for RM_SAT_019 specifically before this batch should be trusted
  the way RM_SAT_012's crash fix now is.
- 2026-08-15: **Satchel side of this handoff done.** Lead Dev (Curtis) resolved all four
  (RM_SAT_012, RM_SAT_013, RM_SAT_014, RM_SAT_015 — see each node's own log for what changed).
  `RM_SAT_016`/`RM_SAT_019` are now newly actionable as a result (their dependencies just
  cleared) but were not part of this session's assignment — not started. `RM_FRO_009` (the
  FrontierMode side of this same ticket) also untouched this session, matching the sequencing
  preference below (Satchel first, proven out, before FrontierMode follows). Nothing here was
  build-tested against a real `gradlew build` — this session's sandbox has no Forge/Mojang maven
  access — flagging a real compile + smoke test as the next step before treating the Satchel
  batch as fully proven.
- 2026-08-14: PM (Walter) confirmed the sequencing preference laid out above — Satchel first
  (RM_SAT_012-015), FrontierMode's RM_FRO_009 in parallel since it has no dependency on the
  Satchel batch. Moving to in-progress: the batch is ready to hand to Curtis (Lead Dev) on those
  four RM_SAT nodes plus RM_FRO_009, none of which need a separate BHT ticket — Lead Dev works
  from the roadmap nodes directly (matches RM_SAT_013/014/015/RM_FRO_009's existing `ticket: null`
  pattern). RM_FRO_006 stays held back, unchanged, pending RM_SAT_020.
  - SAT_028 closed in the same pass — promoted onto the roadmap as RM_SAT_012, so the ticket and
    node were no longer doing separate jobs. See SAT_028's own log.
  - Still open, not dev work: FRO_016 needs one real join/play/quit cycle to confirm before
    RM_FRO_008 is airtight end-to-end — flagging back to the project owner, since this needs an
    actual play session, not something a PM/Architect/Dev session can do from here.
- 2026-08-14: Ticket opened at the close of this session's roadmap-build pass.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
