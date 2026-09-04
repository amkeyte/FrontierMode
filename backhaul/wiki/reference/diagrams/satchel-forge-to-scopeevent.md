---
id: reference/diagrams/satchel-forge-to-scopeevent
category: reference/diagrams
slug: satchel-forge-to-scopeevent
title: 'Satchel: Forge Event to ScopeEvent'
summary: 'High-level block diagram of Satchel''s own pipeline: how a raw Forge event
  (LevelEvent, PlayerEvent, TickEvent) becomes a ScopeEvent.Loaded/Tick/Unloaded that
  module EventHandlers subscribe to.'
keywords: null
status: verified
updated: '2026-09-03'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · reference / diagrams
<!-- bh-header:end -->

# Satchel: Forge Event to ScopeEvent

High-level block diagram of the pipeline every real Forge event runs through before a module ever
sees a `ScopeEvent.Loaded`/`Tick`/`Unloaded`. This is Satchel's own plumbing, not any one mod's --
every `EventHandlers.builder().on(ScopeEvent.Tick.class, ...)` registration a module makes (e.g.
`BorderModule`/`BossModule` in FrontierMode) is the very last box in this diagram.

Large-font standalone render: [html/satchel-forge-to-scopeevent.html](html/satchel-forge-to-scopeevent.html).

Two genuinely separate paths run through `ServerForgeIngress`, not one:

- **Scope discovery** (`LevelEvent.Load`/`Unload`, player login/logout) runs synchronously off the
  raw Forge event itself -- `introduceSource`/`tryRemoveSource` ask every installed `JigInfo`'s
  `ScopeCoupler` to resolve the source, right then. This is where a brand-new `ScopeInfo` gets
  created (phase `NEW`), or where an existing one gets torn down directly via `jig.onUnload(info)`
  -- unload does **not** wait for the next tick pulse.
- **Everything else** (`Loaded`, `Tick`) only happens from inside
  `FoundationLifecycleDispatcher.pulse()`, driven once per server tick off
  `TickEvent.ServerTickEvent`. `pulse()` walks every `JigInfo` x `ScopeInfo` pair and calls
  `handleExecutionPulse` (readiness convergence: `NEW` -> ask the coupler `tryMarkReady` -> once
  accepted, `onLoad` -> `ScopeEvent.Loaded`) and `onTick` (`coupler.onJigTick` ->
  `ScopeEvent.Tick`, a no-op unless the scope is already `LOADED`).

`ASatchelJig` is the one base class `LevelJig`/`PlayerJig`/`MobJig` all extend, and it's the only
place that ever calls `ScopeLifecycleDispatcher` -- which is in turn the only place a `ScopeEvent`
object actually gets constructed and posted to `SatchelEventBus`. The bus itself is a plain
synchronous, class-keyed pub/sub with no filtering of its own -- by the time anything reaches it,
every invariant (readiness, phase, scope-kind matching) has already been enforced upstream.

**Deliberately not shown:** every `handleExecutionPulse`/`onTick` call in this diagram also drives
a second, genuinely separate system through the same coupler -- `coupler().onExecutionPulse`/
`onJigTick`/`onScopeUnload` (in `AScopeCoupler`) each delegate straight to a `ScopeEngine`
(`engine().onExecutionPulse`/`onJigTick`/`unload`), which handles bundle-backend mechanics
(persistence hydration/flush server-side, bundle construction only client-side) -- see that
interface's own doc: "how backend operations are executed, not when or why they occur." The Engine
call and the `ScopeEvent` signal are sibling steps off the same jig hook, not a pipeline where one
feeds the other -- on tick the Engine runs first, then the signal; on unload that order flips
(the `ScopeEvent.Unloaded` signal fires *before* `engine().unload()` tears the bundle down, so a
module's unload handler still sees a live bundle). Not expanded into this diagram since it's an
orthogonal concern to "how does a ScopeEvent get made," not a step in that pipeline.

Derived from: `server/lifecycle/ServerForgeIngress.java`, `common/jig/guts/LogicalFoundation.java`
(`introduceSource`/`tryRemoveSource`/`installConfigs`), `common/lifecycle/
FoundationLifecycleDispatcher.java`, `common/jig/guts/ASatchelJig.java`, `common/jig/guts/
AScopeCoupler.java`, `common/jig/guts/ScopeEngine.java`, `common/lifecycle/
ScopeLifecycleDispatcher.java`, `common/lifecycle/SatchelEventBus.java`, `common/newconfig/
EventHandlers.java`. `ClientForgeIngress` mirrors `ServerForgeIngress` client-side and isn't
expanded here -- same shape, different Forge event set (client tick, no player login/logout).

```mermaid
%%{init: {'themeVariables': {'fontSize': '20px'}, 'flowchart': {'useMaxWidth': false}}}%%
flowchart TD
    subgraph FORGE["Forge (Minecraft) -- raw events"]
        LVLEVT["LevelEvent.Load / Unload"]
        PLYEVT["PlayerLoggedInEvent / LoggedOutEvent /<br/>PlayerChangedDimensionEvent"]
        TICKEVT["TickEvent.ServerTickEvent<br/>(END phase, every server tick)"]
    end

    ING["ServerForgeIngress<br/>(ClientForgeIngress mirrors this client-side,<br/>not shown) -- the only real Subscribe-Event<br/>listeners in all of Satchel"]

    LVLEVT --> ING
    PLYEVT --> ING
    TICKEVT --> ING

    subgraph DISCOVERY["Scope discovery -- driven directly off<br/>Load/Unload and login/logout, not the tick engine"]
        INTRO["introduceSource(source)"]
        ADDSCOPE["Every installed JigInfo asks its ScopeCoupler<br/>to resolveScope(source) -- LevelScopeCoupler /<br/>PlayerScopeCoupler / MobScopeCoupler, per jig kind.<br/>First match -> JigInfo.addScope(...) --<br/>new ScopeInfo, phase NEW"]
        REMOVE["tryRemoveSource(source)"]
        UNLOADNOW["Same coupler resolution; on a match,<br/>calls jig.onUnload(info) directly --<br/>synchronous, not pulse-driven"]
    end

    ING -->|"LevelEvent.Load,<br/>PlayerLoggedInEvent"| INTRO
    ING -->|"LevelEvent.Unload,<br/>PlayerLoggedOutEvent"| REMOVE
    INTRO --> ADDSCOPE
    REMOVE --> UNLOADNOW

    PULSE["FoundationLifecycleDispatcher.pulse() --<br/>the real engine, once per server tick"]
    ING -->|"TickEvent.ServerTickEvent"| PULSE

    subgraph PERSCOPE["For every JigInfo, for every one of its<br/>ScopeInfo -- run every pulse"]
        HEP["jig.handleExecutionPulse(info)"]
        TICK["jig.onTick(info)"]
    end

    PULSE --> HEP
    PULSE --> TICK

    subgraph JIGBASE["ASatchelJig -- shared base every jig kind<br/>(Level / Player / Mob) extends"]
        READY["Phase NEW: ask coupler.tryMarkReady(info) --<br/>per-scope-kind readiness check"]
        ONLOAD["Accepted -> onLoad(info): coupler.onScopeLoad(info),<br/>then signal Loaded"]
        STEADY["Phase LOADED instead runs<br/>coupler.onExecutionPulse(info) --<br/>steady-state work, no ScopeEvent"]
        ONTICKJ["coupler.onJigTick(info), then signal Tick"]
        ONUNLOADJ["signal Unloaded first,<br/>then coupler.onScopeUnload(info)"]
    end

    HEP --> READY
    READY -->|"accepted"| ONLOAD
    READY -->|"already LOADED"| STEADY
    TICK --> ONTICKJ
    UNLOADNOW --> ONUNLOADJ

    DISP["ScopeLifecycleDispatcher --<br/>the only place a ScopeEvent object<br/>actually gets constructed"]
    ONLOAD -->|"signalScopeLoaded"| DISP
    ONTICKJ -->|"signalScopeTick<br/>(no-op unless phase LOADED)"| DISP
    ONUNLOADJ -->|"signalScopeUnloaded"| DISP

    BUS["SatchelEventBus.post(event) --<br/>synchronous, class-keyed pub/sub"]
    DISP -->|"ScopeEvent.Loaded"| BUS
    DISP -->|"ScopeEvent.Tick"| BUS
    DISP -->|"ScopeEvent.Unloaded"| BUS

    HANDLERS["Module EventHandlers, installed onto the bus<br/>at boot from each module's JigConfig --<br/>e.g. BossModule::onBossJigTick,<br/>BorderModule::onPlayerScopeTick"]
    BUS --> HANDLERS
```
