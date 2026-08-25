---
id: FRO_025
uid: FRO
number: 25
client: FrontierMode
status: done
title: 'Client crash: BORDERS_JIG not installed on CLIENT side'
context: 'First real client+dedicated-server run after FRO_024''s fix: client crashed
  on render thread with JigNotFound for frontiermode:borders_jig. Server installed
  all 3 registered jig configs correctly; client installed 0. Resolved on re-run with
  diagnostic logging in place -- CLIENT now correctly installs 1/1. Root mechanism
  (why it failed originally) not fully confirmed -- see closing log entry.'
priority: high
opened: '2026-08-16'
closed: '2026-08-16'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Reported by the project owner from a fresh `run/crash-reports/crash-2026-08-16_11.16.22-client.txt`
— the first client run against a rebuilt jar (Satchel `%23191`, FrontierMode `%23198`), so
[FRO_024](FRO_024_rendering-eager-static-crash.md)'s fix was in and the client got past the
previous eager-static crash. Player was ~6 minutes into a real session (`Level time: 7041`) when
it crashed on the render thread:

```
com.arryn.satchel.common.jig.guts.SatchelException$JigNotFound: No Jig scopeInfo was found:
JigInfo not installed for key: JigKey[id=..., name="frontiermode:borders_jig", scopeType=LevelJig]
  at LogicalFoundation.requireJigInfo -> tryScopeInfo -> BorderAPI.borders -> RenderContext.fixture
  -> RenderContext.standby -> WorldBordersRenderer.render -> Rendering.onRenderLevel
```

**Confirmed facts, from both client and server `logs/debug.log`:**
- `BorderModule.init()` (FrontierMode), `TrackingModule.init()`, and `PlayerTrackingModule.init()`
  (both Satchel) all run cleanly on both sides, same log pattern, no exceptions, no
  "Duplicate JigConfig" or validator errors anywhere near mod construction on either side.
- Server: `Installed 3 jig configs for side SERVER` (Border=BOTH, Tracking=SERVER,
  PlayerTracking=SERVER — all 3 correctly apply to SERVER).
- Client: `Installed 0 jig configs for side CLIENT` — should be 1 (only Border is
  BOTH-applicability; `appliesToSide(BOTH, side)` in `JigConfigCompiler` is an unconditional
  `true`, so this can't be an `appliesToSide` logic bug).
- Both sides read `Satchel-0.0.2-dev.jar` with an identical sha256 (confirmed via checksum) and
  FrontierMode runs from the same `build/classes` output for both `runClient`/`runServer` — ruled
  out a stale-jar mismatch.
- `build.gradle` has no shading/embedding of Satchel's classes into FrontierMode's own output
  (only `compileOnly files(satchelJar)`) and both `runs.client`/`runs.server` share the same
  `mods {}` source config via `configureEach` — ruled out classpath duplication as an obvious
  cause (though not fully ruled out at the JVM level — see below).
- Registration (mod construction, `modloading-worker-0`) finishes ~3.4s before
  `ClientFoundationBooter.installFoundation()` runs (`Render thread`, first `ClientTickEvent.END`
  of the session) — no plausible ordering race, and FML's phase-transition synchronization should
  provide a happens-before edge between mod construction and the game loop starting regardless of
  which thread does what (this is standard FML behavior every mod relies on, so a raw
  cross-thread visibility bug here would be surprising, not impossible).
- `compileForSide()` returned cleanly (no exception logged, `frozen` didn't block anything,
  `INSTALLED` only flips true after a successful `installFoundation()` call, and it only ran
  once) — this rules out a validator exception mid-loop or a partial/aborted compile.

**Not yet determined:** whether CLIENT's `compileForSide()` call is reading a `CONFIGS` map with
genuinely fewer entries than what `register()` put there (map identity or visibility issue), or
something else entirely. Investigated by Lead Dev (Curtis) via static log/code analysis only — no
way to attach a debugger or add print-and-rerun in this sandbox (no Forge/Mojang maven access, see
[FRO_023](FRO_023_playtest-checklist-batch2.md)).

**Diagnostic logging added** (`JigConfigCompiler.register()` and `.compileForSide()`, both now
log `Thread.currentThread().getName()`, `System.identityHashCode(CONFIGS)`, and the map size) —
purely additive, no behavior change. A rebuild + one more client run will show directly whether
`register()`'s three calls and `compileForSide()`'s one call are looking at the same map instance
with the same growing size, which should make the actual cause obvious instead of requiring more
guessing. Marked to be removed once this resolves.

**Deliberately not done:** did not make `tryScopeInfo`/`BorderAPI.borders()` swallow
`JigNotFound` to "fix" the crash — that would silently convert a real, diagnosable defect (a jig
that never installs on the client) into permanently dead client-side rendering with no signal
anything's wrong, which is worse. Per project owner's standing instruction, not deciding an
architectural question (why does registration silently not reach the client's compiled config
set) unilaterally without being able to verify a fix — flagging it here instead.

**Ask:** rebuild both repos, run the same client+server session again (reproducing the crash is
fine — it should crash the same way, just with more log lines now), and share
`FrontierMode/run/logs/debug.log` (or just the `[JigConfigCompiler]` lines from it) back. That
should be enough to close this out on the next pass.

## Log

- 2026-08-24: Normalized `status: closed` -> `done` per [BKHL_006](BKHL_006_closed-status.md) —
  outside BHT's `open/in-progress/blocked/done` vocabulary, no distinct meaning intended.
- 2026-08-16: **Resolved on re-run.** Project owner rebuilt and reran with the diagnostic logging
  in place. New `[JigConfigCompiler]` lines confirm: on CLIENT, `register()` and
  `compileForSide()` now see the **same map identity** (`mapIdentity=1233264370` both times) and
  the **same growing size** (3 after all three registrations, 3 seen at compile time) —
  `Installed 1 jig configs for side CLIENT` (correct: only Border's BOTH-applicability config
  should apply to CLIENT). No crash report generated this session; client ran a full
  overworld → nether → overworld session cleanly (`Stopping!` at the end, no exception).
  **Root mechanism not fully confirmed** — the code path is unchanged from the crashing run
  except for the two added log lines, so this either means the original failure was a genuine,
  non-reproduced cross-thread visibility gap on the un-synchronized static `CONFIGS`
  `LinkedHashMap` (plausible: `register()` writes on `modloading-worker-0`, `compileForSide()`
  reads on the client's `Render thread`, and adding logging/I-O around both call sites can
  incidentally close a narrow timing window without addressing the underlying lack of
  synchronization), or something else that this session's evidence doesn't fully distinguish.
  Leaving the diagnostic logging in place for now (harmless at DEBUG level) rather than pulling it
  immediately — if this recurs, the next log will still have the instrumentation. Worth a real fix
  (e.g. making `CONFIGS` a `ConcurrentHashMap` or adding an explicit memory barrier at the
  construction/compile boundary) at some point regardless of whether it was the actual cause here,
  since an unsynchronized static field shared across FML's worker and render threads is fragile by
  construction even if it happened to work this time. Not filing that as a new ticket
  preemptively — no repro, no evidence it's still broken, would be speculative work. Unblocking
  [FRO_023](FRO_023_playtest-checklist-batch2.md)'s client-rendering checklist items.
  Also observed (not a bug): `BundleNotFound ignored; falling through to create` +
  `[engine] CLIENT bundle became dirty (read-only violation)` WARN fired once per new
  scope/dimension this session — this is the documented SAT_024 first-creation fallback path
  firing exactly as designed, immediately followed by `clearDirty()` and a successful
  `Borders loaded: 5`. Noting only so it isn't mistaken for a new bug on the next log read.
- 2026-08-16: Investigated by Lead Dev (Curtis) — see Summary for full findings. Added additive
  diagnostic logging to `JigConfigCompiler` (Satchel), left the crash itself unfixed pending a
  re-run with that logging, per project owner's standing instruction not to guess at architectural
  fixes without verification. Also flagged in [FRO_023](FRO_023_playtest-checklist-batch2.md) —
  client-side items on that checklist are blocked until this resolves.
- 2026-08-16: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
