---
id: FRO_039
uid: FRO
number: 39
client: FrontierMode
status: done
title: Shirley and Karen each name two nodes
context: RM_FRO_014/016 were deleted and their persona names reissued to the live
  RM_FRO_018/019.
priority: low
opened: '2026-08-21'
closed: '2026-08-21'
---

<!-- board:start -->
<!-- board:end -->

## Summary

RM_FRO_014 was named "Shirley" and RM_FRO_016 was named "Karen." Both were deleted on 2026-08-16 when
the standalone Tier 0 convergence was cut and [RM_FRO_010](../roadmap/RM_FRO_010_susan.md) ("Susan")
absorbed the designation. Both persona names were then reissued — to
[RM_FRO_018](../roadmap/RM_FRO_018_shirley.md) and [RM_FRO_019](../roadmap/RM_FRO_019_karen.md),
which are live, `open`, and precisely the two nodes Tier 1 runs through.

Six documents discuss the deleted Shirley and Karen by name in their historical prose —
[RM_FRO_006](../roadmap/RM_FRO_006_sandra.md), [RM_FRO_010](../roadmap/RM_FRO_010_susan.md),
[RM_FRO_017](../roadmap/RM_FRO_017_donna.md),
[BKHL_004](BKHL_004_deprecated-convergence-tracking.md),
[FRO_026](FRO_026_sandra-implementation.md), and
[FrontierMode Operational Tiers](../wiki/plans/operational-tiers.md). Every bare-name reference in
this project is now ambiguous without an ID beside it.

It resolves today only by accident: RM_FRO_018 is the sole actionable node, so "start on Shirley"
has exactly one sensible reading. That stops being true as soon as Frank lands (see
[FRO_030](FRO_030_frank-first.md)) or Donna grows a sibling.

## The one place this is recorded is orphaned

[Top Baby Names of 1945](../wiki/reference/baby-names-1945.md) records the reuse honestly — "Rank 14
(Shirley) and rank 16 (Karen) were briefly assigned to RM_FRO_014 and RM_FRO_016, both deleted." But
`backhaul lint` reports that page as orphaned: nothing anywhere links to it. It is the project's
canonical persona-name source and it is unreachable by navigation.

## Options

1. **Leave the names, add a note.** A one-line "persona name previously used by the deleted
   RM_FRO_014" on 018 and 019, plus real links to `baby-names-1945.md` from somewhere that gets
   read — [BHRM — Roadmap Conventions](../wiki/meta/bhrm.md) is the natural home, since it's where
   the slug convention is already explained.
2. **Rename 018 and 019** to unused 1945 names. Cheap while both are `open` and nothing has been
   built against them; the filenames change, the IDs don't. Gets more expensive after Frank lands.
3. **Adopt a no-reuse rule** for retired personas generally, recorded in `bhrm.md`, so this doesn't
   recur. Complements 1 or 2 rather than replacing either.

PM's read: option 1 plus option 3. Renaming live nodes to fix a naming collision trades a real
ambiguity for a real churn, and the ambiguity is cheap to annotate.

## Log

- 2026-08-21: **Done — options 1 and 3, project owner's call.** Their words on the reuse: "it was a
  convenience at one point. turned out a bad idea." Option 2 (renaming the live nodes) was not
  taken — the ambiguity is cheap to annotate and renaming trades it for churn.

  **Option 1, annotate.** [RM_FRO_018](../roadmap/RM_FRO_018_shirley.md) and
  [RM_FRO_019](../roadmap/RM_FRO_019_karen.md) each carry a short name note directly under the
  heading: the persona also named a Tier 0 convergence deleted 2026-08-16, so a bare name in older
  prose may mean either node, and the name should always be paired with its ID.

  **Option 3, a standing rule.** [BHRM — Roadmap Conventions](../wiki/meta/bhrm.md) gains a
  "Persona names are not reusable" section — a retired node's name does not go back in the pool.
  The reasoning is recorded as the concrete failure rather than as a principle: reissuing made every
  bare-name reference ambiguous *including in the log entries explaining why the originals were
  deleted*, which necessarily still name them. The ID is the identity; the persona is a handle for
  talking about a node out loud, and a handle pointing at two things is worse than none.

  **Side effect worth noting:** that section links
  [Top Baby Names of 1945](../wiki/reference/baby-names-1945.md), which `backhaul lint` had been
  reporting as orphaned — the project's canonical persona-name source, previously unreachable by
  navigation. It now has an inbound link from the page that tells you to use it. That was listed as
  a finding on [BKHL_007](BKHL_007_lint-routine.md); it's resolved as a byproduct rather than needing
  its own pass.
- 2026-08-21: Ticket opened by PM off a doc audit of Susan's subtree. Filed low — nobody has
  actually been confused by it yet, and the fix is cheap whenever it's picked up.

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
