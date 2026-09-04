---
id: FRO_078
uid: FRO
number: 78
client: FrontierMode
status: done
title: Add BorderMath to BorderAPI surface
context: '[Donna_02] Route BorderMath''s geometry ops through BorderAPI''s surface.
  FRO_074#5'
priority: normal
opened: '2026-09-03'
closed: '2026-09-03'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Add BorderMath to BorderAPI surface

Split from [FRO_074](FRO_074_cartographer-findings.md#5-add-bordermath-to-borderapis-surface-for-consistency----per-project-owner)
finding 5. `BorderMath` (stateless geometry helpers -- containment, distance,
`randomPointInAnnulus`) is the one thing a cross-module consumer (`DefaultBossRules`) reaches in
the Border package without going through `BorderAPI` -- everything else Boss touches in Border
has a facade entry point.

**Per project owner (stated decision, no open ruling question):** route `BorderMath`'s
operations through `BorderAPI`'s own surface, so every cross-module touch point -- stateful or
not -- goes through the one facade consistently.

**For Lead Dev to decide at implementation time:**
- Whether `BorderAPI` wraps/delegates to the existing `BorderMath` methods, or `BorderMath`
  moves under `BorderAPI` outright.
- Whether `BorderMath` stays public afterward or drops to package-private once nothing external
  calls it directly.

## Log
- 2026-09-03: Change order from project owner after playtest checkpoint. Reworked per direction: (1) restructured BorderAPI's math delegation into a nested `BorderAPI.MATH` namespace (`BorderAPI.MATH.isInside(...)`, etc.) instead of flat static methods, mirroring PATH/CRUD/RULES/INFO's grouped shape. (2) Consolidating BorderCurveMath/BorderMathLogic into BorderMath was requested but withdrawn by project owner pending their own investigation -- both classes deliberately Minecraft-classpath-free for BorderMathLogicTest/BorderCurveMathTest (done-bar tests for RM_FRO_026/RM_FRO_027); left untouched. Noted for that investigation: those two tests' package (test.arryn.frontiermode.border.common) differs from BorderMathLogic/BorderCurveMath's own package (com.arryn.frontiermode.border.common) -- package-private access across different packages doesn't compile under normal Java rules regardless of any change here, worth checking independently of this ticket. (3) BorderMath reverted to public (project owner had set it package-private locally to surface every real call site via a compile-error sweep -- 25 errors, captured in FrontierMode/build.log). BorderAPI lives in a different package (com.arryn.frontiermode.border) than BorderMath (com.arryn.frontiermode.border.common), so BorderAPI.MATH can never legally reach a package-private BorderMath -- same structural situation as BordersFixture (border.md's "External Access Is Not Compiler-Enforced"), same accepted mitigation: public, with doc-comment discipline pointing at BorderAPI.MATH as the sanctioned path. Migrated every real call site the build log identified to BorderAPI.MATH.xxx(): BordersRulesFacet.containing(), GrowthTriggerRenderer.spawnParticles(), BorderPlayerLogic.evaluate() (four call sites), DefaultBorderRules.getRelevant()/growPathCriteria(), and DefaultBossRules.choosePosition() (already migrated to the flat form in the prior pass, now updated to BorderAPI.MATH.randomPointInAnnulus()). No sandbox compile available this session (see FRO_079's log) -- verified by manual review (brace/paren balance, grep sweep confirming no remaining direct BorderMath.* call sites outside BorderMath.java/BorderAPI.java, doc-only mentions in Shape.java/BossRules.java left alone).
- 2026-09-03: Built by Lead Dev. Added a `BorderMath` delegating surface to `BorderAPI` (isInside x2, distanceToSurface, distanceSqToCenter, randomPointInDisk, randomPointInAnnulus, distanceTo, direction, intensityAt x2) -- thin wrappers, `BorderMath` itself stays the real implementation and stays public (used directly across several border.* sub-packages internally: client rendering, fixtures, player logic, rules -- package-private wasn't viable without breaking those). Migrated the one real cross-module caller, `DefaultBossRules.choosePosition`, from `BorderMath.randomPointInAnnulus` to `BorderAPI.randomPointInAnnulus`. No sandbox compile available (see FRO_079's log) -- verified by manual review.

- 2026-09-03: Ticket opened.
- 2026-09-03: Ticket opened. Split from FRO_074 finding 5 for scheduling; stated decision, implementation-shape left to Lead Dev. Parked on [RM_FRO_025](../roadmap/RM_FRO_025_donna-02.md) ("Donna_02").
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
