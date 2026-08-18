---
id: FRO_023
uid: FRO
number: 23
client: FrontierMode
status: closed
title: 'Build+playtest checklist: Sat020/Fro009-013'
context: Sandbox has no Forge/Mojang maven access; owner runs real gradlew build +
  playtest per checklist in ticket body. All five nodes (RM_SAT_020, RM_FRO_009/011/012/013)
  confirmed and resolved as of 2026-08-16.
priority: normal
opened: '2026-08-16'
closed: '2026-08-16'
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

**Before running any of this: rebuild.** [FRO_024](FRO_024_rendering-eager-static-crash.md) fixed
a real dedicated-server crash found from the first `run-server/latest.log` (`Rendering`'s eager
static field construction crashed on the first server tick) — that fix has to actually be in the
jar being tested, or every item below hits the same crash before getting anywhere.

**[FRO_025](FRO_025_client-crash-borders-jig-not-installed-o.md) is resolved (2026-08-16)** — the
client crash from the first post-rebuild session (BORDERS_JIG never installing client-side) didn't
recur on the next run; client now installs its jig config correctly and completed a full
overworld/nether session with no crash. Client-side items below are unblocked. See that ticket's
closing log entry for one caveat: the root mechanism isn't fully confirmed, so if this crash comes
back, it's not a new bug — check FRO_025 first.

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
- [x] Connect a real client (`./gradlew runClient`) to a running dedicated server.
- [x] **`PlayerTrackingModule` confirmed (2026-08-16)** — read directly from
  `[PlayerTracking]` lines in a real login/nether/logout/login/overworld/logout session:
  - [x] Login: three clean `LOADED` lines, one per login.
  - [x] Dimension change: confirmed live, mid-session, twice — `TICK` lines show `dim=` change
    while `scope=` stays identical, no second `LOADED` in between.
  - [x] Disconnect: three clean `LOADED`/`UNLOADED` pairs, no accumulation. Node's done-bar met —
    see RM_SAT_020's log.

**RM_FRO_009 — Clean up dead BorderView code.** Low-risk pure deletion.
- [x] `gradlew build` succeeds after removing `BorderView`.
- [x] `runClient`, join a world with an active border, confirm rendering is unaffected — "border
  rendering works" per project owner, confirmed in the same session as RM_SAT_020's test above.

**RM_FRO_011 — Border mutation validation hardening.** Command-reachable, singleplayer/integrated
is sufficient (no networking-boundary dependency).
- [x] `/border transform <selector> here` — confirmed individually (`/border transform 0 here`).
- [x] `/border transform <selector> <pos>` (omit radius) — confirmed individually
  (`/border transform 0 ~ ~ ~ 15`, relative coords, via the vanilla `pos` argument).
- [x] `/border transform <selector> radius <r>` (omit position) — confirmed individually
  (`/border transform 0 radius 100`). Project owner's call to stop testing variations here —
  agreed, item 1 is closed.
- [x] `/border add ~ ~ ~ 999999999 0` — rejected, not silently accepted. Confirmed across three
  rounds: no more crash-looking error, single log line (not double), and now the player sees the
  actual reason (`radius 999999999 outside allowed range [1, 512]`) instead of a generic message.
- [x] `/border path fixlayers` — confirmed behaving as designed: reports "No changes made" rather
  than a false "Reconciled" positive. (Not because there's nothing to fix — the reorder logic
  itself isn't built yet, on purpose, per RM_FRO_011's log. Low priority, parked for
  Architect/PM.)
- [x] Tab-complete on a border selector argument shows `@none` — confirmed present (alphabetical
  order: `@all`, `@containing`, `@coord`, `@none`, `@relevant`). Separately noted: `@all` being
  the first suggestion for `/border transform` specifically feels like the wrong default to the
  project owner (`@relevant` would fit better) — real but low-priority UX idea, not a bug, not
  actioned.
- [x] No leftover `Config.java` boilerplate values referenced anywhere (grep clean).

**RM_FRO_012 — Client render lifecycle cleanup.** Singleplayer/integrated is sufficient.
- [x] No `FLAME` particle spawns above the player during normal play.
- [x] Visit two different worlds/dimensions in one client session — confirmed twice now, zero
  errors both times. `RenderContext.CACHE`'s single-live-entry claim specifically still isn't
  provable from text logs, but treating the done-bar as satisfied in practice — see RM_FRO_012's
  log for the reasoning (no diagnostic added, no evidence of a problem, revisit only if a real
  symptom shows up later).

**RM_FRO_013 — Border fixture & compass robustness.** Singleplayer/integrated is sufficient.
- [x] Hand-edit one border entry in a save file to be malformed (bad UUID tag, wrong type); confirm
  the rest of that world's borders still load and only the bad entry is skipped (check logs for
  the skip message). **Confirmed** — fresh server start showed `[Border] Skipping malformed
  border entry during load: ...Expected UUID-Tag to be of type INT[], but found STRING.` followed
  by `Borders loaded: 5` (Ironveil skipped, other five intact).
- [x] Move a frontier compass to the offhand slot, wait through a few finder-items poll cycles
  (~5 ticks each), confirm no duplicate compass appears in inventory. Confirmed, and extended:
  also no duplication in the main hand after a path-grow event.
- [x] Grep confirms `BordersAPIException` is gone — re-confirmed directly (`grep -rn
  BordersAPIException FrontierMode/src`, no hits).

## Log

- 2026-08-16: **All five nodes confirmed — ticket closed.** Malformed-entry test (RM_FRO_013's
  last open item) confirmed on a fresh server start: correct skip warning, five borders loaded
  (not six), server ran normally. RM_SAT_020, RM_FRO_009, RM_FRO_011, RM_FRO_012, RM_FRO_013 all
  marked `resolved` on their own roadmap nodes.
- 2026-08-16: **Second playtest round — everything closed except the malformed-entry test.**
  RM_SAT_020 and RM_FRO_009 (untouched in the first round) both confirmed clean from a real
  login/nether-portal/logout/login/overworld/logout session. RM_FRO_011's three `/border
  transform` forms confirmed individually, `fixlayers` confirmed behaving as designed, `@none`
  tab-complete confirmed present, the add-rejection message fix confirmed showing the real reason.
  RM_FRO_012's dimension-visit item accepted as satisfied without a diagnostic (project owner
  asked for a recommendation; two clean round-trips with zero symptoms was judged sufficient).
  RM_FRO_013's `BordersAPIException` grep re-confirmed clean directly. Only remaining item across
  all five nodes: the malformed-save-entry test, now staged directly (Lead Dev corrupted a real
  border entry in the project owner's own save, backed up first) — awaiting the project owner's
  restart + log check. This ticket can close as soon as that one comes back.
- 2026-08-16: First real playtest pass reviewed (project owner + Lead Dev, reading
  `run/logs/latest.log` + `run-server/logs/latest.log` together, plus project owner's direct
  observations for items logs can't show). RM_FRO_011, RM_FRO_012, RM_FRO_013 checkboxes updated
  above — see each item for specifics. One real bug found and fixed in the process: `/border add`/
  `/border transform` rejections were surfacing as Brigadier's generic "unexpected error" instead
  of the actual reason, plus a redundant double-validation call — both fixed in `BorderAPI.java`
  and `BorderCommandHandler.java`, unverified pending rebuild. Still outstanding: individual
  `/border transform` form isolation, `/border path fixlayers`, `@none` tab-complete visual check,
  the malformed-save-entry test, and a `BordersAPIException` grep re-check. RM_SAT_020's items
  untouched this pass (not part of this feedback round).
- 2026-08-16: RM_SAT_020's login/dimension-change/logout item updated — `PlayerTrackingModule`
  (see that node's own log) now makes this a real log-watching check instead of an unresolved
  "no way to do this yet." Also added a rebuild note referencing
  [FRO_024](FRO_024_rendering-eager-static-crash.md)'s crash fix, found from the project owner's
  first real `run-server/latest.log`.
- 2026-08-16: Ticket opened, checklist drafted by Lead Dev ahead of implementation. Project owner
  will execute against real builds and update each roadmap node's own log with results — this
  ticket can close once all five nodes are confirmed.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
