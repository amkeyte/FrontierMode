---
id: SAT_015
uid: SAT
number: 15
client: Satchel
status: done
title: Runtime architecture reference page
context: No standalone doc for jig/scope/foundation runtime machinery. See SAT_014.
priority: high
opened: '2026-08-13'
closed: '2026-08-13'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Write a standalone reference page for the jig/scope/foundation runtime machinery underneath
Satchel's tick/event delivery. `bundle.md`/`fixture.md` cover state aggregation and the
persisted-unit model; `persistence.md` covers disk save/load; `net.md` covers transport. But the
runtime itself — `LogicalFoundation`, `LogicalSideContext`, `ScopeInfo`, `JigInfo`,
`SatchelJig`/`ASatchelJig`, `FoundationLifecycleDispatcher`, `ScopeLifecycleDispatcher`,
`SatchelEventBus`, and the `JigConfig`/`JigConfigCompiler`/`CompiledJigConfig` declarative config
layer on top of it — has no standalone doc. It currently only exists as narrative inside
[jig-registration-break.md](../wiki/satchel/architecture/jig-registration-break.md) and
[jig-registration-recovery-plan.md](../wiki/satchel/architecture/jig-registration-recovery-plan.md),
which are a break-and-fix story, not the intended long-term doc surface.

Best time to write this is now, while the mechanism is freshly re-verified end to end (see the
"Confirmed by a real compile" sections of jig-registration-break.md). Highest-priority item on
[SAT_014](SAT_014_status-doc-gaps-for-pm-to-ticket.md)'s doc-gap list.

### Scope addendum (documentation-coverage survey, 2026-08-13)

Two more things confirmed to have zero coverage anywhere in the wiki — fold both into this page
rather than opening separate tickets, since they're the same architectural layer:

- **The concrete jig kinds.** `common/jig/level/*` (`LevelJig`), `common/jig/model/*`
  (`ModelJig`), and `common/jig/player/*` (`PlayerJig`) are Satchel's three jig implementations.
  Only `LevelJig` is mentioned anywhere in the wiki, and only in passing as "the jig Border uses"
  ([border.md](../wiki/frontiermode/architecture/border.md),
  [jig-registration-recovery-plan.md](../wiki/satchel/architecture/jig-registration-recovery-plan.md)) —
  nothing explains what a jig kind *is* architecturally, or what distinguishes Level/Model/Player
  from each other. `ModelJig` and `PlayerJig` have no mentions at all.
- **Bundle-level lifecycle events.** `common/lifecycle/BundleLifecycleDispatcher.java` and
  `BundleEvent.java` sit alongside `FoundationLifecycleDispatcher`/`ScopeLifecycleDispatcher`/
  `SatchelEventBus` (already in this ticket's scope) but weren't named in the original SAT_014
  gap description. Same layer, should be covered by the same page.

See [documentation-coverage plan](../wiki/plans/doc-coverage.md) for the full survey
this was found in.

## Log

- 2026-08-13: Written and closed. New page:
  [Jig & Scope Runtime](../wiki/satchel/architecture/runtime.md) (`status: verified`, checked
  directly against source, not assumed). Covers: `LogicalFoundation`/`LogicalSideContext` and how a
  mod actually plugs in; the full Forge-event-to-`SatchelEventBus.post` dispatch chain;
  `JigInfo`/`ScopeInfo`/`SatchelJig`; the `JigConfig`/`JigConfigCompiler`/`CompiledJigConfig`
  declarative registration system with `TrackingModule` as a worked example (cross-referencing
  `border.md#runtime-wiring` for Border's); `BundleLifecycleDispatcher`/`BundleEvent` vs.
  scope-level `ScopeEvent` (the scope-addendum finding); and the three jig kinds (`LevelJig` live
  and dual-consumer, `ModelJig` real but has no `JigConfig` subclass or caller anywhere so it can
  never be instantiated, `PlayerJig` fully commented out across all seven files). Also found and
  documented as a known gap, not filed as its own ticket per this ticket's scope: `ScopeInfo.jigInfo()`
  unconditionally returns `null`, which makes `TrackingModule`'s three event handlers
  (`info.jigInfo().jig`) NPE on the first `ScopeEvent` delivered to any tracked scope — reachable
  now, not latent, since `TrackingModule.init()` runs unconditionally from `SatchelMod`'s
  constructor. Flagged to the Architect's caller for a possible follow-up ticket. Linked from
  `satchel.md`'s Architecture list, `jig-registration-break.md` and
  `jig-registration-recovery-plan.md` (forward-pointer notes plus `Related pages`, ahead of
  SAT_016's planned retitle of the break page), and `border.md`'s `Related pages`.
- 2026-08-13: Scope broadened per documentation-coverage survey — add jig-kinds
  (Level/Model/Player) and bundle-level lifecycle events, both previously undocumented.
- 2026-08-13: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
