---
id: satchel/architecture/mob-lifecycle-signals
category: satchel/architecture
slug: mob-lifecycle-signals
title: Mob Lifecycle Signals
summary: MobDied, MobGainedInterest, MobLostInterest -- the Mob-kind signals Satchel
  posts alongside or independent of the generic ScopeEvent triad, and why each is
  shaped the way it is.
keywords: null
status: verified
updated: '2026-09-03'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · satchel / architecture
<!-- bh-header:end -->

# Mob Lifecycle Signals

Boss's defeat detection reached raw Forge directly (`LivingDeathEvent`) rather than going through
Satchel's own dispatch -- the one inconsistency in an otherwise `ScopeEvent`-mediated design. This
page is the fix: three new signals, narrowly scoped to what Boss actually needs. Not a commitment
to [Forge Event Conduit](forge-event-conduit.md)'s broader, still-parked idea -- that page's own
open questions (side-applicability declarations, multi-jig-type registries) stay unresolved; this
is a bespoke, Mob-specific addition that doesn't need them answered.

## MobGainedInterest / MobLostInterest

Real `ScopeEvent` siblings, fired by `MobJig` **alongside** the existing generic `Loaded`/
`Unloaded` -- additively, never instead of. `ScopeLifecycleDispatcher`/`ASatchelJig`'s shared
signal path is untouched, for every jig kind; `MobJig`, as a subclass, posts one extra signal of
its own at the same moment `Loaded`/`Unloaded` already fire.

**Additive, not a reroute, on purpose.** Rerouting would mean `MobJig` stops firing the generic
pair, silently blinding any consumer that expects `Loaded`/`Unloaded` uniformly across every jig
kind. That's not hypothetical -- [Satchel Health](satchel-health.md)'s canary-mob violation check
is exactly such a consumer, already live and verified on a real run, subscribed to `MOB_JIG`'s own
`ScopeEvent.Unloaded`. Rerouting would have broken it.

**The rename is honest, not decoration.** `Loaded`/`Unloaded` fire for `MobJig` off the
interest-registry-driven poll (`MobInterestRegistry` plus the ~20-tick reconciliation cycle -- see
[Jig & Scope Runtime § MobJig](runtime.md#mobjig)), not off any chunk-load-shaped event the way
they do for `LevelJig`. "Gained/lost interest" is what's actually happening; "Loaded"/"Unloaded"
was vocabulary borrowed from `LevelScope`, where it's literally true, and carried along generically
to a jig kind where it isn't.

`Tick` gets no Mob-specific equivalent -- nothing in `BOSS_MOB_JIG`'s wiring uses it; the poll and
materialization logic live on `BOSS_JIG`'s own tick, a `LevelJig`, not `BOSS_MOB_JIG`.

## MobDied

A standalone event -- `(Level, UUID, LivingDeathEvent)` -- posted to `SatchelEventBus` when a real
`LivingDeathEvent` fires for a UUID something has registered interest in. **Deliberately not a
`ScopeEvent`,** for two independent reasons:

1. **`ScopeEvent`'s contract assumes an always-resolved `ScopeInfo`.** `MobDied` can't make that
   assumption -- a boss spends most of its life with no attached scope at all (see [Boss § The
   central fact that shapes this whole
   design](../../frontiermode/architecture/boss.md#the-central-fact-that-shapes-this-whole-design)),
   and even mid-reentry -- loaded and killable again -- the scope doesn't reattach until the next
   poll tick, not instantly. A death in that window is routine, not a corner case, and `MobDied`
   has to be able to fire for it regardless.
2. **`ScopeInfo` itself isn't a fit public payload.** It's an internal type, not something module
   code should be handed directly. A real public payload wrapper to replace it across all of
   `ScopeEvent` is a separate, future need -- not solved here; see [Forge Event
   Conduit](forge-event-conduit.md)'s open items.

**Where it's produced:** a bespoke `@SubscribeEvent LivingDeathEvent` handler added directly to
`ServerForgeIngress` -- not the general, still-parked Forge Event Conduit machinery. Boss is the
only consumer today; this is scoped to what Boss actually needs, not a commitment to a generic
multi-jig-kind conduit.

**The gate:** before constructing anything, check the *union* of every currently-registered
`MobInterestRegistry` supplier's set for the dying entity's UUID. No match anywhere -- the
overwhelming common case, every ordinary mob death in the world -- drop, nothing constructed,
nothing posted. A match anywhere -- post one bare `MobDied`, to the whole bus, not scoped to
whichever consumer's interest happened to match.

**Why a bare, unscoped post is correct, not sloppy.** Every subscriber already has to check its own
relevance before acting -- exactly the discipline the raw `LivingDeathEvent` listener this replaces
already required (`onLivingDeath` resolves the dying entity against `BossFixture` and no-ops if
there's nothing there). A subscriber receiving a stray match for another module's interest just
repeats that same no-op -- standard Forge convention, not a new pattern. Routing the event by
matched `JigKey` would avoid that stray delivery, but at real cost (walking every registered
supplier per death, encoding matched keys into the payload) for a benefit that doesn't exist, since
the self-check is mandatory either way.

**Why the gate calls suppliers live, not from a cached snapshot.** Interest can't lag -- a boss's
UUID has to be checkable the instant `materialize()` registers it, not on the next poll cycle, or a
same-tick materialize-then-die could be missed entirely. A registered supplier whose own
computation is expensive (a hypothetical "creepers within N chunks" tracker, say) pays that cost on
every death that clears the gate, not amortized over a poll cadence -- that's a real cost, and it's
each such supplier's own responsibility to cache internally if it matters, not something the shared
gate should solve by reintroducing staleness. (A single Satchel-owned registry consumers push
UUIDs into and out of, instead of each pulling from its own supplier, would trade this cost for a
different one -- every consumer having to remember to deregister on every removal path, symmetrically,
forever. Discussed and set aside for now; see [Forge Event Conduit](forge-event-conduit.md)'s open
items.)

**No forced ordering or coupling with `MobLostInterest`/`Unloaded`.** Three genuinely different
events, for three different reasons, none synchronized with each other. A boss's `LivingDeathEvent`
firing and its `MobLostInterest`/`Unloaded` firing (on the next poll cycle, whenever that runs) are
two independent facts; a consumer holding a live reference across that gap must check
`isAlive()`/`isRemoved()` itself rather than assume presence implies liveness.

## Migration: Boss's defeat detection

- `BossModule`'s `LivingDeathEvent` handler ([RM_FRO_019](../../../roadmap/RM_FRO_019_karen.md)
  "Karen") moves off the raw Forge listener and onto `EventHandlers.on(MobDied.class, ...)`. The
  raw Forge registration itself relocates into `ServerForgeIngress`, alongside `LevelEvent.Unload`/
  `PlayerLoggedOutEvent` -- Satchel's own established "only these classes touch raw Forge" boundary,
  not a new exception to it.
- `BOSS_MOB_JIG`'s attach/release handlers move from `ScopeEvent.Loaded`/`Unloaded` onto
  `MobGainedInterest`/`MobLostInterest`.
- This closes [Boss § Known
  gaps](../../frontiermode/architecture/boss.md#known-gaps)' first item, for the case where
  `LivingDeathEvent` actually fires -- `MobDied` is now the real death signal that gap called for.
  The narrower remaining gap (a removal that never fires `LivingDeathEvent` at all -- external
  world-editing, an unrelated mod's bug) is untouched by construction; `MobDied` can't help there.
- [`MobScope.getFor()`'s](../spec/mobscope-getfor.md) "immediate attachment" guarantee still holds
  -- `SatchelEventBus.post()` is synchronous, so calling `getFor` from inside a `MobDied` handler is
  still the same call stack, same tick, entity still guaranteed present, as calling it directly
  inside the old raw listener. Only the wording describing *where* that call sits needed updating,
  not the guarantee itself.

## Related pages

- [Boss](../../frontiermode/architecture/boss.md) -- the concrete motivating consumer
- [Jig & Scope Runtime § MobJig](runtime.md#mobjig) -- the poll/interest machinery these signals
  ride on
- [MobScope.getFor() Contract](../spec/mobscope-getfor.md) -- the boundary contract `MobDied`'s
  handler calls into
- [Forge Event Conduit](forge-event-conduit.md) -- the broader, still-parked idea this narrows;
  carries the future public-payload-wrapper and single-shared-registry open items this page's
  design surfaced but didn't resolve
- [Satchel Health](satchel-health.md) -- the already-live consumer confirming why
  `MobGainedInterest`/`MobLostInterest` had to be additive, not a reroute
