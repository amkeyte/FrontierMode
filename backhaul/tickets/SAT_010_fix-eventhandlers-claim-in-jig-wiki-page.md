---
id: SAT_010
uid: SAT
number: 10
client: Satchel
status: done
title: Fix eventHandlers claim in jig wiki page
context: jig-registration-break.md says no new-path event-subscription slot exists;
  it does, just unconsumed. Routed to PM -- pause/reroute when picked up.
priority: normal
opened: '2026-08-13'
closed: '2026-08-13'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Fix eventHandlers claim in jig wiki page

### Detail

[Jig & Strap Registration](../wiki/satchel/architecture/jig-registration-break.md) ("The new
path" section) states: "There is currently no 'new'-generation equivalent at all for the *strap*
half (subscribing to `ScopeEvent`) ... that side of the migration doesn't appear to have started."

That's not quite accurate. `JigExecutionConfig` already has an `eventHandlers` slot (accessor
`eventHandlers()`, mutator `eventHandlers(EventHandlers)`, plus an inline
`eventHandlersBuilder()`), and `TrackingModule.init()` (`common/newconfig/TrackingModule.java`)
already populates it correctly via `EventHandlers.builder().on(...)...build()`.

The real gap: nothing downstream ever calls `.install(bus)` on the resulting `EventHandlers`
object. It's stored in `JigExecutionConfig.Presets.eventHandlers` and read by nothing —
`ScopeLifecycleDispatcher` posts `ScopeEvent`s directly to `foundation.eventBus()`, with no code
path connecting it to any jig's configured `EventHandlers`. So the slot exists and is populated;
it's just never wired to the bus that would make it do anything.

Wiki fix: correct the "no new-generation equivalent" claim to describe the actual gap (slot
exists + populated, but `.install(bus)` is never called by anything). Found while working
[SAT_008](SAT_008_fix-4-satchel-compile-blocking-errors.md).

### Log

- 2026-08-13: Ticket opened.
- 2026-08-13 (Architect): Verified independently (read `EventHandlers.java`,
  `JigExecutionConfig.java`, current `TrackingModule.java`, and grepped for any `.install(` call
  site — none found outside `EventHandlers`'s own declaration). Confirmed accurate. Corrected both
  [Jig & Strap Registration](../wiki/satchel/architecture/jig-registration-break.md) and
  [the recovery plan](../wiki/satchel/architecture/jig-registration-recovery-plan.md). Filed
  [SAT_011](SAT_011_wire-eventhandlers-install-into-boot.md) for the actual remaining gap
  (`.install(bus)` never called). Closing.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
