---
id: SAT_049
uid: SAT
number: 49
client: Satchel
status: open
title: SatchelFixture.onJigTick() is never actually invoked -- dead per-fixture tick
  dispatch
context: 'Discovered while building RM_FRO_037 (FrontierMode''s Sick Wildlife). SatchelBundle.onJigTick()
  correctly loops fixtures.values() and calls fixture.onJigTick() (code reads right),
  and the per-tick driver chain (FoundationLifecycleDispatcher.pulse() -> jig.onTick(info)
  -> coupler().onJigTick(info) -> engine().onJigTick(info)) also reads right on paper
  -- but in a real play session it never actually reaches any fixture. Proof: Satchel''s
  own TrackerFixture (com.arryn.satchel.common.newconfig.TrackerFixture) increments
  internalTicks only from onJigTick() and externalTicks from a separate countExternalTick()
  call; after a session that logged externalTicks=2708 on unload, internalTicks stayed
  at 0. This isn''t isolated to FrontierMode''s new code -- BorderPregenFixture (FrontierMode,
  LevelJig-scoped) relies on the exact same dispatch and has apparently never once
  ticked either: zero "[BorderPregen]" log lines exist anywhere across this world''s
  whole play history despite 7 borders on record needing pregeneration. Suspect the
  break is somewhere between engine().onJigTick(info)''s bundle sweep and it actually
  being reached per real scope -- possibly bundlesFor(info) never getting populated
  the way onJigTick''s sweep expects, a lifecycle-state check silently continuing
  past every bundle, or FoundationLifecycleDispatcher.pulse()''s jInfo.scopeInfos()
  not including the scopes onJigTick''s sweep needs. FrontierMode worked around this
  locally for RM_FRO_037 (ExteriorTellFixture is now driven by a direct manual call
  from BorderModule.onPlayerScopeTick, same pattern BorderPlayerStatusFixture already
  used), but BorderPregenFixture has no such workaround yet and its whole pregeneration
  pipeline may be silently non-functional in real play. Recommend instrumenting the
  dispatch chain (or reproducing with TrackerFixture in isolation) to find exactly
  where internalTicks stops incrementing.'
priority: high
opened: '2026-09-08'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

SatchelFixture.onJigTick() is never actually invoked -- dead per-fixture tick dispatch

## Log

- 2026-09-09: ## Parking this ticket

  Drafted and verified a patch to ScopeEngine_Server.hydrateBundle() that would always call
  bundle.hydrateFrom(...) (from an explicitly empty source when persistence isn't required)
  instead of skipping the call entirely -- this would have closed the specific symptom
  (bundle stuck at CREATED forever for any jig that doesn't declare requiresPersistence(true)).

  Project owner stopped this before it shipped: routing lifecycle promotion to ACTIVE through
  persistence-hydration machinery, for a jig that has no persistence need at all, is the
  actual defect here, not just an implementation gap to patch around -- "If we're depending
  on hydrating a non-persistable object to be hydrated, that's a deeper problem than just a
  bug fix." The patch was reverted (git checkout on ScopeEngine_Server.java; working tree
  confirmed clean of that change).

  Parking SAT_049 here. Root cause is confirmed, reproduced against real logs, and fully
  documented in this ticket's log history -- the symptom is understood, but the fix is
  deferred to a proper architecture pass rather than a targeted patch. Follow-up:
  SAT_053 (redesign of engine-level lifecycle promotion / hydration coupling, plus fallout
  across every jig on ScopeEngine_Server). That work will happen on its own branch
  (satchel/SAT_053-lifecycle-redesign) so current playtesting can continue undisturbed on
  existing workarounds (e.g. ExteriorTellFixture's manual-call bypass of this exact path,
  noted earlier in this ticket).
- 2026-09-09: ## Root cause found (source-level trace, not just log symptoms)

  Traced the full dispatch chain from FoundationLifecycleDispatcher.pulse() down through
  ASatchelJig -> AScopeCoupler -> ScopeEngine_Server for the exact jigs SatchelHealth
  registers (MOB_JIG / LEVEL_JIG / PLAYER_JIG). Confirmed this is not stale/duplicate code --
  SatchelHealth.java is the only registrant of TrackerFixture (grep -rln "TrackerFixture"
  across all of Satchel/src/main/java returns only TrackerFixture.java and SatchelHealth.java).
  The old "absorbed" modules (MobTrackingModule/TrackingModule/PlayerTrackingModule, still
  present under common/newconfig/) do not independently instantiate a competing TrackerFixture.

  The actual bug is a gap in SatchelHealth's own jig registration:

  - registerMobHealthCheck() / registerLevelHealthCheck() / registerPlayerHealthCheck()
    all call config.execution().lifecycle(JigPolicies.Lifecycle.defaults().withTick(true))
    but never call .capabilities(...) to set requiresPersistence(true). They inherit
    JigPolicies.Capabilities.defaults(), which is requiresPersistence=false.

  - On the server side, ScopeEngine_Server.hydrateBundle() only calls
    bundle.hydrateFrom(...) when resolveServerLevel(info, CapableOf.PERSISTENCE) returns
    a non-null ServerLevel. resolveServerLevel() explicitly returns null whenever
    capabilities().requiresPersistence() is false ("capability not required -> skip").
    So for every SatchelHealth jig, hydrateFrom() is simply never invoked.

  - bundle.isHydrated() therefore never becomes true, so the
    "if (!hydratedBefore && bundle.isHydrated()) { bundle.onLoaded(); ... }" check in
    hydrateBundle() never fires -- and SatchelBundle.onLoaded() is the ONLY code path
    that transitions a bundle from HYDRATED -> LOADED -> ACTIVE.

  - ScopeEngine_Server.onJigTick()'s per-bundle loop skips anything that isn't ACTIVE:
    "if (bundle == null || bundle.lifeCycleState() != LifecycleState.ACTIVE) continue;"
    So the bundle sits at CREATED forever, fixture.onJigTick() never runs, and
    TrackerFixture.internalTicks never increments -- exactly matching every log observed
    (loads=1, unloads=1, externalTicks=N, internalTicks=0).

  - This is silent by design, not a masked exception: LifecycleGuard only throws on an
    explicit require/transition call, and the code here just never attempts the
    transition in the first place.

  - The parallel safety net is also dead for the same reason: SatchelBundle.healthCheckPulse()
    (the RM_SAT_013 "stuck at non-ACTIVE" diagnostic) is only invoked from
    ScopeEngine_Server.onExecutionPulse(), which is gated by the separate
    participatesInExecutionPulse flag. SatchelHealth never calls .withExecutionPulse(true)
    either (only .withTick(true)), so onExecutionPulse() is never reached and the
    health-check warning never fires. That's why nothing was ever logged about this.

  ## Design contradiction underneath the bug

  Two comments in the engine directly disagree with each other:

  - ScopeEngine_Server.hydrateBundle(): "resolveServerLevel only returns null when
    persistence isn't required for this jig... A null here means 'nothing to hydrate,'
    not an error" (implies skipping hydrate is safe).
  - SatchelBundle.hydrateFrom() javadoc: "the bundle must leave CREATED to ever reach
    ACTIVE, since saveAll()/onJigTick() both require it" (implies skipping hydrate is fatal).

  Both are true as written, and together they mean any jig that doesn't opt into
  requiresPersistence(true) can never reach ACTIVE at all, regardless of whether it
  actually needs persistence. This engine is shared (ScopeEngine_Server), so the same trap
  applies to any other jig on the same pattern, not just SatchelHealth's -- worth flagging
  since BorderPregenFixture (FrontierMode) sits on the same engine.

  ## Conclusion

  Not old/misconfigured code running in parallel -- SatchelHealth is the sole owner of
  TrackerFixture, and its own registration is incomplete: it never declares a capability
  that the engine silently requires in order to ever reach ACTIVE. Ticket stays open; a fix
  needs a design decision (see below) rather than a one-line patch.

  Candidate fix directions (not yet chosen/implemented):
  1. SatchelHealth explicitly declares requiresPersistence(true) for its jigs -- cheapest,
     but arguably papers over the deeper issue (a jig with no real persistence need would be
     forced to declare one just to become active).
  2. ScopeEngine_Server.hydrateBundle() treats "persistence not required" as "hydrate from
     an empty/no-op source" instead of "skip hydrateFrom entirely" -- so isHydrated() still
     becomes true and onLoaded()/ACTIVE still happens for jigs that genuinely don't need
     persistence. This resolves the contradiction generally (fixes it for any future jig with
     the same shape, not just SatchelHealth's) but touches the shared engine.

  Leaning toward (2) since this is an engine-level correctness bug, not a SatchelHealth-only
  misconfiguration -- but flagging both since (2) touches code FrontierMode's
  BorderPregenFixture also depends on.
- 2026-09-09: Checked whether this could be closed on the theory that a stale (non-reset) server was the
  real cause and a fresh world would show onJigTick() dispatch working. It doesn't hold up.

  FrontierMode/run-server/logs debug.log, session 04:49:20-04:51:24 (fresh post-reset world,
  clean unload -- not a crash/kill):

    [Tracking] UNLOADED LevelScope[minecraft:overworld]...  TrackerFixture{loads=1, unloads=1, externalTicks=2149, internalTicks=0}
    [Tracking] UNLOADED LevelScope[minecraft:the_end]...    TrackerFixture{loads=1, unloads=1, externalTicks=2149, internalTicks=0}
    [Tracking] UNLOADED LevelScope[minecraft:the_nether]... TrackerFixture{loads=1, unloads=1, externalTicks=2149, internalTicks=0}

  externalTicks=2149 (~2 minutes loaded) across all three dimensions, internalTicks still 0 --
  identical shape to this ticket's original finding. onJigTick() never fired once on a fresh
  world either. Whatever the user observed "working normally" after their reset, it wasn't this
  dispatch chain -- most likely the unrelated SAT_052 reobf/build fix (NoSuchMethodError on
  vanilla methods, a completely different bug), or something riding on ExteriorTellFixture's
  existing manual-call workaround, which bypasses this exact path by design and would look fine
  regardless. Not closing. Still needs the instrumentation this ticket originally recommended.
- 2026-09-08: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
