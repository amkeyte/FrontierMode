---
id: BKHL_007
uid: BKHL
number: 7
client: Backhaul
status: open
title: lint/dashboard not in any refresh routine
context: backhaul lint catches today's broken links for free and BACKHAUL.md was stale
  on all 3 counts.
priority: normal
opened: '2026-08-21'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

`backhaul lint` audits content for broken links and orphaned pages. Run against this project on
2026-08-21 it immediately surfaced every link finding from that day's PM doc audit, plus several
nobody had noticed. It does not appear to be part of anyone's routine.

Found for free, in one command:

- Four live docs linking to `frontier-reconciliation.md` at its pre-[FRO_028](FRO_028_reconciliation-page-cleanup.md)
  path — see [FRO_036](FRO_036_recon-halfdeleted.md).
- Three links in [FRO_029](FRO_029_border-vocab-conformance.md) to a wiki page that doesn't exist.
  **These turned out to be deliberate** — FRO_029's Phase 6 deleted the checklist page on close and
  left the inbound links as historical record, per this project's "don't rewrite history"
  convention (see [FRO_038](FRO_038_vocab-checklist.md), closed as a false positive). *Resolved
  2026-08-21 by un-linking them* — text kept, link syntax dropped — which is the workaround this
  ticket's class-2 discussion below is about needing a real answer for.
- 21 broken outbound links inside the moved `frontier-reconciliation.md` itself.
- [Top Baby Names of 1945](../wiki/reference/baby-names-1945.md) orphaned — the canonical persona-name
  source, unreachable by navigation, and the only record of the Shirley/Karen reuse in
  [FRO_039](FRO_039_persona-collision.md).
- [Forge Event Conduit (Parked)](../wiki/satchel/architecture/forge-event-conduit.md) orphaned.
- Eight tickets orphaned, including [FRO_009](FRO_009_wiki-dehistoricize.md) and
  [FRO_010](FRO_010_wiki-no-status.md) — the two that established the wiki conventions
  [FRO_037](FRO_037_wiki-redrift.md) is about re-applying.

## The other half: the dashboard goes stale the same way

Before today's refresh, [BACKHAUL.md](../../BACKHAUL.md) reported 4 open tickets (actual: 2), 31 wiki
pages (actual: 34), and 4 actionable nodes (actual: 3).

[BHRole — Agent Role Conventions](../wiki/meta/bhrole.md) predicts this precisely: "Refreshing the
sub-indexes without refreshing the root dashboard is how `BACKHAUL.md` silently goes stale while
everything underneath it looks fine — it happened once already in this project (sat at '5 pages' for
an entire session while the wiki grew to 11)." It has now happened a second time, against a
documented warning, and every role's bootstrap prompt sends the next session to `BACKHAUL.md` first.

That page's own prescription — "run `backhaul dashboard` as part of any refresh, not just
`bht`/`bhw`/`bhrm`/`bhrole` individually" — is correct and is not being followed, which suggests the
problem isn't that people don't know the rule.

## Suggested direction, not a committed design

1. **A single refresh entry point.** Something like `backhaul refresh` that runs `bht board`,
   `bhw index`, `bhrm index`, `bhrole index`, `backhaul dashboard` and `backhaul lint` in order.
   The current five-commands-plus-remember-the-sixth shape is what fails.
2. **Lint as part of it, advisory not blocking** — same spirit as `bhrm`'s `convergence-bypass`
   check. Orphaned pages in particular are judgement calls, not errors.
3. **A `--check` filter worth having**: the two wiki-convention rules in
   [BHW — Wiki Conventions](../wiki/meta/bhw.md) (no changelog content, no status prose) are the ones
   this project keeps re-applying by hand — [FRO_009](FRO_009_wiki-dehistoricize.md) and
   [FRO_010](FRO_010_wiki-no-status.md) both closed on 2026-08-13 and both have already drifted back
   (see [FRO_037](FRO_037_wiki-redrift.md)). A heuristic check for dated status markers on wiki pages
   would be imperfect but cheap, and a rule re-applied by hand every few weeks is a rule without a
   mechanism.

**The ignore mechanism is not optional, and this is the real finding.** Two distinct classes of
permanent false positive already exist in this project, and both are *correct* content that lint
cannot distinguish from a defect:

1. **Illustrative link syntax in prose.** [BHW — Wiki Conventions](../wiki/meta/bhw.md) contains a
   worked markdown-link example — a title in square brackets followed by a placeholder path in
   parentheses — which lint reports as broken because it can't tell an example from a real link.
2. **Deliberately-dangling historical links.** This project's "don't rewrite history" convention
   (see [BHW — Wiki Conventions](../wiki/meta/bhw.md)) means a closed ticket's log keeps pointing at
   a page that was intentionally deleted afterwards.
   [FRO_029](FRO_029_border-vocab-conformance.md) had three, from its own Phase 6 retirement. There
   will be more, by design, every time a working artifact is retired.

**On 2026-08-21 the project owner's call was to un-link FRO_029's three** — keep the text, drop the
link syntax — which clears them permanently and drops the project's broken-link count from 30 to 27.
That works, and it's cheap. Worth being honest about what it costs, though: it's a lossy fix. The
reader of that log entry can no longer tell the phrase "Border Vocabulary Conformance Checklist"
ever named a real page, and the next retirement needs someone to remember to do the same thing by
hand. A marker convention — an explicit "historical link" annotation the checker skips — would keep
the link visible as a link while telling lint to leave it alone, and wouldn't rely on anyone
remembering. Un-linking is the right call until something like that exists; it isn't a reason not
to build one.

Class 2 is still the reason lint can't be a blocking gate as-is: without either treatment, the
broken-link count can never reach zero. Prefer a marker convention over a per-path ignore list if
this gets built — the distinction is semantic, and an ignore list would grow with every
retirement.

## Log

- 2026-08-21: Ticket opened by PM off a doc audit of Susan's subtree. `backhaul dashboard` and the
  four index commands were re-run the same day, so the specific staleness above is already
  corrected — this ticket is about the recurrence, not that instance.

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/backhaul)
<!-- bh-header:end -->
