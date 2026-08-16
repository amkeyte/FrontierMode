---
id: FRO_012
uid: FRO
number: 12
client: FrontierMode
status: done
title: Port Border to JigConfig/EventHandlers
context: Last piece of the registration recovery plan; Border still calls dead SatchelJigRegistrar/Strap.
priority: high
opened: '2026-08-13'
closed: '2026-08-13'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Port Border to JigConfig/EventHandlers

### Context

The last confirmed break in
[Jig & Strap Registration](../wiki/satchel/architecture/jig-registration-break.md): `BorderModule.java`
imports and calls `SatchelJigRegistrar` (doesn't exist anywhere, dead or alive) and
`SatchelStrap`/`SatchelStrapRegistrar` (fully dead, commented out) — 15 of FrontierMode's compile
errors, confirmed by a real build. Everything else this depended on is now fixed and closed
(SAT_008, SAT_011, FRO_011, SAT_005). Full design and rationale:
[Jig & Strap Registration — Recovery Plan](../wiki/satchel/architecture/jig-registration-recovery-plan.md) —
this ticket is that plan's action items 2-4 (item 1, `BorderAPI.levelJig()`, already fixed via
FRO_011). `common/newconfig/TrackingModule.java` (Satchel) is a live, compiling template for the
whole shape of this port — copy its pattern rather than building it from scratch.

### Suggested fix

Per the recovery plan:

1. Build a `JigConfig` for Border's `LevelJig` — `LevelJigConfig` is already shaped for this but
   hardcodes `sideApplicability = SERVER` and leaves `bundles.schema` unset. Needs Border's real
   schema (`BordersBundle`/`BordersFixture`) wired in, and a decision on one `BOTH`-applicability
   config vs. two side-specific instances (Border currently registers `LevelJig` separately per
   side — check whether `compileForSide`'s filtering actually produces two independent jig
   instances from one `BOTH` config, or whether two configs are still needed).
2. Replace `SatchelJigRegistrar.register(...)` in `BorderModule.init()` with
   `JigConfigCompiler.register(...)`.
3. Build an `EventHandlers` via `EventHandlers.builder().on(ScopeEvent.Tick.class, ...).build()`
   for `BordersTriggers::updateFinderItems` and `Rendering::onClientTick`, attach with
   `config.execution().eventHandlers(...)` — replacing the `BorderStrap`/`StrapDeclaration`/
   `SatchelStrapRegistrar.register(...)` block entirely.
4. Once nothing references them, delete the dead old-path scaffolding for real: Satchel's
   `common/jig/guts/SatchelJigRegistrar2.java`, `common/jig/strap/SatchelStrap.java`,
   `SatchelStrapRegistrar.java`, and the commented-out `Satchel.activateRegistrations(LogicalSide)`
   block in `Satchel.java`. Safe — nothing depends on keeping them; do this last, after the port
   compiles, not before.

Confirm with a real `gradlew build` on FrontierMode at the end — should be `BUILD SUCCESSFUL` with
zero errors for the first time this investigation.

### Required By

*(none)*

## Log

- 2026-08-13: Ticket opened.
- 2026-08-13 (Architect): Reviewed the plan before work started. Confirmed the `BOTH`
  applicability reasoning independently (one config in `JigConfigCompiler.CONFIGS`, filtered
  independently per side by `compileForSide()`, each side's own `LogicalFoundation.installConfigs()`
  instantiates its own jig — two instances from one declaration). Flagged a real prerequisite the
  ticket didn't call out: `BorderModule.init()` must run strictly before either
  `ClientFoundationBooter`/`ServerFoundationBooter.installFoundation()`, since `compileForSide()`
  freezes `JigConfigCompiler`'s registry and a late `register()` call throws. Confirmed safe —
  `init()` runs at FML mod-construction time; `installFoundation()` is gated behind Forge's first
  `LevelEvent.Load`/tick event, strictly later.
- 2026-08-13: Ported as planned, with one addition beyond the ticket's suggested fix: set
  `config.policies().capabilities(new JigPolicies.Capabilities(true, false, false))` on Border's
  config. Without it, `ScopeEngine_Server.hydrateBundle()`/`flushIfDirty()` throw `AccessFailed`
  the first time a border loads, since `LevelJigConfig`'s default `Capabilities` has
  `requiresPersistence = false` and Border's whole feature is persisted state. Also confirmed
  `BundleFactories.registerFactory(...).registerFixture(...)` had to stay untouched — it's a
  separate, still-live registry that actual bundle construction goes through
  (`ScopeEngine.create()`), not the same thing as `JigBundles.Schema`/`bundles().schema(...)`,
  which `JigConfigValidator` requires but nothing downstream currently reads back for
  construction. `FrontierKeys.java` also had its own dead `SatchelJigRegistrar` import (unused,
  separately compile-blocking) — removed alongside the port. Deleted
  `SatchelJigRegistrar2.java`, `SatchelStrap.java`, `SatchelStrapRegistrar.java`, and the
  commented `Satchel.activateRegistrations(...)` block once grep confirmed nothing else
  referenced them. Confirmed by a real `gradlew build` on both repos: `BUILD SUCCESSFUL`, zero
  errors, first time this investigation. Filed a follow-up for the same persistence-capability
  gap in `TrackingModule`, routed to PM. Closing.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
