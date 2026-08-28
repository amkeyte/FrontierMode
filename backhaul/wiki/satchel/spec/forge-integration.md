---
id: satchel/spec/forge-integration
category: satchel/spec
slug: forge-integration
title: Forge Integration & Sidedness Contract
summary: Which classes may touch Forge's event buses directly, which bus each legitimate
  touch-point uses, and the sidedness/thread-binding rules any code reaching into
  Satchel must follow.
keywords: null
status: verified
updated: '2026-08-27'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · satchel / spec
<!-- bh-header:end -->

# Forge Integration & Sidedness Contract

Defines which classes may register directly against Forge's event buses, which bus each
legitimate touch-point uses, and the sidedness/thread-binding rules any code reaching into
Satchel must follow. Doesn't re-derive how Satchel's runtime works internally — see
[Jig & Scope Runtime](../architecture/runtime.md) for that; this page is the boundary contract
only, current-state, no history.

## Forge has two separate event-bus hierarchies

- **The mod event bus** (`FMLJavaModLoadingContext.get().getModEventBus()`, or
  `@Mod.EventBusSubscriber(bus = Bus.MOD)`) — per-mod lifecycle events: setup phases, config
  load/reload (`ModConfigEvent`), registry events. Fires once, in order, per mod — not per-tick,
  not gameplay-driven.
- **The Forge (global) event bus** (`MinecraftForge.EVENT_BUS`, or the default
  `@Mod.EventBusSubscriber(bus = Bus.FORGE)`) — gameplay events: `LevelEvent`, `TickEvent`,
  `BlockEvent`, `RenderLevelStageEvent`, `RegisterCommandsEvent`, etc. This is the bus Satchel's
  own ingress classes subscribe to.

Know which one a given `@SubscribeEvent` method is actually on before assuming it fires per-tick
or in gameplay context — the annotation looks identical either way; only the `bus =` parameter (or
which bus a manual `.register(...)`/`.addListener(...)` call targets) tells you.

## Who touches which bus directly, and why it's legitimate

| Touch point | Bus | Registration style | Why direct |
|---|---|---|---|
| `ServerForgeIngress` / `ClientForgeIngress` (Satchel) | Forge | `@Mod.EventBusSubscriber` (static class) | Satchel's own ingress — see below |
| `FrontierMode.onRegisterCommands` | Forge | Instance — `MinecraftForge.EVENT_BUS.register(this)` in the constructor | Command registration has no Satchel equivalent |
| `Rendering.onRenderLevel` | Forge, client-only | `@Mod.EventBusSubscriber(value = Dist.CLIENT)` (static class) | `RenderLevelStageEvent` is per-frame; Satchel's tick isn't frame-accurate |
| `BorderModule.onBlockPlaced` | Forge | Explicit `MinecraftForge.EVENT_BUS.addListener(BorderModule::onBlockPlaced)` inside `init()` | Raw gameplay input (block placement) driving domain logic — not scope/bundle state |
| `Config.onLoad` | Mod | `@Mod.EventBusSubscriber(bus = Bus.MOD)` (static) | Config reload is inherently mod-lifecycle, not gameplay |
| `ServerForgeIngress.onPlayerLoggedIn`/`.onPlayerChangedDimension` | Forge | `@Mod.EventBusSubscriber` (static class, same as the rest of `ServerForgeIngress`) | World-identity token push (`PlayerEvent.PlayerLoggedInEvent`/`PlayerChangedDimensionEvent`) — per-player, not scope-shaped, so it rides the existing ingress class rather than a `ScopeEvent` |
| `ClientForgeIngress.onLoggingOut` | Forge, client-only | `@Mod.EventBusSubscriber` (static class, same as the rest of `ClientForgeIngress`) | Clears the session-scoped world-identity token on disconnect (`ClientPlayerNetworkEvent.LoggingOut`) |

Four different registration idioms show up here for legitimate reasons (static class-subscriber,
instance-registered, explicit method-reference listener) — that's Forge's own API surface having
multiple valid entry points, not drift to clean up. What makes a touch point legitimate isn't
which idiom it uses; it's whether a Satchel-mediated equivalent already exists and was skipped for
no reason. `Rendering` itself demonstrates both sides of that line in one file:
`onRenderLevel(RenderLevelStageEvent)` is a real, necessary direct Forge touch (no Satchel
equivalent for per-frame render staging exists), while `onClientTick(ScopeEvent.Tick)` in the same
class is a plain method wired through `BorderModule`'s `EventHandlers` — not annotated, not
Forge-registered at all, reached entirely through Satchel's own bus.

## Satchel's own ingress discipline

Within Satchel itself: **`ServerForgeIngress`/`ClientForgeIngress` are the only classes allowed to
touch raw Forge events.** Every other Satchel class reads `ScopeEvent`/`SatchelEvent`/`BundleEvent`
off `SatchelEventBus` instead. This is what makes `Satchel.require()` resolvable at all, and what
keeps side-correctness at least structurally centralized everywhere except these two classes.

This rule is Satchel-internal — it does not mean a module built on Satchel can never touch Forge
directly. It means: before adding a new direct Forge subscription in a module, check whether
Satchel already exposes the semantic equivalent as a `ScopeEvent`/`BundleEvent` (load, tick,
unload). If it does, use that instead of a raw Forge hook. If it doesn't — per-frame rendering,
raw block/entity input, command registration, config — a direct touch is legitimate, but should
live in one clearly-named place per concern (`Rendering` for render hooks, `Config` for config, the
module's own `init()` for domain-input events like `onBlockPlaced`), not scattered across the
module.

## Sidedness — the contract, not just the mechanism

- `LogicalSideContext` is a bare `ThreadLocal<LogicalSide>`. It answers exactly one question:
  which side is this thread bound as right now.
- Every `@SubscribeEvent` handler in `ServerForgeIngress`/`ClientForgeIngress` calls
  `BOOTER.bindFoundation()` — which binds `LogicalSideContext` — as its first action, on *every*
  call, not once at startup.
- **The rule for any code reaching into Satchel** (`Satchel.require()`, `.ask()`,
  `.getOrCreate()`, `.foundation()`): it must run on a thread one of the two ingress classes has
  already bound this tick/event. Gameplay code reached from inside a Forge event handler is safe.
  Worker threads, async callbacks, or anything scheduled off-thread is not — `LogicalSideContext.require()`
  throws `IllegalStateException` rather than silently resolving the wrong side. That's loud
  failure, not a wrong answer, but only for "never bound at all." A thread that *is* bound can
  still claim the wrong side if the side-check itself is wrong (`requireClient()`'s inverted
  check, fixed) — thread discipline buys "some side, reliably," not "the right side, guaranteed."
- `Rendering.onRenderLevel` is worth copying as a pattern: despite already being restricted to
  `Dist.CLIENT` by its class-level annotation, it still explicitly re-checks
  `Satchel.foundation().filter(f -> f.side().isClient())` before doing anything. Forge's
  dist-restriction and Satchel's own side resolution are enforced by two entirely different
  mechanisms (annotation processing vs. a runtime `ThreadLocal`) — one passing doesn't guarantee
  the other agrees, so both get checked.
- **Client-side scope recognition can now be legitimately deferred, not just missing.**
  `LevelResolver.resolveScope` withholds recognizing a `Level` as a scope at all until a
  server-issued world-identity token has round-tripped to the client (`WorldIdentityContext`,
  RM_SAT_019) — a real, expected window at world join, not an error state. Code that queries a
  scope during that window (e.g. render code reached before the round-trip completes) should use
  `LogicalFoundation.tryScopeInfo` (returns `Optional<ScopeInfo>`) instead of
  `requireScopeInfo` (throws `ScopeNotFound`) if there's any chance it runs before the scope is
  guaranteed registered. `requireScopeInfo` is still correct for the common case — dispatch
  reached from an already-tracked `ScopeEvent`/tick, where the scope is guaranteed to exist.
  `tryScopeInfo` answers "does a jig know about this scope yet" — a different, narrower question
  from the general readiness gate below; a scope can be unknown even after Satchel itself is
  ready, and vice versa during the boot window.
- **`Satchel.isReady()` is the general readiness gate — check it before anything else.**
  ([SAT_032](../../../tickets/SAT_032_isready-gate.md)) Being bound to a side
  (`LogicalSideContext`) only means a thread can ask Satchel a question; it doesn't mean the
  answer is available yet. `Satchel.isReady()` is true on the server once a foundation is
  installed, and on the client only once the world-identity token has been received and bound —
  the same precondition `tryScopeInfo`'s deferral above exists for, generalized to apply anywhere,
  not just scope lookup. Code that can run before readiness (rendering, commands, anything outside
  the two ingress classes) should check this proactively and skip gracefully, the same "standby,
  don't crash" pattern `BorderAPI`'s facet resolvers (`PATH`/`CRUD`/`RULES`/`INFO`) and
  `RenderContext.getInstance()` both use.
  Ticking itself is gated on this too: `ServerForgeIngress`/`ClientForgeIngress.onExecutionPulse`
  no-op the foundation's lifecycle pulse until `Satchel.isReady()`, with the bootstrap chain
  itself (`bindFoundation`, `ensureInstalled`, and the client's token-arrival detection) carved
  out as the deliberate exception that has to run unconditionally to make readiness happen at all.
- **Constructing a `LevelScope` directly pre-readiness throws.** A `LevelScope` built before the
  world-identity token arrives would otherwise fall back to a dimension-only UUID instead of a
  token-folded one — convenient, but a landmine for any caller
  that treats that UUID as a stable identity (a long-lived cache key, for instance: two different
  UUIDs mean two different map entries for what's really the same level, once the token lands).
  `LevelScope`'s constructor now throws `SatchelException.NotReady` instead of falling back.
  `LevelScope` is still directly constructable from anywhere — that's unchanged — but callers that
  might run before readiness should either check `Satchel.isReady()` first or, better, go through
  `LevelResolver.resolveScope(...)` (returns `null` during the defer window rather than throwing).

## Unload is a real teardown now

`LevelEvent.Unload` handlers in both ingress classes call `LogicalFoundation.tryRemoveSource`,
which drives real jig teardown (`onUnload` → `ScopeEngine.unload` → bundle eviction from both
engines' `active` maps).

## Related pages

- [Jig & Scope Runtime](../architecture/runtime.md)
- [Satchel mod summary](../satchel.md)
- [Border](../../frontiermode/architecture/border.md)
