---
id: SAT_011
uid: SAT
number: 11
client: Satchel
status: done
title: Wire EventHandlers.install() into boot
context: JigExecutionConfig.eventHandlers() is built and stored but nothing ever calls
  .install(bus) on it.
priority: normal
opened: '2026-08-13'
closed: '2026-08-13'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Wire EventHandlers.install() into boot

### Context

`JigExecutionConfig` has an `eventHandlers` slot (`eventHandlers()`/`eventHandlers(EventHandlers)`/
`eventHandlersBuilder()`) backed by `common/newconfig/EventHandlers.java` — a declarative builder
of `SatchelEventBus` subscriptions with a working `.install(SatchelEventBus bus)` method.
`TrackingModule.java` already builds and attaches one correctly (its own comment: "replaces
Strap"). Confirmed by direct grep that `.install(` is never called anywhere in the codebase except
its own declaration — the object is built, stored in the compiled config, and never read again.
This is the one missing piece keeping the new declarative config system from being a complete
Strap replacement. See
[Jig & Strap Registration](../wiki/satchel/architecture/jig-registration-break.md#the-new-path-fully-built-wired-and-empty)
for full context. Found while working [SAT_008](SAT_008_fix-4-satchel-compile-blocking-errors.md),
filed as [SAT_010](SAT_010_fix-eventhandlers-claim-in-jig-wiki-page.md).

### Suggested fix

Most likely belongs in `LogicalFoundation.installConfigs(List<CompiledJigConfig>)`, right after
each `JigInfo` is constructed: if `cfg.execution().eventHandlers()` is non-null, call
`.install(this.eventBus())` (the foundation's own bus is already available via `eventBus()`).
Single shared fix — benefits `TrackingModule` and any future consumer (Border included), not
per-mod work.

### Required By

*(none)*

## Log

- 2026-08-13: Ticket opened.
- 2026-08-13: Fixed as suggested in `LogicalFoundation.installConfigs()`. Found one thing the
  suggestion didn't account for: `ClientFoundationBooter.installFoundation()` was calling
  `installConfigs()` *before* `installEventBus()` (unlike `ServerFoundationBooter`, which already
  had the right order) — `eventBus()` would have thrown `IllegalStateException` the first time a
  client-side `JigConfig` carried event handlers. Reordered Client to match Server. Currently
  dormant either way, since `TrackingModule`'s `LevelJigConfig` is server-only
  (`sideApplicability = SERVER`), but it's a landmine for the next client-side config that adds
  handlers. Confirmed by a real `gradlew build`: `BUILD SUCCESSFUL`, no errors. Closing.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
