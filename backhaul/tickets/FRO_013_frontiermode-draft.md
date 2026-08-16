---
id: FRO_013
uid: FRO
number: 13
client: FrontierMode
status: done
title: frontiermode.md, reconciliation draft
context: Both still draft; reconciliation is an open loop from Sasha design-vocab
  work. Low pri.
priority: normal
opened: '2026-08-13'
closed: '2026-08-13'
---

<!-- board:start -->
<!-- board:end -->

## Summary

`frontiermode.md` and `frontier-reconciliation.md` (both under `backhaul/wiki/frontiermode/`)
are still `status: draft`. Not blocked by anything — `frontier-reconciliation.md` doesn't
reference any of the jig/compile wiring the recent Satchel investigation touched, so it isn't
stale because of that; it's just an open loop from Sasha's design-vocab work, worth a priority
call whenever that's back in scope. Filed under FRO since both pages live in the frontiermode
wiki category, even though it originates from Satchel's
[SAT_014](SAT_014_status-doc-gaps-for-pm-to-ticket.md) item 6 (lowest priority on that list).

## Log

- 2026-08-13: Verification pass on both pages. `frontiermode.md`: checked mod ID, display name,
  version, author, license, and MC/Forge ranges against `mods.toml`; checked the Satchel
  dependency declaration (`satchel`, `[0.0.1,)`, mandatory, `AFTER`) against both `mods.toml` and
  `satchel.md` — consistent both directions. Checked the "hardcoded absolute-path flatDir"
  dependency claim against `build.gradle` and FRO_004 — accurate, matches the documented decision
  to keep FrontierMode/Satchel as separate Gradle projects. Checked the Architecture section's
  summary of `border.md` — accurate, doesn't undersell or overclaim. Checked all seven `design/*`
  links — all exist; fixed one link-text/title mismatch ("Progression & Frontier Mechanics" →
  "Progression and Frontier Mechanics", matching the page's actual title). No other drift found.
  Moved to `status: verified`.
  `frontier-reconciliation.md`: re-checked every claim about Border's architecture against the
  current (2026-08-13-rewritten) `border.md`. Found one section had gone stale: the
  "commands vs. levers" gap claimed Border creation/mutation was only reachable through the
  command path — source-checked (`BorderAPI`, `BordersTriggers`, `BorderCommandHandler`) and
  found this is no longer true (if it ever was): `BorderAPI` is already a side-agnostic,
  trigger-agnostic surface that both the command path and a block-placement-driven trigger
  (`BordersTriggers.growPath`) already call into independently. Rewrote that section to reflect
  this — the gap is a missing boss-defeat *caller*, not a missing capability. Also updated the
  `BorderPlayerBundle` open item: `border.md`'s rewrite now documents (via its own source read)
  that this is real, evidenced, tracked-as-gap code (`RM_FRO_006`), not an unresolved mystery —
  confirms the project owner's earlier guess. Left the design-mapping question (what
  `BorderPlayerBundle` should mean to Frontier design, if anything) open, since that's a design
  call, not a source-reading one. Left three other open items unchanged (age-based
  overlap-resolution query, geometry recollection, boss/ambient-difficulty warning-signal
  feasibility) — none are addressed by anything in `border.md`'s current content, still genuinely
  open. Left `status: draft` — this page's job includes holding live open design questions, and
  real ones remain; not everything reduces to "verified."
- 2026-08-13: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
