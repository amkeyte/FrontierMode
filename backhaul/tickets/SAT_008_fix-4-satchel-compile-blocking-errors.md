---
id: SAT_008
uid: SAT
number: 8
client: Satchel
status: done
title: Fix 4 Satchel compile-blocking errors
context: Satchel.java facade + 3 others broken per real javac run; see wiki page for
  detail.
priority: high
opened: '2026-08-13'
closed: '2026-08-13'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Fix 4 Satchel compile-blocking errors

### Context

Confirmed by a real `gradlew build` (Java 17) against Satchel alone — see
[Jig & Strap Registration](../wiki/satchel/architecture/jig-registration-break.md#confirmed-by-a-real-compile-2026-08-13)
for the full compiler output and analysis. Satchel does not currently compile standalone; four
distinct errors, none related to each other:

1. **`Satchel.java` (highest priority — blocks the whole library)**: `ask()`/`get()`/`getOrCreate()`
   call `LogicalFoundation.askJig(jigKey)`/`.requireJig(jigKey)`, which don't exist.
   `LogicalFoundation` now has `askJigInfo(JigKey<?>)`/`requireJigInfo(JigKey<?>)` instead,
   returning a `JigInfo` wrapper (the actual `SatchelJig` is on `JigInfo.jig`) rather than a
   `SatchelJig` directly. Looks like `LogicalFoundation` was refactored and this, its own public
   façade, was never updated to match.
2. **`ClientFoundationBooter.java:52,54`**: declares `CompiledJigConfig config = JigConfigCompiler.compileForSide(...)`,
   but that method returns `List<CompiledJigConfig>`. `ServerFoundationBooter.java` has the
   correct pattern already — straightforward mismatch to copy from.
3. **`ScopeEngine_Client.java`**: doesn't override `freezeBundleSchema()` (required by
   `ScopeEngine`); likely also missing `registerBundleSchema(...)` — javac only reports one
   missing method at a time, so check for a second error once the first is fixed.
   `ScopeEngine_Server.java` implements both correctly.
4. **`common/newconfig/TrackingModule.java:86`**: calls `JigConfig.<LevelScope, Level>builder()`,
   a static factory that no longer exists on `JigConfig` (current shape is a per-jig-type
   subclass, e.g. `LevelJigConfig`). Needs a judgment call: port the call site to the current
   pattern, or confirm `TrackingModule` is itself being phased out before touching it.

### Suggested fix

Fix in the order above — (1) unblocks everything else and is the most load-bearing. (2) and (3)
are mechanical, matching the already-correct server-side sibling. (4) needs a quick check on
whether `TrackingModule` still has a live purpose before deciding how to fix the call site.
Re-run the build after each fix; javac may be masking further errors behind these.

### Required By

*(none)*

## Log

- 2026-08-13: Ticket opened.
- 2026-08-13: All 4 fixed and confirmed by a real `gradlew compileJava` (Java 17,
  BUILD SUCCESSFUL, only a pre-existing unrelated deprecation warning left in
  `SatchelNetwork.java`). Note on (3): the second masked error behind
  `freezeBundleSchema()` wasn't `registerBundleSchema(...)` as guessed — it was the rest of
  `IJigConfigurable` (`installJigConfig`, `binding()`, `execution()`, `policies()`, `bundles()`),
  which `ScopeEngine_Client` was missing entirely. Mirrored `ScopeEngine_Server`'s
  implementation for all of it. (4) turned out to be a clean, non-judgment-call port:
  `LevelJigConfig` already pins every binding field `TrackingModule` was setting by hand, so it
  reduces to `new LevelJigConfig(JIG)` + `.bundles().schema(...)` + `.execution().eventHandlers(...)`.
  Two follow-ups opened and routed to PM: SAT_009 (backwards `validateTypes(jig, scope)` call
  order, found in `Satchel.get()`/`requireScopeInfo()`, out of scope here since it's a runtime
  bug not a compile error) and SAT_010 (this ticket's linked wiki page overstates the
  "strap"-equivalent gap — the `eventHandlers` slot already exists and `TrackingModule`
  populates it, it's just never installed onto a `SatchelEventBus`).
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
