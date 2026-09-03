---
id: FRO_069
uid: FRO
number: 69
client: FrontierMode
status: done
title: 'Diane pregen: skip already-generated overlap on border growth'
context: 'RM_FRO_028 (Diane) follow-on: skip already-generated overlap on border-growth
  pregen jobs.'
priority: normal
opened: '2026-09-01'
closed: '2026-09-01'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Diane pregen: skip already-generated overlap on border growth

## Log

- 2026-09-01: Ticket opened.
- 2026-09-01: Fixed. `BorderPregenFixture.runBatch()` prechecks each offset with a non-forcing
  `level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false)` before the existing
  forcing `level.getChunk(chunkX, chunkZ)` call -- `require=false` returns the chunk only if
  it's already at/past `FULL` (already generated), `null` if it would actually need generating.
  An already-there chunk is skipped without spending `CHUNKS_PER_BATCH`'s budget, so a grown
  layer blows through its overlap with the previous (concentric, smaller-radius) border almost
  immediately and only throttles once it reaches genuinely new chunks at the outer ring. Added a
  second, independent cap (`MAX_OFFSETS_SCANNED_PER_BATCH=64`) bounding how many offsets get
  *examined* per batch regardless of skip/generate -- a skip is a cheap status lookup, not free,
  and an overlap run can span hundreds of chunks, so this keeps a long already-generated run from
  becoming its own "Can't keep up" case the way unthrottled real generation did earlier this
  session. Cursor semantics unchanged (still indexes the same deterministic offset list), so
  resumability across a restart is unaffected either way.
  **Not yet build-verified from this session** -- same device-bridge limitation noted on
  FRO_065/066 (Java 11, no network egress in this session's Linux VM). Brace/paren balance
  checked; needs a real border-growth playtest on the Windows dev environment to confirm the
  `getChunkSource().getChunk(..., false)` precheck behaves as expected on this project's actual
  1.20.1/Forge 47.4.10 toolchain before it's trusted the way this session's other, playtest-
  confirmed fixes now are.
- 2026-09-01: Closed -- project owner confirms the pregen overlap-skip is holding on the real build.

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
