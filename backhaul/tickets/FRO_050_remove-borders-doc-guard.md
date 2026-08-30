---
id: FRO_050
uid: FRO
number: 50
client: FrontierMode
status: done
title: Remove borders(), doc-guard facets
context: '[Susan_01] Remove vestigial BordersBundle.borders(); doc-comment guard facets
  against outside calls.'
priority: low
opened: '2026-08-28'
closed: '2026-08-28'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Two small, related pieces of polish out of the 2026-08-28 Architect review of
[FRO_047](FRO_047_border-interface-refactor.md)'s build -- low priority, doc/cleanup only, no
behavior change.

**1. `BordersBundle.borders()` (`border/common/bundle/BordersBundle.java`) is vestigial.**
`BorderAPI` resolves fixtures itself now (`levelJig().getOrCreate(...).get(FrontierKeys.BORDERS)`
inside its own `resolveFixture()`), never through this convenience wrapper, and grep confirms zero
remaining callers anywhere in the repo. Remove it.

**Explicitly Border-specific, not a repo-wide pattern purge.** `BossBundle.boss()`,
`BossMobBundle.bossMob()`, and `BorderPlayerBundle.status()` carry the identical shape and stay
untouched -- Border's bundle is `intentionally boring: no logic, no state beyond fixtures` (its
own doc comment) hosting exactly one fixture, so a bundle-level unwrap buys nothing a facet
resolver doesn't already give. Boss's bundle situation may genuinely be different in kind as its
data model grows (already two bundles across two scope kinds) -- don't extend this reasoning to
Boss without a separate call.

**2. Final ruling, recorded on [Border § Data
model](../wiki/frontiermode/architecture/border.md#data-model) and [Fixture § External Access Is
Not Compiler-Enforced](../wiki/satchel/architecture/fixture.md#external-access-is-not-compiler-enforced):
`BordersFixture` cannot go fully protected, because Satchel's `FixtureKey`/`SatchelBundle.get()`
mechanism can't be closed off, not from any choice specific to Border.** `SatchelBundle.get(FixtureKey<T>)`
stays public and unguarded (it's the generic mechanism every module's own API class needs), so
anything holding a `BordersBundle` and `FrontierKeys.BORDERS` can still reach the raw fixture and
its public facets directly, bypassing `BorderAPI`'s readiness gate. No compiler fix is possible.
For now, mitigate with doc comments only -- this is polish, not a hardening pass.

## What to build

1. Delete `BordersBundle.borders()` (and its now-unused `Optional` import, if nothing else in the
   file needs it).
2. Add a short doc-comment warning to each of `BordersFixture`'s four facet fields (`PATH`,
   `CRUD`, `RULES`, `INFO`) -- to the effect of: not readiness-gated on its own; reach only through
   the matching `BorderAPI.<NAME>(Level)` resolver, never by holding a `BordersFixture` reference
   directly. Wording is yours; the content above is the ruling to convey, not a literal string to
   paste.
3. Extend `BordersFixture`'s own class doc (it already carries the FRO_047 paragraph on why the
   class stays public) with a sentence or two noting this is the final word, not an open question
   -- point at the wiki ruling above rather than re-deriving the reasoning in source.

4. **Drive-by, found during [RM_FRO_020](../roadmap/RM_FRO_020_susan-01.md)'s regression-doubt
   check (2026-08-28):** `BorderModule.onBordersScopeLoaded()`'s own doc comment still says
   `BorderAPI.borders(level)` by name -- that method is gone. Update the comment to name the
   facet resolver it actually goes through now (`BorderAPI.INFO(level)`). Cosmetic only, not
   part of this ticket's original scope, no behavior change either way.

No behavior change. Done bar: compiles clean, zero remaining references to
`BordersBundle.borders()`, doc comments present on the four facet fields and the class doc, and
`onBordersScopeLoaded()`'s doc comment no longer names the deleted method.

## Standing constraint

**No Gradle in the agent sandbox.** Real build/playtest isn't required to close this out (doc and
dead-code removal only, no logic touched) -- confirm a clean compile the usual way before marking
done.

## Log

- 2026-08-28: Ticket opened (Architect), off the review of FRO_047's build. Both the removal and
  the doc-comment ruling agreed with the project owner in discussion. Not yet built.

- 2026-08-28: **Built (Lead Dev).** All four "What to build" items done: `BordersBundle.borders()`
  deleted along with its now-unused `FrontierKeys`/`Optional` imports; doc-comment guards added to
  `BordersFixture`'s four facet fields (`PATH`/`CRUD`/`RULES`/`INFO`); the class doc extended with
  a `FRO_050:` paragraph marking the public-visibility ruling final (also fixed its stale opening
  line, which still read "class to become package private" -- directly contradicted the ruling
  being finalized in the same doc block); `BorderModule.onBordersScopeLoaded()`'s doc comment
  updated from the deleted `BorderAPI.borders(level)` to `BorderAPI.INFO(level)`. One further
  drive-by found by grep: `BorderPlayerBundle`'s own class doc carried a dangling `{@link
  BordersBundle#borders()}` -- retargeted to `BossBundle#boss()`, the still-live example of the
  same pattern. Repo-wide grep confirms zero remaining references to the deleted method (the one
  other `.borders()` hit, `WorldBordersRenderer`'s `ctx.borders()`, is `RenderContext`'s own
  unrelated method). No Gradle in this sandbox -- reviewed by hand for balanced braces/javadoc
  syntax, but a real compile is still owed on the project owner's machine per this ticket's own
  standing constraint -- left `open`, not `done`, until that's confirmed.

- 2026-08-28: **Confirmed -- real `build.log` from the project owner's machine.** `compileJava` ran fresh (not up-to-date) and produced 2 warnings, both pre-existing deprecation notices in `FrontierMode.java` (`FMLJavaModLoadingContext`/`ModLoadingContext#get()`), unrelated to anything touched here. `BUILD SUCCESSFUL`. Closing.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
