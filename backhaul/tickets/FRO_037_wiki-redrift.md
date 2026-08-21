---
id: FRO_037
uid: FRO
number: 37
client: FrontierMode
status: done
title: Status drift back on Border wiki pages
context: Changelog and status prose FRO_009/FRO_010 stripped has returned to 3 Border
  pages.
priority: normal
opened: '2026-08-21'
closed: '2026-08-21'
---

<!-- board:start -->
<!-- board:end -->

## Summary

[FRO_009](FRO_009_wiki-dehistoricize.md) stripped changelog-style history off the mod wiki pages.
[FRO_010](FRO_010_wiki-no-status.md) stripped ticket/roadmap status duplication out of them. Both
closed 2026-08-13. Both classes of content are back, in the three pages Shirley and Karen build
against.

The rules being broken are stated plainly in [BHW — Wiki Conventions](../wiki/meta/bhw.md): a wiki
page "describes the thing as it is now, not how it got there," and "doesn't say whether something is
open, resolved, in progress" because "a wiki page has no mechanism forcing someone to update it the
moment a ticket closes."

**[Border Path & Layer Reconciliation](../wiki/frontiermode/architecture/path-layer-reconciliation.md)**
— five dated status markers in a 151-line page: "**Implemented, 2026-08-20:** the design below is
now real code," "Real build + the command sequence in Done bar below are still owed," "**Revised
again, 2026-08-20, same day**," "**Superseded, 2026-08-20:** an earlier revision of this design...,"
and "**Confirmed, 2026-08-20** — real build + real command sequence." Both banned classes at once.
Every one of these facts already lives in [RM_FRO_015](../roadmap/RM_FRO_015_margaret.md)'s log.

**[Border](../wiki/frontiermode/architecture/border.md)** — the Known Gaps section carries "Path/layer
reconciliation — implemented 2026-08-20, real build/command verification still owed," which is a
roadmap node's status restated in prose on a `verified` page, and which went stale the moment the
2026-08-21 build ran (see [FRO_032](FRO_032_build-of-0821.md)). The same page also carries "Rewritten
2026-08-13 following FRO_012," "**Corrected 2026-08-16**: this page previously described a second,
separate...," and an opening note that it was "written 2026-08-11 as part of the post-strip-down
documentation/roadmap-backfill pass" — the exact "don't narrate that content was backfilled or
reconstructed" case `bhw.md` calls out by name.

**[Border Vocabulary](../wiki/frontiermode/architecture/border-vocabulary.md)** — narrates its own
frontmatter history: "Written 2026-08-18 from an Architect/Ziltoid noodling session, promoted from
`draft`/Parked to `verified` on 2026-08-20." Plus "Not required to be unique, **confirmed
2026-08-20**. An earlier pass (RM_FRO_011) added validation...," and a dated section heading,
"Resolved by Game Designer (2026-08-18)."

## Scope

Strip the dated narration and status prose from all three pages; keep every underlying fact. The
Layer/Path divorce on `border-vocabulary.md` in particular is load-bearing — it's the whole reason
Margaret's design reversal happened — so this is about removing the dates and the resolution story
wrapped around the facts, not the facts.

Where a page needs to gesture at outstanding work, `bhw.md`'s rule applies: name the ticket or node
and stop there.

## Worth deciding, not just fixing

This is the second time these pages have been cleaned. A rule that has to be re-applied every few
weeks by hand is a rule without a mechanism. Whether that's worth a lint check is
[BKHL_007](BKHL_007_lint-routine.md)'s territory, but the recurrence is the finding here, not just
the drift.

## Log

- 2026-08-21: **Done — all three pages cleaned, and the rule pushed into the role bootstraps so it
  stops being something people have to remember.**

  **[Border Path & Layer Reconciliation](../wiki/frontiermode/architecture/path-layer-reconciliation.md)
  — rewritten.** It had accreted five dated status markers and was reading as a changelog wrapped
  around a design. It now describes the mechanism as it currently stands: why Layer and Path can
  diverge, that Layer is not unique and does not need to be, what `reassignLayers(Map)` actually
  does (including the phantom-path-entry self-heal, which was only recorded in Margaret's log
  before), and how the command reports its two outcomes. The "Done bar" section is gone — a done
  bar is a roadmap node's field, and [RM_FRO_015](../roadmap/RM_FRO_015_margaret.md) owns it.

  **[Border](../wiki/frontiermode/architecture/border.md) — four sites.** Dropped the "written
  2026-08-11 as part of the backfill pass" provenance note, the "Rewritten 2026-08-13" narrative
  under Runtime wiring, and the "Corrected 2026-08-16: this page previously described..." paragraph,
  each replaced by a plain statement of what is true now. The two path/layer items moved **out of
  Known gaps**, which was the more interesting problem: path/layer reconciliation isn't a gap any
  more, and layer non-uniqueness never was one — it's a design fact. Both now live in a
  "Layer and Path can legitimately diverge" subsection under Data model, where a reader looking for
  how the thing works will actually find them.

  **[Border Vocabulary](../wiki/frontiermode/architecture/border-vocabulary.md) — six sites.**
  Removed the opening note narrating its own promotion from `draft` to `verified`, the
  `layerIndex()` rename parentheticals, the dated "confirmed 2026-08-20" on the uniqueness rule, and
  the dated "Resolved by Game Designer (2026-08-18)" heading. The old "Tracking" section, which was
  a ticket-status recap, is now "Difficulty's rules surface" and states the actual architectural
  fact it was burying: `layerToDifficulty`/`ambientDifficultyAt` live on `BorderRules` rather than a
  separate `DifficultyRules` interface.

  **No facts were dropped.** Checked by diffing against the pre-edit state and reading every removed
  line: everything cut was dated narration, a correction-of-a-previous-version, or a ticket/roadmap
  status restated in prose. Two things that had been buried inside narration were promoted to plain
  statements rather than lost — the schema-only registration fact on border.md, and the
  Difficulty rules-surface decision on border-vocabulary.md. Also confirmed the cuts didn't orphan
  anything: `FRO_021` and `RM_SAT_012` were reachable only through the deleted "Corrected" paragraph
  on this page, and both remain reachable elsewhere ([SAT_032](SAT_032_isready-gate.md) and the
  roadmap index respectively).

  **The prevention half, project owner's call: the rule is now in every role's bootstrap prompt.**
  All four role pages ([Architect](../roles/architect.md),
  [Game Designer](../roles/game-designer.md), [Lead Dev](../roles/lead-dev.md),
  [PM](../roles/pm.md)) carry it, tailored one line each — Architect for `*/architecture/*`, Game
  Designer for `frontiermode/design/*`, Lead Dev for the specific case of updating a page right
  after implementing something, PM for catching it when it drifts back. It went in the **fenced
  bootstrap block**, not just the page body, because that block is what `bhrole` extracts verbatim
  into the Launch link — a rule in the body only would never reach a launched session. Each role
  page also gained a short "Wiki discipline" section for anyone reading the role rather than
  launching it. `ROLES_INDEX.md` regenerated, so all four Launch links now carry it.

  This is the second time these pages have been cleaned ([FRO_009](FRO_009_wiki-dehistoricize.md)
  and [FRO_010](FRO_010_wiki-no-status.md) did it on 2026-08-13). Putting the rule where every
  session reads it is the difference between this close and those two — but it is still convention,
  not enforcement. A lint check remains the real answer; see
  [BKHL_007](BKHL_007_lint-routine.md) item 3.
- 2026-08-21: Ticket opened by PM off a doc audit of Susan's subtree.

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
