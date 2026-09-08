---
id: SAT_048
uid: SAT
number: 48
client: Satchel
status: done
title: Satchel.isServer() is inverted
context: 'Root cause of FRO_090: side check returns backwards, breaking every server-only
  gate mod-wide.'
priority: high
opened: '2026-09-06'
closed: '2026-09-06'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Satchel.isServer() is inverted

## Log

- 2026-09-06: Project owner applied the fix. Confirmed working from the next playtest's server log: onBordersScopeLoaded's diagnostic receipt log (added for FRO_090) reported side=SERVER on the real server thread instead of the previous CLIENT, and the full Boss/Border bootstrap chain ran end-to-end for the first time -- border grown, boss created, position finalized, then repeated correctly across further layer growths (layer 1, layer 2) later in the same session. Closing.
- 2026-09-06: Root cause of FRO_090 (new-map bootstrap: boss and border path not created), found via the diagnostic logging added for that ticket, confirmed against the fresh playtest logs (not just read -- this is empirical, from actual log output).

  **The bug**, `Satchel.java` around line 194:

  ```java
  public static boolean isServer(){
      var side = require().side();
      return side != LogicalSide.SERVER;
  }
  ```

  The comparison is backwards -- this returns `true` when the side is NOT server (i.e. on the client) and `false` when it genuinely IS the server. Compare to `requireServer()`/`requireClient()` immediately above it in the same file, which use the identical `side != LogicalSide.X` pattern correctly (to decide whether to *throw*, where the inverted sense is the right one) -- `isServer()` looks like it was copy-pasted from that pattern without flipping the return.

  **Confirmed empirically** from the fresh `run-server/logs/latest.log` (a true dedicated server process, no client in this JVM at all -- confirmed via `[Server thread]`-prefixed lines and the earlier `DedicatedServer`/`OFFLINE MODE` banner): FRO_090's new unconditional `onBordersScopeLoaded` receipt log printed `side=CLIENT` for every single received event, including the ones on the actual server thread. Every downstream `Satchel.isServer()` gate in the mod is therefore silently inverted on the real server.

  **Confirmed blast radius** -- every call site of `Satchel.isServer()` mod-wide (grepped both Satchel/src and FrontierMode/src, only 3 hits):

  1. `Satchel/src/.../common/bundle/SatchelBundle.java:234` -- `if (Satchel.isServer()) { markDirty(); }` in `onCreated()`. Meant to mark a freshly-created bundle dirty only when the creation happened server-side (so it persists/syncs). Inverted: now marks it dirty on the CLIENT instead. This is the exact, previously-unexplained "[engine] CLIENT bundle became dirty (read-only violation): BordersBundle[...]" warning flagged as an unrelated oddity in FRO_089's and FRO_090's logs -- it's not unrelated, it's this same bug. Confirmed still reproduces in this session's client log.
  2. `FrontierMode/src/.../border/common/fixture/BorderPregenFixture.java:195` -- `if(!Satchel.isServer()){ return; }` inside `onJigTick()`'s tick-driven work. Meant to skip pregeneration work on the client and run it on the server. Inverted: now returns early on the real server, meaning **border pregeneration has never run on a dedicated server** -- a second, previously undiagnosed casualty of this same bug, not something wrong with FrontierMode's own pregen code.
  3. `FrontierMode/src/.../boss/BossJigHandlers.java` (two call sites: `onTick`'s server guard, and `onBordersScopeLoaded`'s server guard). This is FRO_090's actual symptom -- `onBordersScopeLoaded` receives BORDERS_JIG's `ScopeEvent.Loaded` for the overworld just fine (confirmed in the logs), but its `if (!Satchel.isServer()) return;` guard bails immediately afterward on the real server, so the bootstrap grow()/createBoss() never runs. `onTick`'s identical guard likely means BOSS_JIG's own per-tick work (`finalizeUnpositioned`, `materializeUnresolved`, path/boss reconciliation) has also never run server-side.

  **Proposed fix** (one line, `Satchel.java`): `return side == LogicalSide.SERVER;`

  **Not applied by this role.** Per Dev(FrontierMode)'s lane, Satchel/src is never touched directly -- this is filed here for the Architect/Satchel maintainer, with the exact line and fix identified, rather than patched. FRO_090's diagnostic logging (already in place, not yet removed) will directly confirm the fix once applied: the same unconditional receipt log should start showing `side=SERVER` for events received on the actual server thread, and the "BORDERS_JIG loaded server-side..." / "grow(): succeeded..." / "bootstrap complete..." lines should start appearing.

  Not committed (git managed by project owner this session).
- 2026-09-06: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
