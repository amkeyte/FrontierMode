---
id: FRO_079
uid: FRO
number: 79
client: FrontierMode
status: open
title: Deprecate /border debug create
context: '[Donna_02] Deprecate BorderCommandHandler.debugCreate() and its escape-hatch
  accessors. FRO_074#6'
priority: low
opened: '2026-09-03'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

Deprecate /border debug create

Split from [FRO_074](FRO_074_cartographer-findings.md#6-deprecate-bordercommandhandlerdebugcreate----per-project-owner)
finding 6. `debugCreate()` (`/border debug create`) is the only place anything reaches past
`BorderAPI`'s facet-resolver ready gate to the raw jig entry, via `BorderAPI.levelJig()` and
`BorderAPI.scope(level)` -- both public `BorderAPI` methods with no other caller anywhere in the
codebase (confirmed by search). Self-documented as deliberate at the time: the normal gated
accessors collapse "facet absent" and "not ready yet" into one `Optional.empty()`, and this
command needed to tell those apart.

**Per project owner (stated decision, no open ruling question): deprecate it.**

**Removal surface** (naming what's reachable from this command and nothing else):

- The `.then(Commands.literal("create")....)` node under `debug()` in `BorderCommands.java`
- `BorderCommandHandler.debugCreate()` itself
- `BorderAPI.levelJig()` and `BorderAPI.scope(Level)` -- dead surface once `debugCreate()` is
  gone, since it's currently their only caller. Whoever removes this decides whether those two
  go with it or stay as a lower-level accessor for future debug tooling.

The plain `/border debug` -> `BorderCommandHandler.debug()` command is untouched -- it goes
through the ordinary `BorderAPI.CRUD()` gate and isn't part of this ticket.

## Log

- 2026-09-03: Ticket opened.
- 2026-09-03: Ticket opened. Split from FRO_074 finding 6 for scheduling; stated decision, cleanup-scale removal. Parked on [RM_FRO_025](../roadmap/RM_FRO_025_donna-02.md) ("Donna_02").
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
