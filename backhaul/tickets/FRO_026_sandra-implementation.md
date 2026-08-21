---
id: FRO_026
uid: FRO
number: 26
client: FrontierMode
status: done
title: Implement RM_FRO_006 (Sandra) — per-player border evaluation
context: null
priority: normal
opened: '2026-08-16'
closed: '2026-08-20'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Ready for Lead Dev (Curtis) now. [RM_FRO_006](../roadmap/RM_FRO_006_sandra.md) ("Sandra" —
per-player border evaluation) was genuinely blocked on Satchel's `PlayerJig`/`PlayerScope`
([RM_SAT_020](../roadmap/RM_SAT_020_jerry.md), "Jerry") not existing. Jerry is now `resolved` and
real-play confirmed (see [FRO_023](FRO_023_playtest-checklist-batch2.md)), so this node's blocker
is cleared — this is real, assignable work now, not a stub.

**Why this matters right now:** Sandra is the one thing standing between here and Tier 0.
[RM_FRO_010](../roadmap/RM_FRO_010_susan.md) ("Susan," the prototype-hardening convergence, which
now also carries the Tier 0 designation directly — see her own node) was briefly flipped to
reached and then reverted — a review caught Sandra skipping Susan's gate to feed a separate Tier 0
node directly, so Sandra was folded into Susan's own `depends_on` instead. Susan can't re-reach
until Sandra resolves. This ticket is the whole remaining gap.

## Scope

Per RM_FRO_006's own node text — all six files under `border/common/player/*` are commented out,
split cleanly in two:

- `BorderPlayerEval`, `BorderPlayerLogic`, `BorderPlayerStatus`, `BorderPlayerStatusProposal` have
  no Satchel dependency at all and should compile unchanged the moment they're uncommented.
- `BorderPlayerStatusFixture` and `BorderPlayerBundle` are the real blockers — written against a
  `SatchelSetting` base class and package paths that predate the current `SatchelFixture`
  structure. These need a real rewrite against the now-finished `PlayerJig` system, reusing the
  four pure-logic files essentially as-is.
- Wire registration into `BorderModule.init()` the same way `BordersBundle` (the world-scoped
  bundle) already is.
- `BorderSelector.resolveRelevant()` (`@relevant` selector, currently stubbed to `List.of()`) is
  the other loose end this node covers per [RM_FRO_011](../roadmap/RM_FRO_011_betty.md)'s note —
  worth closing out here since the underlying data this node builds is exactly what `@relevant`
  needs to resolve against.

## Done bar

Real build + real play, same standard as the last batch — not just "compiles." At minimum:
confirm the four pure-logic files behave unchanged, confirm `BorderPlayerStatusFixture`/
`BorderPlayerBundle` construct and persist correctly against `PlayerJig`/`PlayerScope` across a
login/dimension-change/logout cycle (mirroring RM_SAT_020's own done-bar), and confirm `@relevant`
resolves to something real instead of the current empty-list stub. No Forge/Mojang maven access in
the Cowork sandbox — project owner runs the real `gradlew build`/`runServer`/`runClient` checks and
reports back, same pattern as [FRO_023](FRO_023_playtest-checklist-batch2.md).

## Log

- 2026-08-20: **Closed — project owner's call.** All three done-bar items now have real positive
  evidence: build/playtest confirmed item 2 directly, the project owner's `@relevant` test
  confirmed item 3 directly and item 1 indirectly. [RM_FRO_006](../roadmap/RM_FRO_006_sandra.md)
  ("Sandra") marked `resolved` alongside this ticket.
- 2026-08-20: **First real build + playtest pass since implementation, read from
  `run/logs/latest.log` and `run-server/logs/latest.log`.** Real `gradlew build`: `BUILD
  SUCCESSFUL`. A login/dimension-change/logout cycle was exercised (overworld → the_nether →
  overworld → logout) — no `/border` or `@relevant` command was run this pass. Read against the
  four checklist items in the entry below, item by item, without touching their checkboxes (still
  genuinely unconfirmed, not just unchecked by oversight):
  - **Pure-logic files behave unchanged** — not directly evidenced. No crash occurred, which is
    consistent with correctness, but nothing in this pass specifically exercises border-command
    behavior to positively confirm it.
  - **`BorderPlayerStatusFixture`/`BorderPlayerBundle` construct correctly across a
    login/dimension-change/logout cycle** — reasonably confirmed, the strongest finding of this
    pass. `frontiermode:border_player_bundle` fell through to create once at login (the expected
    first-touch path, same shape every other bundle in the log takes), then survived the
    overworld→nether→overworld round-trip and logout with zero errors, exceptions, or
    `AccessFailed` logged against it. Weaker than a positive confirmation would be — this fixture
    has no dedicated log line of its own the way Satchel's `PlayerTracking` verification aid does —
    but nothing points to a problem either.
  - **`@relevant` resolves to a real border** — not exercised. No command was run this pass.
  - **`@relevant` reports honestly when nowhere near a border** — not exercised, same reason.

  **Still not done: this ticket stays `in-progress`, [RM_FRO_006](../roadmap/RM_FRO_006_sandra.md)
  stays `open`.** A command-exercising pass (any `/border ... @relevant` usage) is what's left
  before this done bar is actually met. Full mirrored write-up on
  [RM_FRO_006](../roadmap/RM_FRO_006_sandra.md)'s own log.
- 2026-08-20: **Project owner reports `/border info @relevant` was tested separately and resolves
  correctly** — closes the gap the entry above flagged. Full reasoning on
  [RM_FRO_006](../roadmap/RM_FRO_006_sandra.md)'s own log, including why this also bears on the
  first checklist item (pure-logic files), not just the third. **All three checklist items now
  have real positive evidence.** Leaving `in-progress` — per this ticket's own text above, closing
  is the project owner's call, not assumed here.

  **Separately observed, not part of this ticket's scope:** the client log shows a repeating
  `[engine] CLIENT bundle became dirty (read-only violation)` WARN for `BordersBundle` (the
  world-scoped bundle, not this ticket's player-scoped one) once per dimension load. Flagging so
  it isn't lost; not this ticket's concern to fix.
- 2026-08-16: **Tier 0 node deleted, merged into Susan.** The separate Tier 0 convergence
  referenced above (first RM_FRO_014 "Shirley," then rebuilt as RM_FRO_016 "Karen") is gone —
  both were functionally just a pointer back to RM_FRO_010, so Susan now carries the Tier 0
  designation directly. This ticket's own scope (RM_FRO_006) is unaffected.
- 2026-08-16: **Real bug found by playtest, fixed: `@relevant` was returning the wrong border.**
  Project owner reported `@relevant` "isn't returning the lowest layer" after the first real
  build/playtest of this node's implementation. Root cause: `BorderPlayerLogic.evaluate()`'s
  uncommented-as-is logic picked whichever border had the smallest raw `distanceToSurface(border,
  pos)` value across *all* borders, not just ones the player is actually inside. Since borders are
  concentric per layer and `distanceToSurface` = `distanceSqToCenter - radius`, a bigger radius
  makes that number *more* negative -- so for a player standing inside a fully-grown border stack,
  this always picked the **outermost** containing border, backwards from what "lowest layer"
  means. This was a real defect in the code being uncommented "as-is" per this node's original
  plan, not a design ambiguity -- the correct semantics already exist and are already the single
  source of truth elsewhere in the same codebase: `DefaultBorderRules.getRelevant(containing,
  pos)` (lowest `layerIndex` among borders actually containing the position, ties broken by
  nearest center) -- already referenced by name in `BordersCrudFacet`/`BordersPathFacet`'s own
  comments as the established ranking. Fixed `BorderPlayerLogic.evaluate()` to filter to borders
  actually containing the position, then delegate to `BorderRules.ACTIVE.getRelevant(containing,
  pos)` directly instead of duplicating that ranking -- keeps this in sync with the one
  authoritative implementation instead of drifting from it again. Falls back to closest-surface
  (the old behavior) only for the genuinely different case of a player outside every border, where
  `getRelevant` has nothing to rank. Unverified pending rebuild -- no build access this session.
- 2026-08-16: **Implemented by Lead Dev (Curtis) — pending real build/playtest.** Full detail on
  [RM_FRO_006](../roadmap/RM_FRO_006_sandra.md)'s own log. Summary: all six
  `border/common/player/*` files uncommented/rewritten (four pure-logic files reused essentially
  as-is, `BorderPlayerStatusFixture`/`BorderPlayerBundle` rewritten against the current
  `SatchelFixture`/`SatchelBundle`), registered into `BorderModule.init()` as a second
  `PlayerJigConfig` following `PlayerTrackingModule`'s established pattern, and
  `BorderSelector.resolveRelevant()`/`BorderAPI.getRelevant()` now real (no more empty-list stub).

  **Ask, same pattern as [FRO_023](FRO_023_playtest-checklist-batch2.md)/
  [FRO_024](FRO_024_rendering-eager-static-crash.md)/[FRO_025](FRO_025_client-crash-borders-jig-not-installed-o.md):**
  rebuild both repos, then against a real client+server session (singleplayer/integrated should be
  sufficient per this node's own log — no dedicated-server requirement carries forward from
  RM_SAT_020) confirm:
  - [ ] The four pure-logic files behave unchanged (no crash, no regression in border command
    behavior generally).
  - [ ] `BorderPlayerStatusFixture`/`BorderPlayerBundle` construct correctly against
    `PlayerJig`/`PlayerScope` across a login/dimension-change/logout cycle — no crash, no
    "JigNotFound"-shaped error (see FRO_025 for what that looks like if it recurs for this new
    jig specifically).
  - [ ] `/border transform @relevant ...` (or any command using the bare/`@relevant` selector)
    resolves to a real border when standing near/inside one, instead of "No borders matched
    selector."
  - [ ] `/border transform @relevant ...` while nowhere near any border returns the same honest
    "No borders matched selector." (not a crash, not the old placeholder message).

  Not yet done: this ticket stays `in-progress`, [RM_FRO_006](../roadmap/RM_FRO_006_sandra.md)
  stays `open` — closing either is the project owner's call once the above comes back.
- 2026-08-16: Ticket opened. Sandra's blocker (RM_SAT_020) confirmed cleared; this is the sole
  remaining prerequisite for Susan to re-reach and Shirley (Tier 0) to progress.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
