---
id: FRO_019
uid: FRO
number: 19
client: FrontierMode
status: done
title: Border ring rendered at hardcoded Y=100, ignoring border's own center height
context: WorldBordersRenderer drew every ring at a fixed RING_Y=100.0f regardless
  of the border's actual center().getY() or local terrain -- no crash, no error, just
  invisible whenever that height happened to sit underground or otherwise depth-occluded.
  User confirmed empirically by flying up to Y 100 and finding the ring right there.
  Fixed by using border.center().getY() instead of the constant.
priority: normal
opened: '2026-08-14'
closed: '2026-08-14'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Not a crash, not a missing/broken data path — [SAT_030](SAT_030_client-refresh-single-shot-hydrate.md)
confirmed working immediately: growth ritual particles rendered correctly on this run. Only the
border ring itself stayed invisible.

**Root cause:** `WorldBordersRenderer.renderOne()` drew every ring at a hardcoded
`RING_Y = 100.0f`, ignoring the border's own `center().getY()` entirely (only `getX()`/`getZ()`
were read). `Thornwall`'s actual center, per the user's own `/border info` output, is at Y 72 —
28 blocks below where the ring was actually drawn. `GrowthTriggerRenderer`'s particle ritual site
never had this problem because it explicitly does a ground-height lookup
(`level.getHeight(Heightmap.Types.MOTION_BLOCKING, ...)`) before placing itself; the ring never
did anything equivalent. No error anywhere — `debugFilledBox()` depth-tests normally, so a ring
drawn inside solid terrain (or otherwise oddly placed relative to the visible world) just doesn't
show, silently. Confirmed empirically, not just by source reading: the user flew up to around
Y 100 and found the ring sitting right there, exactly where the hardcoded constant put it.

**Fix applied:** removed the `RING_Y` constant; `renderOne()`/`drawRingBand()`/`vertex()` now
thread the border's real `center().getY()` through instead.

**Left `in-progress`, not `done`:** need a real re-run to confirm the ring now appears at normal
eye level without needing to fly up to find it.

## Log

- 2026-08-14: Confirmed — user re-ran and reports rings and borders working as intended. Closing.
- 2026-08-14: Root cause traced (see above; user's own empirical test — "look up" — confirmed the
  diagnosis before the fix was even written), fix applied to `WorldBordersRenderer.java`. Left
  `in-progress` pending a real re-run to confirm.
- 2026-08-14: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
