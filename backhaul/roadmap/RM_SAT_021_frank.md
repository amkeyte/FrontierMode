---
id: RM_SAT_021
uid: RM_SAT
number: 21
kind: work
status: open
title: Build MobJig/MobScope
owner: Arryn
depends_on:
- RM_SAT_017
created: '2026-08-16'
superseded_by: null
ticket: SAT_035
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_SAT
<!-- bh-header:end -->

## Build MobJig/MobScope

- 2026-08-16: Node opened. Fourth real jig kind (after `LevelJig`, `PlayerJig`, and the deleted
  `ModelJig`) — the mob/entity generalization of `Scope`, same reasoning
  [RM_SAT_020](RM_SAT_020_jerry.md) ("Jerry," `PlayerJig`) gave for why `Scope` has to generalize
  beyond `Level`: a boss entity has state (which `Border`/level it belongs to, alive/defeated) that
  has to travel with the entity itself, not get derived fresh from wherever it happens to be
  standing. **Consumer waiting on this:** [RM_FRO_018](RM_FRO_018_shirley.md)
  ("Shirley," FrontierMode's boss entity/spawn system) — cross-graph, not a `depends_on` edge
  (`bhrm` graphs are UID-independent), documented in both directions, same convention
  RM_SAT_020/RM_FRO_006 established.

- 2026-08-16: **Design refined through Architect/project-owner discussion — corrects several
  specifics in the entry above.** Kept for the record rather than silently rewritten:
  - **`Mob`, not `LivingEntity`.** `LivingEntity` includes `Player`/`ServerPlayer`, which
    `PlayerJig` already owns — typing `MobScope` against it would let the same object be scoped by
    two different jig kinds with no type-level guard against it. `Mob` (vanilla's class for
    AI-driven, non-player creatures) excludes `Player` at the type level, not just by convention,
    and matches the real first consumer (a tagged vanilla creature) more precisely than the
    broadest possible supertype does.
  - **No `MobResolver`.** Checked `LevelResolver`/`PlayerResolver` directly rather than assuming
    the split was worth mirroring: neither does anything that couldn't live as a public static
    method directly on its own `Scope` class. The only reason the statics landed in a separate
    class was `LevelJigConfig.createPresets()` needing a public method reference for
    `uuidDeterminer` — a public static on `LevelScope` itself would have satisfied that equally
    well. Not worth the extra indirection for a jig kind being built fresh. (Not touching
    `LevelResolver`/`PlayerResolver` themselves — already-resolved work, out of scope here; flagged
    only so this reasoning doesn't need rediscovering later.)
  - **Entry point: `MobScope.getFor(Mob mob)` → `Optional<MobScope>`, a static factory on
    `MobScope` itself** — not a new `Satchel.introduceMobSource(...)` method. Keeping mob-specific
    methods off `Satchel`'s own top-level facade matches its existing shape: `Satchel`/
    `LogicalFoundation` stay generic over `Object` sources (`introduceSource`/`tryRemoveSource`),
    with each jig kind deciding what it recognizes — there's no `Satchel.introducePlayer()`/
    `introduceLevel()` today, and a Mob-specific method would be new, unnecessary surface. `getFor`
    does, in order: check `Satchel.isReady()` (cheap, side-agnostic — it resolves against whichever
    foundation is bound on the calling thread, so this one check correctly guards both sides with
    no Mob-specific logic); reject an already-`isRemoved()` reference (`Optional.empty()`, not a
    throw — the "bad reference" case worth guarding for now); call
    `Satchel.require().introduceSource(mob)`; return `Optional.of(new MobScope(mob))`. Confirmed
    against `LogicalFoundation.introduceSource`'s real implementation that step is safe to call
    unconditionally, every time `getFor` runs — it already loops the small, fixed set of registered
    `JigInfo`s and only calls `addScope` if `!ji.hasScope(scope)`, so `getFor` doesn't need to track
    "already introduced" itself.
  - **No opinionated `sideApplicability` default.** The entry above defaulted `MobJigConfig` to
    `SERVER`, mirroring `PlayerJigConfig`. Reconsidered: Satchel itself shouldn't assume mob-scoped
    state is forever server-only just because the first named consumer happens to be.
    `introduceSource` already degrades gracefully per-`JigInfo` regardless of side (confirmed from
    source — an unmatched side just logs "matched no jigs," never throws), so there's no
    correctness reason to bias the default. Each consumer's own `JigConfig` states its own
    `sideApplicability` explicitly — `BossModule`'s starts `SERVER` because defeat-detection
    genuinely is server-only today (see [RM_FRO_018](RM_FRO_018_shirley.md)), but that's a
    Boss-level decision, not baked into Mob-level infrastructure. A later client-rendering consumer
    (a boss health bar, say) would register its own `BOTH`-applicability config the same way
    Border's `LevelJigConfig` already does for rendering, without `MobJig` itself needing to change.
  - **Teardown hooks `EntityLeaveLevelEvent`, not `LivingDeathEvent`.** The entry above's "thin
    `LivingDeathEvent`/`EntityLeaveLevelEvent` listener" was two different concerns wearing one
    hook. `EntityLeaveLevelEvent` is reason-agnostic — death, discard, chunk-unload-without-a-player,
    and dimension-change all funnel through it — which is what a generic jig-kind's own lifecycle
    teardown should key off, the same way `LevelEvent.Unload`/`PlayerLoggedOutEvent` already do for
    the other two kinds. `LivingDeathEvent` is a different, domain-specific signal that only
    matters to a consumer that cares *why* something left (Boss does; `MobJig` itself doesn't need
    to). Per [Forge Integration & Sidedness Contract](../wiki/satchel/spec/forge-integration.md)'s
    own rule — check whether Satchel already exposes a `ScopeEvent` equivalent before adding a
    direct Forge subscription; if it doesn't, a direct touch is legitimate — `LivingDeathEvent` has
    no `ScopeEvent` equivalent and isn't `MobJig`'s to own.
    [RM_FRO_019](RM_FRO_019_karen.md) hooks it directly in `BossModule.init()`, the same pattern
    `BorderModule.onBlockPlaced` already establishes (that spec page's touch-point table wants a new
    row once this lands). The two hooks don't race: a death fires `LivingDeathEvent` immediately,
    but the entity stays valid for about 20 ticks (vanilla's death-animation window — `deathTime`
    counts up before `remove()` actually runs) before `EntityLeaveLevelEvent` fires, so Boss reads
    whatever it needs (position, for the border-growth center) synchronously in its own handler,
    well before `MobJig`'s own teardown runs.
  - **Real, source-confirmed risk found, not yet resolved: stale `ScopeInfo.source` across a chunk
    reload.** Checked `JigInfo`/`ScopeInfo`/`ASatchelScope` directly. `ASatchelScope.equals()`/
    `hashCode()` are UUID-based (final, overridden) — good, it means two different `MobScope`
    instances built for the same entity's UUID (e.g. before and after a chunk unload/reload hands
    back a *new* `Mob` Java object carrying the same persistent UUID) correctly collide as the same
    map key, so re-`introduceSource`-ing a reloaded entity won't create a duplicate `ScopeInfo` or
    throw. But `LogicalFoundation.introduceSource`'s `if (!ji.hasScope(scope)) { ji.addScope(scope,
    source); }` guard means `addScope` — the only place that captures `source` — runs exactly once
    per UUID. `ScopeInfo.source()` is a plain final field, never updated after that first call. So
    after a reload, the tracked `ScopeInfo` keeps pointing at the *original*, now-stale `Mob`
    object, not the fresh one the reload produced. Nothing in `LevelJig`/`PlayerJig` exposed this:
    a `Level` object doesn't get recreated while its dimension stays loaded, and a `PlayerScope`
    deliberately never survives past one login session (a reconnect is a fresh scope, not a resumed
    one — confirmed in RM_SAT_020's own real-play log). Mob is the first jig kind where the
    underlying Java object is genuinely transient for what's conceptually one continuous scope.
    **Recommendation, not yet settled:** don't treat `ScopeInfo.source()` (or any field cached on
    `MobScope` itself) as a reliable live-entity handle across ticks — a consumer needing the
    current, guaranteed-live entity should re-resolve it fresh via `ServerLevel.getEntity(UUID)`
    rather than trust a held reference. Whether `JigInfo`/`ScopeInfo` should instead be fixed to
    refresh `source` on re-introduction (so this isn't every consumer's own problem to route
    around) is a real open question for whoever implements this — flagging rather than deciding,
    since it may touch shared `JigInfo` shape, not just `MobJig`'s own code.

- 2026-08-17: **Second correction, larger than the first — the opt-in/`EntityLeaveLevelEvent`
  ingress-and-teardown model above is superseded.** Project owner identified the real problem this
  design hadn't accounted for: a boss's chunk is essentially never loaded at the moment its
  existence needs to be checked or defined — that's the normal state a boss spends most of its
  time in, not an edge case a "consumer calls `getFor()` when it happens to hold a reference"
  model can gracefully degrade into. `getFor(mob)` only works at all when something already has a
  live `Mob` in hand; it has nothing to say about a boss whose chunk is unloaded, which is most
  bosses, most of the time. Generalized per the same discussion: `MobJig` shouldn't be scoped to
  "whatever Boss needs" — it's the mob/entity generalization of `Scope` full stop, and needs a
  presence model that doesn't assume a live reference is the normal case.
  - **No more automatic teardown via `EntityLeaveLevelEvent`, and no more "ingress is just
    `getFor` called by a holder."** Both assumed presence was persistent once established, which
    isn't true for an unloaded chunk — `EntityLeaveLevelEvent` firing (or not) was never actually
    the signal that mattered; what matters is *currently resolvable via `Level.getEntity(UUID)`*,
    re-checked, not inferred from an event.
  - **Replacement: poll-driven presence, not event-driven.** A consumer registers a
    **`MobJigConfig` interest supplier** — effectively "here are the UUIDs I care about, per
    level" — rather than calling an ingress method per entity. `MobJig` adds a new reconciliation
    step inside `foundationLifecycle().pulse()` (the same pulse `ServerForgeIngress.onExecutionPulse`
    already runs every server tick, not a new tick source) that, each cycle, calls
    `Level.getEntity(UUID)` for every UUID any registered supplier is interested in. `getEntity` is
    a cheap O(1) lookup and is safe to call whether or not the chunk is loaded — it just returns
    null when there's nothing there, no exception, no precondition. A UUID that resolves and isn't
    yet scoped gets introduced (fires `ScopeEvent.Loaded`); a UUID that was scoped last cycle and
    no longer resolves gets torn down (fires `ScopeEvent.Unloaded`) — reason-agnostic, same
    contract `LevelEvent.Unload`/`PlayerLoggedOutEvent` teardown already gives the other two jig
    kinds, just reached by re-checking instead of subscribing. Doesn't need to run every tick;
    something on the order of every 20 (`PlayerTrackingModule`'s own cadence) is the working
    assumption, tunable once there's something real to measure against.
  - **`MobScope.getFor(Mob mob)` isn't removed — it's demoted to a fast path, not the ingress.**
    At the one moment presence is already guaranteed without waiting on a poll cycle — a boss
    freshly spawned this tick, entity reference already in hand — calling `getFor` directly still
    works and still short-circuits a cycle of lag. The poll is what makes every *other* moment
    (chunk reload, server restart, "player wandered back into range") work too, which `getFor`
    alone never could.
  - **This is genuinely new Satchel-side work for this node, not a rewire of the design already
    written up above.** The interest-supplier registration surface and the pulse-driven
    reconciliation loop don't exist anywhere in `LevelJig`/`PlayerJig` to copy from — both of those
    still rely on Forge join/leave events because their populations (dimensions, logged-in
    players) are small and the event firing reliably *is* an acceptable assumption for them. `Mob`
    is the first jig kind where that assumption breaks, and the poll is being built specifically
    because of it.
  - **The stale-`ScopeInfo.source()` risk flagged in the entry above is now largely addressed as a
    side effect, not separately fixed.** Since a `MobScope`'s backing `Mob` is re-resolved fresh
    via `Level.getEntity(UUID)` every reconciliation cycle rather than assumed live indefinitely
    from one `getFor` call, a stale post-reload object no longer silently lingers uncorrected — the
    next poll cycle either confirms the same UUID resolves to a (possibly new) live object or tears
    the scope down. Whether `ScopeInfo.source()` itself gets refreshed in place, or the poll instead
    tears down and re-introduces on every reload, is still an implementation detail worth deciding
    explicitly rather than assuming — carried forward, not newly resolved.

- 2026-08-21: **Scheduling, not design: this node goes before its FrontierMode consumer, project
  owner's call.** [RM_FRO_018](RM_FRO_018_shirley.md) ("Shirley," boss entity/spawn system) is
  currently the only node `bhrm frontier --uid RM_FRO` reports as actionable, and it is built
  directly on this node's `MobScope.getFor(mob)` fast path, `MobJig`-scoped fixtures, and presence
  poll. Since the cross-graph relationship can't be a `depends_on` edge, that actionable listing is
  misleading on its own — so the ordering is now stated on both nodes. Practical effect for whoever
  picks this up: FrontierMode's Tier 1 is queued behind it, so this node's "Verification aid"
  section below matters more than usual — a synthetic consumer proving introduce/tick/remove is
  what unblocks Shirley, not a compiling `MobJig` alone. Tracked as
  [FRO_030](../tickets/FRO_030_frank-first.md).

- 2026-08-21: **Architect ticket opened: [SAT_034](../tickets/SAT_034_mobjig-docs.md).** This node's
  design is settled but lives only in this log; nothing in `wiki/` mentions `MobJig` or `MobScope`.
  SAT_034 covers consolidating it into [Jig & Scope
  Runtime](../wiki/satchel/architecture/runtime.md)'s jig-kinds section, and deciding whether
  `MobScope.getFor()` earns a spec page. **Read that ticket's item 1 before acting on the
  "build fresh against `PlayerJig`'s current shape" instruction below** — `runtime.md` currently
  describes `PlayerJig` as entirely commented-out scaffolding, which is false (verified against
  source 2026-08-21; [RM_SAT_020](RM_SAT_020_jerry.md) is `resolved` and FrontierMode consumes
  `PlayerScope` today). The reference this node says to copy from is documented as not existing.

- 2026-08-21: **Design signed off, build ticket opened: [SAT_035](../tickets/SAT_035_mobjig-build.md).**
  Architect completed [SAT_034](../tickets/SAT_034_mobjig-docs.md) — this node's design is now on
  [Jig & Scope Runtime § MobJig](../wiki/satchel/architecture/runtime.md#mobjig) and
  [MobScope.getFor() Contract](../wiki/satchel/spec/mobscope-getfor.md), and `runtime.md`'s stale
  `PlayerJig` claims are corrected. **Those two pages are authoritative over this log for anyone
  building.** This log contains two superseded designs (the opt-in/`EntityLeaveLevelEvent` ingress
  model and the earlier `LivingEntity` typing), both explicitly reversed — building from it
  front-to-back would build the wrong thing twice. The done bar and verification aid below stay
  authoritative here.

**Build fresh against `PlayerJig`'s current shape for the scope/config plumbing, minus the
corrections above — the ingress mechanism itself is new, not ported.** `MobScope extends
ASatchelScope`, holding a `Mob` directly (not `LivingEntity`), with `determineUUID` and the
`getFor` fast-path factory living on `MobScope` itself — no separate `MobResolver`. `MobJigConfig`
mirrors `PlayerJigConfig`'s four-category-lens shape structurally, but ships no `sideApplicability`
default, and adds the interest-supplier registration surface described above (nothing in
`PlayerJigConfig` to mirror for that part). UUID is `mob.getUUID()` directly — no token-fold
(unlike `LevelScope`), no readiness-gated defer window (unlike `LevelResolver`, matching
`PlayerResolver`'s simpler shape instead) — an entity's own persistent UUID is always immediately
resolvable on either side, once something is actually looking for it.

**Real architectural difference from `LevelJig`/`PlayerJig`: presence must be actively
re-verified, not assumed from an event or a held reference.** `ServerForgeIngress` introduces
*every* level (`LevelEvent.Load`) and *every* player (`PlayerLoggedInEvent`) as a source, and tears
down on the matching leave event — safe because both populations are small, bounded, and their
Forge events reliably fire for the full lifecycle. Entities aren't that population on either
count: a busy server has hundreds of mobs spawning and despawning per chunk load (so a blanket
`EntityJoinLevelEvent` hook is the wrong shape regardless), and — the real finding driving this
node's second design pass — a tracked entity spends most of its life in an unloaded chunk, where
no join/leave event is going to fire on any predictable schedule at all. The interest-supplier +
foundation-pulse poll above is the actual answer: `MobJig` doesn't wait to be told a boss exists or
stops existing, it periodically asks.

**Verification aid:** follow `PlayerTrackingModule`'s precedent — a minimal real consumer proving
introduce/tick/remove fire correctly (INFO-level logging), useful even before `RM_FRO_018` lands,
same reasoning RM_SAT_020 gave for building one ahead of its own real consumer. For this node,
that consumer should register interest in a UUID, confirm the scope appears within one poll cycle
of the entity becoming resolvable (not immediately — that's the real behavior to verify), and
confirm it disappears within one cycle of the entity no longer resolving, including across a
simulated chunk unload/reload, not just a genuine removal.

**Done bar:** same standard as RM_SAT_020 — compiling clean is necessary but not sufficient.
Confirm via a synthetic test consumer (or `MobTrackingModule`, mirroring `PlayerTrackingModule`):
registering interest in a UUID that later becomes resolvable creates a scope within one poll
cycle, the tick pulse reaches it, and the entity no longer resolving (chunk unload *or* genuine
removal — confirm both, since the poll has to treat them identically) tears the scope down
cleanly within one cycle, no leaked `MobScope`. Also confirm the fast path: `getFor(mob)` called
at spawn time attaches immediately, without waiting on the next poll cycle. This needs the
dedicated-server/real-chunk-unload path to verify properly (unlike the superseded design's claim
that integrated/singleplayer alone would do) — chunk unload behavior is exactly the thing under
test, and it's the one piece of this that couldn't be verified against source alone in this
session (no Forge/Mojang maven access in the sandbox this design pass ran in — flagged, not
assumed).

- 2026-08-21: **Stale `ScopeInfo.source()` question (flagged 2026-08-16, carried forward
  2026-08-17) resolved: no separate refresh mechanism — Architect ruling.** The poll model already
  treats loss of resolution and regain of resolution as teardown-then-reintroduce, not as one
  continuous `ScopeInfo` living through the gap: a UUID that stops resolving tears its `ScopeInfo`
  down; a UUID that later resolves again goes through `addScope` as if for the first time. Since
  `source` is captured exactly once — on the `addScope` call that creates a `ScopeInfo` — and no
  `ScopeInfo` instance survives the gap where staleness could occur, there's nothing left for an
  in-place refresh to fix: the teardown/reintroduce cycle already guarantees a fresh `source` on
  every reintroduction by construction, not as a separately-verified property. Documented as
  current design in [Jig & Scope Runtime](../wiki/satchel/architecture/runtime.md#mobjig). No
  change to the done bar below — this was a design question, not a new verification requirement.
  Consolidated into the wiki along with the rest of this node's design per
  [SAT_034](../tickets/SAT_034_mobjig-docs.md).

## Required By

*(computed — nothing depends on this yet)*
