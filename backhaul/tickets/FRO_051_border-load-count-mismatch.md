---
id: FRO_051
uid: FRO
number: 51
client: FrontierMode
status: open
title: 'Border load count: client vs server'
context: '[Susan_02] Client BordersFixture load count (1) mismatches server''s (0)
  on same fresh world.'
priority: normal
opened: '2026-08-28'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

Found during [FRO_047](FRO_047_border-interface-refactor.md)'s build+playtest review (its own
2026-08-28 "Build + playtest review" log entry) -- flagged there and left open, not that ticket's
diff. Filed as its own bug ticket per project owner's direction.

**The discrepancy, quoted from FRO_047's own log:** "server-side `Borders loaded: 0` (fresh world,
expected) vs. client-side `Borders loaded: 1` for the same `LevelScope[minecraft:overworld]`,
logged ~20s later than the server's read, right after a `[engine] CLIENT bundle became dirty
(read-only violation): BordersBundle[...]` warning -- looks like a client-side stale/local read
rather than the synced server state, in Satchel's bundle engine layer, not this ticket's
border-facet code."

Both reads come from the same method, `BordersFixture.onLoaded()`:

```java
@Override
public void onLoaded() {
    super.onLoaded();
    OUT.info("            Borders loaded: " + borders.size());
}
```

**Not a regression from FRO_047's own diff** -- that build didn't touch bundle sync/hydration
timing, per its own note (confirmed via `git show` on the refactor commit). This points at
Satchel's bundle-sync layer, not Border's fixture code -- the `[engine] CLIENT bundle became dirty
(read-only violation)` warning logged immediately before the mismatched read is the strongest
lead.

**Ruled out during Architect review, 2026-08-28 -- saves a dead end:** `onCreated()`'s
`borderPath.clear()` (same "Lifecycle" section, two methods up in the same file) does not explain
this. Traced against `ScopeEngine_Server.create()`'s actual call order (construct fixture ->
`bundle.onCreated()` -> `hydrateBundle()`): `onCreated()` always fires on a freshly-constructed,
still-empty `borderPath`, strictly *before* hydration ever populates anything -- there's no
ordering in this engine where it could see non-empty state. It's also a different field than
`borders`, the one actually logged here. Not the cause, whatever the real one turns out to be.

## What to build

1. **Investigate the actual mismatch** -- `BordersBundle`/`SatchelBundle`'s client-side
   sync/hydration path is the strongest lead (the `[engine] CLIENT bundle became dirty (read-only
   violation)` warning logged immediately before the mismatched read). Root cause not identified
   yet; this is genuinely open investigation, not a known fix waiting to be typed in.
2. **Delete `onCreated()`'s `borderPath.clear()`** (`BordersFixture.java`, "Lifecycle" section) --
   confirmed dead code, not related to item 1's mismatch (see "Ruled out" above), but real enough
   to remove while in the file rather than leave sitting there. No behavior change.

## Done bar

- Root cause of the client/server `Borders loaded` count mismatch identified, and either fixed or
  written up here with enough detail (what's actually happening, why) for a follow-up fix if it
  can't land in the same pass.
- `onCreated()`'s `borderPath.clear()` removed, compiles clean.

## Log

- 2026-09-06: Drive-by item from this ticket's own "What to build" #2, done while investigating FRO_055
  (same suspected root area -- Satchel's client bundle-sync layer): removed
  `BordersFixture.onCreated()`'s dead `borderPath.clear()` call. Already confirmed dead code and
  unrelated to the actual count-mismatch investigation by the 2026-08-28 Architect review noted
  above -- just real enough to remove while in the file. No behavior change; compiles clean
  (brace-balance checked, and this file's live in a project that's been building/running all
  session).

  Item 1 (the actual client/server count mismatch investigation) is still open -- see the FRO_055
  log entry for where that investigation currently stands (points at Satchel's bundle-sync layer,
  same as this ticket's own "Ruled out" section already suspected via the "CLIENT bundle became
  dirty (read-only violation)" warning).

  Not committed (git managed by project owner this session).
- 2026-08-28: Ticket opened (Architect), off FRO_047's playtest note plus a same-day trace ruling
  out `onCreated()`'s `borderPath.clear()` as the cause. Not yet investigated by Lead Dev.

- 2026-08-28: **`borderPath.clear()` removal promoted from optional drive-by note to required
  scope**, per project owner's direction -- confirmed dead code, no reason to leave it. Done bar
  updated to cover both items.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
