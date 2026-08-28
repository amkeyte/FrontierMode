---
id: SAT_041
uid: SAT
number: 41
client: Satchel
status: done
title: Build MobJig side-agnostic resolution (RM_SAT_022)
context: Lead Dev build for Roger. Built against ForgeEgress/egress() (RM_SAT_022's
  own log + SAT_038), not SAT_037's own closing text (MobEntityLookup via booter()),
  which was stale and can't compile against SAT_038's already-shipped private booter().
  Project owner's call, 2026-08-24.
priority: high
opened: '2026-08-24'
closed: '2026-08-24'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Lead Dev build for [RM_SAT_022](../roadmap/RM_SAT_022_roger.md) ("Roger") -- reversing
[RM_SAT_021](../roadmap/RM_SAT_021_frank.md)'s server-only call so `MobJig` resolves mobs through
one side-agnostic interface instead of casting to `ServerLevel` directly. This is what
[RM_FRO_018](../roadmap/RM_FRO_018_shirley.md) ("Shirley") is queued behind, and it also fixes
a real latent bug for free: a `CLIENT`/`BOTH`-applicability `MobJig` consumer's scopes get torn
down every reconcile cycle regardless of whether the mob is still there, because Phase 2's
re-resolution today `continue`s outright on anything that isn't a `ServerLevel`.

Architect (Douglas) signed off via [SAT_037](SAT_037_mobjig-sidedness.md): the design question is
answered, the shape is one side-resolved interface, resolved at foundation boot, mirroring the
`ScopeEngine`/`engine()` precedent exactly. **Which interface that actually is turned out to be a
real discrepancy, not a formality -- see the Log below.** SAT_037's own closing text names it
`MobEntityLookup`, reached via a new `ASatchelFoundationBooter.mobEntityLookup()` through
`booter()`. RM_SAT_022's own log entry and SAT_038 (closed on "the new `ForgeEgress` ... never
routes through `booter()` at all") both describe a differently-named, differently-shaped design:
`ForgeEgress`, reached via a new `LogicalFoundation.egress()`, with no new abstract method on
`ASatchelFoundationBooter` at all. Only the second is consistent with `booter()` actually being
private in source today (SAT_038, already shipped) -- built against that one.

**Deliberately sequenced behind [SAT_039](SAT_039_jig-self-test-audit.md)/
[SAT_040](SAT_040_health-followup.md), per the node's own log.** Both are now done and, more to
the point, both are proven live against real runs, not just compiled -- `MobJig`'s own
`sideApplicability` is already `BOTH` and its canary check already exercises the client-side
reconcile path (confirmed live 2026-08-23, caught this exact bug shape once already). This build's
own done bar leans on that existing coverage rather than standing up new throwaway verification.

## Built against ForgeEgress/`egress()`, not SAT_037's literal text

[RM_SAT_022](../roadmap/RM_SAT_022_roger.md)'s own log is where the *problem* is documented (the
four coupling points, the latent bug, the done bar) -- still worth reading for that. The *design*
actually built, per the project owner's direction after the discrepancy below was flagged:

- **New interface**, `common/jig/guts/ForgeEgress.java` (beside `ScopeEngine` -- this is a general
  "resolve a UUID in a level" capability, not `MobJig`-specific, unlike SAT_037's own text
  claimed):
  ```
  Optional<Entity> getEntity(Level level, UUID uuid);
  ```
  `Entity`-typed, not `Mob`-typed -- `MobJig` (the only caller today) narrows to `Mob` itself
  after the fact.

- **`LogicalFoundation`** (`common/jig/guts/`) gains `installEgress(ForgeEgress)` and a public
  `egress()` getter, mirroring its existing private `booter`/`booter()` pair in shape but public --
  deliberately not routed through `booter()`, which is private (SAT_038). `ASatchelFoundationBooter`
  itself gains **no** new abstract method.

- **`ServerFoundationBooter.installFoundation()`** (`server/jig/guts/`) calls
  `foundation.installEgress(new ServerForgeEgress())`. `ServerForgeEgress` (new file,
  `server/lifecycle/`, paired with `ServerForgeIngress`) casts to `ServerLevel` and calls
  `getEntity(uuid)` -- today's exact logic, relocated, not rewritten.

- **`ClientFoundationBooter.installFoundation()`** (`client/jig/guts/`) calls
  `foundation.installEgress(new ClientForgeEgress())`. `ClientForgeEgress` (new file,
  `client/lifecycle/`, paired with `ClientForgeIngress`) casts to `ClientLevel` and iterates
  `entitiesForRendering()` for a UUID match, since `ClientLevel` has no UUID-keyed lookup.

- **`MobJig.reconcile()`** (`common/jig/mob/MobJig.java`): Phase 1's loop (was
  `Map<ServerLevel, Set<UUID>> interests = supplier.interestedMobs()`, then
  `level.getEntity(uuid)` per entry) and Phase 2's re-resolution (was
  `if (!(mobScope.mob().level() instanceof ServerLevel serverLevel)) continue;` followed by
  `serverLevel.getEntity(scopedUuid)`) both now call
  `Satchel.require().egress().getEntity(level, uuid)` instead. Phase 2's old `instanceof
  ServerLevel` guard is exactly what fixed the client-side teardown bug -- once it's gone, a
  client-scoped mob re-resolves through `ClientForgeEgress` exactly like a server-scoped one
  resolves through `ServerForgeEgress`, no separate patch needed.

- **`MobInterestSupplier.interestedMobs()`** (`common/jig/mob/MobInterestSupplier.java`) widened
  `Map<ServerLevel, Set<UUID>>` to `Map<Level, Set<UUID>>`. Its class doc, which previously stated
  the server-only limitation as a "real, current limitation... not an oversight," is rewritten --
  that limitation is exactly what this ticket removes.

- **`SatchelHealth`'s interest surface** (`common/tracking/SatchelHealth.java`) widened the same
  way -- `INTERESTS` (`Map<ServerLevel, Set<UUID>>`), `watch(ServerLevel, UUID)`,
  `unwatch(ServerLevel, UUID)`, and `currentInterests()` are now `Level`-typed. This is
  `SatchelHealth` now, not `MobTrackingModule` -- SAT_039 relocated the whole
  interest-registration surface there and left a forward-pointing comment at its own line ~108
  anticipating exactly this ticket. `MobTrackCommands.java` (`server/commands/`) needed its own
  `currentInterests()`-typed local variables (`list` subcommand) widened to match -- passing a
  `ServerLevel` where `Level` is expected still compiles unchanged, but a `Map<ServerLevel,...>`
  local can't hold a `Map<Level,...>` return value, Java generics being invariant.

## Four things worth knowing before you start

Same spirit as SAT_035's own list -- places the obvious guess is wrong, checked against source
2026-08-24:

1. **`MobScope`'s UUID-derivation static is named `resolveUUID`, not `determineUUID`.** SAT_035
   renamed it mid-build to resolve a compile clash with `ASatchelScope`'s forced instance override
   of the same name. Don't reintroduce the old name from stale memory of the design docs -- check
   current source.
2. **`MobJigConfig` ships no default `sideApplicability`** (deliberately, per its own class docs)
   -- every consumer states its own. This build doesn't change that; it only makes `BOTH`/`CLIENT`
   configs actually work correctly, which today they don't.
3. **`SatchelHealth`'s own `MOB_JIG` config is already `BOTH`**, registered via
   `MobInterestRegistry.register(MOB_JIG, () -> INTERESTS)`. This is your live, already-wired test
   subject -- no need to stand up a new consumer to verify against.
4. **No other jig kind polls.** `LevelJig`/`PlayerJig` stay purely event-driven. `ForgeEgress`
   itself is general (`Entity`-typed, lives beside `ScopeEngine`), but `MobJig` is its only caller
   today -- if a second poll-driven jig kind shows up later, it's already positioned to reuse this
   rather than needing its own.

## Done bar

Per [RM_SAT_022](../roadmap/RM_SAT_022_roger.md)'s own bar, compiling clean is necessary and not
sufficient. Unlike SAT_035, this build gets to lean on coverage that already exists rather than
building its own from scratch:

- A client-scoped (or `BOTH`-scoped) `MobJig` consumer registers interest, gets a scope introduced,
  sees ticks, and survives more than one reconcile cycle while the mob is still resolvable --
  `SatchelHealth`'s own canary check is exactly this consumer already. Confirm it keeps ticking
  normally (steady `[SatchelHealth] TICK ... side=SERVER` / client-side attachment) across many
  cycles post-build, not just the one cycle it survived before.
- The RM_SAT_022 latent bug is confirmed actually gone, not just reasoned away: a `CLIENT`/`BOTH`
  scope must NOT be torn down while its mob is still there. `SatchelHealth`'s own violation check
  (`onMobScopeUnloaded`, `isRemoved()`-based) is the existing mechanism that would catch a
  regression here -- if this build is correct, that check should stay silent through many reconcile
  cycles on a live client-attached canary, where before Roger it would eventually be expected to
  misbehave for a `BOTH` config that wasn't `SatchelHealth`'s own already-server-anchored one.
  (`SatchelHealth`'s canary itself is server-spawned with `BOTH` applicability, so it's already a
  reasonable stand-in for a genuinely client-participating consumer -- confirm this reasoning holds
  before treating the done bar as met, rather than assuming it without checking.)
- Server-side behavior unchanged: `ServerForgeEgress.getEntity` must be behaviorally identical
  to today's inline `ServerLevel.getEntity(uuid)` call -- this is a relocation, not a rewrite, and
  regressing the already-proven server path would be a real step backward.
- `runtime.md`'s `MobJig` section interim note ("That is a defect under repair, not settled
  design") gets corrected to describe `ForgeEgress` as built -- done as part of this ticket's own
  build pass rather than deferred, since the correction and the code landed together.

**Needs the dedicated-server-plus-client path, not integrated/singleplayer** -- same standing
reason as every prior node here: the whole point is that the two sides behave the same, and
integrated/singleplayer can't tell the difference between "works" and "never exercised."

## Standing constraint

**No Gradle in the agent sandbox.** No Forge/Mojang maven access, so a session cannot run
`gradlew build` itself. Real build and playtest are the project owner's machine, same as every
prior node in this project. Self-review (brace/paren balance, cross-checked against the exact
current source of every touched file) is not a substitute for a real compile, and won't be
represented as one.

## Log

- 2026-08-24: Ticket opened, off the project owner's direction to pick up RM_SAT_022 now that
  SAT_039/SAT_040 give it real coverage to build and verify against.

- 2026-08-24: **Real discrepancy found before writing any code, flagged rather than guessed
  through: SAT_037's own closing text and RM_SAT_022's own log describe two different, mutually
  incompatible designs for the same decision.** SAT_037 (read directly): a `MobEntityLookup`
  interface, `Optional<Mob> resolve(Level, UUID)`, in `common/jig/mob/`, reached via a new
  `ASatchelFoundationBooter.mobEntityLookup()` through `booter()`. RM_SAT_022's own 2026-08-23 log
  entry, and SAT_038's stated motivation ("the new `ForgeEgress` installed there deliberately
  never routes through `booter()` at all"): a `ForgeEgress` interface, `Optional<Entity>
  getEntity(Level, UUID)`, in `common/jig/guts/`, reached via a new `LogicalFoundation.egress()`,
  with no new abstract method on `ASatchelFoundationBooter` at all. Neither existed in source yet
  (confirmed by grep), so this was a pure documentation conflict, not a half-built mess -- but a
  real one: SAT_038 already shipped, and `LogicalFoundation.booter()` is genuinely `private` in
  source right now. Version A's own code (`booter().mobEntityLookup()`) cannot compile against
  that. Flagged to the project owner directly rather than picking one -- this determined the
  ticket's entire "What to build" section, and building the wrong one would have meant a second
  pass through all of it. Owner's direction: build Version B (`ForgeEgress`/`egress()`), since
  it's the one actually consistent with shipped source.

  **Built:** `ForgeEgress` (`common/jig/guts/ForgeEgress.java`, new), `ServerForgeEgress`
  (`server/lifecycle/ServerForgeEgress.java`, new, paired with `ServerForgeIngress`),
  `ClientForgeEgress` (`client/lifecycle/ClientForgeEgress.java`, new, paired with
  `ClientForgeIngress`, resolves via `ClientLevel#entitiesForRendering()`). `LogicalFoundation`
  gained `installEgress(ForgeEgress)`/`egress()` (public getter, unlike the private
  `booter`/`booter()` pair it sits beside). `ServerFoundationBooter`/`ClientFoundationBooter`
  each install their side's `ForgeEgress` from `installFoundation()`, right after `installBooter`.
  `MobJig.reconcile()`'s both phases resolve through `Satchel.require().egress().getEntity(level,
  uuid)` instead of casting to `ServerLevel` -- Phase 2's old `instanceof ServerLevel` guard (the
  actual cause of the RM_SAT_022 latent bug, per SAT_037's own correct-either-way diagnosis) is
  gone entirely, not special-cased. `MobInterestSupplier.interestedMobs()` widened to `Map<Level,
  Set<UUID>>`, its class doc rewritten (previously described the server-only limitation as
  deliberate, which this ticket's whole point is to remove). `SatchelHealth`'s `INTERESTS`/
  `watch`/`unwatch`/`currentInterests()` widened the same way; `MobTrackCommands.java`'s `list`
  subcommand updated to match (`Map<ServerLevel,...>` locals can't hold a `Map<Level,...>` return
  value -- Java generics are invariant -- even though passing a `ServerLevel` argument where
  `Level` is expected still compiles unchanged). `runtime.md`'s `MobJig` section's two affected
  paragraphs (the poll mechanism, the teardown re-resolution) corrected to describe `ForgeEgress`
  as built, replacing the "defect under repair" framing.

  Verified against real, current source before every edit (`LogicalFoundation.java`,
  `ASatchelFoundationBooter.java`, `ServerFoundationBooter.java`, `ClientFoundationBooter.java`,
  `MobJig.java`, `MobInterestSupplier.java`, `MobScope.java`, `SatchelHealth.java`,
  `MobTrackCommands.java`, `ServerForgeIngress.java`, `ClientForgeIngress.java`) rather than
  built from the (now corrected) design docs alone -- confirmed, among other things, that
  `ClientLevel#entitiesForRendering()` is the right client-side iteration target (no existing
  precedent for it in this codebase, so checked Forge/vanilla 1.20.1 API directly rather than
  assumed) and that `Mob.level()`/`Entity.level()` already returns the common `Level` with no cast
  needed once the `instanceof ServerLevel` guard is gone. Self-reviewed (brace/paren balance
  across every touched and new file) but **not compiler-confirmed** -- same standing constraint as
  every prior node in this project, no Gradle/Forge access in this sandbox. Status stays `open`.

  **Not yet done, this ticket's own remaining scope:** a real `gradlew build`, then the done bar's
  live verification against `SatchelHealth`'s own already-`BOTH` canary (does it keep ticking
  cleanly across many reconcile cycles post-build; does the existing `onMobScopeUnloaded`
  violation check stay silent through that same window). Also owed but out of this ticket's own
  scope: correcting SAT_037's closing text so it doesn't stand as if it were the design that got
  built -- tracked as its own log entry on SAT_037 directly, not rewritten here, per this
  project's own "log honestly, don't erase" convention.

- 2026-08-24: **Confirmed live: compiles, loads, and the RM_SAT_022 latent bug does not
  reproduce.** Fresh server+client run, read directly from `run-server/logs/latest.log` and
  `run/logs/latest.log` (`22:55`-`22:56`). Mod loaded cleanly on both sides (`HELLO SATCHEL`,
  `SatchelHealth Initializing`) -- the `ForgeEgress`/`egress()` wiring compiles, which this
  sandbox could not confirm on its own. Server-side canary ticked steadily the whole session, no
  change from prior confirmed-good behavior. Client-side: `[SatchelHealth] LOADED mob=... side=CLIENT`
  at `22:55:58`, then `[SatchelHealth] TICK mob=... side=CLIENT` at `22:56:03` -- the same
  client-scoped `MobScope`, still alive, still ticking, roughly one throttle cycle later. **This
  is the actual behavior RM_SAT_022 exists to fix**: before this ticket, a `side=CLIENT` scope was
  torn down within about one reconcile cycle regardless of the mob's real state (Phase 2's
  `instanceof ServerLevel` guard), and `SatchelHealth`'s own violation check would have caught
  that and crashed loudly, same as it already did once for this exact bug shape (SAT_039's
  2026-08-23 confirmation). No `VIOLATION`, no exception, no new crash report here -- client
  closed cleanly on its own (`Stopping!`) after LOADED and one TICK, not from a teardown-forced
  crash. A full sweep of both logs for `ERROR`/`Exception`/`VIOLATION` turned up nothing
  attributable to this build (one unrelated `RealmsClient` auth warning, expected in offline dev
  mode).

  **Not fully exhaustive**: the client session was short (~11s after connecting), so this is
  LOADED-plus-one-TICK, not the "many reconcile cycles" the done bar asks for -- real, clean
  evidence the fix works, but a longer client session would strengthen it further before treating
  this as airtight. Server-side has run far longer (multiple minutes, many ticks) across this and
  prior confirmations with no issue. `runtime.md`'s corrected `MobJig` section and `SAT_037`'s
  correction note both stand as written -- nothing here contradicts them. Project owner's call on
  whether this is enough to close now or worth one more, longer run first.

- 2026-08-24: **Confirmed live, much more thoroughly this time -- done bar met.** A second,
  longer server+client run (`22:59`-`23:03`), read directly from both logs. The client-side
  `MobScope` (`side=CLIENT`) loaded at `23:00:13.950` and ticked **thirteen consecutive times**
  (`23:00:18` through `23:01:23`, over a full minute, roughly every 5s) with zero teardown and
  zero violation -- not "loaded plus one tick" like the previous confirmation, genuinely "many
  reconcile cycles." It then unloaded cleanly at `23:01:25.952`
  (`[SatchelHealth] UNLOADED scope=... side=CLIENT`) with **no accompanying `VIOLATION`** -- a
  real, legitimate teardown (the mob actually became unresolvable, e.g. chunk unload), correctly
  distinguished from the illegitimate case this whole check exists to catch. A fresh scope then
  loaded again at `23:02:53.952`, ticked once more, and the client shut down cleanly
  (`Stopping!`) with the session still clean. Server-side: the canary ticked continuously the
  entire session (`22:59:52` through `23:03:17`), Dev's `PlayerScope` loaded, ticked repeatedly,
  and unloaded cleanly on real logout (`23:03:00`, `Dev left the game` -> `PlayerTracking
  UNLOADED`, no violation), and the server itself began a clean shutdown at the very end of the
  log. A full sweep of both logs for `ERROR`/`Exception`/`VIOLATION` again found nothing
  attributable to this build.

  This satisfies the done bar's "survives more than one reconcile cycle while resolvable" and
  "stays silent through many cycles" criteria directly, with real evidence of both a sustained
  live scope and a correctly-silent legitimate teardown, not just an absence of crashes. Compiling
  clean is already confirmed (prior entry); this entry is the "not sufficient" other half. Ready
  to close on the project owner's word.

- 2026-08-24: **Done -- project owner's own call to close.** Both halves of the done bar met and
  confirmed live: compiles clean (proven by real mod load across two separate runs), and the
  RM_SAT_022 latent bug is gone, not just untriggered -- a client-scoped `MobScope` survived
  thirteen consecutive reconcile cycles over a full minute, then unloaded cleanly with no
  violation on a real teardown, correctly distinguishing legitimate from illegitimate the whole
  way through. Built against `ForgeEgress`/`egress()` after catching and resolving a real
  discrepancy between SAT_037's own closing text and what RM_SAT_022/SAT_038 actually describe --
  logged in full above, with a correction note left on SAT_037 itself rather than silently
  edited. [RM_SAT_022](../roadmap/RM_SAT_022_roger.md) marked `resolved` to match.

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
