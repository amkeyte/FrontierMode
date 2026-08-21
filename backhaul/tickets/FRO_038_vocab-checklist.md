---
id: FRO_038
uid: FRO
number: 38
client: FrontierMode
status: done
title: FRO_029 cites a nonexistent wiki page
context: Three links to architecture/vocab-conformance-checklist.md. No such page
  exists and none is indexed.
priority: low
opened: '2026-08-21'
closed: '2026-08-21'
---

<!-- board:start -->
<!-- board:end -->

## Summary

[FRO_029](FRO_029_border-vocab-conformance.md) links three times to
`wiki/frontiermode/architecture/vocab-conformance-checklist.md`, under the title "Border Vocabulary
Conformance Checklist." No such file exists, and no page by that title appears in
[WIKI_INDEX.md](../WIKI_INDEX.md). `backhaul lint` flags all three.

FRO_029 stays in circulation: both [RM_FRO_018](../roadmap/RM_FRO_018_shirley.md) and
[RM_FRO_019](../roadmap/RM_FRO_019_karen.md) cite it in their vocab notes as the ticket that fixed
`level` → `layer` terminology on [Boss](../wiki/frontiermode/architecture/boss.md), so a reader
following that trail hits the dead links.

## Question to answer first

Which happened:

- the checklist was scoped but never written, in which case the links should go and whatever they
  were meant to point at needs a home; or
- it was written and its content was folded into
  [Border Vocabulary](../wiki/frontiermode/architecture/border-vocabulary.md) during FRO_029's own
  work, in which case the links should be repointed there and this is a five-minute fix.

The second is more likely — `border-vocabulary.md` was promoted to `verified` on 2026-08-20
specifically once FRO_029's conformance work landed — but that's a guess, and FRO_029's body should
settle it.

## Log

- 2026-08-21: **Closing as a false positive — the links are deliberate and should stay broken.**
  Answered from [FRO_029](FRO_029_border-vocab-conformance.md)'s own body, which this ticket asked
  to be read first. Its 2026-08-20 closing entry, Phase 6 (checklist retirement), states plainly:
  "The checklist page itself (`wiki/frontiermode/architecture/vocab-conformance-checklist.md`) is
  deleted as of this entry. Earlier entries in this log and other wiki pages that linked to it are
  left as originally written, per this project's 'don't rewrite history' convention — those links
  are now historical and won't resolve; **this entry is the authoritative record of what they
  said**."

  So the page was written, used through Phases 1-5, and retired on purpose; the three dangling
  links are the convention working as designed, not drift. Neither of this ticket's two proposed
  branches applies — nothing to repoint, nothing missing a home. `difficulty.md`'s own references
  were already updated at the time, which is why lint finds these three only in FRO_029 itself.

  **One real thing came out of it, carried to [BKHL_007](BKHL_007_lint-routine.md):** this is a
  permanent lint finding. Correct-by-convention links that can never be fixed mean broken-link
  count can never reach zero in this project, so `backhaul lint` can't become a blocking gate
  without a way to mark a link as deliberately historical. That's now written up there as the
  second of two false-positive classes, alongside `bhw.md`'s illustrative link example.

  No files changed by this ticket. My error at filing: I flagged the dangling links from lint
  output without reading FRO_029's closing entry first.
- 2026-08-21: Ticket opened by PM off a doc audit of Susan's subtree. Low priority: nothing is
  blocked by it, but it's cheap and it's on a path Shirley's implementer will walk.

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
