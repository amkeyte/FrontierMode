---
id: RM_FRO_012
uid: RM_FRO
number: 12
kind: work
status: open
title: Client render lifecycle cleanup
owner: Arryn
depends_on:
- RM_FRO_008
created: '2026-08-16'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_FRO
<!-- bh-header:end -->

## Client render lifecycle cleanup

- 2026-08-16: **Doc follow-up done ahead of the code** — [New Module Checklist](../wiki/satchel/architecture/new-module-checklist.md)
  now has item 6 (subscribe to `ScopeEvent.Unloaded` for any client-side scope-keyed cache, and
  why the eviction handler itself doesn't need its own `Satchel.isReady()` check) and
  [Border](../wiki/frontiermode/architecture/border.md) links this node from its own "Known gaps"
  section, alongside a correction to that page's stale `BundleFactories` description and a new
  note on `BorderAPI.borders()`'s `isReady()` gate. This node's own code fix (items 1 and 2 below)
  is still open — the doc groundwork just doesn't need to wait for it.
- 2026-08-16: Node opened, sibling to [RM_FRO_011](RM_FRO_011_betty.md) and
  [RM_FRO_013](RM_FRO_013_judy.md) under [RM_FRO_010](RM_FRO_010_susan.md) — same source-level
  resilience pass over `border/*`, this one scoped to `border/client/render/level/*` and
  `Rendering.java`.

Two gaps, both about the render package not participating in lifecycle/cleanup the way the rest
of Border does:

**1. `GrowthTriggerRenderer.debugFlame()` is live in production, unconditionally, every client
tick.** It's the first statement in `GrowthTriggerRenderer.tick()`, which is wired live via
`BorderModule`'s `EventHandlers` (`.on(ScopeEvent.Tick.class, Rendering::onClientTick)` →
`GrowthTriggerRenderer.tick()`). It spawns a `FLAME` particle above the player's head every tick,
no flag, no config gate — visible to every player, always. `Rendering.onClientTick` has an
almost identical block already commented out a few lines below the live call
(`mc.level.addParticle(ParticleTypes.FLAME, ...)`), which is a strong signal this was meant to be
disabled the same way and got missed when the call site moved. Fix: delete `debugFlame()` (or
gate it behind an actual debug flag if it's wanted for real diagnostics — but nothing about its
current form looks intentional).

**2. `RenderContext.CACHE` (and the `BordersRevisionMonitor` maps nested inside every entry) are
never evicted.** `CACHE` is a `static Map<LevelScope, RenderContext>`, populated via
`computeIfAbsent` every frame in `RenderContext.getInstance()`, with no corresponding removal
anywhere in the file or its callers. Every world a client visits in one session leaves one
`RenderContext` (plus the two maps inside its own `BordersRevisionMonitor`) parked in a static map
for the life of the JVM.

  **This should not be solved as a bespoke FrontierMode cache-management scheme.** Checked
  directly against Satchel's source: `ScopeEvent.Unloaded` already exists
  (`ScopeLifecycleDispatcher.signalScopeUnloaded`), already fires on **both** sides — driven by the
  same `tryRemoveSource` path [RM_SAT_014](../../roadmap/RM_SAT_014_joseph.md) wired into both
  `ServerForgeIngress` and `ClientForgeIngress`'s `LevelEvent.Unload` handlers — and its own
  javadoc states it's "the final guaranteed safe access point for the scopeInfo and any data
  associated with it." `BorderModule.init()`'s `EventHandlers` currently only subscribes to
  `ScopeEvent.Tick`; it never listens for `Unloaded`. The signal Satchel already provides for
  exactly this purpose is simply unused, not missing. Fix direction: add
  `.on(ScopeEvent.Unloaded.class, Rendering::onClientUnload)` (or equivalent) alongside the
  existing `Tick` registration in `BorderModule.init()`, guarded by the same client-side check
  `Rendering.onClientTick` already uses, and have the handler call
  `RenderContext.CACHE.remove(event.info().scope())` (package-visible eviction method, since
  `CACHE` is currently `private`). `BordersRevisionMonitor`'s own maps go away for free once the
  owning `RenderContext` is evicted — no separate cleanup needed there.

  **No fixture and no Satchel-side change needed.** `RenderContext` is deliberately not
  persisted/bundle state — [Border's architecture page](../wiki/frontiermode/architecture/border.md)
  already notes the render package is real client-only code sitting outside the shared
  fixture/facet facade on purpose, since there's no server-side equivalent of drawing a ring.
  Wrapping it in a `SatchelFixture` to get eviction "for free" would pull in persistence/revision
  machinery this cache doesn't want, when the actual missing piece is one existing event
  subscription.

  **Worth carrying into [New Module Checklist](../wiki/satchel/architecture/new-module-checklist.md)
  once this lands:** any future client-side cache keyed by scope should subscribe to
  `ScopeEvent.Unloaded` from the start rather than rediscovering this gap independently — flagging
  here so the doc update isn't lost; not part of this node's own done-bar.

**Done bar:** real play confirmation that (a) no flame particle appears during normal play, and
(b) `RenderContext.CACHE` shrinks back down after leaving a world (visit two worlds in one client
session, confirm only one live entry remains) — not just a compile check, matching this project's
standard for anything touching client lifecycle.

- 2026-08-16: **Implemented by Lead Dev (Curtis), unverified — no build access this session.**
  - **Item 1 fixed as described:** `GrowthTriggerRenderer.debugFlame()` deleted outright (not
    gated behind a flag — nothing about its prior form looked intentional, matching this node's
    own read).
  - **Item 2 fixed as described, via a new public seam rather than direct package access:**
    `RenderContext` is package-private in `border.client.render.level`, but `Rendering` (the
    subscriber home) lives in `com.arryn.frontiermode` — a different package — so `Rendering`
    can't reach `RenderContext` directly. Added `RenderContext.evict(LevelScope)` (package-visible,
    same as the node's fix direction asked for) plus a public
    `GrowthTriggerRenderer.onUnload(LevelScope)` seam that calls it (mirroring how `tick()` is
    already `GrowthTriggerRenderer`'s public per-tick seam for `Rendering.onClientTick`).
    `Rendering.onClientUnload` subscribes to `ScopeEvent.Unloaded` (wired into `BorderModule`'s
    `EventHandlers` alongside the existing `Tick` subscription), guards client-side the same way
    `onClientTick` does, and defensively `instanceof LevelScope`-checks the event's scope (skip,
    don't crash, if a future client-applicable jig kind ever shares this bus) before calling the
    new seam. `BordersRevisionMonitor`'s own maps go away for free once the owning `RenderContext`
    is evicted, as the node's fix direction predicted — no separate cleanup needed there.
  - **Not yet carried into [New Module
    Checklist](../../wiki/satchel/architecture/new-module-checklist.md)** the way this node's own
    doc-follow-up log entry (above) said it should be — that page already has item 6 from the
    doc-follow-up pass; didn't re-touch it since nothing about the actual fix changed the
    guidance already written there.
  - **Unverified this session** — no Forge/Mojang maven access (confirmed via curl). This node's
    own done-bar specifically requires real play (no flame particle during normal play; visiting
    two worlds in one client session leaves only one live `CACHE` entry) — see
    [FRO_023](../../tickets/FRO_023_playtest-checklist-batch2.md). Singleplayer/integrated is
    sufficient; nothing here crosses a client/server network boundary.

## Required By

*(computed — nothing depends on this yet)*
