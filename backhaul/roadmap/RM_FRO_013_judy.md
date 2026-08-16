---
id: RM_FRO_013
uid: RM_FRO
number: 13
kind: work
status: open
title: Border fixture & compass robustness
owner: Arryn
depends_on:
- RM_FRO_008
created: '2026-08-16'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_FRO
<!-- bh-header:end -->

## Border fixture & compass robustness

- 2026-08-16: Node opened, sibling to [RM_FRO_011](RM_FRO_011_betty.md) and
  [RM_FRO_012](RM_FRO_012_carolyn.md) under [RM_FRO_010](RM_FRO_010_susan.md) — same source-level
  resilience pass over `border/*`. Grouped together because both items are the same shape: a
  specific piece of stored state (saved NBT, a player's inventory) not defended against a
  realistic bad input, rather than a design gap.

**1. `BordersFixture.loadBorders()` doesn't degrade gracefully on malformed data.**
[Persistence](../wiki/satchel/architecture/persistence.md)'s own error-handling table states
"Invalid fixture NBT → Log + skip" as Satchel's contract. `loadBorders()` doesn't follow it: it
loops `Border.load(this, (CompoundTag) t)` over every entry in the saved list with no try/catch.
A single malformed or hand-edited border entry (missing `id`, wrong tag type, a future format
change) throws during `Border.load`'s `tag.getUUID("id")`/etc. and — as far as static reading
shows — aborts hydration of the entire border list for that world, not just the one bad entry.
Fix: wrap the per-entry load in a try/catch, log and skip a failing entry, keep the rest — matching
the contract this class is supposed to already honor.

**2. `BorderPathCompass.find()` only scans the main inventory — a live item-duplication bug.**
`find()` iterates `serverPlayer.getInventory().items` (the 36-slot main inventory) looking for the
`FrontierCompass` NBT marker. It never checks the offhand slot
(`Inventory.offhand`, a separate field in Forge 1.20.1's `Inventory`). `BordersTriggers.updateFinderItems`
calls `BorderPathCompass.giveOrUpdate` on a throttled poll (every 5 ticks, via
`BordersTriggers.FINDER_ITEMS_UPDATE_MONITOR`) — every single poll where a player's frontier
compass is sitting in their offhand, `find()` reports `ItemStack.EMPTY`, and `giveOrUpdate` grants
a brand new one. Trivially reproducible: move the compass to offhand, wait, watch inventory fill
with duplicates. Fix: check offhand (and consider armor/other non-main slots if there's any other
legitimate place a player might stash it) alongside the main inventory scan.

**3. `BordersAPIException.java` is dead, non-functional scaffolding.**
`public class BordersAPIException { }` — no fields, no constructor, and it doesn't extend
`Exception`/`Throwable`, so it can't actually be thrown as-is. Grep confirms nothing constructs or
throws it anywhere. Fix: delete it, unless whoever picks this up wants to actually build it out
into a real exception type for the Border API's error paths — either is fine, leaving it as
dead-but-present is not.

- 2026-08-16: **Folded in ahead of implementation, project owner's call** — item 3 above
  (`BordersAPIException`) added from the same resilience pass's final sweep, before Lead Dev
  started on this batch. Kept in this node rather than opening a fourth sibling, since it's the
  same "dead/undefended fixture-adjacent code" shape as items 1-2.

**Done bar:** (1) confirmed by loading a save file with one deliberately-corrupted border entry
among valid ones and verifying the valid ones still load; (2) confirmed by moving a frontier
compass to offhand through a full finder-items poll cycle and verifying no duplicate is granted;
(3) confirmed by grep showing no remaining reference once deleted (or, if built out instead, that
it's a real usable exception type with at least one real throw site). All three are cheap to
verify directly, no multi-session play test needed.

- 2026-08-16: **Implemented by Lead Dev (Curtis), unverified — no build access this session.**
  - **Item 1 fixed as described:** `BordersFixture.loadBorders()` now wraps each entry's
    `Border.load(...)` in a try/catch, logs and skips a failing entry via `OUT.warn`, and keeps
    hydrating the rest of the list — matches Persistence's documented "Invalid fixture NBT → Log +
    skip" contract instead of aborting the whole list on one bad entry.
  - **Item 2 fixed as described:** `BorderPathCompass.find()` now also scans
    `ServerPlayer.getInventory().offhand` after the existing main-inventory (`.items`) scan.
  - **Item 3: deleted**, not built out — grep confirms no remaining reference
    (`border/common/util/BordersAPIException.java` removed; the now-empty `common/util/`
    directory was left in place, harmless for the build).
  - **Unverified this session** — no Forge/Mojang maven access (confirmed via curl). This node's
    own done-bar is cheap to verify directly (a deliberately-corrupted save entry; one
    offhand-compass poll cycle; a grep) — see
    [FRO_023](../../tickets/FRO_023_playtest-checklist-batch2.md). Singleplayer/integrated is
    sufficient; nothing here crosses a client/server network boundary.

## Required By

*(computed — nothing depends on this yet)*
