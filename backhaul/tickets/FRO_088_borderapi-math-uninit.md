---
id: FRO_088
uid: FRO
number: 88
client: FrontierMode
status: done
title: BorderAPI.MATH declared but never initialized
context: Debt — FRO_078 partial landing. BorderAPI.MATH is public static but always
  null.
priority: low
opened: '2026-09-05'
closed: '2026-09-06'
---

<!-- board:start -->
<!-- board:end -->

## Summary

`BorderAPI` declares `public static BorderMath MATH;` but the field is never assigned.
The nested implementation class introduced by FRO_078 is entirely commented out in source.
Any call to `BorderAPI.MATH.*` will NPE at runtime.

## Workaround

Call `BorderMath` static methods directly — `BorderMath.intensityAt(border, curve, pos)` is
public and works fine. Do not reference `BorderAPI.MATH` anywhere until this is resolved.

## What to fix

- Uncomment / complete the FRO_078 nested MATH class in `BorderAPI`
- Assign `BorderAPI.MATH` during mod init (or make it a proper singleton/static accessor)
- Grep for any existing `BorderAPI.MATH` call sites and verify they don't NPE

## References

- `FrontierMode/src/main/java/com/arryn/frontiermode/border/BorderAPI.java` — the uninitialized field
- `FrontierMode/src/main/java/com/arryn/frontiermode/border/common/BorderMath.java` — working static methods

## Log
- 2026-09-06: Closing. The dead field / uninitialized-facade defect is fixed (real MATH holder replacing the
  commented-out placeholder), and this session has since done many real Gradle builds and live
  server runs against this exact file with no compile or runtime issues -- the fix is inert
  (pass-through static methods) and has not regressed anything. The facade itself (BorderAPI.MATH)
  still has no direct call site in this codebase (BossTellFixture calls BorderMath statics
  directly, both paths equivalent per FRO_088's own log) -- that's a style choice, not a defect, and
  doesn't block closing a ticket about the field being broken.

  Not committed (git managed by project owner this session).
- 2026-09-06: Reviewed and fixed. `BorderAPI.MATH` was a declared-but-never-initialized field of type `BorderMath`, with a fully commented-out companion nested class sitting unused beneath it. Confirmed this is **not** a live NPE today: every `BorderMath` method is `public static`, and Java resolves a static call through an instance-typed reference at compile time without dereferencing the reference -- so `BorderAPI.MATH.isInside(...)` etc. never actually touch the null field at runtime. (The ticket's framing of "any call will NPE at runtime" is overstated; correcting that here for the record.) That said, the field was genuinely broken as a *facade* -- assigning to it, reading it before use elsewhere, or any future refactor that starts treating it as a real object would NPE immediately, and it doesn't do what the wiki's own facade convention implies it should do.

  Fix: deleted the dead field and the fully-commented-out nested class, replaced with a real `public static final class MATH { ... }` holder carrying all the original pass-through static methods (`isInside` x2, `distanceToSurface`, `distanceSqToCenter`, `randomPointInDisk`, `randomPointInAnnulus`, `distanceTo`, `direction`, `intensityAt` x2) -- each just forwards to the matching `BorderMath` static method. `BorderAPI.MATH.foo(...)` now works exactly as the facade always implied it should.

  No behavior change for existing call sites that already bypass the facade and call `BorderMath` directly (e.g. `BossTellFixture`) -- both paths are equivalent now.

  Not committed (git managed by project owner this session). Real build/playtest still owed before this can close.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
