---
id: FRO_092
uid: FRO
number: 92
client: FrontierMode
status: done
title: Border Pregen carryforward build
context: Lead Dev build for FRO_091's finalized rulings -- see ticket body for full
  scope.
priority: low
opened: '2026-09-06'
closed: '2026-09-07'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Lead Dev build ticket for [FRO_091](FRO_091_pregen-carryforward-spec.md) -- the Architect
spec-review ticket, closed with the actual rulings written directly onto
[border-pregeneration.md](../wiki/frontiermode/architecture/border-pregeneration.md#open-questions)'s
Open Questions section. This ticket is the build itself, same split as
[FRO_058](FRO_058_boss-mutation-validation-reconciliation.md)/[FRO_059](FRO_059_border-proposal-center-bounds-id-display.md)
-> [FRO_060](FRO_060_boss-border-mutation-validation-build.md).

## What to build

Per FRO_091's rulings, no new scoping decisions -- straight implementation against three of the
five resolved items (the other two need no code change at all -- see "Not touched" below):

1. **Exact throttle budget.** Add a tunable coefficient to `BorderRules`/`DefaultBorderRules`
   (chunks-per-allowed-tick, feeding `BorderPregenFixture`'s `TickThrottler` interval) --
   same shape as `DefaultBorderRules.GROWTH_FACTOR`. Pick a safe default value; it's explicitly
   Lead Dev's call, not an architecture decision.
2. **Stalled-trigger warning.** `BorderPregenFixture` gets a second, longer-interval
   `TickThrottler` instance alongside its existing chunk-pacing one. On each allowed tick, check
   whether any in-progress (`complete == false`) border record has sat past a reasonable pregen
   duration and, if so, log loudly via `OUT.warn` naming the stalled `borderId` -- no new event
   type, log-only for now.
3. **Loud logging on disk-validation failure.** If an entire border's disk somehow fails
   validation (the accepted-risk edge case), log it at `OUT.warn` naming the border and cursor
   rather than letting it fail silently or get treated as a routine no-op.

## Not touched, per FRO_091's own rulings

- **`BorderPregenEvent.Complete`'s registration discipline** -- ruled fine as-is, following the
  `MobDied` precedent. No code change.
- **Retroactive pregen / grandfathering for pre-existing worlds** -- ruled moot; no old-world
  compatibility requirement exists at this project stage. Nothing to build.
- **Retry/reroll mechanism itself** -- ruled accepted risk, no mechanism. Only the logging (item 3
  above) is in scope, not an actual retry.

## Standing constraint

**No Gradle in the agent sandbox.** Real build/playtest happens on the project owner's own
machine -- confirm via a real `build.log` and/or in-game session evidence before closing, same
discipline as FRO_060/FRO_057's own precedent.

## Log

- 2026-09-07: **Real playtest confirmation received, project owner's own session.** All three Standing-constraint checks pass: (1) compiles clean -- `build.log` shows `BUILD SUCCESSFUL`, covering this ticket's three touched files. (2) Normal border pregeneration confirmed at existing pace across a wide range of real sizes in one session -- borders from 13 chunks (radius 2, 0.1s) up through 3209 chunks (radius 32, 263.9s), each completing via the new `BorderRules.pregenChunksPerBatch()`/`pregenThrottleIntervalTicks()` tunables with no behavior change from before. (3) Multiple full pregen jobs ran to completion (several kills, several `/boss transform`) with zero false "appears stalled" crashes from the new 200-tick liveness check -- including one run that continued and completed unattended after the project owner logged out, confirming the watchdog doesn't depend on a player being present. (4) Disk-generation-failure crash path never triggered (no disk failures occurred) -- remains unexercised but unchanged from its verified-propagates-uncaught state logged above.

  Item 4 from the original checklist (deliberately stalling a job to confirm the crash names the border and cursor) wasn't reproduced -- project owner couldn't find a reliable way to induce it live, reasonably treating it as a genuine edge case not worth spending more session time chasing. Not a blocker: the check exists to confirm the crash *message* is legible, not that stalls can happen; the code path itself was already verified via source read-through (propagates uncaught through `BorderPregenFixture -> SatchelBundle -> ScopeEngine_Server -> ASatchelJig`, logged above).

  Separately, the same session surfaced a real instance of an already-anticipated edge case in Boss's own placement logic (not this ticket's scope): the radius-32 border above hit `DefaultBossRules.choosePosition()`'s existing "all candidates scored hazardous" fallback and placed its boss at y=-64 over water; that boss's later death then also hit `BossJigHandlers.onMobDied()`'s existing out-of-build-height fallback and recovered correctly. Both are pre-existing, already-logged defensive paths working as designed, not a regression from this build -- parked as a maintenance item on [RM_FRO_025](../roadmap/RM_FRO_025_donna-02.md) ("Donna epoch review/fix") per project owner's direction rather than actioned here.

  **Closing.** Standing constraint met (real build.log + extensive real-session playtest evidence, same discipline as FRO_060/FRO_057).
- 2026-09-07: Wiki/spec drift flagged in the previous log entry is corrected: border-pregeneration.md's Open Questions section now restates both the stalled-trigger check and the disk-validation-failure case as the hard-crash behavior actually built here, replacing the original log-only (OUT.warn) ruling. The stalled-trigger item is also corrected to describe what was actually built -- a cursor-liveness check (200-tick interval, throws if the cursor hasn't moved), not the elapsed-duration estimate this page originally guessed at. Both bullets now link this ticket. Page's own intro paragraph updated to point at this ticket as the completed (pending playtest) Lead Dev build. No further wiki drift found.
- 2026-09-07: Flagging wiki/spec drift found during PM review, not yet corrected: border-pregeneration.md's Open Questions section still documents FRO_091's original rulings for the stalled-trigger check and the disk-validation-failure case as log-only (OUT.warn, no new mechanism, not a redesign). What actually shipped in this ticket's 2026-09-07 build entry is a hard crash (throw, propagating uncaught) for both cases instead -- discussed and simplified with the project owner mid-build, so presumably an intentional, sanctioned change, but the wiki page hasn't been updated to match and still describes the old log-only behavior. border-pregeneration.md is Architect (Douglas) territory to correct, per BHW convention -- needs a pass to restate both items as crash-on-failure before or alongside this ticket's close, so the page matches what's actually built rather than what FRO_091 originally ruled.
- 2026-09-07: Lead Dev build complete, pending build/playtest confirmation per this ticket's own Standing constraint (no Gradle in the agent sandbox). All three items built against FRO_091's rulings, no new scoping decisions: (1) exact throttle budget -- moved off BorderPregenFixture's own hardcoded constants onto BorderRules.pregenChunksPerBatch()/pregenThrottleIntervalTicks(), implemented on DefaultBorderRules carrying over the existing playtest-backed default values (2 chunks/4 ticks), same shape as GROWTH_FACTOR. (2) stalled-trigger warning -- built as a liveness check rather than a predicted-duration estimate (discussed and simplified with project owner): a second TickThrottler at a 200-tick interval compares each in-progress job's cursor against its own last-checked value and throws if it hasn't moved, naming the border and cursor. Deliberately disk-size-agnostic, no BorderRules involvement. (3) disk-generation-failure logging -- also simplified with project owner from a log-and-continue to a hard crash: runBatch() re-verifies FULL status immediately after each forced level.getChunk() call and throws, naming the border, chunk coordinates, and cursor, if it didn't land. Both crash paths confirmed to propagate uncaught through BorderPregenFixture -> SatchelBundle.onJigTick() -> ScopeEngine_Server -> ASatchelJig (no try/catch in that path) rather than being swallowed. Files touched: BorderRules.java, DefaultBorderRules.java, BorderPregenFixture.java (all FrontierMode/src). No Gradle build attempted in this session by project owner's choice -- real build/playtest confirmation is still owed before this ticket closes, same discipline as FRO_060/FRO_057.
- 2026-09-06: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
