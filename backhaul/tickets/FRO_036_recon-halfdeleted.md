---
id: FRO_036
uid: FRO
number: 36
client: FrontierMode
status: done
title: Finish FRO_028's page deletion
context: frontier-reconciliation.md sits in _to_delete/ but is still indexed and linked
  from 4 docs.
priority: normal
opened: '2026-08-21'
closed: '2026-08-21'
---

<!-- board:start -->
<!-- board:end -->

## Summary

[FRO_028](FRO_028_reconciliation-page-cleanup.md) moved `frontier-reconciliation.md` into
`wiki/frontiermode/architecture/_to_delete/`. The move was never finished, and the page is currently
in the worst of both states: gone from where everything points, still present everywhere that counts
it.

`backhaul lint` reports all of it:

- **Four live documents still link to the pre-move path** and are broken today —
  [RM_FRO_019](../roadmap/RM_FRO_019_karen.md), [RM_FRO_017](../roadmap/RM_FRO_017_donna.md),
  [Full Documentation Coverage Plan](../wiki/plans/doc-coverage.md), and
  [FrontierMode Operational Tiers](../wiki/plans/operational-tiers.md).
- **The page is still listed in [WIKI_INDEX.md](../WIKI_INDEX.md)** as a live `draft` under
  frontiermode/architecture, with an Edit link pointing into the `_to_delete` folder.
- **Its own 21 outbound links are broken**, since it moved a directory deeper and its relative paths
  weren't adjusted.
- **`lint` also flags it orphaned** — nothing links to it at its real location.

Karen's citation is the one that matters. Her central "real gap found scoping this" argument is
framed explicitly as a correction to this page's "missing caller, not a missing capability" finding
— that `BorderAPI.addBorder()` accepts an arbitrary center but only touches the border list, not the
canonical `borderPath`, so Progression's "centered on the defeated boss's home block" requirement
can't be met by `grow()` alone. The reasoning stands on its own; the citation doesn't resolve.

## Decision needed before the mechanical fix

Is this page being deleted or kept? Both are defensible and the cleanup differs:

- **Deleted** — strip the four inbound links (rewriting Karen's and Donna's prose so the argument
  survives without the citation), and remove it from the wiki tree so `bhw index` stops listing it.
- **Kept** — move it back out of `_to_delete/`, fix its own 21 relative links, and give it a status
  that reflects what it now is.

[FRO_028](FRO_028_reconciliation-page-cleanup.md) is `done`, so whatever was intended is only
recoverable from its body. Worth reading before choosing.

## Log

- 2026-08-21: **Done — page retired, all inbound citations rewritten, lint clean.** Project owner's
  call: take [FRO_028](FRO_028_reconciliation-page-cleanup.md)'s **option 1** (delete outright),
  which was that ticket's own recommended default.

  **Its two merge preconditions were already satisfied before this ticket was opened** — nobody had
  checked and closed the loop. FRO_028 gated deletion on merging two things into
  [Border](../wiki/frontiermode/architecture/border.md) first. Both are there:
  the terminology mapping is now border.md's "Design vocabulary bridge" section, carrying the
  "Frontier = the union of all Borders, not currently a named object or computed aggregate" line
  directly, with [Border Vocabulary](../wiki/frontiermode/architecture/border-vocabulary.md) as the
  canon page behind it; and the ecosystem framing is in border.md's own intro ("Border is also
  FrontierMode's proof-of-concept module, not just its current entire substance"). FRO_028's other
  closing instruction — fix border.md's and boss.md's own inbound links — had already been done too.

  **What actually changed here:**
  - **Three substantive citations repointed.** [RM_FRO_017](../roadmap/RM_FRO_017_donna.md),
    [RM_FRO_019](../roadmap/RM_FRO_019_karen.md) and
    [FrontierMode Operational Tiers](../wiki/plans/operational-tiers.md) all cited the same one
    thing — the "missing caller, not a missing capability" finding. Karen already carries the
    corrected and fuller version of it (`addBorder()` accepts a center but only touches the border
    list, not `borderPath`), so Donna and operational-tiers now point at Karen, and Karen's own
    sentence was rewritten to state the finding directly instead of framing itself as a correction
    to a page that no longer exists. No claim changed; the citations now resolve.
  - **One historical reference un-linked.** [Full Documentation Coverage
    Plan](../wiki/plans/doc-coverage.md)'s inventory row is a record of what was true on
    2026-08-13, and `plans/*` is a legitimate home for that per
    [BHW — Wiki Conventions](../wiki/meta/bhw.md) — so it keeps its text, marked retired, rather
    than being rewritten.
  - **One open question rehomed.** The only content on the page not already absorbed somewhere was
    the Open Items note that the per-player layer isn't mapped to any Frontier *design* concept.
    That is now a paragraph in border.md's "Known gaps," under the existing per-player evaluation
    section, keeping the Architect-not-Game-Designer attribution and the pointer to
    [Multiplayer Sketch (Parked)](../wiki/frontiermode/design/multiplayer-sketch.md). The rest of
    the page's Open Items were already recorded elsewhere: "whose frontier is it" appears in
    `multiplayer-sketch.md`, `overview.md` and `operational-tiers.md`, and the boss/ambient warning
    signal is Progression's own deferred question.
  - **The file was moved out of the wiki content root**, to `_to_delete/frontier-reconciliation.md`
    at the repo root. That is the mechanical reason it was still showing up: the `_to_delete/` folder
    it sat in was *inside* `content_roots.wiki`, so `bhw index` kept walking it and listing it as a
    live `draft` page. Moving it clear of the content root drops it from the index and takes its 22
    broken outbound links with it.

  **Result:** `backhaul lint` now reports exactly one broken link project-wide — the worked
  markdown-link example in [BHW — Wiki Conventions](../wiki/meta/bhw.md) (a title in square brackets
  followed by a placeholder path in parentheses), which is prose teaching the convention, not a real
  link. Down from 30 this morning. Wiki index 34 → 33 pages. The orphan warning is gone.
  `bhrm validate` still clean on both graphs. Worth noting the trap, since it caught this very
  entry on the first pass: quoting that example verbatim in another file reproduces the false
  positive there too — see [BKHL_007](BKHL_007_lint-routine.md).

  **Not done, and not mine to do: the file still exists.** It is staged at
  `_to_delete/frontier-reconciliation.md`, not deleted — this session can move files on the project
  machine but cannot remove them. Actual removal is the project owner's, either by emptying
  `_to_delete/` or with a `git rm` if it should leave history cleanly. Nothing references it, and
  it is outside every content root, so it is inert where it sits.
- 2026-08-21: Ticket opened by PM off a doc audit of Susan's subtree. All findings above are
  reproducible with `backhaul lint` — see [BKHL_007](BKHL_007_lint-routine.md) on why that wasn't
  already caught.

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
