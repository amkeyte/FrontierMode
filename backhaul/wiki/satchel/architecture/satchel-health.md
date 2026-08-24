---
id: satchel/architecture/satchel-health
category: satchel/architecture
slug: satchel-health
title: SatchelHealth
summary: Satchel's run-monitoring / self-verification home -- live regression checks
  for MobJig, LevelJig, and PlayerJig, checked every real client/server run rather
  than gated behind gradle test.
keywords: null
status: draft
updated: '2026-08-24'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · satchel / architecture
<!-- bh-header:end -->

# SatchelHealth

`common/tracking/SatchelHealth.java` is the home for Satchel's run-monitoring and self-
verification modules: live checks that a major service each jig kind provides actually still
works, run every time a real client/server session runs rather than gated behind `gradle test`.
That's a deliberate trade against build-time (JUnit/GameTest) coverage: it reaches a live entity
resolution path JUnit-without-Forge can't, at the cost of only catching a regression on a session
where someone actually connects a client, not on every commit.

Absorbs what used to be separate, single-purpose tracking modules (`TrackingModule`,
`PlayerTrackingModule`, `MobTrackingModule` -- all now retired, empty stubs) into one home, rather
than one throwaway diagnostic class per jig kind. Covers all three jig kinds Satchel has:
`MobJig`, `LevelJig`, `PlayerJig`.

`MobJig`'s teardown is *inferred* -- a poll-driven reconcile cycle (~every 20 ticks) decides a
scope is gone based on whether the backing entity can still be resolved, which is exactly the kind
of decision that can be wrong (see [Jig & Scope Runtime](runtime.md#mobjig) and RM_SAT_022).
`LevelJig` and `PlayerJig`'s teardown is *authoritative* -- `ServerForgeIngress`/
`ClientForgeIngress` call `tryRemoveSource` from exactly one real Forge event each
(`LevelEvent.Unload`, `PlayerLoggedOutEvent`), never inferred. That difference is why each jig
kind's violation check has its own reasoning below rather than one shared check applied three
times.

## Mechanism -- MobJig

No new raw Forge touch points -- everything rides `ScopeEvent`, the same mechanism every other
tracking module already uses.

A `LevelJigConfig` registered `BOTH` (`COORDINATOR_JIG`) is the always-on coordinator: it gets a
real `ScopeEvent.Tick` on both sides for the overworld regardless of whether anything is scoped
under `MobJig` yet, which `MobJig` itself has no side-agnostic entry point to provide (its own
`reconcile()` only runs once something is already registered). Server-side, on the overworld
`LevelJig` scope loading, the coordinator finds or spawns one tagged, harmless canary (a vanilla
`Bat`, referenced only via `EntityType.BAT` and handled as a plain `Mob` -- the class never
imports or type-names `Bat` itself, so swapping the species later is a one-line change) at the
level's real shared spawn point (`Level#getSharedSpawnPos()`, not a guessed coordinate -- an
earlier hardcoded anchor sat roughly 130 blocks from where a real player actually joined on a test
run, so the client-side half never got exercised at all), tagged `setNoAi`/`setInvulnerable`/
`setPersistenceRequired` so it's never killable or wanders off, and registers it through the
normal interest surface (`SatchelHealth.watch`/`unwatch`). Search radius is 16 blocks around that
point, wide enough to cover vanilla's default `spawnRadius` gamerule (10 blocks). Client-side,
throttled on the coordinator's own tick, it scans for the same tagged canary by custom name and
calls `MobScope.getFor()` on it directly -- the documented fast-path attachment -- to get a
client-side `MobScope` onto `MobJig`'s machinery without needing a client-side
interest-registration mechanism (that's `MobEntityLookup`'s job, see
[Jig & Scope Runtime](runtime.md#mobjig)).

The check itself lives in the `MOB_JIG` config's own `ScopeEvent.Unloaded` handler: a `MobScope`
whose backing `Mob.isRemoved()` is still `false` at teardown time has been torn down for a reason
other than the two `MobJig`'s reason-agnostic contract allows (chunk unload, genuine removal --
both set `isRemoved()` true by construction). That's a direct violation, not an inference from log
lines, and it throws -- unconditionally today, on both sides. No dev/production gate exists yet to
make that safe to ship live; building one is its own future scope, not attempted here.

Confirmed live on a real client+server run (2026-08-23): the canary spawned at the real shared
spawn point, the client discovered it and attached a `MobScope`, and the very next reconcile cycle
tore that scope down while the mob was still present -- exactly the RM_SAT_022 latent bug, caught
and crashed loudly as designed.

**Known limitation:** entity tracking to a given client requires that client's player be within
tracking range of the canary, not merely that its chunk is server-loaded. Standing near world
spawn while connected is what actually exercises the client-side half -- the coordinator can't
force that from server-side alone without a second mechanism (forced chunk-loading does not imply
per-player entity tracking).

## Mechanism -- LevelJig

Relocated from `TrackingModule` verbatim (same `satcheltracker:tracker_*` bundle/fixture/jig IDs,
same debug-level `[Tracking] LOADED`/`UNLOADED`/tick-count logging -- no behavior change), with
`sideApplicability` widened `SERVER` -> `BOTH`. FrontierMode's Border already proves `BOTH` works
for a `LevelJig` consumer, but Border lives in FrontierMode, not Satchel -- widening
`TrackingModule`'s own config is what lets a server/client pair running *only* Satchel prove
`LevelJig`'s client-side lifecycle path works, without depending on Border to be the only thing
that's ever exercised it.

Because `LevelJig` teardown is authoritative rather than inferred, "is the dimension actually
gone" is trivially true given how teardown is wired today -- the violation check instead asks "did
teardown get triggered while the dimension is still the server's actively-registered instance for
that key" (`MinecraftServer#getLevel(dimension) == thisLevel`). That catches a future regression
that calls `tryRemoveSource` from somewhere other than `ServerForgeIngress#onLevelUnload`'s real
`LevelEvent.Unload` handler (a stray extra hook, a stale-reference bug like RM_SAT_014's).

The check doesn't run synchronously inside the `ScopeEvent.Unloaded` handler -- Forge fires
`LevelEvent.Unload` before the level is actually deregistered, so a same-instant check reads
"still registered" on every legitimate unload too, not just an illegitimate one. Two fixes were
tried here before landing on one that actually works: wrapping the check in
`MinecraftServer#execute(Runnable)` looked like a one-tick deferral but wasn't -- `execute()` runs
its task inline whenever called from the thread that already owns the loop, which the server
thread always is on this call path, so it false-positived identically to the un-deferred version.
The check now instead lands on `SatchelHealth.PENDING_HEALTH_CHECKS` (a plain queue) and is
drained by `SatchelHealth.pumpPendingHealthChecks()`, called once per real server tick from
`ServerForgeIngress#onExecutionPulse` -- a genuinely later call chain than the one that queued it.
By the time that pulse runs, a legitimate unload's real deregistration has long since finished; an
actually-illegitimate teardown leaves the level registered indefinitely, not just for one pulse --
that's what the queued check distinguishes on. On final process shutdown specifically, the tick
loop has already stopped by the time `LevelEvent.Unload` fires for the remaining levels, so the
queue never gets pumped again and the check is silently abandoned there -- the correct outcome,
not a gap: nothing is left running to observe a stale registration once the server is going away
anyway. Confirmed live 2026-08-24: an ordinary shutdown produced no violation and no exception,
matching this design exactly.

**Known limitation:** this check is server-side only. The client-side equivalent would need
`Minecraft.getInstance().level`, and per the Forge Integration & Sidedness Contract,
`ClientForgeIngress` is the only class meant to touch client-engine state directly -- adding that
reference into `SatchelHealth` would cross that boundary for a check whose value hasn't been
weighed against the cost. `LevelJig`'s config is still `BOTH`, so the client-side load/tick/unload
path is genuinely exercised -- an exception anywhere in it still crashes loudly -- just not this
specific violation check.

Also worth noting: `LevelEvent.Unload` basically only fires on datapack reload or server shutdown,
not routine gameplay, so this check gets far fewer chances to fire in an ordinary dev session than
`MobJig`'s (constant reconcile churn) or `PlayerJig`'s (fires every logout).

## Mechanism -- PlayerJig

Relocated from `PlayerTrackingModule` verbatim (same `satcheltracker:player_tracker_*` IDs, same
info-level `[PlayerTracking] LOADED`/`UNLOADED`/`TICK` logging -- no behavior change).
`sideApplicability` stays `SERVER` -- unlike `MobJig`/`LevelJig`, there's no client-side gap to
close here: `PlayerScope` wraps a `ServerPlayer` directly and has no client-side existence at all
(see `PlayerScope`'s own class docs).

Same authoritative-not-inferred reasoning as `LevelJig`: the violation check asks whether the
player is still present in the server's own online-player list
(`MinecraftServer#getPlayerList().getPlayer(uuid) != null`). That catches a future regression
wiring `tryRemoveSource` to something that isn't a real logout -- `PlayerChangedDimensionEvent` in
particular, which `ServerForgeIngress`'s own comment already flags as deliberately *not* hooked to
teardown (an easy mistake for someone to reintroduce later without this check in place).

Same queue-and-pump deferral as `LevelJig`, for the same reason: `PlayerList#remove(ServerPlayer)`
fires `PlayerLoggedOutEvent` before actually removing the player from the online list, so a
same-instant check false-positived on every ordinary logout, and the first fix attempt
(`MinecraftServer#execute(Runnable)`) turned out not to defer anything either -- see the `LevelJig`
section above for why. The check now lands on the same `PENDING_HEALTH_CHECKS` queue and is drained
on the next real server tick, by which point `PlayerList#remove` has long since finished removing
the player from its own lists for an ordinary logout. A real illegitimate teardown leaves the
player online indefinitely, not just for one pulse -- that's what the queued check distinguishes
on. Confirmed live 2026-08-24: an ordinary logout produced no violation and no exception.

## `sideApplicability` by jig kind

- `MobJig`: `BOTH`, not `SERVER` (SAT_039) -- exercises `MobJig`'s client-side reconciliation path
  at all, which nothing in either repo previously did (see
  [Jig & Scope Runtime](runtime.md#mobjig) for the poll mechanism this exercises, and
  [MobScope.getFor() Contract](../spec/mobscope-getfor.md) for the fast-path attachment the
  client-side canary discovery uses).
- `LevelJig`: `BOTH`, not `SERVER` (SAT_040) -- exercises `LevelJig`'s client-side lifecycle path
  independent of FrontierMode's Border.
- `PlayerJig`: `SERVER` (unchanged) -- no client-side existence to widen into.

## Related pages

- [Jig & Scope Runtime](runtime.md) -- the poll/reconciliation machinery `MobJig`'s check exercises
- [MobScope.getFor() Contract](../spec/mobscope-getfor.md)
- [Satchel mod summary](../satchel.md)
