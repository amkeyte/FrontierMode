---
id: FRO_063
uid: FRO
number: 63
client: FrontierMode
status: done
title: 'Boss: can a path layer legitimately be boss-less?'
context: '[Susan_02] Design Q: can a path layer legitimately be boss-less? Not yet
  scoped. See body.'
priority: low
opened: '2026-08-29'
closed: '2026-09-03'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Boss: can a path layer legitimately be boss-less?

## Summary

**A note to hold the question, not a scoped item -- project owner + Architect (Douglas) to
consider together, not decided here.**

[FRO_062](FRO_062_boss-reconciliation-warning-spam-edge-tr.md)'s own trigger turned out to be
intentional: the project owner ran `/boss delete` against every boss on the world during
playtest cleanup, confirmed. That's exactly the scenario
[boss.md](../wiki/frontiermode/architecture/boss.md)'s reconciliation check currently has no
room for -- its "What can actually go wrong" section states a path layer with no matching boss
record is "a real data bug... not a normal transient state," full stop, and
`BossModule.reconcilePathAgainstBossRecords()` warns accordingly (now edge-triggered per
FRO_062, but still always framed as corruption). There's currently no concept of a
*deliberately* boss-less path layer anywhere in the design.

**The actual question:** should a path layer be allowed to legitimately have no boss? Two
sub-questions worth separating once this gets picked up:

- If yes, the reconciliation check's own framing needs to change -- it can't keep calling every
  boss-less layer "a real data bug" once that's sometimes true on purpose. Would need some way
  to distinguish "this layer was never supposed to have a boss removed" from "this layer's boss
  is missing and that's fine" -- a flag, a different removal path, something.
- **Attach/detach as a real, eventual operation:** move a boss on/off a path layer deliberately,
  distinct from outright record deletion. [FRO_061](FRO_061_boss-spawned-selector-delete-despawns-it.md)'s
  ruling (delete despawns the mob) already produces a boss-less layer as a side effect today --
  worth considering whether that's already a crude form of "detach," or whether a real
  attach/detach mechanism is a different, more deliberate operation than delete ever should be
  (e.g., one that doesn't destroy the record, just unlinks it from progression).

Not scoped, not ruled on, no code implied by this ticket. Parked on
[RM_FRO_021](../roadmap/RM_FRO_021_susan-02.md) ("Susan_02") as the holding pen this session's
other found-along-the-way items already live in.

## Log

- 2026-08-29: Ticket opened to hold the question for a future project owner + Architect
  conversation. No triage, no scope, no ruling yet -- deliberately.
- 2026-08-30: Project owner adds a concrete data point sharpening the attach/detach
  sub-question: after deleting a single boss from a path layer, there is currently no way
  to replace it, and after a delete-all, the server has to be reset entirely to recover.
  Quoting directly: "after deleting a boss from the path there is no way to replace it;
  and after delete all the server must be reset. so attach is probably going to be pretty
  important." Still not scoped or ruled on here -- this just raises the practical stakes
  for whenever the project owner + Architect pick up the design conversation.
- 2026-08-30: **First sub-question answered.** While working out [FRO_064](FRO_064_boss-defeat-cascade-grows-border-level-r.md)'s
  grow-game-rule note, project owner ruled that `/border path grow` ([FRO_048](FRO_048_pathgrow-no-boss.md))
  stays boss-less permanently by design, with attach as the intended second step -- "let's leave
  the path command boss-less. once we can attach a boss to the path tip, that's the solution. a
  two step admin job for now." So: yes, a path layer can legitimately have no boss, at least in
  the window between a manual grow and its follow-up attach. Reconciliation's current "any
  boss-less layer is a real data bug" framing is now confirmed too strict as a blanket rule --
  still needs a way to distinguish that window from genuine corruption, which is this ticket's
  other still-open sub-question. Attach/detach itself remains not scoped, not built -- this
  raises its priority (it's now the sanctioned completion step for FRO_048's admin workflow, not
  just a nice-to-have for boss replacement) but doesn't resolve it.
- 2026-09-03: **Both sub-questions ruled on by the Architect (Douglas).**

  **Reconciliation framing:** a small persisted `Set<UUID> pendingAttach` (border ids) on
  `BossFixture` replaces after-the-fact inference. `pathGrow()` succeeding, and `/boss delete`
  removing an on-path record, both add the border's id to it; `reconcilePathAgainstBossRecords()`
  now checks `pendingAttach` before logging a gap as a real data bug -- a covered gap logs at a
  lighter level ("awaiting attach"), an uncovered one still logs loud, same as today.

  **Attach/detach:** no separate detach operation -- `/boss delete` already produces the
  `pendingAttach` state above once it's updated per this ruling, which *is* what a deliberate
  detach would do. What was actually missing is attach: `/boss attach <border-selector>`, a thin
  wrapper around the normal creation path that sets the new record's `borderId` and clears
  `pendingAttach`. Depends on `Optional<UUID> borderId` landing on `BossRecord` --
  [boss-commands.md](../wiki/frontiermode/architecture/boss-commands.md)'s "Selector scheme"
  section already ruled on its shape; this ruling is what makes building it necessary now.

  Full ruling written onto
  [boss.md](../wiki/frontiermode/architecture/boss.md#boss-less-path-layers-and-attach)'s new
  "Boss-less path layers and attach" section, with matching updates to
  [boss-commands.md](../wiki/frontiermode/architecture/boss-commands.md)'s command tree and
  selector scheme. Build routed to [FRO_082](FRO_082_boss-attach-build.md), same split pattern
  FRO_058/FRO_059 -> FRO_060 used. Closing this ticket -- the design question it held is answered.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
