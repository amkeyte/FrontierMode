---
id: SAT_035
uid: SAT
number: 35
client: Satchel
status: done
title: Build MobJig/MobScope (RM_SAT_021)
context: Lead Dev build for Frank. Design lives on runtime.md and the new getFor spec
  page, not the node log.
priority: high
opened: '2026-08-21'
closed: '2026-08-22'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Lead Dev build for [RM_SAT_021](../roadmap/RM_SAT_021_frank.md) ("Frank," `MobJig`/`MobScope`) — the
fourth jig kind, and the thing FrontierMode's whole Tier 1 is queued behind. Project owner's
ordering call: Frank lands before [RM_FRO_018](../roadmap/RM_FRO_018_shirley.md) ("Shirley") and
[RM_FRO_019](../roadmap/RM_FRO_019_karen.md) ("Karen").

Architect (Douglas) signed off via [SAT_034](SAT_034_mobjig-docs.md): the design is documented,
`runtime.md`'s stale `PlayerJig` claims are corrected, and the `getFor` boundary contract has a spec
page. This ticket is the code.

## Build against the wiki pages, not the node log

This matters enough to state plainly. [RM_SAT_021](../roadmap/RM_SAT_021_frank.md)'s log is a
decision *history* — it contains two superseded designs (an opt-in/`EntityLeaveLevelEvent` ingress
model, and an earlier `LivingEntity`-typed scope) that were both explicitly reversed. Reading it
front-to-back and building what it describes would build the wrong thing twice before arriving at
the right one.

The current design is on these two pages, and they are authoritative over anything in the node:

- **[Jig & Scope Runtime § MobJig](../wiki/satchel/architecture/runtime.md#mobjig)** — the scope
  model, `Mob` typing, why there's no `MobResolver`, `MobJigConfig`'s shape, and the poll-driven
  presence model with its reason-agnostic teardown contract.
- **[MobScope.getFor() Contract](../wiki/satchel/spec/mobscope-getfor.md)** — the boundary contract
  for the static factory: call sequence, both `Optional.empty()` cases, the immediate-attachment
  guarantee, idempotency under overlap with the poll, and what it explicitly does *not* guarantee.

Use the node for its **done bar** and **verification aid** sections, which stay authoritative there.

## What to build

- `common/jig/mob/*` — `MobJig`, `MobScope`, `MobScopeCoupler`. Scope holds a `Mob` directly.
- `common/newconfig/newnew/MobJigConfig.java` — mirrors `PlayerJigConfig`'s four-category-lens shape
  structurally, but **ships no default `sideApplicability`**; each consumer states its own.
- The reconciliation step inside `foundationLifecycle().pulse()`, plus the per-consumer interest
  supplier `MobJigConfig` exposes.
- `MobScope.getFor(Mob)` per the spec page.

`PlayerJig` is the right template for the scope/config plumbing — it is live and consumed today
(`common/jig/player/*`, four files; `BorderModule`/`BorderAPI` use `PlayerScope`). The *ingress*
mechanism is the part that is genuinely new and shouldn't be ported from it.

## Four things worth knowing before you start

Checked against source on 2026-08-21 while verifying Architect's pass — each of these is a place the
obvious guess is wrong:

1. **There is no per-player tick source, and `MobJig` doesn't need a per-mob one either.**
   `PlayerTickEvent` appears nowhere in the codebase. The shared `TickEvent.ServerTickEvent` →
   `foundationLifecycle().pulse()` path already walks every `JigInfo`/`ScopeInfo`. Add the
   reconciliation step to that existing pulse; don't introduce a new tick hook.
2. **`introduceSource` already guards against duplicates** — `LogicalFoundation` checks
   `if (!ji.hasScope(scope)) ji.addScope(scope, source)`. That guard is what makes `getFor`
   idempotent under overlap with the poll. Don't add a second one in `MobScope.getFor`.
3. **`ASatchelScope.equals()`/`hashCode()` are `final` and `scopeId`-based.** You cannot override
   them in `MobScope`, and you don't want to — UUID equality across two different `Mob` Java objects
   sharing a persistent UUID (one from before a chunk reload, one from after) is exactly the
   property the spec page's idempotency guarantee rests on.
4. **`ServerForgeIngress` does subscribe to `PlayerChangedDimensionEvent`** — for world-identity
   token sync, not scope lifecycle. If you use `PlayerJig`'s ingress as a reference, don't read that
   subscription as a scope-lifecycle hook and mirror it.

## Done bar

Per [RM_SAT_021](../roadmap/RM_SAT_021_frank.md)'s own bar — compiling clean is necessary and not
sufficient. Build the verification aid the node asks for (a `MobTrackingModule` mirroring
`PlayerTrackingModule`'s precedent) and confirm, with it:

- Registering interest in a UUID that later becomes resolvable creates a scope **within one poll
  cycle** — not immediately. The delay is the designed behavior, so verify it rather than treating
  it as a bug.
- The tick pulse reaches that scope.
- The entity no longer resolving tears the scope down cleanly within one cycle, **for both causes** —
  chunk unload *and* genuine removal. Confirm both, since the whole point of the contract is that
  they're indistinguishable and treated identically.
- No leaked `MobScope` after teardown.
- The fast path: `getFor(mob)` at spawn time attaches immediately, without waiting for the next
  cycle.

**This needs the dedicated-server path, not integrated/singleplayer** — chunk unload behavior is
precisely the thing under test, and the node is explicit that the superseded design's
"singleplayer is enough" claim no longer holds.

## Two standing constraints

**No Gradle in the agent sandbox.** No Forge/Mojang maven access, so a session cannot run
`gradlew build` itself — every prior node in this project hit this. Real build and playtest are the
project owner's machine. Don't mark this resolved on read-through verification alone; that has been
the standing gap all the way through Tier 0 (see
[FRO_032](FRO_032_build-of-0821.md) for how it finally got discharged).

**The spec page is `draft` on purpose.** [MobScope.getFor() Contract](../wiki/satchel/spec/mobscope-getfor.md)
describes a contract for code that doesn't exist yet. Promoting it to `verified` once the build
confirms it against real source is a deliverable of this ticket — and if the implementation has to
diverge from it, the page gets corrected rather than quietly outvoted by the code.

## Log



- 2026-08-21: Ticket opened.
- 2026-08-21: Lead Dev (Cowork sandbox) implemented against the wiki design, verified field-for-field
  against real source (`PlayerJig`/`PlayerScope`/`PlayerScopeCoupler`/`PlayerJigConfig`/
  `PlayerTrackingModule`, `LogicalFoundation`, `JigInfo`, `ScopeInfo`, `ASatchelJig`, `SatchelJig`,
  `FoundationLifecycleDispatcher`, `JigConfig`/`JigBindingConfig`/`JigBundlesConfig`/
  `JigPoliciesConfig`/`JigExecutionConfig`, `JigPolicies`, `JigConfigValidator`, `TickThrottler`)
  rather than guessed, via the device bridge. Added: `common/jig/mob/{MobJig,MobScope,
  MobScopeCoupler,MobInterestSupplier,MobInterestRegistry,MobReconcileLogic}.java`,
  `common/newconfig/newnew/MobJigConfig.java`, `common/newconfig/MobTrackingModule.java`,
  `test/.../jig/mob/MobReconcileLogicTest.java`. Edited `SatchelJig.java` (one default
  `reconcile(JigInfo)` method) and `FoundationLifecycleDispatcher.java` (one call to it in
  `pulse()`) — the only two shared-framework touch points; `LevelJig`/`PlayerJig` inherit the
  no-op unchanged. Teardown in `MobJig.reconcile` calls `onUnload(ScopeInfo)` directly rather than
  `LogicalFoundation.tryRemoveSource`, since the latter needs a live resolvable source object that
  doesn't exist once a UUID stops resolving — confirmed `onUnload` fully evicts the `ScopeInfo` via
  `ScopeLifecycleDispatcher.signalScopeUnloaded` → `JigInfo.removeScope`, so no leak. Interest
  registration lives in a new standalone `MobInterestRegistry` keyed by `JigKey`, kept outside the
  `JigConfig`/`Presets`/`CompiledJigConfig` framework entirely (confirmed no existing extension
  point there, and `JigInfo` — what `reconcile` actually receives — has no back-reference to the
  original `MobJigConfig` instance to hang a field on instead). `MobInterestSupplier` is typed
  against `ServerLevel`, not `Level` — `Level.getEntity(UUID)` is a server-only capability in
  vanilla Minecraft/Forge; noted as a real, current limitation of the poll (a future client-side
  `MobJig` consumer needs its own resolution mechanism), not something this ticket's scope covers.
  Added a first-ever jig/scope-layer unit test (`MobReconcileLogicTest`, plain JUnit 5, no mocking,
  matching `TickThrottlerTest`'s style) for the one pure-logic, correctness-sensitive branch of
  `reconcile` (the teardown set computation) — everything else needs a Minecraft/Forge bootstrap
  this sandbox doesn't have. **Not resolved by this entry**: no Gradle/Forge access in this sandbox
  (standing constraint, same as every prior node), so nothing here has been compiled or run. The
  spec page (`mobscope-getfor.md`) stays `draft` — its promotion to `verified` was explicitly left
  for Douglas (Architect) to decide once real verification happens, not flipped by this session.
  Still needed before this ticket's done bar is met: a real `gradlew build`, then the dedicated-
  server chunk-unload verification the done bar describes, using `MobTrackingModule.watch()`/
  `unwatch()` and a `MobScope.getFor()` call at spawn time to exercise the five checklist items —
  none of that can happen from this sandbox.
- 2026-08-21: **Compile bug found and fixed on review (user-caught, not self-caught): `MobScope` declared both a static `determineUUID(Object)` (the UUID-derivation utility) and an instance `determineUUID(Object)` (the deprecated override `ASatchelScope` forces) with identical name and parameter types in the same class -- an unconditional Java compile error ("determineUUID(Object) is already defined in MobScope"), not a style issue.** `PlayerScope` never hits this because its static lives on a separate class, `PlayerResolver`; consolidating both onto `MobScope` (no `MobResolver`, per design) collapsed that separation without renaming to avoid the clash. Fixed by renaming the static utility to `resolveUUID`, updating every call site: `MobScope`'s own constructor and instance override, `MobJig.determineUUID`'s delegation, and `MobJigConfig`'s `uuidDeterminer` method reference. No other file referenced the old static name (confirmed via a repo-wide grep). Still unverified by an actual compile -- no Gradle in this sandbox, so this fix is corrected-by-inspection, not compiler-confirmed; flagging plainly rather than claiming otherwise.
- 2026-08-21: **Project owner confirms: builds, loads, and runs.** The `determineUUID`/`resolveUUID` name-clash fix above is now compiler-confirmed, not just corrected-by-inspection, and the mod is stable through mod-init/world-load with `MobJig` compiled in. This does not by itself confirm the done bar's five-item checklist (poll-cycle delay on introduce, tick reaching the scope, teardown within one cycle for both chunk-unload and genuine removal, no leaked `MobScope`, immediate `getFor` fast-path attachment) -- that still needs `MobTrackingModule.watch()`/`unwatch()` exercised against a real dedicated-server chunk unload/reload, per the ticket's own standing note that singleplayer/integrated alone doesn't exercise the real path. Status stays `in-progress`, not `done`, until that checklist is actually run.
- 2026-08-21: **Project owner has no way to exercise the done-bar checklist themselves -- closed two gaps.** First, `MobTrackingModule.init()` was written earlier this ticket but never actually called from `SatchelMod`'s constructor (unlike `TrackingModule.init()`/`PlayerTrackingModule.init()`, both called there) -- `MobJig` compiled but was never an installed jig at all, since nothing reached `Satchel.registerJigConfig(...)` for it; fixed by adding the call. Second, added `/satchel mobtrack watch|unwatch|getfor|list` (`server/commands/MobTrackCommands.java`, new file) so the checklist can be driven from chat -- all four subcommands target the nearest `Mob` to the command source rather than taking a UUID argument, to keep every subcommand symmetric and avoid a Brigadier argument type this sandbox has no way to compile-check. Registered via `MinecraftForge.EVENT_BUS.addListener(MobTrackCommands::onRegisterCommands)` in `SatchelMod`'s constructor, mirroring FrontierMode's `BorderModule.onRegisterCommands` idiom (the only Brigadier registration pattern already proven out in this codebase). Also added `MobTrackingModule.currentInterests()`, a read-only snapshot accessor `list` needs and that didn't exist before. Self-reviewed (brace/paren balance, every `CommandSourceStack`/Brigadier call cross-checked against `BorderCommands`/`BorderCommandHandler`'s real, working usage) but -- same standing caveat as the rest of this ticket -- not compiler-confirmed; no Gradle/JDK access from this sandbox. Status stays `in-progress`.
- 2026-08-22: **Real-server testing of `/satchel mobtrack` (project owner) surfaced two real bugs in the command, both fixed by inspection against the server log evidence -- `MobJig`/`MobScope` themselves are not implicated, this is entirely `MobTrackCommands`.** (1) `watch`'s 32-block nearest-mob search let it silently latch onto a mob the player couldn't see or locate -- confirmed from `run-server/logs/latest.log`: it grabbed a skeleton the player never found. Fixed by shrinking `SEARCH_RADIUS` to 6 blocks -- "the mob you're standing next to," not "something within 32 blocks I can't see." (2) `unwatch` re-ran that same nearest-mob search instead of releasing the mob `watch` had actually registered -- confirmed from the same log: the player walked away from the watched skeleton, then `unwatch` found a nearby wolf instead, reported success, and left the skeleton watched indefinitely with no error surfaced. Fixed by recording each caller's last-`watch`ed `(level, mobUuid)` in a new `LAST_WATCHED` map and having `unwatch` release that specific entry, falling back to a (now clearly-labelled) nearest-mob search only when no prior watch is recorded for that caller. Separately clarified, not a bug: the log confirms the watched skeleton's scope *did* tear down via chunk-unload (`[MobTracking] UNLOADED`), about 4.5 minutes after `watch` -- normal Minecraft chunk-unload timing, working as designed. It only *looked* like nothing happened because teardown is logged server-side only, with no player-facing signal, and because `list` shows registered interest, not live scope state, so a torn-down mob stays listed until explicitly `unwatch`ed. Reworded `list`'s output to say this explicitly rather than leaving it to be discovered by confusion again. All fixes self-reviewed (brace/paren balance) but not compiler-confirmed -- same standing sandbox constraint. Status stays `in-progress`.
- 2026-08-22: **Real bug in `MobJig.reconcile()` itself, found from the same re-test (`getfor` case): a scope introduced via `MobScope.getFor()` was torn down about one throttle cycle (~1s) after attaching, with the mob still standing there, unmoved.** Root cause: `resolvedThisCycle` was built ONLY by walking the registered `MobInterestSupplier`'s UUIDs (Phase 1) -- a scope that exists in `JigInfo` but has no matching interest entry, which is exactly what `getFor()` creates by design (it deliberately bypasses the interest walk for its immediate-attachment guarantee, per `mobscope-getfor.md`), could never appear in that set, so Phase 2's teardown diff treated it as unresolved on the very next cycle regardless of whether the mob was actually still resolvable. This directly broke `getFor()`'s documented contract ("not torn down until the mob's chunk becomes unresolvable" -- not "torn down unconditionally one cycle after attaching unless something else happens to also be watching it"). Fixed by adding a second-chance pass in `reconcile()`: any currently-scoped UUID the interest walk didn't already reconfirm now gets its own direct `ServerLevel.getEntity(UUID)` re-resolution (same mechanism Phase 1 uses, via the scope's own held `Mob` object's `level()`) before the teardown diff runs. Reason-agnostic teardown still holds -- it now applies uniformly to every scoped mob, not only interest-registered ones. `watch`/`unwatch`-driven scopes were never affected by this (they're always in the interest walk every cycle) -- confirmed by the same test run, where a `watch`ed pig correctly stayed scoped and ticking for the full ~4.5 minutes it was under interest. `MobReconcileLogic.computeTeardowns` itself needed no change -- the bug was entirely in how `resolvedThisCycle` was assembled before that pure function ran, not in the diff logic. Self-reviewed (brace/paren balance) but not compiler-confirmed. Status stays `in-progress`.
- 2026-08-22: **Side note while verifying, not MobJig-specific -- flagging the scope boundary in case this should split to its own ticket later.** Project owner asked to clean up log spam that was cluttering the verification output: every single scope-load across every jig kind (Level/Player/Mob) was double-logging one ERROR ("A requested bundle was not found...") plus one WARN ("BundleNotFound ignored; falling through to create") for what is actually a completely normal "doesn't exist yet, create it" event -- confirmed via grep that `SatchelException.BundleNotFound` is the only `SatchelException` subtype ever caught anywhere in the codebase (always in `AScopeCoupler.getOrCreate`, always to fall through to `create`), while the base `SatchelException` constructor logs at ERROR unconditionally regardless of whether the exception is about to be caught and handled. Fixed in `common/jig/guts/SatchelException.java` only: added a silent-constructor variant (`(String, boolean)` / `(String, Throwable, boolean)`) and routed both of `BundleNotFound`'s constructors through it with logging off, leaving every other subtype (`AccessDenied`, `AccessFailed`, `ScopeNotReady`, `ScopeNotFound`, `Generic`, `JigNotFound`, `NotReady`, `BadLogicalSide` -- none of which are ever caught anywhere, confirmed by the same grep) logging exactly as before. A `BundleNotFound` thrown somewhere that doesn't catch it still surfaces as an uncaught `RuntimeException` via the JVM's/Forge's own stack trace either way, so nothing about a genuine failure goes silent -- only the redundant pre-emptive log on the routine, immediately-caught path is gone. This touches shared framework code every jig kind depends on, not something SAT_035 introduced -- keeping the note here since it surfaced directly out of this ticket's own verification work, but calling it out explicitly in case it should be split to its own ticket. Self-reviewed (brace/paren balance, confirmed no overload ambiguity against the two pre-existing constructors) but not compiler-confirmed. Status stays `in-progress`.
- 2026-08-22: **Second log-spam pass, also not MobJig-specific -- same scope-boundary flag as the note above.** Project owner pointed out continuous client-side lines (`[SatchelFixture] is ready: BordersFixture[...]`, once every ~1-3s from a per-render-frame readiness check in FrontierMode's border rendering) and server-side lines (`[server engine] Sending Parcel for bundle: ...`, `[ParcelInbox] enqueued/draining Parcel...`). All of these trace back to one root cause in `common/util/out/Tracer.java`: despite the class being named `Tracer` and one of its own overloads literally prefixing output with `[TRACE]`, both of its actual emission points called `LOGGER.debug(...)`, not `LOGGER.trace(...)` -- and `Satchel/build.gradle` sets `forge.logging.console.level = 'debug'` for local dev runs, so every `Tracer` call, throttled (`SatchelFixture.isReady()`, throttled only by a hardcoded 200-tick default since nothing had ever called `registerCaller` for that tag) or fully unthrottled (`ParcelInbox`'s two calls, `ScopeEngine_Server`'s "Sending Parcel" call), leaked straight through to the console by default. Fixed in `Tracer.java` only: both internal `LOGGER.debug(...)` calls (the throttled `traceHelper` path and the untamed `log(String)` path) changed to `LOGGER.trace(...)`, plus the one doc comment that also said `debug`. No call site changed -- every `OUT.TRACE().log(...)` call across the codebase is untouched, still goes through the same throttling/registration/enable-disable machinery; it now actually logs at TRACE, which the project's own DEBUG-level dev console filters out by default, matching what a class called `Tracer` should have been doing already. Someone who genuinely wants to see this detail can still turn their own log level down to TRACE deliberately. Real `INFO`-level output (`[MobTracking]`/`[PlayerTracking]` LOADED/TICK/UNLOADED, all via `OUT.info(...)` directly, a different code path entirely) is untouched and still visible. Shared framework code again, flagged for the same reason as the `BundleNotFound` note above. Self-reviewed (brace/paren balance) but not compiler-confirmed. Status stays `in-progress`.
- 2026-08-22: **Done bar met, real dedicated-server verification, closing.** All five checklist items now have direct log evidence from live `gradlew runServer`/`runClient` sessions (project owner's own machine, real multiplayer, not integrated/singleplayer): (1) poll-cycle delay on introduce -- `watch` consistently produced a `[MobTracking] LOADED` line roughly a throttle cycle after registering interest, never immediately; (2) tick reaching the scope -- steady `[MobTracking] TICK` lines throughout every scope's lifetime, both `watch`- and `getFor`-originated; (3) teardown within one cycle for both causes -- chunk-unload teardown confirmed (a watched skeleton, ~4.5 minutes of walking away, clean `UNLOADED`) and genuine-removal teardown confirmed today (a `getFor`'d pig, killed outright by the project owner, torn down within one cycle) -- both indistinguishable from the log by design, which is itself the point; (4) no leaked scope -- every teardown evicted cleanly via `JigInfo.removeScope`, no orphaned `ScopeInfo`/bundle references observed across the whole session; (5) `getFor` fast-path attachment -- attached immediately every time, and after the `reconcile()` fix below, correctly persisted across many cycles rather than being torn down prematurely. Along the way, real testing also caught and fixed: a `MobTrackingModule.init()` wiring gap (`MobJig` was never actually installed), two targeting bugs in `MobTrackCommands` (`watch`'s search radius, `unwatch`'s nearest-mob re-search instead of releasing the actual watched target), a real `MobJig.reconcile()` bug (a `getFor`-only scope was invisible to the teardown-survival check and got torn down after one cycle regardless of resolvability), and two shared-framework log-noise issues (`SatchelException.BundleNotFound` double-logging, `Tracer` logging at DEBUG instead of TRACE) -- all logged in detail above. `mobscope-getfor.md` stays `draft` -- its promotion is still Douglas's (Architect's) call, not this ticket's. Closing SAT_035 as `done`; [RM_SAT_021](../roadmap/RM_SAT_021_frank.md) marked `resolved` to match. A `bhrm index` refresh is still owed to update `ROADMAP_INDEX.md`'s generated actionable-node listing -- not hand-edited here per that file's own "do not hand-edit" notice.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
