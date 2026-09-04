---
id: FRO_073
uid: FRO
number: 73
client: FrontierMode
status: done
title: 'onLivingDeath: duplicate death events mint phantom borders/bosses'
context: 'Corrects FRO_060''s own explicit ruling that onLivingDeath was immune to
  the double-defeat bug (''a Mob can only die once''). Real playtest log disproved
  it: a burst of already-defeated markDefeated warnings each still ran the full grow+createBoss
  cascade, skipping the boss/border chain several layers ahead in 3ms and pregenerating
  a pointless radius-32 disk. Fixed directly, no Architect spec change.'
priority: high
opened: '2026-09-02'
closed: '2026-09-03'
---

<!-- board:start -->
<!-- board:end -->

## Summary

onLivingDeath: duplicate death events mint phantom borders/bosses

## Log
- 2026-09-03: Resolved the flagged open question -- asked the project owner directly about the four distinct "Boss (Layer 0)" Rabbit deaths. Confirmed a testing artifact: repeated manual `/boss` command usage during that playtest, not a real duplication bug. No further investigation ticket needed. The primary fix (this ticket's actual title -- the `markDefeated()` guard in `onLivingDeath`) survived this session's FRO_081 facet refactor intact, now going through `fixture.CRUD.markDefeated(...)` -- reverified by grep. Build verification: the user confirmed "game built and loaded, ran a few boss commands" earlier this session, covering the current state of this code. Closing.

- 2026-09-02: Ticket opened.
- 2026-09-02: Fixed. `BossModule.onLivingDeath` now checks `fixture.markDefeated(bossId)`'s
  boolean return and returns immediately (no-op) when it's `false` -- mirrors `BossAPI
  .forceDefeat`'s existing guard, which FRO_060 built but explicitly did NOT extend to this
  method, reasoning a Mob can only die once. Real evidence from a fresh-world playtest log
  disproved that: a burst of five `[BorderPregen] Pregeneration started` lines fired within 3
  milliseconds, four paired with `markDefeated(): bossId=... is already defeated -- ignoring`
  for boss ids that appear nowhere else in the log -- each still ran the full
  `grow()`+`createBoss()` cascade below the (unchecked) markDefeated call, skipping the chain
  from layer 4 to layer 9 in milliseconds and pregenerating a pointless radius-32/3209-chunk
  disk. Root cause: a single physical mob death can legitimately fire more than one
  `LivingDeathEvent` (documented Forge/vanilla behavior -- multiple queued damage instances in
  one tick can each drive an entity through death handling), and nothing here treated a
  second/third/fourth firing for an already-processed boss as redundant.
  **Separate, still-unexplained oddity from the same log, flagged but not fixed:** the same
  session also showed four *distinct* Rabbit entities (different entity ids: 503, 508, 515, 520),
  all custom-named "Boss (Layer 0)", all dying "fell from a high place" at the identical
  coordinates (6.50, 71.00, -20.50), roughly 1.5-2.5s apart, interleaved with the legitimate
  layer 1-4 chain progressing normally alongside them. Since a mob's custom name is set once at
  its own `materialize()` call from its own record's `layer`, this means multiple genuinely
  separate layer-0 `BossRecord`s existed and independently materialized -- not just one entity
  re-processed. No player-issued `/boss ...` command appears anywhere in the server log (only
  "Set own game mode to Creative Mode" is logged), which normally would be if the player had
  typed one, so this isn't an admin command loop as far as the log can show. Root cause not
  identified this session -- asked the project owner whether anything else (a command block, a
  macro/keybind, repeated `/boss` usage) was active during that window before investigating
  further.
  **Not yet build-verified from this session** -- same device-bridge limitation as tonight's
  other Java fixes. Brace/paren balance checked.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
