---
id: BKHL_005
uid: BKHL
number: 5
client: Backhaul
status: done
title: Required By blocks never regenerate
context: Every RM_FRO node's Required By says nothing depends on it. Ten have real
  dependents.
priority: normal
opened: '2026-08-21'
closed: '2026-08-24'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Every roadmap node under RM_FRO carries the same computed footer:

```
## Required By

*(computed — nothing depends on this yet)*
```

It is wrong on at least ten of them. [RM_FRO_010](../roadmap/RM_FRO_010_susan.md) is depended on by
[RM_FRO_018](../roadmap/RM_FRO_018_shirley.md); 018 by [RM_FRO_019](../roadmap/RM_FRO_019_karen.md);
018 and 019 both by [RM_FRO_017](../roadmap/RM_FRO_017_donna.md); and all six of Susan's children
(RM_FRO_006, 009, 011, 012, 013, 015) by Susan herself.

This is not a graph error. `bhrm validate --uid RM_FRO` returns "OK — 17 node(s), no cycles,"
`bhrm frontier` computes the correct single actionable node, and
[ROADMAP_INDEX.md](../ROADMAP_INDEX.md) renders the full dependency structure accurately. The
forward `depends_on` edges are all correct. It's the reverse-direction block written into each
node's own file that never regenerates.

## Why it matters

`Required By` is the block a person checks before touching a node — the "what breaks downstream if I
change this" question. It currently answers "nothing" on nodes with real dependents, including on
the convergence the whole Tier 0 designation hangs off. A reader who trusts it will conclude Susan
is safe to modify in isolation.

Worth noting the shape is the same one [BKHL_004](BKHL_004_deprecated-convergence-tracking.md)
describes from the other direction: information that exists in frontmatter but that nothing reads
back. There, it's `superseded_by`; here, it's the reverse edges `bhrm` already computes for
`dependents`.

## Suggested direction, not a committed design

`bhrm dependents <ID>` already answers this correctly on the command line — the data is there and
the traversal exists. The gap is that nothing writes it into the node file. Candidates:

1. Have `bhrm refresh` / `bhrm index` rewrite each node's `Required By` block the way `bh-header`
   blocks are already recomputed — same marker-delimited-region pattern, no new traversal needed.
2. If the block is deliberately not maintained, drop it from the node template rather than emitting
   a claim that is false by default. A missing section is honest; a section that always says
   "nothing depends on this" is not.

Option 1 is preferred — the section is genuinely useful when accurate.

## Log

- 2026-08-24: **Closed.** Not hand-patched here — the stale Required By blocks stay stale until the
  real fix lands, deliberately, rather than risk a manual rewrite drifting from what `dependents()`
  actually computes. Tracked upstream in the Backhaul repo as BH_011 (wire `dependents()` into the
  same marked-block regeneration BH_008 already established for the HTML graphs).
- 2026-08-21: Ticket opened by PM off a doc audit of Susan's subtree. Filed under BKHL per this
  project's convention that reports about the Backhaul CLI itself land here rather than routing
  through the Backhaul repo's own tracker (see
  [BKHL_001](BKHL_001_refresh-dashboard-index-commands-bake-sa.md)).

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/backhaul)
<!-- bh-header:end -->
