---
id: FRO_090
uid: FRO
number: 90
client: FrontierMode
status: done
title: 'New-map bootstrap: boss and border path not created'
context: 'Playtest on a brand-new map: no boss, and border path likely never grew
  either. onBordersScopeLoaded''s cross-jig Loaded listener is the sole bootstrap
  path and is the prime suspect.'
priority: high
opened: '2026-09-06'
closed: '2026-09-06'
---

<!-- board:start -->
<!-- board:end -->

## Summary

New-map bootstrap: boss and border path not created

## Log

- 2026-09-06: Confirmed fixed. Project owner applied SAT_048's fix; the next playtest's `run-server/logs/latest.log` shows the diagnostic logging working exactly as designed: `onBordersScopeLoaded`'s receipt log now reports `side=SERVER` on the real server thread (previously always `CLIENT`), followed by "BORDERS_JIG loaded server-side... running bootstrap check" -> "not yet seeded -- growing..." -> `BordersPathFacet.grow()`'s own "bootstrapping the level's first border... succeeded -- border=... layer=0" -> "bootstrap complete -- border=... boss=...". Confirmed multiple full grow cycles afterward too (layer 1, layer 2), each with a boss position finalized -- the whole Boss/Border bootstrap and progression loop is working end-to-end on a dedicated server for the first time this project cycle.

  Diagnostic logging removed per project owner's request now that the fix is confirmed:
  - `BossJigHandlers.onBordersScopeLoaded`: reverted to its pre-diagnostic body (the original warn-only logging on the two real failure branches stays; the unconditional receipt log, the "loaded server-side" trace, the "already seeded" trace, and the "bootstrap complete" summary are all removed).
  - `BordersPathFacet.grow()`/`grow(BlockPos)`: reverted to their pre-diagnostic bodies (no logging at all, matching how this file looked before this investigation).

  Both files are now byte-identical to their state before FRO_090's diagnostic pass (confirmed via `git diff --stat` showing zero changes on either file).

  Closing this ticket -- root cause was SAT_048 (Satchel.isServer() inverted), already fixed there. No further FrontierMode-side work needed for the original symptom.
- 2026-09-06: Root cause found. The diagnostic logging landed exactly as intended: the fresh playtest's `run-server/logs/latest.log` (a true dedicated server, confirmed via the `DedicatedServer`/offline-mode banner and `[Server thread]`-prefixed lines) shows the new unconditional receipt log firing correctly for `onBordersScopeLoaded` -- including for `jig=...frontiermode:borders_jig... scope=LevelScope[...overworld]...` -- but every single line reports `side=CLIENT`, even on the real server thread. No "BORDERS_JIG loaded server-side" / "grow()" / "bootstrap complete" line appears anywhere after it, confirming the `if (!Satchel.isServer()) return;` guard right after the jig filter is what's silently swallowing the bootstrap every time.

  Root cause is not in FrontierMode: `Satchel.isServer()` itself is inverted (`return side != LogicalSide.SERVER;` -- backwards). Filed as SAT_048 with the full trace, blast radius (also explains the "CLIENT bundle became dirty" warning and silently-broken border pregeneration), and the one-line fix. Not applied here -- Satchel/src is outside this role's lane; SAT_048 is for the Architect/Satchel maintainer.

  FRO_090 stays open pending that fix landing -- the diagnostic logging added here should be left in place until then, since it's exactly what will confirm the fix once applied (receipt log should start showing `side=SERVER`, followed by the bootstrap-complete line).
- 2026-09-06: Project owner report from today's first successful playtest (post FRO_088/FRO_089 fixes): "boss is not created on new map," with the border path likely never growing either.

  Investigation (no code changes yet at this point, just tracing + reading both run logs and the actual world save):

  - Traced the entire new-map bootstrap path. It runs through exactly one place: `BossJigHandlers.onBordersScopeLoaded`, a handler registered on BOSS_JIG that cross-listens for BORDERS_JIG's own `ScopeEvent.Loaded` (intentional design, FRO_075 -- Satchel's event bus is global/shared, one jig's handler can subscribe to another jig's lifecycle events). Confirmed there is no duplicate/legacy bootstrap left behind in `BorderModule`/`BorderJigHandlers` -- that file doesn't even exist; `BorderModule.java` explicitly comments that the bootstrap hook moved to Boss and Border "no longer reaches into Boss for this."
  - Read every branch of `onBordersScopeLoaded()`, `BossAPI.createBoss()`, and `BossFixture.create()`. Every failure branch along that chain already calls `OUT.warn(...)`. None of those warnings appear anywhere in either `run/logs/latest.log` or `run-server/logs/latest.log` from the playtest. Neither does any success confirmation -- but that's because the success path never logged anything at all, a pre-existing observability gap independent of the actual bug.
  - Checked the actual world save rather than relying on logs alone: `run-server/world/data/` (where Satchel's `BundleSavedData` persistence writes) was completely empty after the full session -- stronger evidence than log silence that nothing from either jig's bundle ever flushed to disk this run.
  - Audited every event-handler registration in FrontierMode for the same shape of bug (project owner's ask: "make sure there's no one waiting for an event that never arrives too"). Grepped every `.on(ScopeEvent...)`/`.on(MobDied...)` registration and every `info.jigInfo().key` filter in the codebase. `onBordersScopeLoaded` is the *only* handler in the entire mod that's registered on one jig but filters for a *different* jig's event -- every other handler (`onTick`, `onPlayerScopeTick`, `Rendering.onClientTick/onClientUnload`, `BossMobJigHandlers`) either filters for its own jig or is intentionally jig-agnostic with a defensive instanceof guard (`Rendering.onClientUnload`'s own doc comment says as much). So this audit turned up exactly one genuine "waiting on someone else's event" listener -- the one already suspected.
  - One unrelated oddity, filed here rather than chased down: the client log shows `[engine] CLIENT bundle became dirty (read-only violation): BordersBundle[...]`. Traced the message to Satchel's own `ScopeEngine_Client`, not anything touched this session -- did not isolate the actual mutating call. Not clearly connected to the bootstrap gap (BORDERS_JIG runs BOTH-applicability by design, so a legitimate client-side BordersBundle exists; something is just mutating it when it shouldn't).

  Diagnostic logging added (FrontierMode/src only, nothing in Satchel/src touched):
  - `BossJigHandlers.onBordersScopeLoaded`: an unconditional `OUT.info` at the very top, before either filter, logging jig key + scope + side for every `ScopeEvent.Loaded` this handler receives (Loaded is a rare once-per-scope event, not a tick, so this is cheap and permanent-safe). This directly answers "is anyone even listening and does the event ever arrive." Plus new info-level lines for: entering the bootstrap check (BORDERS_JIG loaded server-side for overworld), the already-seeded skip (previously silent), the not-yet-seeded/about-to-grow branch, and a final bootstrap-complete summary with the resulting border id and boss id (or an explicit "createBoss failed" note, since that path already warns separately).
  - `BordersPathFacet.grow()` and `grow(BlockPos)`: added an entry log (bootstrapping vs. growing from tip, which level) and a success log (border id + layer) to both overloads, plus a warn on `applyProposal` rejection (previously the rejection reason traveled back in the `Result` but nothing logged it at this layer -- `BordersCrudFacet` itself does warn on proposal rejection separately, so this is a second confirmation at the call site, not the only one).

  Next step: project owner will run one more fresh-map playtest with this build. The new logs should show conclusively whether `ScopeEvent.Loaded` for BORDERS_JIG ever posts on the server at all:
  - If the "received ScopeEvent.Loaded -- jig=BORDERS_JIG side=SERVER" line never appears, the scope's lifecycle promotion itself never happens on a truly fresh world -- that points at Satchel's own scope-readiness/coupler plumbing (`AScopeCoupler`/`ScopeLifecycleDispatcher`), which is out of this role's lane to fix directly; would need a ticket to the Architect/Satchel side with these findings attached.
  - If it does appear and the bootstrap-complete line shows a real border+boss, the bug is downstream of creation (e.g. `/boss` command selector, or the persistence flush that leaves `world/data/` empty) -- would chase that specifically next.

  Not committed (git managed by project owner this session).
- 2026-09-06: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
