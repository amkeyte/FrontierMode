---
id: RM_SAT_018
uid: RM_SAT
number: 18
kind: convergence
status: WIP
title: Universal sidedness facade
owner: Arryn
depends_on:
- RM_SAT_017
created: '2026-08-14'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_SAT
<!-- bh-header:end -->

## Universal sidedness facade

- 2026-08-14: Node opened as **WIP** — a forward-looking target opened well ahead of its work
  being scoped, deliberately. See
  [Universal Sidedness Facade](../wiki/satchel/architecture/facade-vision.md) for the full vision
  and open questions; this node is the roadmap-side tracker, not a restatement of it.

Marks the point where Satchel becomes the exclusive path to Forge for every module — every
touch point, not just tick/lifecycle, guaranteed side-correct by construction rather than by the
thread-discipline convention [Forge Integration & Sidedness Contract](../wiki/satchel/spec/forge-integration.md)
currently documents. Motivated by what happens once testing moves past the integrated-server
setup: side-correctness bugs are already real today (SAT_024, SAT_029 — both found in
single-player), and dedicated-server deployment removes the accidental same-JVM safety net
currently limiting their blast radius, it doesn't introduce the risk.

**First real piece inserted: [RM_SAT_019](RM_SAT_019_dennis.md)** (a Satchel-issued
world-identity token, synced to the client, folded into `LevelScope`'s UUID derivation) — the
first concretely scoped instance of "Satchel issues an explicit identity value instead of a
consumer deriving one ambiently," the same shape the rest of this vision will take. Still mostly
undecomposed beyond that — a generic side-bound event-forwarding registration (retiring
`BorderModule`'s direct `MinecraftForge.EVENT_BUS.addListener` call) is the next most concretely
buildable piece; a frame-driven dispatch path for the render thread is the hardest piece and
likely one of the last, per the vision page's own read of the difficulty. Building this on top of
a foundation with known, unresolved fragility would have been premature — hence the RM_SAT_017
gate, and why RM_SAT_019 itself depends on RM_SAT_014 landing first rather than starting in
parallel.

- 2026-08-15: `depends_on` narrowed to [RM_SAT_017](RM_SAT_017_paul.md) alone — this node
  previously also named RM_SAT_019 directly, in parallel with RM_SAT_017, which routed around
  this convergence's own gate. RM_SAT_019 is now one of RM_SAT_017's own prerequisites instead
  (see that node's 2026-08-15 log entry); the relationship described above is unchanged, just
  expressed as a single edge.

## Required By

*(computed — nothing depends on this yet)*
