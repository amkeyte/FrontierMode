---
id: BKHL_017
uid: BKHL
number: 17
client: Backhaul
status: done
title: Backport epoch-maintenance-node convention to Backhaul defaults
context: 'mcRepos worked out a new roadmap convention live (epoch containers -- kind:
  work nodes with no ticket: field, gathering unplanned rework/review-fix work per
  epoch instead of it only existing in the tickets folder). Documented in wiki/meta/bhrm.md.
  Needs backporting into Backhaul''s own default docs/templates so future projects
  get it without re-deriving it.'
priority: normal
opened: '2026-08-28'
closed: '2026-08-28'
---

<!-- board:start -->
<!-- board:end -->

## Summary

[Found while using the Backhaul CLI from inside mcRepos's PM role sandbox — same "mcRepos is the
live testbed for this class of finding" pattern as BKHL_001/BKHL_002, filed here rather than
against Backhaul's own tracker.]

mcRepos worked out, over several rounds of live discussion, a roadmap convention this project's
tickets folder had been silently needing: a formal place on the roadmap graph for unplanned
rework/health/regression work — the kind [FRO_047](FRO_047_border-interface-refactor.md) is a real
example of — that doesn't belong on a persona-named feature node but was otherwise only
discoverable by searching tickets.

## The convention, as landed in mcRepos

Full writeup: [BHRM — Roadmap Conventions § Epoch maintenance nodes](../wiki/meta/bhrm.md#epoch-maintenance-nodes-containers).
Short version:

- An **epoch** is the span between two convergence nodes, named for the one that opens it.
- A **maintenance container** is an ordinary `kind: work` node, no schema change needed — slugged
  `<epoch>-01`/`<epoch>-02`/etc. instead of a persona name, `ticket: null` (same pattern a
  convergence node already uses), with what actually landed on it tracked in its own log rather
  than a single frontmatter pointer.
- Every epoch gets two standing containers by default (a start container, an end/"review-fix"
  container that the epoch's next planned node depends on clearing), plus as many middle
  containers as a given epoch's rework actually warrants.
- `depends_on` wiring onto a container is real and blocking, wherever it lands — including
  reopening an already-`resolved` node when a container's work casts real doubt on its done bar
  (this project's earlier reluctance to ever flip a resolved node's status was circumstantial, not
  structural — see the wiki section for why). Regression doubt is chased back one hop only, for
  now.
- **A container's own `status` tracks whether it's currently blocking anything, not whether every
  ticket ever logged on it is closed.** Learned on the very first real instance
  ([RM_FRO_020](../roadmap/RM_FRO_020_susan-01.md)): it gathered [FRO_047](FRO_047_border-interface-refactor.md)
  (the actual blocker) alongside [FRO_050](FRO_050_remove-borders-doc-guard.md) (unrelated
  low-priority polish spawned by the same review) within hours of each other. Waiting for every
  logged ticket to close before flipping the container `resolved` would have kept
  [RM_FRO_019](../roadmap/RM_FRO_019_karen.md) ("Karen") blocked on cosmetic cleanup that has
  nothing to do with her. `resolved` means "nothing currently gating downstream remains open";
  non-blocking follow-up content can still be open and logged underneath that.

## The ask

This emerged from real usage, not a hypothetical — worth having in Backhaul's own default
docs/templates (tracked upstream, same as BKHL_008 → BH_012) so a future project gets this pattern
without re-deriving it live the way mcRepos just did. Three things worth deciding on the Backhaul
side, left open here rather than pre-decided:

1. **Whether this stays purely a documentation/naming convention, or earns a first-class
   `kind: maintenance`** with its own status pair — proposed shape: `collecting <-> clear`,
   mirroring convergence's reversible `WIP <-> reached` (reversible for the same reason: a container
   that looks clear can pick up a new blocker later and go back to collecting). This would make
   `clear` carry exactly the "nothing currently gating a dependent, not necessarily everything
   closed" semantics mcRepos is presently overloading onto plain `work`'s `resolved` — structural
   instead of a documentation habit, plus its own graph color/shape in `bhrm index`'s HTML output.
   mcRepos deliberately deferred building this; not urgent, but worth an opinion once more than one
   project has used the plain-`work` version.

   **Interim stopgap already adopted, worth keeping even if `kind: maintenance` lands:** a
   `Deferred, non-blocking:` callout required at the top of a container's body whenever it's
   `resolved`/`clear` with open non-blocking content still logged underneath — see
   [RM_FRO_020](../roadmap/RM_FRO_020_susan-01.md) for the retrofit that prompted writing this down.
   Worth keeping as documentation practice regardless of whether the status vocabulary itself ever
   changes — a reader shouldn't have to already know the convention to spot loose ends on a node.
2. Whether `bhrm new` should grow an ergonomic shortcut for creating the standing start+end pair
   together (today it's two ordinary `bhrm new --kind work` calls) once there's evidence this
   pattern holds up outside mcRepos.

Priority: normal — not blocking anything, but the underlying gap (unplanned work with no roadmap
home) is exactly the kind of thing that quietly accumulates across projects the way BKHL_002's
convergence-bypass bug did before anyone noticed.

## Log

- 2026-08-30: **Addendum for whoever picks up BH_021 — a fourth thing worth deciding, found the
  same day as the first real "un-fold" case.** The convention's own "wiring is real, not
  decorative" rule turned out to need a corollary: a container earns a `depends_on` edge onto a
  convergence only when it holds a genuine blocker, not merely because it's real, logged, open
  scope during that epoch. mcRepos folded [RM_FRO_021](../roadmap/RM_FRO_021_susan-02.md)
  ("susan-02") into [RM_FRO_017](../roadmap/RM_FRO_017_donna.md) ("Donna")'s `depends_on` on a
  too-literal reading of Donna's own "fold real siblings in" instruction, then reversed it the same
  day once a closer look showed nothing on the container actually blocked Donna's convergence
  claim. Full writeup on [BHRM — Roadmap Conventions § Epoch maintenance nodes](../wiki/meta/bhrm.md#epoch-maintenance-nodes-containers)
  (the "Corollary" paragraph). Worth folding into BH_021 alongside the other three open questions —
  not urgent, same as those.

- 2026-08-28: **Closed.** Tracked upstream in the Backhaul repo as BH_021 — all three open
  questions (first-class `kind: maintenance` vocabulary, a `bhrm new` shortcut for the standing
  pair, advisory checks if the schema lands) carried forward undecided, matching this ticket's own
  "left open here rather than pre-decided" framing. Not urgent — mcRepos' plain-`work` version
  already works today.
- 2026-08-28: Ticket opened. First real instance of the convention:
  [RM_FRO_020](../roadmap/RM_FRO_020_susan-01.md)/[RM_FRO_021](../roadmap/RM_FRO_021_susan-02.md)
  opened in mcRepos's FrontierMode graph, gating [RM_FRO_019](../roadmap/RM_FRO_019_karen.md)
  ("Karen"). Within the same day, real events already exercised the "container holds more than one
  ticket with different urgency" case described above — see RM_FRO_020's own log.

- 2026-08-28: **Interim convention adopted, ask sharpened.** Rather than leave "resolved means
  not-blocking, not everything's-closed" as an implicit reading, formalized a required `Deferred,
  non-blocking:` callout for any container in that state (now in
  [BHRM — Roadmap Conventions](../wiki/meta/bhrm.md#epoch-maintenance-nodes-containers), retrofitted
  onto RM_FRO_020). Item 1's ask above sharpened from a vague "first-class kind" mention to a
  concrete proposed status pair (`collecting <-> clear`) once that's worth building.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/backhaul)
<!-- bh-header:end -->
