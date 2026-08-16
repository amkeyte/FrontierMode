---
id: satchel/architecture/facade-vision
category: satchel/architecture
slug: facade-vision
title: Universal Sidedness Facade
summary: 'Vision: Satchel as the exclusive path to Forge for every module -- every
  touch point, not just tick/lifecycle, guaranteed side-correct by construction rather
  than by thread discipline.'
keywords: null
status: draft
updated: '2026-08-14'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · satchel / architecture
<!-- bh-header:end -->

# Universal Sidedness Facade

*Opened 2026-08-14, from a project-owner conversation about what happens once testing moves past
the integrated-server (single-player) setup. Explicitly a vision/direction page, not a spec — see
[BHW's spec-page criterion](../../meta/bhw.md#spec-pages-a-stricter-sibling-of-architecture-pages):
nothing here is a current contract yet. Stays `draft` by design, the same way
[Border-Frontier Reconciliation](../../frontiermode/architecture/frontier-reconciliation.md) does
— its job is holding open questions, not reporting a finished state.*

## The vision

Satchel already generalizes tick/lifecycle away from raw Forge for mod code — that's its stated
founding purpose (see [Satchel mod summary](../satchel.md)), and `EventHandlers`/`JigConfig`
already deliver it for the four `ScopeEvent`/`BundleEvent` kinds. The vision is finishing that
generalization: **no module ever touches `MinecraftForge.EVENT_BUS` or the mod bus directly, for
anything** — every legitimate touch point identified in
[Forge Integration & Sidedness Contract](../spec/forge-integration.md) (raw gameplay events,
command registration, config, eventually rendering) gets a Satchel-provided facade instead, and
that facade guarantees correct sidedness **by construction**, not by the thread-discipline
convention the current contract documents. Non-sided code that takes a `Level` should be able to
branch on what Satchel's own resolution of that `Level` says, rather than each consumer
re-deriving the answer with its own `instanceof ServerLevel` check.

## Why this isn't a someday problem

Every side-correctness bug found in the runtime-verification pass —
[SAT_024](../../../tickets/SAT_024_client-engine-wrong-exception.md) (client engine's
missing-bundle fallback never engaging),
[SAT_029](../../../tickets/SAT_029_send-sidedness-classcast.md) (unchecked `ServerLevel` cast in
`send()`), the `LogicalSideContext` fragility [Satchel mod summary](../satchel.md) already
documents — was found in single-player, where both `LogicalFoundation`s happen to coexist in one
JVM. That's an accidental safety net, not evidence the risk is dormant. A real dedicated-server
deployment removes the net; it doesn't introduce the class of bug.

## What's already real precedent (partially realized, not yet consistent)

- **`EventHandlers`/`JigConfig`** — proof the facade pattern works, currently scoped to
  `ScopeEvent`/`BundleEvent` only.
- **`JigBindingConfig.sideApplicability`** (`CLIENT`/`SERVER`/`BOTH`) — sidedness *declared and
  validated centrally at compile time* (`JigConfigCompiler.compileForSide()`), not re-derived ad
  hoc per call site. The closest existing thing to "declared, not derived."
- **`LogicalFoundation.side()`** — a real typed value, not just an ambient `ThreadLocal` read.
  Already consulted outside the ingress classes (`Rendering.onRenderLevel`'s
  `Satchel.foundation().filter(f -> f.side().isClient())`).
- **Inconsistently carried through even internally** — `ServerForgeIngress.onFirstLevelLoad`
  itself still does a raw `if (!(e.getLevel() instanceof ServerLevel)) return;` rather than asking
  Satchel's own authority. Even the ingress classes don't fully live the pattern yet.
- **New since this page opened: the world-identity token (RM_SAT_019) is a second, independent
  instance of "declared, not derived."** `WorldIdentityContext` makes dimension identity an
  explicit, server-pushed, client-cached value (`S2cWorldIdentityToken`,
  `WorldIdentitySavedData`) instead of something client code infers locally — the same shape as
  `sideApplicability`, applied to a different question (which world is this, not which side am
  I). `LogicalFoundation.tryScopeInfo` alongside it is the first real instance of "ask, don't
  assume" replacing a throwing `require*` call at a call site outside the ingress classes
  ([BorderAPI.borders](../../frontiermode/architecture/border.md) now uses it). Both are scoped
  narrowly to world/scope identity, not sidedness itself — but they're evidence the "declared
  centrally, resolved safely" pattern generalizes beyond the one place it started, which is
  exactly the bet this vision is making.

## What's genuinely missing, not just unfinished

- **No generic side-bound event-forwarding registration.** `BorderModule.onBlockPlaced` still
  calls `MinecraftForge.EVENT_BUS.addListener(BorderModule::onBlockPlaced)` directly — there's no
  `Satchel.forwardEvent(EventType.class, handler)`-shaped equivalent that would wrap an arbitrary
  Forge event with the same `bindFoundation()`-first discipline `ServerForgeIngress`/
  `ClientForgeIngress` already use for the four event kinds they handle. This is the most
  concretely buildable piece of the vision.
- **No frame-driven dispatch path.** Satchel's entire dispatch chain
  (`FoundationLifecycleDispatcher.pulse()`) is tick-shaped — once per Forge tick. `RenderLevelStageEvent`
  fires many times per tick. Routing render calls through the existing machinery would either
  throttle rendering to tick cadence or require a genuinely new, frame-shaped dispatcher alongside
  the tick one — not an extension of what exists, closer to a fourth event category. Confirmed
  deliberate exception for now, not an oversight: the overhead of routing every render call
  through Satchel's internal management isn't worth it yet.
- **Command registration and mod-bus config are probably permanent, legitimate exceptions** —
  `RegisterCommandsEvent` and `ModConfigEvent` aren't per-tick, per-`Level` gameplay concerns the
  way the rest of this vision is about; they don't obviously need "guaranteed sidedness" in the
  same sense. Worth scoping these *out* explicitly rather than letting them get pulled in by
  default once a generic forwarding facade exists.

## Open questions (not answered here — this page tracks them, doesn't resolve them)

- What does "non-sided code branching on what `require()` imbues" actually look like as an API
  shape? A `Level`/`LogicalFoundation` pair threaded explicitly through call signatures instead of
  `ThreadLocal` lookup? Something else?
- Would a generic event-forwarding facade need its own `sideApplicability`-style declaration per
  registered handler, mirroring `JigConfig`'s model, or can it infer side from the event type
  alone?
- Does the frame-driven dispatch path reuse any part of `FoundationLifecycleDispatcher`, or is it
  a genuinely parallel primitive?

## Sequencing

Deliberately not decomposed into roadmap work nodes yet — see
[RM_SAT_018](../../../roadmap/RM_SAT_018_edward.md), the convergence node tracking this vision.
Depended on [RM_SAT_017](../../../roadmap/RM_SAT_017_paul.md) (prototype hardening) landing
first, on the reasoning that building guaranteed-sidedness scaffolding on top of a foundation with
known, unresolved fragility (the unload-path gap, unconfirmed silent-inertness cases) would be
building on sand. RM_SAT_017 has since reached — both the unload-path gap
([RM_SAT_014](../../../roadmap/RM_SAT_014_joseph.md)) and the silent-inertness question
([RM_SAT_013](../../../roadmap/RM_SAT_013_gary.md)) are resolved/confirmed, so that precondition
is now satisfied. Real work nodes get inserted under RM_SAT_018 as pieces of this get scoped, not
invented wholesale now.

## Related pages

- [Forge Integration & Sidedness Contract](../spec/forge-integration.md)
- [Jig & Scope Runtime](runtime.md)
- [Satchel mod summary](../satchel.md)
- [RM_SAT_018](../../../roadmap/RM_SAT_018_edward.md)
