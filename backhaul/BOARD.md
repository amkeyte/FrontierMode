<!-- bh-header:start -->
**mcRepos** — [Dashboard](../BACKHAUL.md)
<!-- bh-header:end -->

# Work Board

## open

| Ticket | Client | Pri | Title | Context | Edit |
|---|---|---|---|---|---|
| [BKHL_018](tickets/BKHL_018_role-new-missing-template.md) | Backhaul | normal | bhrole new: missing role.md.tmpl in package | bhrole new crashes: FileNotFoundError, roles/templates/role.md.tmpl not shipped in the installed package. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/BKHL_018_role-new-missing-template.md) |
| [BKHL_019](tickets/BKHL_019_cowork-windows-sandbox-silent-plan9-moun.md) | Backhaul | normal | Cowork Windows sandbox: silent Plan9 mount failure | KB5124008 breaks Hyper-V Plan9 share-attach; VM boots clean but shell has no mounted shares. See ticket body. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/BKHL_019_cowork-windows-sandbox-silent-plan9-moun.md) |
| [FRO_027](tickets/FRO_027_known-failed-commands-running-list-not-b.md) | FrontierMode | low | Known-failed commands (running list, not being worked) | Running list of border commands found broken by playtest; not being triaged, per owner's call. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_027_known-failed-commands-running-list-not-b.md) |
| [FRO_031](tickets/FRO_031_betty-donebar.md) | FrontierMode | low | Betty resolved with 2 unconfirmed items | RM_FRO_011's own log leaves an @none tab-complete check and an add-rejection retest unconfirmed. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_031_betty-donebar.md) |
| [FRO_048](tickets/FRO_048_pathgrow-no-boss.md) | FrontierMode | low | Path grow doesn't create paired boss | [Susan_02] Manual path grow doesn't create a paired boss record, unlike automatic bootstrap grow. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_048_pathgrow-no-boss.md) |
| [FRO_051](tickets/FRO_051_border-load-count-mismatch.md) | FrontierMode | normal | Border load count: client vs server | [Susan_02] Client BordersFixture load count (1) mismatches server's (0) on same fresh world. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_051_border-load-count-mismatch.md) |
| [FRO_061](tickets/FRO_061_boss-spawned-selector-delete-despawns-it.md) | FrontierMode | low | Boss: delete despawns its mob | [Susan_02] /boss delete should despawn its mob, not orphan it -- despawn only. See body. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_061_boss-spawned-selector-delete-despawns-it.md) |
| [FRO_068](tickets/FRO_068_ambient-difficulty-unowned.md) | FrontierMode | normal | Ambient mob difficulty scaling unowned | Design promises normal-mob difficulty scaling; nothing wires layerToDifficulty to mob stats yet. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_068_ambient-difficulty-unowned.md) |
| [FRO_097](tickets/FRO_097_exterior-design-ready.md) | FrontierMode | normal | New design ready: Exterior (Frontier Sickness, Feral) | Exterior design (Frontier Sickness, Feral) ready. 3 open Qs for Architect -- see Summary. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_097_exterior-design-ready.md) |
| [FRO_099](tickets/FRO_099_frontier-sickness-core-build.md) | FrontierMode | normal | Frontier Sickness core build | Dev(FrontierMode) build for RM_FRO_037 -- also fixes a live distanceToSurface bug. See body. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/FRO_099_frontier-sickness-core-build.md) |
| [SAT_049](tickets/SAT_049_satchelfixture-onjigtick-is-never-actual.md) | Satchel | high | SatchelFixture.onJigTick() is never actually invoked -- dead per-fixture tick dispatch | Discovered while building RM_FRO_037 (FrontierMode's Sick Wildlife). SatchelBundle.onJigTick() correctly loops fixtures.values() and calls fixture.onJigTick() (code reads right), and the per-tick driver chain (FoundationLifecycleDispatcher.pulse() -> jig.onTick(info) -> coupler().onJigTick(info) -> engine().onJigTick(info)) also reads right on paper -- but in a real play session it never actually reaches any fixture. Proof: Satchel's own TrackerFixture (com.arryn.satchel.common.newconfig.TrackerFixture) increments internalTicks only from onJigTick() and externalTicks from a separate countExternalTick() call; after a session that logged externalTicks=2708 on unload, internalTicks stayed at 0. This isn't isolated to FrontierMode's new code -- BorderPregenFixture (FrontierMode, LevelJig-scoped) relies on the exact same dispatch and has apparently never once ticked either: zero "[BorderPregen]" log lines exist anywhere across this world's whole play history despite 7 borders on record needing pregeneration. Suspect the break is somewhere between engine().onJigTick(info)'s bundle sweep and it actually being reached per real scope -- possibly bundlesFor(info) never getting populated the way onJigTick's sweep expects, a lifecycle-state check silently continuing past every bundle, or FoundationLifecycleDispatcher.pulse()'s jInfo.scopeInfos() not including the scopes onJigTick's sweep needs. FrontierMode worked around this locally for RM_FRO_037 (ExteriorTellFixture is now driven by a direct manual call from BorderModule.onPlayerScopeTick, same pattern BorderPlayerStatusFixture already used), but BorderPregenFixture has no such workaround yet and its whole pregeneration pipeline may be silently non-functional in real play. Recommend instrumenting the dispatch chain (or reproducing with TrackerFixture in isolation) to find exactly where internalTicks stops incrementing. | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/SAT_049_satchelfixture-onjigtick-is-never-actual.md) |
| [SAT_051](tickets/SAT_051_architect-satchel-health-md-stale-canary.md) | Satchel | normal | [Architect] satchel-health.md stale -- canary/coordinator removed (SAT_050) | [Architect] backhaul/wiki/satchel/architecture/satchel-health.md documents SAT_039's MobJig canary (a tagged vanilla Bat + BOTH-applicability LevelJig coordinator, spawned/discovered via ScopeEvent.Tick) as "Confirmed live on a real client+server run" and describes it in the same detail as SatchelHealth.java's own class javadoc did. SAT_050 removed that mechanism from the code entirely (COORDINATOR_* keys, registerCoordinator(), onCoordinatorTick, isCanary, scanForCanaryOnClient, findOrSpawnCanary, anchorPos, searchBoxAround, CANARY_TYPE/CANARY_NAME/CANARY_SEARCH_RADIUS/CLIENT_SCAN_EVERY_N_TICKS) after it caused a NoSuchFieldError: BAT class-load crash on a second dev machine -- the CANARY_TYPE = EntityType.BAT field was evaluated eagerly at class-init, so no runtime flag could have neutralized it; the reference had to be deleted. MobJig's own BOTH-applicability widening and onMobScopeUnloaded's RM_SAT_022 violation check are unchanged and still fire for any real MobScope reaching teardown with its backing mob present -- there is simply no longer a synthetic entity guaranteeing that path gets exercised on every run; coverage now depends on real gameplay putting a mob under MobJig (FrontierMode/Border consumers, chat-command watch()). This is a real regression from 'continuously self-verified' to 'verified only when something happens to scope a mob' for MobJig's CLIENT/BOTH path -- worth an explicit call on whether that coverage gap needs a replacement mechanism (a non-vanilla-entity-typed canary? a dev-only debug command that spawns one on demand? accepting FrontierMode's Border mob traffic as sufficient real-world coverage?) or whether it's fine as-is now that MobJig's BOTH widening itself is settled, mature code rather than the freshly-built thing SAT_039 needed to prove. Not edited directly per role boundaries (wiki is the Architect's); SatchelHealth.java's own class javadoc has already been updated to describe current state, so that half is stitch-ready. See SAT_050's log for the full before/after and the crash's root-cause writeup (not fully diagnosed -- traced to the other machine's build/runtime, not this repo's config, which is identical across both mods and unchanged in git). | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/SAT_051_architect-satchel-health-md-stale-canary.md) |
| [SAT_053](tickets/SAT_053_redesign-engine-level-lifecycle-promotio.md) | Satchel | high | Redesign engine-level lifecycle promotion / hydration coupling (SAT_049 root cause) + fallout | ## Background

SAT_049 (satchel run tick never actually fires for SatchelHealth's tracker bundles) was
root-caused to: ScopeEngine_Server.hydrateBundle() only calls bundle.hydrateFrom(...) --
the ONLY code path that ever transitions a SatchelBundle out of CREATED (CREATED ->
HYDRATED, then onLoaded() takes it HYDRATED -> LOADED -> ACTIVE) -- when the jig's
JigPolicies.Capabilities declares requiresPersistence(true). Any jig that doesn't need
persistence (SatchelHealth's MOB_JIG/LEVEL_JIG/PLAYER_JIG among them) never gets
hydrateFrom() called at all, so it sits at CREATED permanently. ScopeEngine_Server.onJigTick()
skips any bundle that isn't ACTIVE, so ticking is silently dead for every such bundle,
forever, with no exception and no warning (the RM_SAT_013 stuck-bundle diagnostic is
itself gated behind a different flag, participatesInExecutionPulse, that also isn't set --
two independent silent failures stacked on each other). Full trace is in SAT_049's log
entries.

A targeted patch (always hydrateFrom() an explicitly-empty source when persistence isn't
required, so lifecycle promotion no longer depends on persistence being needed) was
drafted and verified against current code, but was explicitly stopped before shipping.

## The actual problem this ticket is for

Lifecycle promotion to ACTIVE is currently coupled to persistence hydration -- a bundle
can only ever leave CREATED via the same method (hydrateFrom) that's meant for loading
real saved data. That coupling is itself the defect: a jig with no persistence
requirement shouldn't need to go through a "hydrate from nothing" fiction just to become
tickable. This has already recurred once before under a different gap (SAT_027 -- see
the historical note in SatchelBundle.hydrateFrom()'s own javadoc), which suggests the
coupling itself is the wrong shape, not that we keep missing edge cases in an otherwise
correct design.

There's also a live internal contradiction in ScopeEngine_Server's own comments:
resolveServerLevel()'s null-return is documented as "nothing to hydrate, not an error"
(implying skip is safe) while SatchelBundle.hydrateFrom()'s javadoc says "the bundle must
leave CREATED to ever reach ACTIVE" (implying skip is fatal). Both are true as currently
written -- that's the bug.

## Scope

This needs an actual architecture pass on ScopeEngine_Server (and whatever of
ASatchelJig / AScopeCoupler / JigPolicies needs to change alongside it), not a patch:
- What should actually gate a bundle's promotion to ACTIVE, decoupled from whether it
  requires persistence, networking, or clock? (Likely candidates: a dedicated
  "activate"/"ready" step separate from hydration, or splitting "hydration" itself into
  a real optional capability vs. an unconditional lifecycle step.)
- Does participatesInTick vs. participatesInExecutionPulse (two independently-gated
  flags) still make sense once promotion no longer depends on capabilities()? Right now
  a jig can tick without ever running an execution pulse, which is also what silenced
  the RM_SAT_013 stuck-bundle warning for this exact case.
- Fallout: ScopeEngine_Server is a single per-side singleton shared by every jig (see its
  own SAT_023 comment), so whatever shape this lands on affects every jig on it -- not
  just SatchelHealth's. FrontierMode's BorderPregenFixture rides the same engine and
  needs to be checked against whatever the new contract is once designed.
- SAT_049 stays open/parked pointing at this ticket; its root cause is fully understood
  and reproducible, only the fix is deferred here.

## Not in scope here

Actually implementing the fix -- this ticket is the design/redesign work. Implementation
follows once the shape is agreed, on its own branch (satchel/SAT_053-lifecycle-redesign),
kept separate from the working tree other playtesting is currently using (which still
carries the SAT_049 workarounds, e.g. ExteriorTellFixture's manual-call bypass). | [Edit](editmd:///C:/_local/mcRepos/backhaul/tickets/SAT_053_redesign-engine-level-lifecycle-promotio.md) |

## in-progress

_No tickets in this state._

## blocked

_No tickets in this state._
