---
id: RM_FRO_017
uid: RM_FRO
number: 17
kind: convergence
status: reached
title: 'Tier 1: Core loop operational'
owner: Arryn
depends_on:
- RM_FRO_018
- RM_FRO_019
- RM_FRO_022
created: '2026-08-16'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_FRO
<!-- bh-header:end -->

## Tier 1: Core loop operational

- 2026-08-30: **Reached — project owner's call.** All three real `depends_on` children are
  `resolved`: [RM_FRO_018](RM_FRO_018_shirley.md) ("Shirley," boss entity/spawn system, ticket
  FRO_043), [RM_FRO_019](RM_FRO_019_karen.md) ("Karen," defeat-detection caller into `BorderAPI`,
  ticket FRO_045), and [RM_FRO_022](RM_FRO_022_joyce.md) ("Joyce," boss control commands, ticket
  FRO_057) — the last of the three to close, playtest-verified six-for-six. The loop [Frontier Mode
  Overview](../wiki/frontiermode/design/overview.md) describes is actually playable: a boss exists,
  can be found, can be killed, killing it grows the border and spawns the next one, and it can now
  be exercised/debugged from chat without a code change. This settles the "do not treat this node as
  actionable or close to reaching" caution every earlier entry on this node carried — that caution
  was correct until today; it no longer is.

  [RM_FRO_021](RM_FRO_021_susan-02.md) ("Susan_02," the Susan epoch's own end container) stays
  un-wired here per this node's entry directly below and [BHRM's
  corollary](../wiki/meta/bhrm.md#epoch-maintenance-nodes-containers) — real, open, non-blocking
  work, not a gate on this flip. [FRO_064](../tickets/FRO_064_boss-defeat-cascade-grows-border-level-r.md)
  specifically re-checked one more time against today's flip: it's a gap in how Joyce's own new
  `/boss add` admin capability interacts with the existing grow trigger, not a regression on
  anything this node's three children's own verified done bars actually cover (ordinary-combat,
  path-tip defeats only). Stands as accepted, named, non-blocking scope.

  Opens the next epoch — "the Donna epoch." Two standing maintenance containers opened alongside
  this flip, [RM_FRO_024](RM_FRO_024_donna-01.md) ("Donna epoch maintenance 1") and
  [RM_FRO_025](RM_FRO_025_donna-02.md) ("Donna epoch review/fix"), per [BHRM — Roadmap Conventions
  § Epoch maintenance nodes](../wiki/meta/bhrm.md#epoch-maintenance-nodes-containers)'s
  two-standing-containers-by-default convention. [RM_FRO_023](RM_FRO_023_kathleen.md) ("Kathleen,"
  Tier 2 skeleton) is the epoch's next planned node — stays un-wired to either container for now,
  same corollary as above: wire only if something on them actually turns out to block it.

- 2026-08-30: **[RM_FRO_021](RM_FRO_021_susan-02.md) ("Susan epoch review/fix") un-folded —
  reversing the entry directly below, project owner's explicit call.** Checked against everything
  actually sitting on that container as of today, seven items, not the four it had when first
  folded in: display/visual-only bugs, a future audit, two closed-and-superseded validation
  findings now tracked on FRO_060's own build ticket, an already-fixed console-spam bug, an open
  design question with no ruling yet, and — the one worth naming specifically —
  [FRO_064](../tickets/FRO_064_boss-defeat-cascade-grows-border-level-r.md), a real bug where
  defeating an off-path boss (creatable via Joyce's own `/boss add`) triggers the grow cascade
  anyway. That one's real, but it's a gap in how a *new* admin capability interacts with an
  *existing* trigger, not a regression on anything [RM_FRO_019](RM_FRO_019_karen.md) ("Karen")'s
  own verified done bar actually covers (ordinary-combat, path-tip defeats only) — full reasoning
  on Susan_02's own log. Nothing here is an actual blocker on "core loop operational." Reading
  Donna's own "fold siblings in" instruction as covering *any* container that happens to be open
  during this epoch, rather than specifically real Tier-1-blocking scope, was over-literal.
  **Refined reading, going forward: a maintenance container only needs a `depends_on` edge here if
  it holds (or is reasonably suspected to hold) something that actually blocks this convergence —
  not merely because it's real, logged, and open.** Susan_02 stays open in its own right (real
  work, just not Donna's gate); it no longer appears below. `depends_on` now: RM_FRO_018,
  RM_FRO_019, RM_FRO_022.

- 2026-08-29: **Two more real siblings folded in, per this node's own standing instruction above**
  ("fold those siblings into this node's own `depends_on` ... don't let them feed some future Tier
  2 node directly and skip this one"), found the same day [RM_FRO_019](RM_FRO_019_karen.md)
  ("Karen") resolved and its playtest surfaced real new scope:

  - [RM_FRO_022](RM_FRO_022_joyce.md) ("Joyce") — a real feature node, in-game admin command
    surface for `BossFixture`/`BossAPI`, gap found during Karen's playtest (`border/` and
    mob-tracking both have a command layer, `boss/` has none). Depends on Karen directly.
  - [RM_FRO_021](RM_FRO_021_susan-02.md) ("Susan epoch review/fix") — the epoch's standing
    end-of-epoch maintenance container, per
    [BHRM — Roadmap Conventions § Epoch maintenance nodes](../wiki/meta/bhrm.md#epoch-maintenance-nodes-containers).
    Still collecting (`status: open`, not yet `resolved`) — folded in now because it already holds
    real, IDed scope (four tickets as of this entry), not because it's finished. This node still
    isn't close to reaching: nothing has changed about that, this just makes the graph honest about
    one more real thing standing between here and there.

  Still **do not treat this node as actionable or close to reaching** — both new siblings are
  themselves unresolved (Joyce not yet built/scoped past capture, the maintenance container still
  open), and more Tier 1 scope may yet surface the same way these two did.

- 2026-08-16: **First real intermediate work scoped and folded in, `depends_on` repointed off
  RM_FRO_010 onto the two new nodes directly** (same convergence-gate correction as every sibling
  list in this project — see [BKHL_002](../tickets/BKHL_002_convergence-gate.md)):
  [RM_FRO_018](RM_FRO_018_shirley.md) ("Shirley," boss entity/spawn system) and
  [RM_FRO_019](RM_FRO_019_karen.md) ("Karen," defeat-detection caller into `BorderAPI`), both of
  which already depend on RM_FRO_010/RM_FRO_018 respectively, so listing RM_FRO_010 here directly
  too would bypass the funnel. Design: [Boss](../wiki/frontiermode/architecture/boss.md). Also
  named, cross-graph, in Shirley's own text: Satchel's
  [RM_SAT_021](RM_SAT_021_frank.md) ("Frank," new `MobJig`/`MobScope` kind) — a real prerequisite,
  not a `depends_on` edge. Still not actionable and still not close to reaching — this is the first
  pass at real scope, not the complete one; expect more siblings (loot, discovery has its own tier)
  as Tier 1 gets built out.
- 2026-08-16: **`depends_on` repointed straight to [RM_FRO_010](RM_FRO_010_susan.md) ("Susan").**
  Originally depended on a separate Tier 0 convergence node (RM_FRO_014 "Shirley," then rebuilt as
  RM_FRO_016 "Karen") — both deleted, project owner's call: neither ever carried anything Susan
  didn't already have, so the indirection was cut. Susan now carries the Tier 0 designation
  directly (see her own node). This node sits directly behind Susan on purpose, expecting several
  real intermediate nodes to be scoped and inserted between the two over time — see below.
- 2026-08-16: **Node opened as a placeholder skeleton, project owner's explicit call** — the
  [FrontierMode Operational Tiers](../wiki/plans/operational-tiers.md) page originally argued
  against creating Tier 1+ convergences before real sibling work exists to gather ("these
  convergences get created once real sibling work exists, not as empty placeholders now"). That
  guidance is overridden here on purpose: the project owner wants the tier sequence structurally
  present now, not rediscovered later. This node is scaffolding, not a claim that Tier 1 is
  scoped.

**Intermediate nodes not yet defined or inserted.** Per [FrontierMode Operational
Tiers](../wiki/plans/operational-tiers.md#the-tiers), Tier 1 needs real work that doesn't exist as
roadmap nodes yet — at minimum a boss entity/spawn system, and a defeat-detection caller into
`BorderAPI.addBorder()` — mostly new-caller work rather than new-capability work, though not
purely: `addBorder()` does accept an arbitrary center, but only touches the border *list*, not the
canonical `borderPath`, so a small path-aware addition is needed too. See
[RM_FRO_019](RM_FRO_019_karen.md) ("Karen") for the full finding and the two options for closing
it. The single
`depends_on` edge below (this node → RM_FRO_010) is accurate as far as it goes — Tier 1 does need
Susan's six children resolved — but it is not a complete prerequisite list, and the project owner
expects it to grow real intermediate nodes over time, not stay a direct edge forever. **Do not
treat this node as actionable or close to reaching.** When real Tier 1 work gets scoped, fold
those siblings into this node's own `depends_on` the same way Susan absorbed Sandra/Margaret, per
[BKHL_002](../tickets/BKHL_002_convergence-gate.md) — don't let them feed some future Tier 2 node
directly and skip this one.

## Required By

<!-- required-by:start -->
- [**RM_FRO_024**](RM_FRO_024_donna-01.md) — Donna epoch maintenance 1
- [**RM_FRO_026**](RM_FRO_026_dorothy.md) — Navigator target-resolution fixture
- [**RM_FRO_027**](RM_FRO_027_janet.md) — BorderCurve intensity-curve fixture
- [**RM_FRO_028**](RM_FRO_028_diane.md) — BorderPregen terrain pregeneration
<!-- required-by:end -->
