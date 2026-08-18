---
id: BKHL_004
uid: BKHL
number: 4
client: Backhaul
status: open
title: 'Deprecated-convergence tracking: no terminal status, no stale-reference detection'
context: null
priority: normal
opened: '2026-08-16'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

Found while retiring a standalone FrontierMode Tier 0 convergence node — first created as
RM_FRO_014 ("Shirley"), then rebuilt as RM_FRO_016 ("Karen") after a review found Shirley had
become a pass-through pointer to RM_FRO_010 ("Susan") with nothing of its own. Karen turned out to
be the same shape, so the project owner cut the indirection entirely: both nodes deleted, Tier 0
folded directly into Susan's own body text. That side-stepped the two gaps below for this specific
case (nothing to deprecate if it's just deleted, and lint's existing broken-link check catches
stale references to a deleted file for free) — but both gaps are real and will recur the next time
someone wants to retire a node without erasing its history. Filing as a general observation, not
blocked on anything.

**Gap 1 — no terminal status for `kind: convergence`.** `WORK_STATES = ("open", "resolved",
"superseded")` but `CONVERGENCE_STATES = ("WIP", "reached")` — a work node can be retired via
`status: superseded` (validated to require `superseded_by` be set), but a convergence node has no
equivalent. Had Shirley been kept on disk as deprecated (rather than deleted, which is what
actually happened), her `status` would have had to stay `WIP` — the only non-`reached` option for
her kind — so `is_actionable()` (`node.status != OPEN_STATUS[node.kind]`, then
`all(deps satisfied)`) would eventually treat a deprecated-but-kept convergence as actionable the
moment its own `depends_on` happens to be satisfied, deprecated or not.

**Gap 2 — no automated stale-reference detection for a node kept on disk as deprecated (rather
than deleted).** `superseded_by` is stored in frontmatter but nothing reads it: not `bhrm
validate`, not `backhaul lint`. A node, wiki page, or ticket that still links to or `depends_on`s
a superseded-but-kept node gets no warning — the only signal would be a human reading the target
node's own body text. `backhaul lint --check links` only catches links to files that don't
resolve/exist, so it's no help for a deprecated node still sitting on disk — though it *did* do
its job here, since we deleted rather than deprecated: every stale link to RM_FRO_014/RM_FRO_016
got caught as a broken link and fixed by hand. That only works because deletion was viable this
time (nothing genuinely needed Shirley's/Karen's history preserved separately from Susan's own
log). A future case that needs the history kept on disk won't get that same free check.

**Suggested direction, not a committed design:**
1. Add a genuine terminal convergence status (e.g. `"retired"` or reuse `"superseded"` for both
   kinds, whichever is the smaller schema change) so `is_actionable()`/`frontier()` can exclude it
   permanently regardless of `depends_on` satisfaction.
2. A `bhrm superseded-refs` (or fold into `convergence-bypass`'s advisory family) check: walk every
   node's `depends_on` and every wiki/ticket link, flag any that resolve to a node with
   `superseded_by` set. Advisory, same spirit as `convergence-bypass` — a superseded node might
   legitimately be linked from historical log entries (dated, past-tense) without that being a
   problem; the useful signal is a *current-tense* dependency or cross-reference, which is harder
   to distinguish automatically. Even a blunt "any link to a superseded node, review by hand" pass
   would beat the current silence.

## Log

- 2026-08-16: **Resolved differently than filed — deleted instead of deprecated.** Project owner's
  call: no reason to keep Shirley/Karen on disk when neither added anything Susan didn't already
  carry. Both gaps described above are still real for the general case (a node that needs to be
  retired but *does* need its history kept separate from wherever it's merged into), just not
  blocking anything right now. Left open as a lower-priority future improvement rather than closed,
  since the underlying tool gap wasn't actually addressed, just avoided this time.
- 2026-08-16: Ticket opened, found live while deprecating RM_FRO_014 in favor of RM_FRO_016 (that
  approach was reversed the same day — see entry above).
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/backhaul)
<!-- bh-header:end -->
