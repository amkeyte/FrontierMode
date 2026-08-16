---
id: FRO_021
uid: FRO
number: 21
client: FrontierMode
status: done
title: Clear FrontierMode's remaining out-of-spec Satchel touch points
context: 'Found during post-Paul git-diff-vs-spec audit: BorderModule still calls
  dead BundleFactories API; two call sites bypass LevelResolver''s token-aware defer
  logic.'
priority: normal
opened: '2026-08-15'
closed: '2026-08-15'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Opened while auditing git diffs against the wiki's spec/architecture pages following
[RM_SAT_017](../roadmap/RM_SAT_017_paul.md) ("Paul") reaching. Most of Curtis's landed work
(RM_SAT_012/013/014/015/019) matched its roadmap spec exactly and is already reflected in
[Forge Integration & Sidedness Contract](../wiki/satchel/spec/forge-integration.md) and
[Jig & Scope Runtime](../wiki/satchel/architecture/runtime.md), both corrected this pass. These
three items are genuinely open — flagged in passing on other roadmap nodes' own logs, but never
turned into tracked work:

**1. `BorderModule.java` still calls the dead `BundleFactories` API, with a now-wrong comment.**
[RM_SAT_012](../roadmap/RM_SAT_012_donald.md) consolidated `ScopeEngine_Server`/`_Client` onto
`bundleDecls` and explicitly logged FrontierMode's `BorderModule` as a known follow-up it
deliberately didn't touch. Confirmed still present:
```
// Still the live bundle/fixture-construction registry -- JigBundles.Schema below
// is required by JigConfigValidator but isn't consumed for actual construction
// (ScopeEngine.create() goes through BundleFactories, not bundleDecls). Keep both.
BundleFactories
        .registerFactory(
                FrontierKeys.BORDERS_BUNDLE,
                scope -> new BordersBundle(scope, FrontierKeys.BORDERS_BUNDLE))
        .registerFixture(
                FrontierKeys.BORDERS,
                BordersFixture::new);
```
(`BorderModule.java`, import at line 10, block at lines 36-43.) The comment is factually wrong as
of RM_SAT_012 — `ScopeEngine.create()`/`.get()` now read `bundleDecls`, not `BundleFactories`. The
`registerFactory`/`registerFixture` call itself may now be dead weight rather than load-bearing;
needs a real check (does anything still read `BundleFactories.entryFor(...)` for Border's key) before
deleting, not an assumption.

**2. Two call sites construct `LevelScope` directly, bypassing `LevelResolver`'s token-aware defer
logic.** [RM_SAT_019](../roadmap/RM_SAT_019_dennis.md) (world-identity token) made
`LevelResolver.resolveScope` withhold recognizing a `Level` as a scope until the client has
received its world-identity token — and flagged both of these as "out of this pass's scope to
touch or verify":
- `BorderAPI.scope(Level level)` — `return new LevelScope(level);` (`BorderAPI.java:60`)
- `RenderContext`'s cache-key construction — `LevelScope scope = new LevelScope(level);`
  (`RenderContext.java:59`, inside `RenderContext.get()`/similar)

Neither goes through `LevelResolver`, so neither participates in the defer-until-token-arrives
window RM_SAT_019 introduced — they'll happily construct a `LevelScope` before the token round-trip
completes. Whether that's actually reachable in practice (BorderAPI/RenderContext usage may only
ever run after a scope is already confirmed live via some other path) isn't verified — that's the
open question this ticket exists to resolve, not assumed to be a live bug.

**3. Minor: `Satchel/testwrite.tmp` is a stray empty file**, unrelated to any module, likely a
leftover from manual testing. Safe to delete; flagging rather than deleting unilaterally since it's
outside this ticket's actual scope.

Not blocking anything — [RM_FRO_010](../roadmap/RM_FRO_010_susan.md) (FrontierMode hardening
convergence) is the natural home for #1 and #2 once scoped as real work nodes, but this ticket
exists so the finding isn't lost before that happens.

## Log

- 2026-08-15: **All three items resolved by Lead Dev (Curtis).**
  1. `BorderModule.java`: removed the dead `BundleFactories.registerFactory(...).registerFixture(...)`
     call and its now-wrong comment, plus the now-unused import. Confirmed first, via grep across
     both repos, that nothing anywhere calls `BundleFactories.entryFor(...)` (the only method that
     would actually read what that call wrote) — `TrackingModule.java`'s only remaining mention is
     a comment, not a call. Now mirrors `TrackingModule.init()`'s already-cleaned, schema-only
     state.
  2. **Investigated, not assumed — resolved as one real (non-crashing) bug plus one confirmed-safe
     path:**
     - `BorderAPI.scope(Level)`: its only real call site is `BorderCommandHandler.debugCreate`,
       which is server-only. The server never defers under RM_SAT_019's block/defer design —
       `ServerForgeIngress` binds the world-identity token before introducing any source — so this
       call site's `LevelScope` UUID is already stable (token-folded) the first time it could
       possibly run. Left as a direct construction (documented why, inline) rather than routed
       through `LevelResolver.resolveScope` and made nullable for no behavioral gain.
     - `RenderContext.getInstance()`: this one was real. Its direct `new LevelScope(level)` is used
       as a long-lived `CACHE` key (`Map<LevelScope, RenderContext>`), and `ASatchelScope`'s
       `equals()`/`hashCode()` delegate to the scope's UUID — which, post-RM_SAT_019, differs
       before vs. after the world-identity token arrives (dimension-only fallback vs. token-folded).
       A `RenderContext` cached under the pre-token UUID would never be found again once the token
       lands, silently forking a second `RenderContext` for the same real level and discarding the
       first's `revisionMonitor`/`cachedBorders` state. Not a crash (each `RenderContext` still
       correctly re-derives its own fresh scope on every `BorderAPI.borders()` call), but a real,
       demonstrable identity-instability bug contradicting the class's own "cached instance per
       level" intent. Fixed by routing through `LevelResolver.resolveScope(level)` instead — it
       already returns `null` during that same pre-token window (RM_SAT_019's own block/defer
       logic), and `getInstance()` already had an established "not ready yet → `Optional.empty()`"
       convention (see its FRO_016 handling) to fold that into.
  3. Deleted `Satchel/testwrite.tmp` — confirmed empty and unreferenced first.
  Not build-tested in this session (same sandbox limitation as the rest of this pass) — needs a
  real `gradlew build` before treating this as fully proven, same caveat as RM_SAT_019 itself.
- 2026-08-15: Ticket opened, findings logged above. Surfaced during a full git-diff-vs-wiki audit
  following RM_SAT_017 ("Paul") reaching — the audit's other outputs were direct wiki corrections
  (forge-integration.md, jig-registration-recovery-plan.md, runtime.md, facade-vision.md), not
  ticket-worthy since they were pure documentation drift, not code needing a decision.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
