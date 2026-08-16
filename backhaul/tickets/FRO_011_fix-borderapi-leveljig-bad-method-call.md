---
id: FRO_011
uid: FRO
number: 11
client: FrontierMode
status: done
title: Fix BorderAPI.levelJig() bad method call
context: Calls foundation().jigInfo(...), which doesn't exist; needs requireJigInfo(...).
priority: normal
opened: '2026-08-13'
closed: '2026-08-13'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Fix BorderAPI.levelJig() bad method call

### Context

Confirmed by a real FrontierMode `gradlew build` (Java 17), 2026-08-13, once Satchel itself
compiled clean ([SAT_008](SAT_008_fix-4-satchel-compile-blocking-errors.md)) and the
build finally reached FrontierMode's own `compileJava`. `BorderAPI.levelJig()`
(`border/BorderAPI.java:57`) calls `foundation().jigInfo(FrontierKeys.BORDERS_JIG).jig`.
`LogicalFoundation` has no method named `jigInfo` — it has `askJigInfo(JigKey<?>)` (returns
`Optional<JigInfo>`) and `requireJigInfo(JigKey<?>)` (throws `SatchelException.JigNotFound`
directly). Same root cause as the `Satchel.java` façade break SAT_008 already fixed — a different
call site left behind by the same `LogicalFoundation` rename. Grepped project-wide for other
`.jigInfo(` calls on `LogicalFoundation`; this is the only remaining one (other hits are
`ScopeInfo.jigInfo()`, a different, legitimate method).

Most of the errors in this same build are the already-documented, already-tracked
`SatchelJigRegistrar`/`SatchelStrap` break — see
[Jig & Strap Registration](../wiki/satchel/architecture/jig-registration-break.md) and its
[recovery plan](../wiki/satchel/architecture/jig-registration-recovery-plan.md). This ticket is
just the one FrontierMode-side error that wasn't already part of that story.

### Suggested fix

Rename `foundation().jigInfo(...)` to `foundation().requireJigInfo(...)`. `levelJig()`'s existing
try/catch already converts a `RuntimeException` into `SatchelException.JigNotFound` —
`requireJigInfo` throws that exact type directly, so the catch block may become redundant (worth
checking whether it can simplify, not just whether it still compiles).

### Required By

*(none)*

## Log

- 2026-08-13: Ticket opened.
- 2026-08-13: Fixed as suggested — renamed to `requireJigInfo(...)` and dropped the try/catch
  entirely rather than keeping a shell around it; `requireJigInfo` already throws
  `SatchelException.JigNotFound` for the not-found case, and the class's own javadoc says it
  should "fail loudly," so a stray `ClassCastException` on a bad cast is better left uncaught than
  silently relabeled. Confirmed by a real `gradlew compileJava`: `BorderAPI.java` no longer
  appears anywhere in the error output. Remaining `BorderModule.java`/`FrontierKeys.java` errors
  are entirely the pre-existing `SatchelJigRegistrar`/`SatchelStrap` break, as expected — untouched,
  not in scope here. Closing.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
