---
id: FRO_041
uid: FRO
number: 41
client: FrontierMode
status: done
title: 'Architecture pages: strip doc self-history'
context: 'Cross-cutting sweep of both mods'' architecture pages: remove page-about-itself
  narration, keep concise facts.'
priority: normal
opened: '2026-08-21'
closed: '2026-08-21'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Architecture pages had accreted a second subject: themselves. Alongside facts about Border, jigs,
fixtures and persistence sat paragraphs about what the page *used to say*, which pass corrected it,
and why — the "used to be this, we changed it for reason Z, so now I'll preserve that in five
places at length" shape. Project owner's call: architecture pages describe their subject concisely.
History reduces to a short lesson where the lesson still changes a decision, and otherwise goes.

Distinct from [FRO_037](FRO_037_wiki-redrift.md), which removed *status* prose (open/resolved/owed)
from three Border pages. This is the sibling problem — narration about the document's own past —
across both mods' architecture trees plus `satchel/spec`.

## The rule applied

Three outcomes per instance, in order of preference:

1. **Remove.** "This page previously named the method `bundle.loadAll`; corrected here" tells a
   reader nothing about networking. Most instances landed here.
2. **Rewrite as a present-tense fact.** `LevelJig` identity "used to be derived purely from
   `dimension().toString()`; as of RM_SAT_019 it folds in a token" becomes what it does now.
3. **Keep as a short lesson**, only where the history still changes what someone would do. Two
   qualified, both because the failure is invisible to the obvious check.

## Pages changed

| Page | What went |
|---|---|
| `satchel/architecture/fixture.md` | −38 lines. A whole retained "Facet (from `notes.md`)" section, kept "for historical context," plus the two-source-file reconciliation preamble. |
| `satchel/architecture/persistence.md` | −22. The migration note and a "re-checked both claims / location was stale and is now corrected / a prior pass claimed it was gone, it wasn't" block. |
| `satchel/architecture/runtime.md` | 4 sites: the SAT_024 narrative, the SAT_020 "previously a stub" note, `LevelJig`'s identity history, the `ModelJig` deletion story, and a "not as this page previously speculated". |
| `frontiermode/architecture/difficulty.md` | −17. A provenance preamble plus "Implemented, 2026-08-20" and "Build-verified, 2026-08-20" blocks. |
| `frontiermode/architecture/border.md` | The fixture/facet "terminology drift" framing and a registrar-pattern aside. |
| `frontiermode/architecture/border-vocabulary.md` | A "previously ambiguous" clause and a note to restage another page. |
| `satchel/spec/forge-integration.md` | 2 sites. A spec page carrying "this page previously documented X as current-state fact; that was accurate when written and is now stale" — the strictest page in the wiki narrating its own error. |
| `satchel/architecture/net.md`, `bundle.md` | Migration provenance notes. |

## The two lessons kept

- **`AScopeCoupler.getOrCreate()`'s exception contract** (`runtime.md`). Recast from "the client
  engine threw the wrong type until SAT_024" to a forward-looking note: a new engine or `get()`
  branch throwing anything but `BundleNotFound` silently disables the fallback, and the symptom is
  a crash on the normal first-access path rather than a visible wiring mistake. Someone adding a
  jig kind can re-break this; that is what makes it worth a sentence.
- **Border's two-call persistence requirement** (`border.md`, already present). `capabilities(true,
  ...)` without the matching `policies().persistence(...)` compiles cleanly and fails at foundation
  boot. Kept because "it compiles" is exactly the check that misses it.

## Verification

- `backhaul lint`: one broken link project-wide, the deliberate example in
  [BHW — Wiki Conventions](../wiki/meta/bhw.md). Unchanged.
- **No orphaning.** Every ticket and node link removed — SAT_003, SAT_004, SAT_017, SAT_020,
  SAT_024, RM_SAT_014, RM_SAT_015, RM_SAT_019 — was checked and remains reachable from elsewhere,
  between 1 and 10 other files each. Zero newly orphaned pages.
- Removed content read line by line against the pre-edit state. `fixture.md`'s deleted "Facet"
  section was confirmed a near-duplicate of the "Fixture" section in older wording; its one
  apparently-unique line described `FacetHydrator`, a type that no longer exists.

## Deliberately untouched

- **[Jig & Strap Registration — History](../wiki/satchel/architecture/jig-registration-break.md)**
  and its **[recovery plan](../wiki/satchel/architecture/jig-registration-recovery-plan.md)** —
  pages dedicated to this material by design, which is the project owner's stated exception. They
  are where a "how did this get this way" question is supposed to land.
- **Design pages** (`frontiermode/design/*`) and `wiki/plans/*` — not architecture, and `plans/*` is
  a legitimate home for initiative history per [BHW](../wiki/meta/bhw.md).

## Found while sweeping, not fixed

[Bundle](../wiki/satchel/architecture/bundle.md) opens by defining a bundle as a "named, keyed
container of **facets**." [Fixture](../wiki/satchel/architecture/fixture.md) and
[Border](../wiki/frontiermode/architecture/border.md) both state that bundles contain *fixtures*
and that facets are non-persisted views onto a fixture. That is a live contradiction between two
`verified` pages, not leftover history — and changing Satchel's own core definition is an Architect
call, not a PM cleanup. Flagged here rather than filed as its own ticket since it wants a decision
first; worth routing to Architect.

## Log



- 2026-08-21: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
