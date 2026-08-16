---
id: BKHL_002
uid: BKHL
number: 2
client: Backhaul
status: done
title: bhrm validate has no convergence-gate check
context: Depending on a convergence node plus another node in parallel defeats the
  gate; render-html silently draws the bypass edge too. Found + hand-fixed 3 instances
  in RM_SAT. See body.
priority: normal
opened: '2026-08-15'
closed: '2026-08-15'
---

<!-- board:start -->
<!-- board:end -->

## Summary

bhrm validate has no convergence-gate check

## Full report

[Found while using the Backhaul CLI from inside mcRepos's PM role sandbox — same "mcRepos is the
live testbed for this class of bug" pattern as BKHL_001, filed here rather than against
Backhaul's own tracker.]

**The rule (project owner's own framing, verbatim):** "The point of a convergence node is to
ensure a certain level of overall order and still allow for the idea of milestones to exist. If
X depends on convergence node C, then X cannot also depend on some other node directly" — i.e. a
convergence node is supposed to be the *sole* funnel a dependent passes through for everything C
represents. A dependent that names C **and** some other node in the same `depends_on` list has an
edge that routes around C's own gate, defeating the reason C exists as `kind: convergence` rather
than plain `work`.

**Checked and confirmed missing:** neither `bhrm validate` (cycle detection only — see
`validate_graph` in `modules/roadmap/graph.py`) nor the new `bhrm render-html` (BH_005; draws
whatever edges `export_json` hands it, no gate awareness) nor the new `backhaul lint`
(BH_004; only `orphaned`/`links` checks) enforces this anywhere. `kind: convergence` today only
drives status vocabulary (`schema.py`'s `OPEN_STATUS`/`SATISFYING_STATUS`) and chart
color/height (`_html_color`/`_html_layout`) — it carries no structural meaning `validate_graph`
actually checks.

**Concrete evidence, found in this project's real RM_SAT graph (since hand-fixed, not left as a
repro case):**
- `RM_SAT_009` depended on `RM_SAT_007` (convergence) **and** `RM_SAT_005`/`RM_SAT_006`/
  `RM_SAT_008` directly, all in parallel with the convergence edge.
- `RM_SAT_018` (itself a convergence) depended on `RM_SAT_017` (convergence) **and**
  `RM_SAT_019` directly, in parallel.
- Visually confirmed in the `render-html` output too: the bypassing edges were drawn as long
  bezier curves cutting straight through the convergence node's own column, since layout/edge
  drawing has no concept of a gate either.
- Fix applied by hand: folded the bypassing deps into the convergence node's own `depends_on`
  (e.g. `RM_SAT_007` now also depends on 005/006/008; `RM_SAT_009` depends on `RM_SAT_007`
  alone) rather than changing any node's `kind`. Both nodes' own log entries (2026-08-15) have
  the full before/after.

**Suggested shape (not a committed design):** a new `bhrm validate` check — for every node N,
for every convergence node C in N's `depends_on`, no other entry in N's `depends_on` may be
reachable from C only via a *different* path than through C itself... concretely, simplest
version: **N's `depends_on` may name at most one convergence node, and if it does, every other
entry in that same list must already be an ancestor of that convergence node** (i.e. redundant
without it) **or the check just flatly forbids mixing a convergence dependency with any sibling
dependency at all**, forcing the author to either fold the sibling into the convergence's own
`depends_on` (like the RM_SAT_007/009 fix above) or drop the convergence relationship. The
stricter flat-forbid version is probably right for v1 — cheaper to implement, matches what both
real instances in this project actually needed, and a false positive here just means "fold this
into the convergence node instead," a one-line fix.

Should surface as a `bhrm validate` failure (not just `bhrm render-html` coloring it
differently), same tier as the existing cycle check, since it's the same class of problem —
authors can write structurally-inconsistent `depends_on` data that nothing catches today.

Priority: normal — not blocking, but the flaw is silent (no error today, only a visually odd
chart if you happen to generate one) and can accumulate the same way this project's two
instances did before anyone noticed.

## Log

- 2026-08-15: Landed as `bhrm convergence-bypass --uid X` — a new advisory query
  (`find_convergence_bypasses()`/`ancestors()` in `modules/roadmap/graph.py`), not a
  `validate`/`lint` hard failure as this ticket's "suggested shape" proposed. Deliberately
  softer: for each convergence C, flags any node N (not an ancestor of C, not already gated by
  C) whose own ancestor closure overlaps C's — advisory because a shared ancestor doesn't always
  mean N *should* route through C, per the tool's own docstring.
  Ran against both mcRepos graphs: the three violations hand-fixed earlier (RM_SAT_009/007,
  RM_SAT_018/017/019) did not reappear. Two new findings came back — `RM_SAT_020 → RM_SAT_018`
  and `RM_FRO_006 → RM_FRO_010` — both reviewed and judged benign, not real bypasses: in each
  case the flagged node (020, 006) never actually names the convergence in question in its own
  `depends_on` at all; it's two independent siblings hanging off the same lower convergence
  (017, 008 respectively), one of which happens to itself be `kind: convergence`. Exactly the
  "shares an early, unrelated prerequisite" noise case the tool's docstring calls out as
  advisory rather than a verdict. No data change made for these two. Closing — the gap this
  ticket exists to track is filled.
- 2026-08-15: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/backhaul)
<!-- bh-header:end -->
