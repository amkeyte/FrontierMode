---
id: RM_SAT_020
uid: RM_SAT
number: 20
kind: work
status: open
title: Build PlayerJig/PlayerScope
owner: Arryn
depends_on:
- RM_SAT_017
created: '2026-08-14'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_SAT
<!-- bh-header:end -->

## Build PlayerJig/PlayerScope

- 2026-08-14: Node opened, ruled ("option A") over hosting per-player state on the existing
  world-scoped `BordersBundle` instead. Project owner's reasoning: `Scope` is meant to generalize
  — levels, blocks, players, mob bosses, anything that represents a live Forge-adapted data set a
  bundle's fixtures operate on — and Player is the long-term proof that the abstraction isn't
  just `LevelJig` with extra steps. Concretely: a player's applicable border layer can be modified
  by buffs that can't be recomputed generically — it has to follow the player, across dimensions,
  independent of which world-level bundle they're currently standing in. Same for "which border ID
  is a border-compass currently attuned to" — identity-tied state, not level-derivable state.
  Explicitly rejected hosting this on `BordersBundle` instead (cheaper short-term) for two reasons:
  it would need a manual handoff on every dimension change, and more importantly it would make
  `BordersBundle` "the go-to mechanism" every future module dumps player-state into — precisely
  the SavedData-sprawl problem Satchel was built to replace in the first place.

**Not a resurrection — a rebuild.** `PlayerJig`/`PlayerScope`/`PlayerScopeCoupler` (all in
`common/jig/player/*`) predate not just the Strap→JigConfig migration but an earlier
`jig`/`requireJig` package rename — they reference `com.arryn.satchel.common.requireJig.player`,
a package that no longer exists, and implement a `SatchelScope`/`ScopeCoupler` shape from before
the current `ASatchelScope`/`JigConfig` system. Build fresh against what `LevelJig`/`LevelScope`/
`LevelJigConfig` demonstrate today: a `PlayerScope extends ASatchelScope`, a `PlayerJigConfig`
(mirroring `LevelJigConfig`'s four-category-lens shape), a `PlayerScopeCoupler`, and ingress
wiring — `PlayerEvent.PlayerLoggedInEvent`/`PlayerLoggedOutEvent`/a per-player tick source — using
the old files only as a sketch of intent, not a starting point. See
[Forge Integration & Sidedness Contract](../wiki/satchel/spec/forge-integration.md) and
[Jig & Scope Runtime](../wiki/satchel/architecture/runtime.md) for the pattern to follow.

**Sequencing note, not a hard gate:** this doesn't depend on
[RM_SAT_018](RM_SAT_018_edward.md) (universal sidedness facade) — that vision is still
undecomposed and gating a ruled, concrete feature on it would stall real progress. If facade work
lands first, this jig's ingress can be built against whatever generic forwarding mechanism it
introduces instead of a dedicated ingress class; if this lands first, it just follows the current
per-scope-kind ingress pattern `LevelJig` already uses. Either order works.

**Consumer waiting on this:** [RM_FRO_006](RM_FRO_006_sandra.md) — cross-graph,
not a `depends_on` edge (`bhrm` graphs are UID-independent), documented in both directions.

- 2026-08-16: **Done bar added, project owner's call, before implementation started** — this node
  previously had no stated done-bar, unlike its RM_FRO siblings in the same handoff batch
  ([FRO_022](../../tickets/FRO_022_handoff-batch2.md)). Added ahead of Lead Dev picking this up.

**Done bar:** compiling clean (`gradlew build` on Satchel, then FrontierMode via `buildSatchel`)
is necessary but not sufficient — matching this project's standing rule that anything touching
lifecycle/ingress needs real-play confirmation, not a compile check alone. Concretely:

- A full player login → tick → dimension change → logout cycle, run against a **real dedicated
  server** (`runServer`) with a **separate client** (`runClient`) connecting to it — not the
  integrated/singleplayer path alone. Flagged specifically because `PlayerJig`'s login/logout
  ingress (`PlayerEvent.PlayerLoggedInEvent`/`PlayerLoggedOutEvent`) is the first jig kind in
  either repo whose scope lifecycle is driven by a per-connection event rather than a per-level
  one, and the [Universal Sidedness Facade](../wiki/satchel/architecture/facade-vision.md) vision
  this node feeds into already names dedicated-server deployment as the point where the same-JVM
  safety net (both `LogicalFoundation`s alive together in singleplayer) stops covering for
  sidedness bugs. An integrated-server-only test wouldn't exercise the real cross-JVM boundary
  this ingress has to be correct against.
- Confirm a `PlayerScope`'s state survives a dimension change without a manual handoff — the
  specific property this node's design log (above) cites as the reason it isn't hosted on
  `BordersBundle` instead.
- Confirm logout actually tears the scope down (mirroring [RM_SAT_014](RM_SAT_014_joseph.md)'s
  `LevelJig` unload fix) rather than leaking a `PlayerScope` per player per session — the same
  class of bug [RM_FRO_012](../../roadmap/RM_FRO_012_carolyn.md) found on the render-cache side.
- No `RM_FRO_006` consumer exists yet to exercise this end-to-end, so verification here is
  necessarily synthetic (a minimal test consumer, or direct inspection of jig/scope state) rather
  than an observable player-facing effect — log that explicitly rather than treating "nothing
  visibly changed" as confirmation of anything.

- 2026-08-16: **Implemented by Lead Dev (Curtis), unverified — no build access this session.**
  Built fresh against `LevelJig`'s current pattern, not a resurrection of the old commented-out
  files (confirmed those predate both the jig/requireJig rename and the current
  `SatchelScope`/`ScopeCoupler` shape, exactly as this node's design log said):
  - `PlayerJig`, `PlayerResolver`, `PlayerScope`, `PlayerScopeCoupler`
    (`common/jig/player/*`) — mirror `LevelJig`/`LevelResolver`/`LevelScope`/`LevelScopeCoupler`
    exactly. `PlayerScope` holds a `ServerPlayer` (not the abstract `Player`); UUID is derived
    from the player's own persistent UUID, deliberately not folded with the world-identity token
    (a player's identity isn't level-scoped).
  - `PlayerJigConfig` (`common/newconfig/newnew/`) — mirrors `LevelJigConfig`'s four-category-lens
    shape. Defaults `sideApplicability` to `SERVER` (not `LevelJigConfig`'s default, which Border
    overrides to `BOTH`) since every named consumer (RM_FRO_006) is server-authoritative. No
    `referenceLevel` default — deliberately left for a future consumer to supply, since unlike
    `LevelScope` there's no single `Level` a `PlayerScope` is intrinsically tied to. Not registered
    anywhere yet — no module calls `Satchel.registerJigConfig` with one, matching "no real
    consumer yet" from this node's own text.
  - Ingress wiring in `ServerForgeIngress`: `PlayerEvent.PlayerLoggedInEvent` →
    `introduceSource(player)`, `PlayerEvent.PlayerLoggedOutEvent` → `tryRemoveSource(player)`.
    Deliberately does **not** hook `PlayerChangedDimensionEvent` — a `PlayerScope` has to survive
    a dimension change intact, unlike `LevelScope`. No separate "per-player tick source" was
    added: the existing shared `TickEvent.ServerTickEvent` → `foundationLifecycle().pulse()` path
    already walks every `JigInfo`/`ScopeInfo` including a future `PlayerJig`'s, the same way it
    already does for `LevelJig` — nothing level-specific in that path.
  - Deleted `PlayerLoadEvent`/`PlayerTickEvent`/`PlayerUnloadEvent` (`common/jig/player/*`) —
    fully commented-out scaffolding referencing a `Lifecycle.Domain`/`ISatchelLoadEvent` event
    system that no longer exists (current model is the generic `ScopeEvent.Loaded`/`Tick`/
    `Unloaded` posted via `SatchelEventBus`, already sufficient for any future `PlayerJig`
    consumer — no per-domain event classes needed). Not mentioned in this node's own rebuild-shape
    description, and pure dead weight the same way `RM_FRO_009`'s `BorderView` is.
  - **Not built:** any bundle/fixture or module `init()` — that's `RM_FRO_006`'s job per this
    node's own text ("this node's own work is comparatively small... wire registration into
    `BorderModule.init()`").
  - **Unverified this session** — no Forge/Mojang maven access in this sandbox (confirmed via
    curl). Real build + the dedicated-server login/tick/dimension-change/logout cycle from this
    node's done-bar (above) is owed before this counts as resolved — see
    [FRO_023](../../tickets/FRO_023_playtest-checklist-batch2.md).

## Required By

*(computed — nothing depends on this yet)*
