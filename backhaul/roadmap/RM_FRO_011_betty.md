---
id: RM_FRO_011
uid: RM_FRO
number: 11
kind: work
status: open
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

## Required By

*(computed — nothing depends on this yet)*
