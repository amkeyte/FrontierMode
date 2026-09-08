---
id: FRO_055
uid: FRO
number: 55
client: FrontierMode
status: done
title: Growth-trigger particles don't follow new boss loc
context: '[Susan_02] GrowthTriggerRenderer particles stay at old spot after boss-defeat
  border growth.'
priority: normal
opened: '2026-08-28'
closed: '2026-09-07'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Found live during [RM_FRO_019](../roadmap/RM_FRO_019_karen.md) ("Karen") playtesting, against
[FRO_045](FRO_045_karen-build.md)'s freshly-built defeat -> grow -> next-boss loop. Owner's report:
after a tagged boss is defeated and the border grows, the growth-trigger particle effect keeps
rendering at the previous (now-cleared) location instead of relocating to the new tip / next boss
location.

**Where this lives:** `border/client/render/level/GrowthTriggerRenderer.tick()` — client-only,
purely visual, no gameplay authority. Reads its anchor via
`RenderContext.pathTip() -> BordersPathFacet.tip()`, which returns
`fixture.borderPath.get(fixture.borderPath.size() - 1)` — the last entry in the client's own
synced `borderPath` list.

**Server-side check (done, looks correct):** `BordersPathFacet.grow(BlockPos)`
(FRO_045's new overload) does `fixture.borderPath.add(result.border().id())` — appends, so the
just-grown border becomes the new last element / new `tip()` result — then calls
`fixture.markSeeded()` / `fixture.markPathDirty()`, same as the pre-existing no-arg `grow()`. On
the server side alone, `tip()` should already report the new border immediately after a defeat.

**Not yet diagnosed further — needs Architect/Lead-Dev investigation, not assumed here:**
whether this is a client-sync timing/delivery gap (the dirty-mark not reaching the client fast
enough, or not at all, after `markPathDirty()`) or a client-side render-cache issue in
`RenderContext` (`standby()`'s `revisionMonitor.poll(...)` throttle, or a stale
`cachedPathTip`/`cachedBorders` not being invalidated on this particular mutation path).

**Worth checking together with [FRO_051](FRO_051_border-load-count-mismatch.md)** — an existing
open, unrelated-looking client/server `BordersFixture` sync-count mismatch on a fresh world,
confirmed not caused by FRO_047's diff. Different symptom, same general area (client-side
`BordersFixture` state trailing the server's) — worth a shared look rather than assuming these are
two unrelated bugs.

Not fixed here per project owner's own instruction ("let's ticket this") — logged, not
investigated live, while playtesting continues.

## Log

- 2026-09-07: **Confirmed closed, direct evidence this time -- a fresh session's client log shows exactly the check this ticket asked for.** Unlike the previous session (where the `[FRO_055]` diagnostic never once appeared client-side), this session's `run/logs/debug.log` shows it firing repeatedly on the Render thread and tracking every real grow exactly:

  | client `size` | client `tip` (short) | matches server's `[BorderPregen]`/`finalizeUnpositioned` border |
  |---|---|---|
  | 1 | ...f1b78b06 | f1b78b06 (boss layer 0) |
  | 2 | ...63bc8fec | 63bc8fec (boss layer 1) |
  | 3 | ...611a6e41 | 611a6e41 (boss layer 2) |
  | 4 | ...dcf54fd4 | dcf54fd4 (boss layer 3) |
  | 5 | ...e0715b2f | e0715b2f (boss layer 4) |

  Five real boss-defeat/border-grow cycles, five clean +1 size increments (never an unbounded jump, confirming `borderPath.clear()` is doing its job), and the tip updates to the just-grown border every single time, with a same-second correspondence to the server's own grow log. This is the client/server tip-match confirmation the 2026-09-06 log entry asked for, obtained directly rather than inferred.

  Project owner separately confirmed visually: growth-trigger particles render at the new boss's spawn area (some distance from the actual platform, which is expected -- see FRO_094's approximate-anchor design, not this ticket's concern) rather than sticking at an old location. Original reported symptom does not reproduce.

  This also puts the previous session's total client-side silence in perspective: rather than a standing defect, it looks like a session-specific gap (this session's periodic client refresh fired every ~5s without issue; whatever prevented that then isn't happening now). Not chasing that further -- the fix itself is now positively confirmed working, which is what this ticket exists to establish; a one-off sync hiccup in an earlier session isn't grounds to keep this open. No Architect ticket needed after all for this specific question.

  **Closing.** `borderPath.clear()` fix confirmed via real playtest, both by direct instrumented client-log evidence and by visual confirmation. Per this ticket's own 2026-09-06 note, the temporary `[FRO_055]` diagnostic line in `BordersFixture.loadBorders()` is now flagged for removal on project owner's own machine (their call per their own "tell me and I'll pull it"). Does **not** close [FRO_051](FRO_051_border-load-count-mismatch.md) -- same general area, separate unresolved root cause, unaffected by this evidence.
- 2026-09-07: **Real playtest run, partially confirming, and one concrete new piece of evidence for the still-open "client sync timing/delivery gap" hypothesis.** Project owner grew a border with a client connected across several boss-defeat cycles (layers 1 through 9, up to a radius-32 border) and confirmed the particle ring followed the new tip each time -- the ticket's original reported symptom did not reproduce. `build.log` shows `BUILD SUCCESSFUL` with `BordersFixture.class` among the changed inputs, so the `borderPath.clear()` fix itself compiles clean.

  The instrumented check this ticket's own log asked for (compare the client's logged `borderPath` size/tip UUID against the server's after a real grow) could not be completed as designed -- project owner found the diagnostic line only in the server log, not the client's. Confirmed by re-reading both logs from this same session end to end: the `[FRO_055] borderPath after load` line fired exactly 3 times, all on the `Server thread`, all clustered at session startup (23:01:16) -- and **zero times** in `run/logs/debug.log` (the connected client), across the entire session, including after the client explicitly disconnected and reconnected around 23:23-23:24 (chat log shows the reconnect and a later `/debug goto boss` use). The `OUT` logger itself is confirmed working client-side in the same log (36 unrelated `OUT.*` lines present, including this mod's own startup banners) -- so this isn't a logging-level or filter issue, `loadBorders()` itself is what never ran on the client.

  This is a more concrete version of the same client-sync-gap hypothesis this ticket's own 2026-09-06 log entry already favored over render-cache staleness (which that entry already ruled out by direct source read). It doesn't yet distinguish *which* client-side path is at fault -- whether `loadBorders()` (the `registerCustom`-registered full-tag deserialize callback) is simply not the mechanism that carries live grows to the client at all (in which case its client-side silence here is expected, and the ring-following that visibly worked this session went through some other, already-correct path), or whether it's supposed to fire on the client (at minimum once, on the reconnect's fresh hydrate) and genuinely doesn't -- which would be the real bug FRO_051 also circles. Not something to resolve by more source reading alone, per this ticket's own 2026-09-06 conclusion (both remaining suspects live in Satchel's bundle engine, out of this role's lane).

  Net effect: the reported symptom (stale ring) did not reproduce over an extensive real session, which is a good sign for the `borderPath.clear()` fix, but the specific instrumented confirmation this ticket asked for is inconclusive rather than passed -- leaving it as "a strong, well-evidenced candidate fix, not a confirmed close," same standing as before. The temporary `[FRO_055]` diagnostic line is **not yet removed** -- the client-side silence just observed is itself useful evidence for whoever picks up the Satchel-side half of this, so pulling it now would erase that. Recommend leaving it in place until an Architect ticket is opened for the Satchel-side sync question (this ticket's own 2026-09-06 log already named that as the real next step); happy to open one on request.
- 2026-09-06: Went to add the diagnostic logging discussed and found something more concrete while locating
  where it should go: `BordersFixture.loadBorders()` clears `borders` before repopulating it
  (`borders.clear()`) but never did the same for `borderPath` -- the append loop just kept adding
  onto whatever was already there.

  `loadBorders` is the exact callback registered for both the one-time `hydrateAll()` and every
  subsequent `refreshFrom()` call SAT_030 (Satchel) added so client syncs keep landing after the
  first one. Each of those calls delivers the server's *complete* current path (`saveBorders`
  writes the whole list every time, not a diff) -- so on the client, every sync after the first
  re-appended the whole thing on top of the existing (already-once-appended) list, duplicating every
  prior entry rather than replacing them.

  Why this plausibly explains -- or at least contributes to -- the reported symptom even though a
  single fresh growth's `tipId()` (`borderPath.get(size()-1)`) would often still land on a real,
  current border (the newest complete list is appended last, in order, so the very last element is
  usually still correct right after one grow): the list itself was bloating without bound on the
  client, and anything reading more of it than just the last index -- `indexOf`, `contains`,
  `moveUp`/`moveDown`, `insert`'s duplicate-id guard, `fixLayers` -- would see corrupted,
  duplicate-laden state. Over several growth cycles, or interacting with any of those other
  operations, this is a very plausible way for the "stale tip" symptom to actually surface, and it's
  a real, unambiguous defect either way -- not speculative.

  Fix: added `borderPath.clear()` immediately before the repopulation loop, matching `borders
  .clear()`'s existing idiom exactly.

  Also added a temporary diagnostic OUT.info line (both sides log unconditionally, same as every
  other line in this class) printing borderPath's size and tip UUID after every load -- kept in
  alongside the fix specifically so the next real playtest (grow a border with a client connected)
  can confirm the client's log now shows a small, correct size instead of an ever-growing one, and
  that client/server tip UUIDs actually match after a grow. Remove this log line once confirmed.

  Not committed (git managed by project owner this session). Real playtest still owed to confirm
  this actually resolves the reported symptom -- flagging as a strong, well-evidenced candidate
  fix, not a confirmed close.
- 2026-09-06: Re-read the render side end to end (GrowthTriggerRenderer.tick() -> RenderContext.pathTip()/
  standby()) against this ticket's own two open hypotheses ("client sync timing/delivery gap" vs.
  "client-side render-cache staleness").

  Ruling out render-cache staleness: `RenderContext.pathTip()` unconditionally recomputes
  `path().flatMap(BordersPathFacet::tip)` on every call -- no gate, no throttle. `standby()`'s own
  `revisionMonitor.poll(...)` throttle only gates `cachedBorders`/a *separate* `cachedPathTip` field
  that nothing else reads; `tick()` always calls the real `pathTip()` method fresh, every tick, once
  `standby()` lets it through at all. So whatever `BordersPathFacet.tip()` returns client-side IS
  what renders -- there's no stale value being held onto on the FrontierMode render side.

  That points the remaining suspect squarely at whether the client's own `BordersPathFacet
  .borderPath` list (or the `BordersFixture` id->Border map `tip()` resolves against via
  `fixture::get`) actually receives the new border after a grow. Checked SAT_030 (closed 2026-08-14,
  Satchel): that ticket already fixed "client bundle sync only ever fires once" wholesale
  (`hydrateAll()`'s strict CREATED->HYDRATED transition rejecting every parcel after the first) and
  was confirmed working via growth particles rendering correctly on its own re-run -- so this isn't
  that same already-fixed class of bug. FRO_051 (still open) independently points at the same
  general area though: a `[engine] CLIENT bundle became dirty (read-only violation)` warning logged
  right before its own client/server `Borders loaded` count mismatch, also traced to Satchel's
  bundle-sync layer, not FrontierMode's fixture code.

  Working theory: some narrower edge case in Satchel's post-SAT_030 sync/refresh path -- possibly
  list-vs-map field handling in `refreshFrom`/hydration, possibly the same "read-only violation"
  path FRO_051 already flagged -- causes `borderPath` (a `List<UUID>`) or the `borders` map to lag
  behind a real growth event specifically, even though general border sync clearly does work most of
  the time (SAT_030's confirmed re-run, this session's own extensive playtesting with real borders
  loading/growing/persisting correctly).

  Not something I can root-cause further by reading source alone -- both suspects live in Satchel's
  bundle engine (`SatchelBundle`/`ScopeEngine_Client`), out of this role's lane (Dev(FrontierMode)
  builds against Satchel's API, doesn't edit Satchel/src). Next step: one instrumented playtest
  (temporary FrontierMode-side log lines comparing the client's `borderPath` list size/tip id and
  the `borders` map's contents immediately after a real grow event, while a client is connected and
  near the old tip) to pin down which of the two is actually stale -- that evidence is what an
  Architect ticket needs to act on, rather than guessing at Satchel internals from outside.

  Not fixed. Plan handed to project owner (see chat).
- 2026-08-28: Ticket opened, diagnosed against source during live playtest. Symptom, render entry
  point, and the server-side path confirmed correct on read-through above; root cause (client sync
  timing vs. render-cache staleness) still open. Possibly related to
  [FRO_051](FRO_051_border-load-count-mismatch.md) — flagged, not confirmed.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
