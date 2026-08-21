---
id: RM_FRO_011
uid: RM_FRO
number: 11
kind: work
status: resolved
title: Border mutation validation hardening
owner: Arryn
depends_on:
- RM_FRO_008
created: '2026-08-16'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_FRO
<!-- bh-header:end -->

## Border mutation validation hardening

- 2026-08-16: **Housekeeping items folded in ahead of implementation, project owner's call** —
  `Config.java` boilerplate, the duplicate stray `@SuppressWarnings`, and the missing `@none`
  tab-complete suggestion, all found in the same resilience pass's final sweep, added here before
  Lead Dev started rather than opening a fourth sibling node for lower-stakes cleanup. See the
  "Housekeeping" section below.
- 2026-08-16: Node opened, one of three siblings added to [RM_FRO_010](RM_FRO_010_susan.md)
  ("Susan") after a source-level resilience pass over `border/*` prompted by the project owner,
  following the same "look for holes like SAT_031 found" review that produced
  [RM_FRO_012](RM_FRO_012_carolyn.md) and [RM_FRO_013](RM_FRO_013_judy.md). Not from a ticket —
  found directly by reading `BorderProposal`, `BordersCrudFacet`, `BordersPathFacet`, and their
  command-layer callers (`BorderCommandHandler`, `BorderCommands`, `BorderSelector`) against real
  behavior, not by a reported bug.

Three separate, evidenced gaps in the same mutation surface (`border/common/fixture/*` +
`border/server/commands/*`), all already live and op-reachable via the `/border` command tree:

**1. `/border transform` throws on three of its five real usage forms.**
`BorderAPI.transformBorder(Level, UUID, BlockPos newCenter, Integer newRadius)` calls
`proposal.insert(border).center(newCenter).radius(newRadius)` unconditionally. `newCenter`/
`newRadius` are nullable specifically so a caller can update center or radius independently
(`proposal.insert(border)` seeds both from the existing border first, and the intent is clearly
that a null argument should leave that seeded value alone) — but nothing actually checks for
null before calling through:
- `BorderProposal.radius(int radius)` takes a primitive `int`. Passing a null `Integer` auto-unboxes
  and throws `NullPointerException` immediately — hit by `/border transform <selector> here` and
  `/border transform <selector> <pos>` (both omit radius).
- `BorderProposal.center(BlockPos center)` accepts the null fine, but `Border`'s constructor calls
  `Objects.requireNonNull(center, "center")`, so it throws one step later, inside
  `BordersCrudFacet.applyProposal` — hit by `/border transform <selector> radius <r>` (omits
  position).

  Only the two forms that supply both center and radius work today. Fix: `transformBorder` (or
  `BorderProposal` itself) needs to treat a null `newCenter`/`newRadius` as "leave the
  `proposal.insert(border)`-seeded value alone," not pass it straight through.

**2. `BordersCrudFacet.validateProposal()` is a hardcoded no-op — none of `BorderConstants`'
  bounds are enforced on the admin path.** `MIN_RADIUS`/`MAX_RADIUS` (1–512) are only applied
  inside the organic-growth path (`DefaultBorderRules.chooseNextRadius`). `/border add` and
  `/border transform` go through `BordersCrudFacet.applyProposal`, which calls
  `validateProposal(proposal)` and then proceeds regardless of the answer (it always returns
  `true` — see the method's own comment, "pretty much future use"). Concretely reachable today:
  `/border add ~ ~ ~ 999999999 0` creates a border with an unbounded radius and an unbounded,
  unvalidated `layerIndex` — the same `layerIndex` [DefaultBorderRules.getRelevant()](../wiki/frontiermode/architecture/border.md)
  uses to resolve oldest-ring-wins overlap (confirmed correct and load-bearing for
  [Progression's design](../wiki/frontiermode/design/progression.md) in a prior session's
  source-level check). An out-of-band `layerIndex` from an unvalidated `/border add` call silently
  corrupts that ordering. Fix: give `validateProposal` a real body — at minimum, clamp/reject
  radius outside `MIN_RADIUS`/`MAX_RADIUS`, and reject a `layerIndex` that collides with or breaks
  the monotonic ordering `getRelevant()` assumes.

**3. `/border path fixlayers` reports success while doing nothing.**
`BordersPathFacet.fixLayers()` is a literal empty stub — its own comment says "Currently a no-op
by design" — but it still calls `markPathDirty()`, and `BorderCommandHandler.pathFixLayers` still
tells the command sender "Reconciled border layers with path order." This is more than a stub
left unfinished: `pathMoveUp`/`pathMoveDown` are fully wired, op-exposed commands
(`/border path moveup|movedown <selector>`) that reorder the *path list* — but `Border.layerIndex`
is immutable (no mutator; only a fresh `BorderProposal` can set it), so reordering the path never
touches the `layerIndex` values `getRelevant()` actually sorts by. An op can desync path order
from difficulty order using two already-live commands, and the one command whose entire job is
reconciling that (`fixlayers`) silently doesn't, while claiming it did. Fix: either implement the
reorder-`layerIndex`-to-match-path-order logic the comment describes, or — if that turns out to be
the wrong fix once someone looks closely at what "layer order" is supposed to mean relative to
"path order" — change the command's response to say what actually happened instead of a false
positive. Either outcome is acceptable; leaving the current silent-success behavior is not.

**Also worth a look while in this file, not blocking:** `BorderSelector.resolveRelevant()` is
stubbed to `List.of()` (real body commented out, blocked on the same `BorderAPI.getRelevant(Player)`
placeholder [RM_FRO_006](RM_FRO_006_sandra.md) already tracks). Since `@relevant` is also the
*default* selector when no selector text is given, `/border delete`/`/border transform` with a
blank selector currently reports "No borders matched selector" — a false negative, not an honest
"not implemented yet." Not itself new work (RM_FRO_006 is the real fix), but a one-line message
change is cheap to make alongside item 1 above while `BorderCommandHandler` is already open.

**Housekeeping, folded in ahead of implementation (lower priority than 1-3, bundled in rather than
opening a separate ticket, project owner's call since Lead Dev hadn't started yet):**
- `Config.java` is stock, uncustomized Forge MDK example boilerplate (`LOG_DIRT_BLOCK`,
  `MAGIC_NUMBER`, `magicNumberIntroduction`, the `items` list) — never adapted or removed, same
  category of leftover template scaffolding as `README.txt` ([FRO_001](../tickets/FRO_001_readme-boilerplate.md)).
  Not Border-specific, but small enough to clear here rather than opening a fourth sibling for it
  alone.
- `FrontierMode.java` and `Config.java` both carry a class-level `@SuppressWarnings("...")` whose
  string references a Minecraft version this project doesn't target ("1.21.1"/"1.20.6") and isn't
  a recognized `SuppressWarnings` key either way — does nothing, looks like the same stray
  copy-paste habit hit twice. Safe to delete both.
- `BorderSelectorArgumentType.listSuggestions()` offers `@containing`/`@coord`/`@all`/`@relevant`
  but not `@none` — the one selector mode `BorderSelector.parse()` actually supports that never
  shows up in tab-complete. Cheap one-line fix while this file's already open for the `@relevant`
  message change above.

**Done bar:** items 1-3 each have a concrete before/after behavior described above — three
specific, reproducible bugs with a stated fix shape each. Real-build/real-command confirmation for
each (not just "compiles") before this node counts as resolved, per this project's established
standard for anything touching command-reachable mutation. The housekeeping items are lower
stakes — compiling clean and a quick manual check (tab-complete shows `@none`, no leftover
boilerplate config values referenced anywhere) is enough for those specifically.

- 2026-08-16: **Implemented by Lead Dev (Curtis), unverified — no build access this session.**
  - **Item 1 fixed as described:** `BorderAPI.transformBorder` now only calls
    `proposal.center(...)`/`.radius(...)` when the corresponding argument is non-null, leaving
    `proposal.insert(border)`'s seeded value alone otherwise. All five `/border transform` forms
    should now work, not just the two that supplied both center and radius.
  - **Item 2 fixed as described:** `BordersCrudFacet.validateProposal` now rejects radius outside
    `BorderConstants.MIN_RADIUS`/`MAX_RADIUS` and rejects a `layerIndex` colliding with a
    *different* border's (a proposal updating its own border via `insert()` is correctly excluded
    from colliding with itself). **[Superseded, 2026-08-20: the layer-collision half of this
    validation was removed by
    [RM_FRO_015](../roadmap/RM_FRO_015_margaret.md) — project owner's design call, Layer and Path
    are definitionally unrelated and `getRelevant()`'s own nearest-center tie-break already
    resolves a same-layer overlap without a uniqueness guard. The radius-bounds half of this fix is
    unaffected and still stands. This log entry is left as originally written, not restated.]** `applyProposal`'s failure message improved slightly (was the
    unconditional "Something went wrong").
  - **Item 3 — took the alternative the node's text explicitly sanctioned, not the reorder
    implementation.** Actually reordering `layerIndex` to match path order turns out to interact
    badly with item 2's new collision check: a naive in-place reassignment of every path member to
    `0..n-1` can transiently collide with an off-path border's existing `layerIndex`, or with
    another path member not yet reassigned, partway through — fixing that needs a real two-pass
    (or bypass) design, not something safe to guess at without a real build to verify against.
    `BordersPathFacet.fixLayers()` now returns `boolean` (`false`, always, until that design
    lands) instead of unconditionally returning `void` and leaving the caller to assume success;
    `BorderCommandHandler.pathFixLayers` reports "No changes made" instead of the previous false
    "Reconciled" positive. Flagging this as a real follow-up worth its own roadmap node once
    someone designs the reorder properly — not filing that node myself since it's a design call,
    not a mechanical one, and the project owner said not to route around architectural decisions
    without a ticket.
  - **`@relevant` message fixed**, but scoped narrower than "blank selector" — the false-negative
    only actually reaches the player through `BorderCommands.applySelector`'s shared "no borders
    matched" path (delete/transform/info all route through it), so that's the one place patched:
    empty results specifically from `Mode.RELEVANT` now say "@relevant isn't implemented yet (see
    RM_FRO_006)" instead of the generic message; every other selector mode's empty-result message
    is unchanged (those are honest).
  - **Housekeeping, all done:** `Config.java` stripped to just the empty, registered `SPEC` (the
    stock `LOG_DIRT_BLOCK`/`MAGIC_NUMBER`/`magicNumberIntroduction`/`items` fields and their
    `onLoad` handler deleted — confirmed via grep that nothing outside `Config.java` read any of
    them). Both stray `@SuppressWarnings` deleted (`FrontierMode.java`, and `Config.java`'s went
    with the `onLoad` method/`@EventBusSubscriber` annotation it was attached to — nothing left in
    that class to subscribe). `@none` added to `BorderSelectorArgumentType.listSuggestions()`.
  - **Unverified this session** — no Forge/Mojang maven access (confirmed via curl). Real
    `gradlew build` plus the command checklist in
    [FRO_023](../tickets/FRO_023_playtest-checklist-batch2.md) still owed before this counts as
    resolved. Singleplayer/integrated is sufficient for all of this node's testing — nothing here
    crosses a client/server network boundary the way `RM_SAT_020` does.

- 2026-08-16: **First real playtest pass, project owner + Lead Dev (Curtis) reviewing
  `run/logs/latest.log` and `run-server/logs/latest.log` together.** Per-item results:
  - **Item 1 (`/border transform`, three null-arg forms) — confirmed no NPE/crash.** At least one
    successful `Transformed border <id>` went through cleanly. The specific attempts to exercise
    all three individual forms (`here`, position-only, radius-only) hit command-syntax typos
    instead (`Incomplete (expected 3 coordinates)`, `Expected whitespace to end one argument`,
    `Usage: @coord <x> <y> <z>`) — those are Brigadier parser rejections from the typed command
    text, not this node's code path, so they don't confirm or deny anything about the fix. Still
    open: a clean, correctly-typed retry of each of the three forms individually.
  - **Item 2 (`/border add ~ ~ ~ 999999999 0` bounds check) — validation itself confirmed
    working, but found and fixed a real bug in how the rejection surfaces.** Server log showed
    the intended `[Border] Rejected proposal: radius 999999999 outside allowed range [1, 512].`
    — but doubled (logged twice for one command), and the player only ever saw Brigadier's
    generic `An unexpected error occurred trying to execute that command`, not the actual reason.
    Root cause: `BorderAPI.addBorder()`/`transformBorder()` called
    `borders.CRUD.validateProposal(proposal)` themselves *and* discarded the boolean result, while
    `BordersCrudFacet.applyProposal()` already calls `validateProposal()` again internally and
    throws a raw `IllegalStateException` on rejection — explains the double log line (redundant
    first call) and the ugly player-facing message (nothing between `applyProposal()` and
    Brigadier's dispatcher ever caught that exception). **Fixed just now:** removed the redundant
    pre-validation call in `BorderAPI` (dead code, result was already discarded); added
    `try/catch(IllegalStateException)` in `BorderCommandHandler.addExplicit`/`addHere`/`transform`,
    converting the rejection into a clean `[Border] Rejected: <reason>` message via
    `sendFailure`/`sendSystemMessage` instead of letting it bubble into Brigadier's generic
    handler. Also worth noting for the retest: the actual `999999999` (9 nines, in range for
    `IntegerArgumentType`) is what exercised this code path — an earlier typo with 12 nines
    (`999999999999`) got rejected by Brigadier's own int-overflow check before ever reaching this
    code, which is a different, unrelated rejection path, not evidence either way about this
    fix.
  - **Item 3 (`/border path fixlayers`) — not exercised this session**, no `fixlayers` or
    `Reconcil` text anywhere in either log. Still needs a real run against the "reports what it
    actually did" behavior landed above.
  - **`@none` tab-complete** — not verifiable from text logs (client-side UI-only interaction,
    nothing gets logged). Needs a direct visual confirmation from the project owner.
  - **`Config.java` grep-clean** — confirmed directly from source (not logs): the only reference
    to `Config` anywhere in FrontierMode is `FrontierMode.java`'s legitimate
    `ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC)` call. Clean.
  - **Follow-up retest (same session, project owner): the "unexpected error" fix works** — server
    log now shows the rejection logged exactly once (double-log bug confirmed fixed), no more
    generic Brigadier error. But the player-facing message was still just the generic
    `Border proposal rejected by validation -- see server log for the specific reason.` — my first
    fix only stopped the crash-looking message, it didn't carry the *actual* reason
    (`radius 999999999 outside allowed range [1, 512]`) into what the player sees. Root cause:
    `BordersCrudFacet.applyProposal()`'s thrown `IllegalStateException` always used a fixed
    generic string; the real per-case reason only ever reached `OUT.warn()`, server-side only.
    **Fixed just now:** refactored `validateProposal`'s logic into a private
    `failureReason(BorderProposal)` returning `Optional<String>` (empty = valid); `applyProposal`
    now throws with that actual reason text, so `BorderCommandHandler`'s existing catch (no
    changes needed there) relays the real message to the player. `validateProposal` itself keeps
    its `boolean` signature (`BorderLogic`'s two organic-growth call sites are unaffected). Also
    found and cleaned up the same redundant-double-validate pattern in `BorderLogic.getInitial()`/
    `grow()` (identical shape to the one already fixed in `BorderAPI`) while in this file.
    Unverified pending rebuild.
  - **All three `/border transform` forms confirmed individually, project owner's call to stop
    there:** `radius 100`, `here`, and `~ ~ ~ 15` (relative coords, confirming `BlockPosArgument`
    handles `~` fine -- that's the vanilla-provided `pos` argument, not the custom `@coord`
    selector parser) all applied cleanly. Item 1 closed.
  - **`/border path fixlayers` retested — behaves exactly as this session's fix left it**, reports
    "No changes made -- layer/path reconciliation isn't implemented yet." Project owner asked if
    that's because there's nothing to reconcile right now -- worth being precise: it's not a
    "nothing to do" state, it's that the reorder logic itself was deliberately never built this
    session (see the log entry above) because it interacted unsafely with item 2's new collision
    check. The message is honest about that, not about the data being already clean. Confirmed
    low priority, parked for Architect/PM per the existing flag.
  - **Tab-complete default noted, not acted on:** `/border info` + Tab correctly suggests `@all`.
    `/border transform` + Tab also suggests `@all` first (alphabetical order, same as everywhere
    else `BorderSelectorArgumentType` is used) -- project owner's read is `@relevant` would be a
    more useful default for `transform` specifically (usually acting on "the border I'm standing
    in" rather than "all of them"). Not implemented -- would mean per-command-context suggestion
    ordering instead of the current shared list, and `@relevant` doesn't even resolve to anything
    yet (RM_FRO_006). Flagged as a real but low-priority UX idea, not a bug.
  - Unrelated but observed in the same log window: a `@none`/empty-selector query returned
    `[Border] No borders matched selector.` cleanly (no crash) — consistent with, but not a
    substitute for, the tab-complete check above.
  - **Not yet re-verified after today's two fixes** — same standing limitation, no Forge/Mojang
    maven access this session. Still open pending: clean individual retests of the three
    `/border transform` forms, a `/border add ~ ~ ~ 999999999 0` retest (should now show the
    clean rejection message and only one log line), `/border path fixlayers`, and the `@none`
    tab-complete visual check.

## Required By

*(computed — nothing depends on this yet)*
