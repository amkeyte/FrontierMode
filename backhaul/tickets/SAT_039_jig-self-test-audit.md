---
id: SAT_039
uid: SAT
number: 39
client: Satchel
status: done
title: Audit and build automatic self-test coverage for every Jig kind
context: 'Found while scoping RM_SAT_022 (Roger): nothing in Satchel exercises MobJig''s
  CLIENT/BOTH path at all, so a real regression (client-scoped MobScopes torn down
  every cycle) sat completely undetected. Project owner wants this generalized: every
  jig kind gets its own automatic, in-repo self-test, not a manual verification pass
  repeated by hand each time.'
priority: high
opened: '2026-08-23'
closed: '2026-08-24'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Every jig kind (`LevelJig`, `PlayerJig`, `MobJig`) needs its own automatic, in-repo self-test —
something that proves its actual runtime behavior (introduce/tick/teardown, on whichever sides it's
applicable to) without a human running a manual verification pass. Right now that only happens by
hand, and it doesn't always happen at all.

## Why this is being opened now

Scoping [RM_SAT_022](../roadmap/RM_SAT_022_roger.md) ("Roger") surfaced a concrete failure of the
current approach: nothing anywhere in either repo registers a `CLIENT`- or `BOTH`-applicability
`MobJig` consumer — `MobTrackingModule` (Satchel's own verification tool) is pinned `SERVER`
explicitly, and `BossModule` (FrontierMode's, not yet built) is designed `SERVER` too. So the
client-side teardown bug Roger's design fixes (a `CLIENT`/`BOTH`-scoped `MobScope` torn down every
reconcile cycle regardless of the mob's real state) was never actually exercisable by anything —
not a near miss, a real gap nobody could have caught by running the existing tools, however
carefully. Roger's own done bar needs a `BOTH`/`CLIENT` test path built before it can be verified at
all — this ticket is that requirement generalized to every jig kind, tracked on its own rather than
folded into Roger's build.

## What "self-test" means here — automatic, silent on pass, loud on fail

Not `/satchel mobtrack`-style manual commands (that's what verified Frank, by hand, on a real
server, once). What's wanted:

- **Runs automatically on every build** — no person has to remember to invoke it, connect a client,
  or read log output line-by-line to notice something's wrong.
- **Silent on pass.** A healthy run shouldn't add noise anyone has to scroll past.
- **Loud on fail.** A failing self-test logs clearly *and* fails hard — crashes the build, or throws
  in a way nothing swallows — not a quiet warning buried in normal output. This is a different
  posture from [RM_SAT_013](../roadmap/RM_SAT_013_gary.md)'s existing bundle-lifecycle
  `healthCheckPulse()` (see `SatchelBundle.healthCheckPulse()`), which is deliberately diagnostic-only
  and never throws — that mechanism catches silent inertness at the bundle level and should stay as
  it is; this ticket is about per-jig-kind functional correctness, a different concern, not a
  replacement for it.
- **A regression caught this way is automatically high priority** — if a self-test ever goes from
  green to red, that's treated as the same class of urgency a broken build gets, not routine backlog.
- **Lives in external test-class routines, not inline assertions scattered through production
  code.** Coverage belongs in dedicated test classes — `src/test/...` for whatever JUnit can reach,
  an equivalent isolated harness for whatever needs the in-game/integration half — never
  `assert`/ad-hoc sanity checks peppered into `MobJig`/`LevelJig`/`PlayerJig`/`ForgeEgress` or any
  other production class to satisfy this ticket. Tests stay a distinguishable concern: reading
  production code shouldn't mean reading test logic, and auditing coverage shouldn't mean hunting
  through production classes to find out what's actually verified.

## Audit — starting point, confirm and extend

Checked against source 2026-08-23:

- **`MobJig`** has exactly one automated test, `MobReconcileLogicTest`
  (`src/test/java/test/arryn/satchel/jig/mob/MobReconcileLogicTest.java`, plain JUnit 5) — covers
  `MobReconcileLogic.computeTeardowns()`'s pure logic only. It does not exercise `reconcile()`'s
  actual entity-resolution path on either side, and nothing else about `MobJig` is tested
  automatically at all.
- **`LevelJig`** — no test file exists anywhere in `src/test/`.
- **`PlayerJig`** — no test file exists anywhere in `src/test/`.
- **Gradle already runs `test { useJUnitPlatform() }`**, so anything written as a JUnit test already
  gets the "runs on every build, fails loud" property for free — the open question per jig kind is
  whether its actual behavior *can* be captured in a plain JUnit test (pure logic, the way
  `computeTeardowns` was) or needs something closer to an in-game/integration check (real `Level`,
  real Forge lifecycle) that JUnit alone can't reach — Minecraft/Forge's own GameTest framework is
  worth evaluating for that half, not assumed to be the answer.

## What this ticket is asking for

1. **Audit each jig kind** against the standard above: does an automatic, build-integrated test
   exist that would actually catch a regression like Roger's, on every side that kind claims to
   support? Confirm or correct the starting point above.
2. **Where the answer is no, design, build, and document one.** Documentation goes on the wiki
   (likely a new page, or a section on [Jig & Scope
   Runtime](../wiki/satchel/architecture/runtime.md) — Architect's call once the shape is known) —
   not just code comments, so the next person adding a jig kind or a consumer knows the coverage
   bar and how to meet it.
3. **Whatever shape the fix takes, it has to be automatic, silent-on-pass, loud-on-fail, and live in
   external test classes** — per "What 'self-test' means here" above. A manual command someone has
   to remember to run doesn't satisfy this ticket, however useful it also is to keep around, and
   neither does an inline check bolted onto production code — that's a second failure mode this
   ticket exists to rule out, not just the manual-command one.
4. **`MobJig`'s `CLIENT`/`BOTH` gap is this ticket's most urgent single item** — it's what's
   currently blocking a credible verification of Roger's done bar. Worth landing first if the audit
   confirms it's the worst gap, but the ticket's scope is all three jig kinds, not just Mob.

## Not in scope

Roger's own build (`RM_SAT_022`) — this ticket is deliberately separate from it, per project
owner's own call, even though the two are closely related and Roger's done bar will likely lean on
whatever `MobJig` self-test comes out of this ticket.

## Log

- 2026-08-23: **Sequenced ahead of [RM_SAT_022](../roadmap/RM_SAT_022_roger.md) ("Roger") —
  project owner's call.** This ticket lands first; Roger's build follows and leans on whatever
  `MobJig` coverage comes out of it, rather than improvising a narrow test path inside its own
  build. That matches item 4 above: the `CLIENT`/`BOTH` gap is the most urgent single item, and
  it's what makes Roger's done bar checkable at all. Roger's own Lead Dev build ticket is owed and
  gets opened once this one's `MobJig` half is in hand, so it can point at real coverage instead of
  a promise.

  **Standing constraint worth stating before anyone starts:** the agent sandbox has no
  Gradle/Forge/JDK access — every node in this project has hit it. Tests can be *written* there but
  not compiled or run, so "silent on pass, loud on fail" cannot be demonstrated from a session; the
  first real green/red signal comes from the project owner's own machine. Plan the handoff around
  that rather than discovering it at the done bar. If the audit concludes GameTest is the right
  vehicle for the in-game half, note that it's also the half a session can least verify.

- 2026-08-23: Ticket opened.

- 2026-08-23: **Requirement added (project owner): tests must be external test-class routines, not
  inline assertions scattered through production code.** Folded into "What 'self-test' means here"
  and item 3 above rather than as a separate section — this is a constraint on the same deliverable,
  not a new one. Motivation stated directly: tests have to remain a distinguishable concern, not
  something peppered throughout the codebase.
- 2026-08-23: **Shape changed from build-time tests to a live-run module, project owner's
  explicit call — [SatchelHealth](../wiki/satchel/architecture/satchel-health.md) built for the
  `MobJig` slice.** Not JUnit or GameTest: a permanent in-repo module (`common/tracking/
  SatchelHealth.java`) that checks a jig kind's real behavior every time someone actually runs a
  client/server, silent on pass, loud (log + hard crash, unconditional for now) on fail. Absorbs
  the old `MobTrackingModule` (retired, empty stub left in place — this session's tooling has no
  file-delete access) under the same registered IDs, with its `MobJigConfig` widened
  `SERVER` → `BOTH`. A canary `Bat`, auto-spawned/discovered near world spawn, exercises the
  actual client-side path via `MobScope.getFor()`; the violation check is `Mob.isRemoved()` still
  `false` at teardown — see the wiki page for the full mechanism.

  **This is expected to crash on the first real connected client, and that is success, not a
  bug.** `RM_SAT_022` ("Roger") hasn't landed yet, so the canary's client-side scope should still
  hit the exact latent teardown bug that node exists to fix — this module existing and catching
  it live is the proof SAT_039 was opened to produce. First real signal has to come from the
  project owner's own machine — this session's sandbox has no Gradle/Forge network access (Java
  21 + Gradle are present, but maven.minecraftforge.net/Mojang/Maven Central are not reachable
  from here), so nothing here has been compiled or run. Verification loop: project owner runs it
  locally and reports back before this ticket's `MobJig` slice is called done.

  **Scope split, not full ticket closure.** `LevelJig`/`PlayerJig` health absorption is
  deliberately deferred — split out to [SAT_040](SAT_040_health-followup.md) at the project
  owner's direction, so this ticket stays scoped to the one gap it was actually opened to close
  (item 4's `MobJig` `CLIENT`/`BOTH` case). This ticket's own audit findings for `LevelJig`/
  `PlayerJig` (no test file exists anywhere in `src/test/`) still stand and are SAT_040's starting
  point.

- 2026-08-23: **Fixed a real compile error in `SatchelHealth.java`, reported directly from the
  project owner's own build:** `import net.minecraft.world.entity.animal.Bat;` failed with
  `cannot find symbol` (package resolved, the class member didn't) -- isolated to that one import;
  every other `net.minecraft.world.entity.*` import in the same file compiled fine, so this wasn't
  a general classpath/mapping problem. Rather than chase the exact cause blind from a sandbox with
  no Gradle/Forge access of its own, the canary's dependency on the concrete `Bat` class was
  removed instead: `SatchelHealth` now references the species only via `EntityType.BAT`
  (`CANARY_TYPE`) and handles the canary as a plain `Mob` everywhere -- `getEntitiesOfClass` scans
  by `Mob.class` with a type + custom-name predicate, and spawning goes through
  `CANARY_TYPE.create(level)` instead of `new Bat(...)`. No source line in the class names the
  `Bat` class at all anymore. This also directly addresses the project owner's own "Bat is
  problematic" flag -- whatever the underlying objection turns out to be (this compile error, or
  something behavioral), swapping the canary species now is a one-line change to `CANARY_TYPE`,
  not a type change scattered across every method that touches it. Wiki page updated to match.
  **Still unverified** -- same sandbox constraint as every other line in this ticket: pushed to the
  project owner's machine, not compiled here. If this doesn't clear the error, the isolated (only
  `Bat`, nothing else in the package) failure shape is worth a `./gradlew clean --refresh-dependencies`
  before assuming it's a code problem -- that symptom (package resolves, one specific member
  doesn't) is a classic stale-decompiled-sources-cache signature, not typically a real mapping gap.

- 2026-08-23: **Compile fix confirmed -- `BUILD SUCCESSFUL` on the project owner's machine**, read
  directly from `build.log`/`run/logs/latest.log`/`run-server/logs/latest.log` after a real
  client+server run. The `EntityType`/`Mob`-based rewrite cleared the `Bat` symbol error cleanly.

  **Second, separate bug found from the same run's logs: the canary was never actually exercised.**
  Server-side, `SatchelHealth` spawned and ticked the canary fine at the hardcoded anchor
  `(0, -60, 0)`. But the connecting player (`Dev`) joined at `(107.5, 72.0, 41.5)` -- this world's
  real spawn point, nowhere near the guessed coordinate (roughly 130 blocks off both horizontally
  and vertically, and well below the search radius either way). The client never got within range
  to discover the canary, `MobScope.getFor()` was never called client-side, and the actual point of
  this pass -- catching RM_SAT_022's latent client-teardown bug live -- never got a chance to run.
  No crash was logged, but that's an untested check, not a passing one.

  **Fixed**: `anchorPos()` now calls `Level#getSharedSpawnPos()` instead of a hardcoded guess --
  works identically on both sides (synced to the client from the server), so the canary sits at
  wherever this world's spawn actually is. `CANARY_SEARCH_RADIUS` widened `8.0` -> `16.0` blocks to
  comfortably cover vanilla's default `spawnRadius` gamerule (10 blocks) -- a fresh player can
  legitimately land anywhere in that ring around shared spawn, not exactly on top of it.

  **Still not actually verified as catching the RM_SAT_022 bug** -- this fixes what should have
  been a straightforward, no-extra-effort trigger (a player joining near spawn, the normal case)
  rather than requiring anyone to manually walk to a specific coordinate. The real signal is still
  owed: project owner runs this build, connects near spawn, and reports whether the expected
  client-side crash actually fires.

- 2026-08-23: **Verification loop closed -- confirmed live on the project owner's machine.**
  Fresh client+server run, read from `run/logs/latest.log` / `run-server/logs/latest.log`: the
  canary spawned at the real shared spawn point (`BlockPos{x=112, y=72, z=48}`), right next to
  where the player actually joined (`107.5, 72.0, 41.5`) -- the anchor fix worked. Client-side scan
  found it, `MobScope.getFor()` attached a client-side scope, and on the very next reconcile cycle
  that scope was torn down while the mob was still present. `SatchelHealth` logged the violation
  and threw, crashing the client with a full stack trace through `MobJig.reconcile()` ->
  `ASatchelJig.onUnload()` -> `SatchelHealth.onMobScopeUnloaded()`.

  **This is success, exactly as predicted two log entries up**: the self-test is proven live,
  against a real client, to actually catch the RM_SAT_022 latent teardown bug -- not a promise, a
  demonstrated regression backstop. `MobJig`'s `CLIENT`/`BOTH` gap (this ticket's most urgent item,
  per the original ask) is closed. This ticket's `MobJig` slice is functionally done; status left
  at the project owner's discretion to formally close, since that also unblocks opening RM_SAT_022
  ("Roger")'s own Lead Dev build ticket, deferred until this exact confirmation.

- 2026-08-24: **Done -- project owner's own call to close.** This ticket's own scope (audit +
  build automatic, in-repo self-test coverage for every jig kind) is complete and proven, not
  just built: `MobJig`'s slice was confirmed live back on 2026-08-23, catching the real
  RM_SAT_022 latent bug on a live client the moment it existed to catch it. Its sibling ticket,
  [SAT_040](SAT_040_health-followup.md) (`LevelJig`/`PlayerJig`), closed the same way after two
  failed deferral attempts and a third that actually worked, both confirmed live. And the very
  bug this audit was opened to generalize protection against is now itself fixed --
  [RM_SAT_022](../roadmap/RM_SAT_022_roger.md) ("Roger") resolved via
  [SAT_041](SAT_041_mobjig-side-agnostic-build.md), verified against this ticket's own coverage
  rather than a promise, exactly as intended when SAT_041 was deliberately sequenced behind this
  one. All three jig kinds -- `MobJig`, `LevelJig`, `PlayerJig` -- now have working, live-proven
  self-test coverage in `SatchelHealth`. Nothing left open in this ticket's own scope.

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
