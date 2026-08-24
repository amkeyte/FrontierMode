---
id: SAT_040
uid: SAT
number: 40
client: Satchel
status: done
title: Absorb LevelJig/PlayerJig health checks into SatchelHealth
context: SAT_039's MobJig slice landed in SatchelHealth (common/tracking/); LevelJig/PlayerJig
  health absorption was deliberately deferred, tracked here.
priority: normal
opened: '2026-08-23'
closed: '2026-08-24'
---

<!-- board:start -->
<!-- board:end -->

## Summary

[SatchelHealth](../wiki/satchel/architecture/satchel-health.md) (SAT_039, `common/tracking/`) is meant to be
the permanent home for every jig kind's run-monitoring/self-verification, not a MobJig-only
module. SAT_039's own build only lands the `MobJig` slice — widening its `sideApplicability` to
`BOTH` and adding the canary-mob regression check — because that was the ticket's single most
urgent gap. `LevelJig` (via the old `TrackingModule`) and `PlayerJig` (via
`PlayerTrackingModule`) still run as their own separate classes, unabsorbed, with no equivalent
"backing state still valid at teardown" violation check of their own.

## What this ticket is asking for

1. Relocate `TrackingModule`'s and `PlayerTrackingModule`'s registration/event-handler logic into
   `SatchelHealth`, same pattern SAT_039 already used for `MobTrackingModule` — same bundle/
   fixture/jig key IDs, no behavior change to what's already proven working.
2. Design and add each jig kind's own violation check, analogous to `onMobScopeUnloaded`'s
   `isRemoved()` check — what "torn down for a reason other than the two the jig's contract
   allows" concretely means differs per jig kind (`LevelJig` ties to dimension load/unload,
   `PlayerJig` to login/logout) and needs its own reasoning, not a copy-paste of MobJig's.
3. Retire `TrackingModule`/`PlayerTrackingModule` the same way SAT_039 retired
   `MobTrackingModule` (empty stub, or deleted outright if whoever picks this up has file-delete
   access).

## Not in scope

Any production-safety gate on the crash-on-violation behavior — SAT_039 left that as its own
explicit follow-up too; whoever builds one should cover all three jig kinds at once rather than
per-kind.

## Log

- 2026-08-23: Ticket opened, split out of SAT_039 at the project owner's direction (SatchelHealth
  is meant to absorb every jig kind's health checks, not just MobJig's) so SAT_039 itself could
  stay scoped to the one gap it was actually opened to close.
- 2026-08-23: **Built -- picked up at the project owner's direction once SAT_039's MobJig slice
  was confirmed live.** `TrackingModule` (`LevelJig`) and `PlayerTrackingModule` (`PlayerJig`)
  relocated into `SatchelHealth` verbatim (same registered bundle/fixture/jig IDs each already
  used, same logging behavior -- no behavior change), matching item 1 above. Item 2 (each kind's
  own violation check) built with its own reasoning rather than a copy of MobJig's, per the
  audit's own instruction:

  - **`LevelJig`**: `sideApplicability` widened `SERVER` -> `BOTH` (same treatment as `MobJig`,
    project owner's explicit call) -- Border (FrontierMode) already proves `BOTH` works for a
    `LevelJig` consumer, but that's not a Satchel-only verification, so it doesn't satisfy the
    "server/client pair running only Satchel" goal on its own. Because `LevelJig` teardown is
    *authoritative* (only ever called from `ServerForgeIngress#onLevelUnload`'s real
    `LevelEvent.Unload`), not *inferred* like `MobJig`'s poll-driven reconcile, "is the dimension
    actually gone" is trivially true by construction -- the check instead asks whether teardown
    fired while the dimension is still the server's actively-registered instance for that key.
    Server-side only: no canary needed (a `LevelScope` already exists continuously once a world is
    running), but also no client-side equivalent -- see the wiki page's "Known limitation" for why
    (would require touching `Minecraft.getInstance()` from common code, which this project
    reserves for `ClientForgeIngress` alone).
  - **`PlayerJig`**: stays `SERVER` -- audited and confirmed structurally server-only (`PlayerScope`
    wraps a `ServerPlayer` directly, no client-side existence to widen into), so unlike Mob/Level
    there was never a `CLIENT`/`BOTH` gap to close here, just a missing violation check. Same
    authoritative-teardown reasoning as `LevelJig`: checks whether the player is still in the
    server's online-player list at teardown time -- catches a future regression wiring
    `tryRemoveSource` to something that isn't a real logout (e.g. `PlayerChangedDimensionEvent`,
    which `ServerForgeIngress`'s own comment already flags as deliberately not hooked to this).

  `TrackingModule`/`PlayerTrackingModule` retired to empty stubs (item 3), same treatment SAT_039
  gave `MobTrackingModule`. `SatchelMod.java` updated to drop their direct `init()` calls --
  `SatchelHealth.init()` now registers all three jig kinds' health checks in one place. Wiki page
  updated to document all three mechanisms and the reasoning split above.

  **Not yet verified** -- same standing constraint as SAT_039: no Gradle/Forge network access in
  this session's sandbox, nothing here has been compiled or run. Also worth setting expectations
  correctly before that run: `LevelEvent.Unload` basically only fires on datapack reload or server
  shutdown, not routine gameplay, so `LevelJig`'s check won't get nearly as many chances to fire in
  an ordinary dev session as `MobJig`'s (constant reconcile churn) or `PlayerJig`'s (fires every
  logout) did.

- 2026-08-23: **Confirmed live, and confirmed broken: both violation checks false-positived on
  completely ordinary use.** First real run after the build above: `PlayerJig`'s check crashed the
  server on Dev's normal disconnect, and `LevelJig`'s check crashed it again seconds later on
  ordinary server shutdown -- both read directly from `run-server/logs/latest.log`. Root cause,
  same shape for both: Forge fires the relevant event (`PlayerLoggedOutEvent`,
  `LevelEvent.Unload`) *before* the corresponding removal actually completes (confirmed by the
  stack traces -- `PlayerList.remove()` fires the event mid-removal;
  `MinecraftServer.stopServer()` fires `LevelEvent.Unload` before deregistering the level). So "is
  the backing thing still there" was true at that exact instant on every legitimate teardown too,
  not just an illegitimate one -- the checks couldn't discriminate what they were built to
  discriminate. Not something static reading of the source could have caught; needed a real run,
  and it found it.

  **Fixed by deferring each check one server tick** (`MinecraftServer#execute(Runnable)`) rather
  than checking synchronously inside the `ScopeEvent.Unloaded` handler. By the next tick, a
  legitimate teardown's real removal has had time to finish; an actually-illegitimate one (nothing
  really disconnecting/unloading) leaves the player/level "present" indefinitely, not just for one
  tick -- that's what the deferred check now distinguishes on, rather than a same-instant snapshot
  that can't tell the two cases apart.

  **Known, accepted gap in the fix**: on final process shutdown specifically (not a mid-session
  `LevelEvent.Unload` from a datapack reload), the tick loop has already stopped by the time
  `LevelJig`'s check fires, so the deferred task may never run at all there -- silently unable to
  verify that one case, rather than firing wrongly the way it did before. Given
  `LevelEvent.Unload` was already documented as mostly a shutdown-only event, this narrows an
  already-rare firing opportunity further; accepted rather than solved this pass.

  **Still not re-verified** -- this fix itself hasn't been run yet. Same loop as always: project
  owner runs it, reports back.

- 2026-08-24: **Confirmed broken a second time: the "deferred one tick" fix from 2026-08-23 never
  actually deferred anything.** Fresh logs from the very next run show both violation checks
  firing again, for the identical reason -- ordinary disconnect, ordinary shutdown -- at the same
  log timestamp granularity as before (no observable tick delay). The stack traces this time
  point straight at why: `SatchelHealth.lambda$onPlayerScopeUnloaded$14` /
  `lambda$onLevelScopeUnloaded$11` were invoked via
  `BlockableEventLoop.execute(BlockableEventLoop.java:90)`, synchronously, inside the exact same
  call chain as the un-deferred version -- `PlayerList.remove()` / `MinecraftServer.stopServer()`
  straight through to the check, no gap.

  Root cause: `BlockableEventLoop#execute(Runnable)` (which `MinecraftServer` inherits, and which
  `server.execute(...)` calls into) only actually queues a task for later when called from a
  thread that does *not* already own the loop. Called from the thread that *does* own it -- which
  the server thread always is here, since `PlayerList.remove()` and `MinecraftServer.stopServer()`
  both already run on it -- it just runs the task immediately, inline. So the previous fix wrapped
  the exact same synchronous check in a lambda and called it deferred; it wasn't. Flagged to the
  project owner directly rather than silently attempting a third fix -- this is the second failed
  attempt at the same feature, which felt like it warranted a check-in before continuing. Owner's
  direction: keep trying for a real deferral rather than reverting.

  **Second fix: a genuine pending-check queue, drained from a real subsequent tick.** Added
  `SatchelHealth.PENDING_HEALTH_CHECKS` (a plain `ArrayDeque<Runnable>` -- single-threaded by
  construction, since both the enqueue side and the drain side only ever run on the server thread)
  and `SatchelHealth.pumpPendingHealthChecks()`, which drains it. Both violation checks now
  `PENDING_HEALTH_CHECKS.add(...)` their check instead of calling `server.execute(...)`. The
  queue is drained from `ServerForgeIngress#onExecutionPulse` -- the mod's existing
  `TickEvent.ServerTickEvent` (`Phase.END`) hook, already firing once per real server tick, now
  also calling `SatchelHealth.pumpPendingHealthChecks()` unconditionally, ahead of the
  `isReady()` gate. This is a genuinely later call chain than the one that enqueued the check --
  not the same synchronous unwind wearing a lambda -- so for an ordinary logout,
  `PlayerList#remove` has long since finished removing the player from its own lists by the time
  the next pulse runs and actually drains the queue.

  For shutdown specifically this also resolves the previously-accepted gap, not just papers over
  it: the server's tick loop has already stopped running by the time `MinecraftServer#stopServer()`
  fires the remaining `LevelEvent.Unload`/`PlayerLoggedOutEvent`s, so the queue simply never gets
  pumped again before the process exits and those particular checks are silently abandoned. That's
  the *correct* outcome here, not a limitation to accept: nothing is left running to observe a
  stale registration once the whole server is going away, so there's nothing real for the check to
  catch in that case anyway.

  Wiki page's `LevelJig`/`PlayerJig` mechanism sections updated to match this design instead of
  the disproven "deferred by one server tick" explanation.

  **Still not re-verified** -- this is now the third distinct version of these two checks (naive
  synchronous -> `server.execute()` -> queue-and-pump) and only the first was ever actually run
  live. Same loop as always: project owner runs it, reports back.

- 2026-08-24: **Confirmed live -- both checks verified working.** Two real runs, read directly
  from `run-server/logs/latest.log` and `run/logs/latest.log`:

  `PlayerJig`: Dev logged in and disconnected normally (`21:54:11`). `PlayerTracking UNLOADED`
  logged, queued check ran on the next tick pulse, found nothing wrong, stayed silent -- no
  exception, server kept ticking. First time this check has survived an ordinary logout.

  `LevelJig`: server stopped cleanly a few minutes later (`21:56:16`) -- "Stopping the server" ->
  players/worlds saved -> "All dimensions are saved" -> log ends there. No violation, no
  exception, no new crash report. This is the predicted shutdown outcome, not an absence of
  testing: the tick loop stops before `LevelEvent.Unload` fires for the remaining levels, so
  `pumpPendingHealthChecks()` never runs again and the queued check is silently abandoned rather
  than firing at all -- exactly the "correct because there's nothing left to observe" case
  described in the previous entry and in the wiki page.

  `MobJig` also fired correctly in the same session (client log, `21:54:10`), catching the real
  RM_SAT_022 latent bug it was designed to catch -- confirmed not a regression from any of this
  work.

  SAT_040's build is done and verified across all three jig kinds. Status left at the project
  owner's discretion to formally close, same as SAT_039.

- 2026-08-24: **Done -- project owner's own call to close, after both checks were confirmed
  live.** `LevelJig` and `PlayerJig` are now on equal footing with `MobJig` inside
  `SatchelHealth`: relocated tracking logic, a violation check reasoned from each jig kind's own
  teardown model (inferred vs. authoritative), and both checks proven against real runs rather
  than left as compiled-but-unverified code. Took two failed deferral attempts to get there
  (naive synchronous check, then a `server.execute()` wrapper that didn't defer anything either)
  before landing on the genuine pending-queue-and-pump mechanism that's in place now -- logged
  honestly at each step rather than smoothed over, per this repo's own standing convention.

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
