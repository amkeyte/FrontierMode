---
id: RM_FRO_010
uid: RM_FRO
number: 10
kind: convergence
status: reached
title: Prototype hardening
owner: Arryn
depends_on:
- RM_FRO_009
- RM_FRO_011
- RM_FRO_012
- RM_FRO_013
- RM_FRO_006
- RM_FRO_015
created: '2026-08-14'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_FRO
<!-- bh-header:end -->

## Prototype hardening

**This node also carries the FrontierMode Tier 0 ("Substrate operational") designation
directly** — see [FrontierMode Operational Tiers](../wiki/plans/operational-tiers.md). There is no
separate Tier 0 roadmap node. Two attempts at one (RM_FRO_014 "Shirley," then RM_FRO_016 "Karen")
were both deleted 2026-08-16, project owner's call: every convergence-gate fix applied to this
node's children left the "Tier 0" node as a single pass-through pointer back here with nothing of
its own — no reason to keep a node around that has no functional difference from this one. This
node reaches when its own six children below all resolve. Once it does,
[RM_FRO_017](RM_FRO_017_donna.md) ("Donna," Tier 1 skeleton) is next — depends on this node
directly, expecting several real intermediate nodes to be scoped and inserted between the two over
time (fold them into Donna's own `depends_on` as they're found, same convergence-gate convention
as this node's own six children).

**Satchel-side prerequisite, named but not graph-enforced** (`bhrm` graphs are UID-independent —
there's no real `depends_on` edge across RM_FRO/RM_SAT): [RM_SAT_017](RM_SAT_017_paul.md) ("Paul,"
Satchel's own hardening convergence, `reached`) and [RM_SAT_020](RM_SAT_020_jerry.md) ("Jerry,"
`PlayerJig`/`PlayerScope`, `resolved`). Both currently satisfied. Since there's no edge, nothing
re-checks this automatically if either ever regresses — flagged in
[BKHL_004](../tickets/BKHL_004_deprecated-convergence-tracking.md). Treat this as a manual
checklist item any time this node's own `reached` flip is being considered, alongside the six
children below.

- 2026-08-21: **Standing build caveat satisfied — project owner ran a full `gradlew build` and
  reports it clean.** This settles the "Real full-build verification of the complete Tier 0
  substrate remains owed" clause in the entry below, and the same caveat carried by
  [RM_FRO_009](RM_FRO_009_judith.md), [RM_FRO_011](RM_FRO_011_betty.md),
  [RM_FRO_012](RM_FRO_012_carolyn.md) and [RM_FRO_015](RM_FRO_015_margaret.md) — all five instances
  had one root cause, documented on [FRO_023](../tickets/FRO_023_playtest-checklist-batch2.md): no
  agent session had Forge/Mojang maven access, so none could run the build itself. Those entries
  are left as originally written; this one is the reconciliation.

  **What this does not settle.** A green build is not a green playtest, and three things stay open
  independently of it: [FRO_031](../tickets/FRO_031_betty-donebar.md) — two items on
  [RM_FRO_011](RM_FRO_011_betty.md)'s own done bar (`@none` tab-complete, the `/border add`
  rejection-message retest) that her log leaves unconfirmed, which is the one place this
  convergence's evidence trail currently claims more than the record supports;
  [FRO_033](../tickets/FRO_033_margaret-reruns.md) — the specific in-game sequences
  [RM_FRO_015](RM_FRO_015_margaret.md) lists as owed beyond a build; and
  [RM_FRO_012](RM_FRO_012_carolyn.md)'s render-cache eviction item, which remains a deliberate,
  recorded waiver rather than a verified result. `reached` still rests partly on read-through
  verification and owner-reported confirmation, not solely on independent evidence — narrower than
  before today, not eliminated.

  Recorded per [FRO_032](../tickets/FRO_032_build-of-0821.md).
- 2026-08-20: **Reached — all six children now `resolved`, project owner's call.**
  [RM_FRO_015](RM_FRO_015_margaret.md) ("Margaret") was the last one open; closed today after its
  own `fixLayers()` design item and a run of four real playtest-found bugs were fixed and confirmed
  (see its own log). Full roster, all `resolved`: RM_FRO_009 (dead `BorderView` deletion),
  RM_FRO_011 (validation hardening), RM_FRO_012 (render lifecycle cleanup), RM_FRO_013 (fixture &
  compass robustness), RM_FRO_006 (`@relevant` real implementation), RM_FRO_015 (command-surface
  completion). Satchel-side manual checklist re-checked per this node's own instruction above: both
  still hold — [RM_SAT_017](RM_SAT_017_paul.md) ("Paul") `reached`, [RM_SAT_020](RM_SAT_020_jerry.md)
  ("Jerry") `resolved`. This is a conscious flip, not automatic, same standard this node used for its
  earlier (later-reverted) 2026-08-16 attempt and the standard RM_SAT_017 itself used.

  **Standing caveat inherited from every child:** none of this round's FrontierMode-side work has
  been independently verified by a full `gradlew build` run from this side — the sandbox has no
  network access to fetch the Gradle distribution, the whole session's constraint. Every fix was
  manually read-through verified, and several were confirmed live in-game via real server/client
  logs where the project owner ran an actual build on their own machine. Real full-build
  verification of the complete Tier 0 substrate remains owed before treating this as more than a
  conscious, evidence-backed call.

  [RM_FRO_017](RM_FRO_017_donna.md) ("Donna," Tier 1 skeleton) is next per this node's own text
  above.
- 2026-08-16: **`depends_on` widened to add [RM_FRO_015](RM_FRO_015_margaret.md) ("Margaret" —
  Border command-surface completion).** Found via an Architect source-level check of the actual
  `border/*` tree (not just the wiki) while scoping "what's still not done for Tier 0" ahead of
  what was then RM_FRO_014 (deleted, see this node's Tier 0 note above) closing: `BordersPathFacet.fixLayers()` is a real,
  self-documented incomplete gap (hardcoded `return false`; RM_FRO_011's own log explicitly asked
  for this as a follow-up node rather than filing it itself). Folded in here rather than routed
  around Susan, same convergence-gate shape as every other sibling in this list. See RM_FRO_015 for
  full scope — its own text also covers a couple of smaller command-surface rough edges found in
  the same pass.
- 2026-08-16: **Reverted to WIP, and `depends_on` widened to add RM_FRO_006 ("Sandra").**
  what was then RM_FRO_014 (a separate Tier 0 convergence node, since deleted — see this node's
  Tier 0 note above) originally depended on this node *and* directly on RM_FRO_006 in parallel —
  the same shape as the RM_SAT_007/009 bypass this project's convention already forbids (see
  [BKHL_002](../tickets/BKHL_002_convergence-gate.md)): Sandra was skipping this convergence to
  feed the Tier 0 node directly instead of routing through it. Caught on review, corrected the
  same way RM_SAT_009's own siblings were folded in: RM_FRO_006 now named here in `depends_on`,
  and removed from the Tier 0 node's own list. Since RM_FRO_006 is real, unresolved work
  (see its own node — genuinely blocked on RM_SAT_020, which has since landed, but the FrontierMode
  side hasn't been implemented yet), this node's **reached** flip below was premature under the
  corrected graph shape and is reverted to WIP until RM_FRO_006 actually resolves.
- 2026-08-16: **Reached** *(superseded by the entry above — kept for the record).* All four
  dependencies then listed were `resolved` and real-play confirmed per
  [FRO_023](../tickets/FRO_023_playtest-checklist-batch2.md): RM_FRO_009 (dead `BorderView`
  deletion, border rendering confirmed unaffected), RM_FRO_011 (all three validation-hardening
  items individually confirmed against a real build), RM_FRO_012 (no stray particle, render-cache
  eviction accepted without a diagnostic per project owner's call), RM_FRO_013 (malformed-entry
  skip and offhand-compass fixes both confirmed on a real server). This convergence now carries
  real evidenced structural work, not just a thin pass-through — flipping to reached is a
  conscious call, same standard used for [RM_SAT_017](RM_SAT_017_paul.md)'s own flip, not an
  automatic consequence of its children resolving.
- 2026-08-16: **`depends_on` widened to add three real siblings to RM_FRO_009** —
  [RM_FRO_011](RM_FRO_011_betty.md) (border mutation validation hardening),
  [RM_FRO_012](RM_FRO_012_carolyn.md) (client render lifecycle cleanup), and
  [RM_FRO_013](RM_FRO_013_judy.md) (border fixture & compass robustness). Found via a
  project-owner-requested source-level resilience pass over `border/*` — same spirit as
  SAT_031's retrospective on the Satchel side, done here by reading the actual implementation
  against its own claimed behavior rather than waiting for a bug report. Eight distinct findings,
  grouped into three nodes by root cause rather than filed one-for-one, per the project owner's
  explicit steer to keep this convergence's fan-out manageable. All three siblings depend on
  RM_FRO_008 directly (not on this node), matching how RM_SAT_012-016/019 relate to RM_SAT_017 —
  named here in `depends_on` so nothing routes around this node's own gate (see
  [BKHL_002](../tickets/BKHL_002_convergence-gate.md), the convergence-bypass check this
  project's own tooling gained after finding exactly that mistake made twice in RM_SAT). This
  node is no longer "thin by design" — it now carries real, evidenced structural work on par with
  RM_SAT_017's own batch.
- 2026-08-14: Node opened as **WIP**, paired with [RM_SAT_017](RM_SAT_017_paul.md) under the
  "convergence pairs for now" convention (see that node).

Originally thin by design: the only FrontierMode-side hardening item identified at open was
[RM_FRO_009](RM_FRO_009_judith.md) (dead `BorderView` cleanup). Two smaller SAT_031 items stayed
off the roadmap on purpose rather than padding this node: recommendation #1 (confirm FRO_016 via a
real disconnect cycle) is closing out an existing ticket, not new roadmap-shaped work, and
recommendation #6 (gold-block growth-trigger radius gut-check) is a cosmetic gameplay-feel
question for the Game Designer role, not a structural one. That prediction ("expect this node to
gain real siblings to RM_FRO_009") held — see the 2026-08-16 entry above.

## Required By

*(computed — nothing depends on this yet)*
