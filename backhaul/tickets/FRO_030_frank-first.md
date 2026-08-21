---
id: FRO_030
uid: FRO
number: 30
client: FrontierMode
status: done
title: Shirley actionable while Frank still open
context: RM_FRO_018 shows as the only actionable RM_FRO node, but its MobJig prerequisite
  RM_SAT_021 is open.
priority: high
opened: '2026-08-21'
closed: '2026-08-21'
---

<!-- board:start -->
<!-- board:end -->

## Summary

`bhrm frontier --uid RM_FRO` returns exactly one node today: [RM_FRO_018](../roadmap/RM_FRO_018_shirley.md)
("Shirley," boss entity/spawn system), and [ROADMAP_INDEX.md](../ROADMAP_INDEX.md) presents it
under "Actionable now" with no qualifier — a section whose own text tells the reader "this is the
menu — pick from here, not from a line."

Shirley is not actually implementable yet. Her design is built on
[RM_SAT_021](../roadmap/RM_SAT_021_frank.md) ("Frank," `MobJig`/`MobScope`) — `MobScope.getFor(mob)`
as the boss-tagging entry point, `BossMobFixture` as a `MobJig`-scoped reverse pointer, and
`MobJig`'s foundation-pulse presence poll. Frank is `open`. Because `bhrm` graphs are
UID-independent by design (a `depends_on` naming a node under another UID is a hard error, see
[BHRM — Roadmap Conventions](../wiki/meta/bhrm.md)), there is no edge to enforce this and no way
for `frontier` to know.

This is a known-recurring shape, not a new one. [RM_FRO_006](../roadmap/RM_FRO_006_sandra.md)
("Sandra") carried the same warning against RM_SAT_020 in its own body — "this node will show as
graph-actionable before it's *really* implementation-ready. Don't assign this to Lead Dev until
RM_SAT_020 is actually done." That worked because a human read the node body. Shirley's equivalent
warning is four log entries deep and is not surfaced anywhere a person picking from the menu would
see it.

## Decision already taken

**Project owner's call, 2026-08-21: Frank goes first.** Satchel's `MobJig`/`MobScope` lands before
FrontierMode-side boss work starts. That ruling currently exists only in a PM session, not in the
repo.

## What this ticket asks for

1. Record the ordering in [RM_FRO_018](../roadmap/RM_FRO_018_shirley.md)'s own body — near the top,
   not as the newest entry in a long log — so it survives a cold read.
2. Record the mirror side on [RM_SAT_021](../roadmap/RM_SAT_021_frank.md): FrontierMode's Tier 1 is
   waiting on it, which is scheduling information Frank's own node does not currently carry.
3. Decide whether the "Actionable now" section can carry a cross-graph caveat at all. If `bhrm`
   can't express it (likely — it's UID-scoped by design), that's a real tooling gap worth its own
   BKHL ticket rather than a convention everyone has to remember. Related in spirit to
   [BKHL_004](BKHL_004_deprecated-convergence-tracking.md)'s "the graph can't see it, so nothing
   re-checks it" finding about [RM_FRO_010](../roadmap/RM_FRO_010_susan.md)'s own Satchel-side
   prerequisites.

## Log

- 2026-08-21: **Done — ordering recorded on both nodes.**
  [RM_FRO_018](../roadmap/RM_FRO_018_shirley.md) now carries a standing note directly under its
  heading, above the log, stating that Frank goes first, naming the three specific pieces of
  Frank's machinery the design rests on (`MobScope.getFor(mob)`, the `MobJig`-scoped
  `BossMobFixture`, the presence poll), and warning explicitly that this node will keep appearing
  under "Actionable now" while not being implementable. Placed as a standing note rather than only
  as a log entry, since a log entry is not where someone picking from the menu looks — the failure
  mode [RM_FRO_006](../roadmap/RM_FRO_006_sandra.md) hit against RM_SAT_020.
  [RM_SAT_021](../roadmap/RM_SAT_021_frank.md) gets the mirror: FrontierMode's Tier 1 is queued
  behind it, and its own "Verification aid" section is what actually unblocks Shirley — a compiling
  `MobJig` alone doesn't.

  **Item 3 of this ticket's scope is not done and is deliberately not carried forward here.** That
  asked whether `bhrm` can express a cross-graph caveat in the "Actionable now" section at all.
  It can't today, and that's a tooling question, not a FrontierMode one — it belongs with
  [BKHL_005](BKHL_005_requiredby-stale.md) and
  [BKHL_004](BKHL_004_deprecated-convergence-tracking.md) in front of whoever looks at the Backhaul
  CLI. Filing it as its own BKHL ticket now would pre-empt that review; noting it here so it isn't
  lost if this ticket is read later as the complete record.
- 2026-08-21: Ticket opened by PM off a doc audit of Susan's subtree. Not a new discovery — Shirley
  names Frank as a prerequisite in her own text; what's missing is that the roadmap's actionable
  menu contradicts it, and that the project owner's "Frank first" ruling isn't written down
  anywhere.

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
