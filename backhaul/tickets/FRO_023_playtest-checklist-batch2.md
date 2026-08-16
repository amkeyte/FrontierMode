---
id: FRO_023
uid: FRO
number: 23
client: FrontierMode
status: open
title: 'Build+playtest checklist: Sat020/Fro009-013'
context: Sandbox has no Forge/Mojang maven access; owner runs real gradlew build +
  playtest per checklist in ticket body.
priority: normal
opened: '2026-08-16'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

Admin ticket, not a bug — this sandbox has no Forge/Mojang maven access (confirmed: `curl` to
`maven.minecraftforge.net`/`files.minecraftforge.net`/`libraries.minecraft.net` all return 403
from the proxy), so Lead Dev can't run a real `gradlew build` or playtest here. Project owner
will run the checks below directly and update each node's log with results — this ticket exists
so the checklist lives in one place instead of being scattered across five roadmap-node done-bars.
Covers the batch handed off in [FRO_022](FRO_022_handoff-batch2.md): RM_SAT_020, RM_FRO_009,
RM_FRO_011, RM_FRO_012, RM_FRO_013.

**Build, both repos, in order** (Satchel first — FrontierMode's `compileOnly files(satchelJar)`
and `buildSatchel` task both depend on a fresh Satchel jar):
1. `cd Satchel && ./gradlew build`
2. `cd FrontierMode && ./gradlew build` (this alone triggers `buildSatchel` again via
   `runClient`/`runServer`'s task dependency, but a plain `build` doesn't — run
   `./gradlew runClient` at least once, or `./gradlew buildSatchel` directly, to be sure the jar
   FrontierMode compiled against is current)

**RM_SAT_020 — Build PlayerJig/PlayerScope.** Needs a **real dedicated server** (`./gradlew
runServer` in Satchel or FrontierMode, whichever hosts the test), not just singleplayer/integrated
— see the node's own done-bar (added 2026-08-16) for why: `PlayerJig`'s login/logout ingress is
the first jig kind in either repo driven by a per-connection event instead of a per-level one, and
dedicated-server deployment is specifically where the same-JVM safety net singleplayer provides
stops covering for sidedness bugs.
- [ ] Connect a real client (`./gradlew runClient`) to a running dedicated server.
- [ ] Confirm `PlayerScope` state is created on login (check via log output or a temporary debug
  command — no real consumer exists yet, so this is necessarily a synthetic check).
- [ ] Change dimension, confirm the same `PlayerScope`'s state persists across the change (no
  manual handoff, no reset).
- [ ] Disconnect, confirm the scope actually tears down (no leaked `PlayerScope` per session —
  check for repeated entries after reconnecting a few times).
- [ ] Note in the node's log whether this was a synthetic check (temporary test hook) or found a
  real consumer to exercise it, since `RM_FRO_006` doesn't land until later.

**RM_FRO_009 — Clean up dead BorderView code.** Low-risk pure deletion.
- [ ] `gradlew build` succeeds after removing `BorderView`.
- [ ] `runClient`, join a world with an active border, confirm rendering is unaffected (ring +
  growth-trigger particle still draw correctly).

**RM_FRO_011 — Border mutation validation hardening.** Command-reachable, singleplayer/integrated
is sufficient (no networking-boundary dependency).
- [ ] `/border transform <selector> here` — no longer throws.
- [ ] `/border transform <selector> <pos>` (omit radius) — no longer throws.
- [ ] `/border transform <selector> radius <r>` (omit position) — no longer throws.
- [ ] `/border add ~ ~ ~ 999999999 0` — rejected or clamped, not silently accepted.
- [ ] `/border path fixlayers` — either actually reconciles layer order to path order, or reports
  what it actually did instead of a false "Reconciled" success message.
- [ ] Tab-complete on a border selector argument shows `@none`.
- [ ] No leftover `Config.java` boilerplate values referenced anywhere (grep clean).

**RM_FRO_012 — Client render lifecycle cleanup.** Singleplayer/integrated is sufficient.
- [ ] No `FLAME` particle spawns above the player during normal play.
- [ ] Visit two different worlds/dimensions in one client session; confirm `RenderContext.CACHE`
  has only one live entry after leaving the first (a debug log line or breakpoint check is fine —
  there's no player-facing signal for this one).

**RM_FRO_013 — Border fixture & compass robustness.** Singleplayer/integrated is sufficient.
- [ ] Hand-edit one border entry in a save file to be malformed (bad UUID tag, wrong type); confirm
  the rest of that world's borders still load and only the bad entry is skipped (check logs for
  the skip message).
- [ ] Move a frontier compass to the offhand slot, wait through a few finder-items poll cycles
  (~5 ticks each), confirm no duplicate compass appears in inventory.
- [ ] Grep confirms `BordersAPIException` is gone (or, if built out instead, has a real throw
  site).

## Log

- 2026-08-16: Ticket opened, checklist drafted by Lead Dev ahead of implementation. Project owner
  will execute against real builds and update each roadmap node's own log with results — this
  ticket can close once all five nodes are confirmed.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
