---
id: FRO_028
uid: FRO
number: 28
client: FrontierMode
status: open
title: Clean up frontier-reconciliation.md and path-layer-reconciliation.md — separate
  history from living reference
context: null
priority: normal
opened: '2026-08-17'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

Project owner's steer: architecture/design wiki pages are high-traffic and should stay tight — no
accreted process narrative mixed in with facts a reader actually needs today. These two pages both
say, in their own opening lines, that they exist to reconcile against
[Border](../wiki/frontiermode/architecture/border.md); border.md is the intended living reference,
and border.md's own "Known gaps" section already links out to both rather than restating their
content. That's the seam to clean up. Read both pages in full before drafting this — the two don't
have the same shape, so they need different treatment. Don't do the actual edit as part of scoping
this ticket; this is the plan, not the diff.

## frontier-reconciliation.md — ready for cleanup now

This page is explicitly framed as a point-in-time process log ("*First pass, 2026-08-12. Ziltoid
answered a round of Architect questions...*"), and it shows: several sections are annotated
"confirmed against source" vs. "memory-recalled, not yet verified," and the Open Items section
has a live correction ("...this page's earlier pass, which is now stale"). That's exactly the kind
of accretion the project owner wants out of the architecture pages — it's a true record of *how*
this got reconciled, not *what's* true now.

**Genuinely reusable content, not yet duplicated in border.md — merge these in:**
- The terminology mapping (Border = one cylindrical range = one design "level"; Frontier = sum of
  all Borders, not currently a named object anywhere in code) — border.md has no
  design-vocabulary-to-code-vocabulary bridge today, and a reader coming from Sasha's design docs
  needs one.
- The "ecosystem shape" framing (Border as the proof-of-concept module; nearly everything else in
  FrontierMode's design is defined in terms of Border's "level"/"distance from origin" concepts) —
  useful context, arguably belongs in border.md's own intro or in
  [FrontierMode mod summary](../wiki/frontiermode/frontiermode.md), whichever reads more
  naturally.

**Everything else on the page is either already covered by border.md's current text (geometry,
persistence layering, commands-vs-levers, oldest-ring-wins resolution, the per-player-bundle and
path/layer gaps) or is pure process narrative (dates, "confirmed vs. recalled" annotations, the
stale-correction note).** Once the two items above are merged, there's nothing left on this page a
reader needs. Two options, pick one — don't invent a third:
1. **Delete the page outright.** Its "how we found this out" story is already preserved in git
   history and in the roadmap/ticket logs that did the actual finding (RM_FRO_006, RM_FRO_011,
   etc.) — this project's convention is that logs live on roadmap nodes and tickets, not on wiki
   pages (see [BHW — Wiki Conventions](../wiki/meta/bhw.md), "no changelog content"). This page
   violates that convention by accretion, not by original design. Recommended default.
2. **Keep it, but move it out of `frontiermode/architecture/` into something explicitly
   process-oriented** (e.g. a `frontiermode/history/` category, if one gets created) — only worth
   doing if the project owner wants to keep it as a template/example of what an Architect
   reconciliation pass looks like. Don't do this by default; ask first if option 1 seems wrong.

Whichever option, fix the two inbound links from border.md's "Related pages" and remove the
`frontier-reconciliation.md`-specific reference from
[Boss](../wiki/frontiermode/architecture/boss.md)'s "Related pages" if it has one — check before
closing this out.

## path-layer-reconciliation.md — not ready for the same treatment yet

This page is a different shape entirely: an active, unimplemented design spec ("*written before
any code changes — this is what Lead Dev builds against, not a description of shipped
behavior*"), tracked by [RM_FRO_015](../roadmap/RM_FRO_015_margaret.md) ("Margaret"). It's not
narrating the past, it's specifying work that doesn't exist in the codebase yet — the
`reassignLayerIndices` design, the batch-reassignment algorithm, the off-path-border collision
handling. None of that is redundant with border.md today; border.md's "Known gaps" section
correctly just points at this page rather than restating an unimplemented design.

**Do not merge or trim this now.** Correct sequencing: leave it in place until RM_FRO_015 actually
resolves and the design is confirmed working in a real build/playtest (this project's usual
done-bar). At that point, fold the *shipped, verified* behavior into border.md's "Known gaps"
section (moving `fixLayers()` out of "gap" and into a normal description of real behavior,
matching how RM_FRO_009/011/012/013's fixes already read in border.md today), and retire this page
the same way frontier-reconciliation.md is being retired here — its design-rationale value is
spent once the thing it specifies is real and documented elsewhere.

**Worth a side note, not part of this ticket's scope:** this page reads like Douglas already did
the Architect design pass RM_FRO_015's own text says is a prerequisite for Lead Dev — worth
confirming with the project owner whether Margaret is actually ready to hand to Curtis now, or
whether this page is a draft still being reviewed. Flagging, not acting on it here.

## Done bar

frontier-reconciliation.md: content merged into border.md (or FrontierMode mod summary) where
called out above, old page deleted or relocated per whichever option the project owner picks,
border.md's "Related pages" updated, `backhaul lint` clean. path-layer-reconciliation.md: no
action until RM_FRO_015 resolves — this ticket can close on the frontier-reconciliation.md half
alone if the project owner wants the two decoupled; the path-layer half can be its own follow-up
ticket opened when RM_FRO_015 actually ships.

## Log

- 2026-08-17: Ticket opened, scoped from a project-owner request to keep the architecture wiki
  tight. Both pages read in full before this plan was written — see above for why they need
  different treatment rather than one mechanical pass.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
