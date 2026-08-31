---
id: SAT_043
uid: SAT
number: 43
client: Satchel
status: open
title: Document SatchelFixture per-fixture lifecycle API
context: RM_SAT_024 (Raymond epoch maintenance 1) -- FrontierMode design pass surfaced documentation
  gap in extensible overload system.
priority: high
opened: '2026-08-31'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

**Write proper API-reference-style documentation for SatchelJig's per-fixture-instance lifecycle
extensible overload system.**

Currently scattered: `fixture.md` documents `onCreated()` and `onLoaded()` only; `isReady()`,
`onJigTick()`, and `onRemoved()` are real, already-relied-on-elsewhere hooks but lack proper
documentation, making it easy for downstream consumers (FrontierMode's Border Pregeneration, for
example) to miss them entirely until a design pass surfaces a need.

**Scope:** Write a new or substantially expanded section in `runtime.md` (or a linked subsection
in `fixture.md` if that's clearer) documenting the complete per-fixture-instance lifecycle
surface `SatchelFixture` exposes for overriding:

- `onCreated()` — when it fires, what state is guaranteed, what it's for
- `onLoaded()` — when it fires, what state is guaranteed, what it's for
- `onRemoved()` — when it fires, what state is guaranteed, what it's for
- `onJigTick()` — when it fires, what state is guaranteed, what it's for
- `isReady()` — what it should return, how it's used, relation to bundle/foundation-level
  `isReady()` (note: name collision with unrelated `Satchel.isReady()` / `LogicalFoundation.isReady()`
  in `runtime.md` — must disambiguate clearly)

**Format:** API reference style, not prose narrative — signature, contract, usage pattern,
examples if helpful. Treat it as documenting an extension point for developers building fixtures.

**Related:**
- [RM_FRO_028](../roadmap/RM_FRO_028_diane.md) ("Diane" — Border Pregeneration) -- triggered this
  gap's discovery; that node's Open Questions section references this ticket.
- [RM_FRO_024](../roadmap/RM_FRO_024_donna-01.md) ("Donna epoch maintenance 1") -- 2026-08-31 log
  entry flags the same gap from FrontierMode side.
- [RM_SAT_024](../roadmap/RM_SAT_024_raymond-01.md) ("Raymond epoch maintenance 1") -- container
  for this item.

## Log

- 2026-08-31: Ticket opened, per RM_SAT_024's first item. Blocks nothing currently, but will
  unblock future cross-module fixtures that need `isReady()` or `onJigTick()` overrides.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
