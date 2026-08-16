---
id: SAT_029
uid: SAT
number: 29
client: Satchel
status: done
title: SatchelNetwork.send() casts LevelScope directly to ServerLevel
context: info.scopeAs() unchecked-cast to ServerLevel, but the runtime scope object
  is a LevelScope wrapping a Level, never a ServerLevel itself. Compiled fine (erasure),
  threw ClassCastException the first time scheduleSync() actually executed -- i.e.
  the first time SAT_027/FRO_018's fix chain let a server bundle's flush reach the
  sync call at all.
priority: high
opened: '2026-08-14'
closed: '2026-08-14'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Tenth real crash — and the first one to actually reach `SatchelNetwork.send()`, thanks to
[SAT_027](SAT_027_server-hydrate-bypasses-lifecycle.md)/
[FRO_018](FRO_018_border-executionpulse-disabled.md) finally letting a server
bundle reach `ACTIVE` and get ticked/flushed for the first time in this whole run sequence:

```
java.lang.ClassCastException: class com.arryn.satchel.common.jig.level.LevelScope cannot be cast
to class net.minecraft.server.level.ServerLevel
	at com.arryn.satchel.common.net.SatchelNetwork.send(SatchelNetwork.java:66)
	at com.arryn.satchel.common.persistence.ParcelEgressSink.emit(ParcelEgressSink.java:52)
	at com.arryn.satchel.server.jig.guts.ScopeEngine_Server.scheduleSync(ScopeEngine_Server.java:363)
	at com.arryn.satchel.common.bundle.SatchelBundle.pulseSync(SatchelBundle.java:275)
	at com.arryn.satchel.server.jig.guts.ScopeEngine_Server.onExecutionPulse(ScopeEngine_Server.java:229)
```

**Root cause:** `SatchelNetwork.send(ScopeInfo info, Object packet)` did `ServerLevel level =
info.scopeAs();`. `ScopeInfo.scopeAs()` is an unchecked generic cast
(`@SuppressWarnings("unchecked") public <S extends SatchelScope> S scopeAs() { return (S) scope;
}`) — it compiles against whatever type the call site infers, with no runtime check. The actual
runtime object behind every `LevelJig` scope is a `LevelScope` (wraps a `Level`, has its own
`.level()` accessor) — never a `ServerLevel` directly, which isn't even a `SatchelScope` subtype.
Type erasure let this compile silently; nothing caught it until this exact line actually executed,
which required the entire fix chain ahead of it (SAT_023 → FRO_015 → SAT_024 → SAT_025 → FRO_016 →
FRO_017 → SAT_026 → FRO_018 → SAT_027) to all land first.

**Fix applied:** changed to `LevelScope levelScope = info.scopeAs(); ServerLevel level =
(ServerLevel) levelScope.level();` — goes through the scope's own real accessor first, then casts
the actual `Level` to `ServerLevel` (safe here since `Satchel.requireServer()` runs immediately
above). Matches this method's own doc comment, which already says it's currently LevelScope-only
and future scope types would need different distribution logic entirely.

**Left `in-progress`, not `done`:** need a real re-run to confirm — this was the last visible
crash in the log the user pasted; unclear yet whether anything else is waiting behind it.

## Log

- 2026-08-14: Confirmed — user re-ran with no crash. `SatchelNetwork.send()` is now reliably
  reached and completes; the log shows real parcels flowing and even growth chat messages
  arriving client-side ("[Border] Thornwall...", "[Border] Skyreach..."). Closing. (A separate,
  new bug found in that same log — parcels past the first one for a given bundle were being
  dropped — is [SAT_030](SAT_030_client-refresh-single-shot-hydrate.md), not a regression of this
  fix.)
- 2026-08-14: Root cause traced (see above), fix applied to `SatchelNetwork.java`. Left
  `in-progress` pending a real re-run to confirm.
- 2026-08-14: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
