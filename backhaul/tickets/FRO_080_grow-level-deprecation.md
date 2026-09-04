---
id: FRO_080
uid: FRO
number: 80
client: FrontierMode
status: done
title: Deprecate BorderAPI.grow(Level)
context: '[Donna_02] pathGrow gets explicit center arg; sequenced after findings 1
  & 3. FRO_074#7'
priority: normal
opened: '2026-09-03'
closed: '2026-09-03'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Deprecate BorderAPI.grow(Level)

Split from [FRO_074](FRO_074_cartographer-findings.md#7-deprecate-borderapigrowlevel-in-favor-of-growlevel-blockpos----per-project-owner-fallout-not-yet-resolved)
finding 7. `BorderAPI` carries two `grow` overloads: no-center `grow(Level)` (thin wrapper over
`BordersPathFacet.grow()`) and explicit-center `grow(Level, BlockPos)` (added for Karen/
RM_FRO_019, Boss's defeat handler its first consumer). **Per project owner: deprecate the
no-center form, standardize on the explicit-center one.**

**Real callers of `grow(Level)` today** (grep-verified):

- `BorderCommandHandler.pathGrow` (`/border grow`) -- no natural center at this call site today.
- `BorderModule.onBordersScopeLoaded` -- the bootstrap call site [FRO_075](FRO_075_bootstrap-ownership.md)
  (finding 1) moves into `BossModule`. Once that lands, this caller moves with it.
- `BordersTriggers.growPath` -- removed outright by [FRO_076](FRO_076_gold-block-growth-removal.md)
  (finding 3), not migrated.

**Sequencing: do not action before [FRO_075](FRO_075_bootstrap-ownership.md) and
[FRO_076](FRO_076_gold-block-growth-removal.md) land.** Once both do, `pathGrow` should be the
only real remaining `grow(Level)` caller -- confirm that's still true before implementing, since
neither had landed as of this ticket's opening.

**Resolved by project owner:** `pathGrow` gets a new, explicit command argument for its center --
no implicit default (not path tip, not the issuing player's position). `/border grow` becomes
`/border grow <center>` (or equivalent), and the caller is required to supply one.

## Log
- 2026-09-03: Implemented, with one correction to the ticket's own pre-implementation assumption -- re-grepped the whole tree per the ticket's own "confirm that's still true before implementing" instruction and found **two** real remaining `BorderAPI.grow(Level)` callers after FRO_075/FRO_076 landed, not just `pathGrow`: the FRO_075-relocated `BossModule.onBordersScopeLoaded` bootstrap listener is the second. The ticket only said this caller "moves into BossModule" (true) without saying it also gets migrated to the explicit-center form -- and it genuinely can't be migrated the way `pathGrow` was: a level's very first border has no natural center to supply (no player, no command context, no prior death location), which is exactly the no-center overload's own reason to exist. Judgment call: kept that one caller on `grow(Level)`, marked it with `@SuppressWarnings("deprecation")` plus a comment naming this ticket, and named it explicitly as the one legitimate exception in `grow(Level)`'s own new `@deprecated` doc comment -- so the method is deprecated (steering every *other* caller toward the explicit-center form) without being removed.
- 2026-09-03: `pathGrow` migrated: `/border path grow` now requires a `center` command argument (`BlockPosArgument`, same shape `add()`'s existing `pos` argument already uses, no implicit default per project owner's resolved sub-question), threading through to `BorderAPI.grow(level, center)`. `BorderAPI.grow(Level)` marked `@Deprecated` with a doc comment pointing at `grow(Level, BlockPos)` and naming the one remaining caller. Verified via brace/paren balance and a whole-tree `.grow(` grep sweep confirming exactly one remaining no-center call site (BossModule's, intentional) and every other call site already on the explicit-center form (no compiler available in this sandbox -- see standing note).

- 2026-09-03: Ticket opened.
- 2026-09-03: Ticket opened. Split from FRO_074 finding 7 for scheduling. Open sub-question (what pathGrow passes as center) resolved by project owner: new required command argument, no implicit default. Sequencing dependency on FRO_075/FRO_076 landing first noted above -- not yet actionable. Parked on [RM_FRO_025](../roadmap/RM_FRO_025_donna-02.md) ("Donna_02").
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
