---
id: FRO_088
uid: FRO
number: 88
client: FrontierMode
status: open
title: BorderAPI.MATH declared but never initialized
context: 'Debt — FRO_078 partial landing. BorderAPI.MATH is public static but always null.'
priority: low
opened: '2026-09-05'
closed: null
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
