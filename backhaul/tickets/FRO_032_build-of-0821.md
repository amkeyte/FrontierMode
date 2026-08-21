---
id: FRO_032
uid: FRO
number: 32
client: FrontierMode
status: done
title: Record 2026-08-21 clean build on Susan
context: Clean build 2026-08-21 clears a caveat repeated in 5 places. Not recorded
  in the repo yet.
priority: high
opened: '2026-08-21'
closed: '2026-08-21'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Project owner ran a full `gradlew build` on 2026-08-21 and reports it clean. That clears the single
most-repeated caveat in [RM_FRO_010](../roadmap/RM_FRO_010_susan.md)'s subtree — and as of filing,
nothing in the repo records it.

The caveat currently stands, in these words or close to them, in five places:

- [RM_FRO_010](../roadmap/RM_FRO_010_susan.md) ("Susan"), reached entry: "none of this round's
  FrontierMode-side work has been independently verified by a full `gradlew build` run from this
  side... Real full-build verification of the complete Tier 0 substrate remains owed before
  treating this as more than a conscious, evidence-backed call."
- [RM_FRO_015](../roadmap/RM_FRO_015_margaret.md) ("Margaret"), four separate entries, each ending
  "Not independently build-verified, same standing limitation."
- [RM_FRO_009](../roadmap/RM_FRO_009_judith.md), [RM_FRO_011](../roadmap/RM_FRO_011_betty.md) and
  [RM_FRO_012](../roadmap/RM_FRO_012_carolyn.md) each carry a version of it in their
  implementation entries.

Every instance shares one root cause, documented on
[FRO_023](FRO_023_playtest-checklist-batch2.md): the agent sandbox has no Forge/Mojang maven access
and can't fetch the Gradle distribution, so no session could ever run the build itself.

## Scope

Add a dated entry to Susan recording that a full build ran clean on 2026-08-21, and reconcile the
standing caveat where it appears. **Do not rewrite the historical entries** — this project's
convention is that log entries stay as originally written (see
[BHW — Wiki Conventions](../wiki/meta/bhw.md) on history belonging to tickets and roadmap logs).
A new entry at Susan's level that names the caveat as satisfied is the right shape, matching how
her earlier `depends_on` widenings were handled.

## Explicitly not covered by this ticket

A clean build is not a clean playtest. [FRO_033](FRO_033_margaret-reruns.md) tracks the specific
in-game sequences Margaret lists as still owed, and [FRO_031](FRO_031_betty-donebar.md) tracks two
unconfirmed items on Betty's done bar. Neither is answered by a green build, and closing this
ticket should not be read as closing those.

## Log

- 2026-08-21: **Done — recorded on [RM_FRO_010](../roadmap/RM_FRO_010_susan.md).** New dated entry
  at the top of Susan's log naming the caveat as satisfied and listing all five nodes that carried
  it (RM_FRO_009, 011, 012, 015 and Susan herself), plus the shared root cause on
  [FRO_023](FRO_023_playtest-checklist-batch2.md). The historical entries are untouched, per this
  project's "don't rewrite history" convention — the new entry is the reconciliation, not a
  correction of the old ones.

  Written into the same entry, because a reader arriving at "the build is clean" should not have to
  reconstruct it: what a green build does **not** settle. Three things stay open —
  [FRO_031](FRO_031_betty-donebar.md) (two unconfirmed items on Betty's own done bar),
  [FRO_033](FRO_033_margaret-reruns.md) (Margaret's owed in-game sequences), and
  [RM_FRO_012](../roadmap/RM_FRO_012_carolyn.md)'s render-cache waiver. Susan's `reached` is now
  better-evidenced than it was this morning, not fully independently evidenced.
- 2026-08-21: Ticket opened by PM. The build result was reported directly by the project owner in a
  PM session; this ticket exists so it lands in the repo rather than staying in a conversation.

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
