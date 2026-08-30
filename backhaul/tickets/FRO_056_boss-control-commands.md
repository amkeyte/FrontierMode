---
id: FRO_056
uid: FRO
number: 56
client: FrontierMode
status: done
title: Boss control commands (in-game admin surface)
context: No command surface exists for BossFixture/BossAPI (list, info, force-defeat,
  etc) -- BorderCommands/MobTrackCommands set the precedent, Boss has none.
priority: normal
opened: '2026-08-28'
closed: '2026-08-29'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Requested by project owner during [RM_FRO_019](../roadmap/RM_FRO_019_karen.md) ("Karen")
playtesting: there is no in-game command surface for Boss at all. `Border`
(`border/server/commands/BorderCommands.java` + `BorderCommandHandler.java`) and mob-tracking
(`server/commands/MobTrackCommands.java`) both have one; `boss/` has none. Exercising or debugging
boss state currently requires either real combat or code changes -- no hands-on admin path, which
is exactly the gap `MobTrackCommands`' own class doc names as its whole reason for existing
("verification aid... so the done bar's checklist can be exercised from chat without code access
or a debugger").

**Existing server-side surface this would wrap** (nothing new needed underneath, this is a command
layer over what's already there):
- `BossAPI.boss(Level)` -- resolve the level's `BossFixture`.
- `BossFixture.all()` / `unmaterialized()` / `layers()` / `get(UUID)` -- listing and lookup.
- `BossFixture.create(BlockPos, int layer)` / `BossAPI.createBoss(Level, Border)` -- record
  creation (unmaterialized).
- `BossFixture.materialize(UUID, BlockPos, UUID entityId)` -- entity-backed materialization.
- `BossFixture.markDefeated(UUID)` -- the new [FRO_045](FRO_045_karen-build.md) call.

**Precedent to follow:** `BorderCommands`/`BorderCommandHandler`'s split (Brigadier registration
in one class, handler logic in another) and `Result`-checking discipline; `MobTrackCommands`'
"nearest mob to the command source" targeting convention (see its own class doc for why it avoids
a UUID argument) is probably the right shape for anything that needs to target a specific boss
mob/entity rather than a bare `bossId`.

**Scoped now, after a full Architect design pass -- [Boss Command Surface](../wiki/frontiermode/architecture/boss-commands.md)
is the spec.** That page covers the complete command-tree design space (`info`/`add`/`delete`/
`transform`/`mob`/`debug`, the full selector chain, a border-move reconciliation gap, etc.) --
deliberately wider than any one build, per the project's own "wiki is the aspirational spec, don't
build out the whole thing in one pass" call. This ticket's actual build scope is the six items
below.

## What to build

Picked as the most likely to see real use first, out of the full design space on the wiki page:

1. `info <selector>` -- full dump of matched boss(es); default selector `@all`, matching Border's
   own default.
2. `add <pos> <layer>` -- plant a boss record; `<pos>` accepts `here`, same as Border's own
   `add here`.
3. `delete <selector>` -- delete a record outright. Net-new: no delete method exists on
   `BossFixture` today.
4. `mob spawn <selector>` -- force materialization now, regardless of chunk-loaded state.
5. `transform defeat <selector>` -- force `markDefeated` without combat; the item that actually
   motivated this whole node, from Karen's own playtest session.
6. `debug goto <selector>` -- teleport to a boss's stored position, spawned or not.

**Selector scope for this pass -- deliberately minimal.** None of the six above need `@id`/
`@name`/`@border`, so this pass ships bare position / `@nearest` / `@all` / `@none` only. That
means **no `BossRecord.borderId` field and no new `BorderSelectorResult` modes are needed for
this build** -- the wiki page's `@id`/`@name`/`@border` chain (and everything it unlocks) is real
design, just not this pass's scope. Keeps this build genuinely small: new `BossCommands`/
`BossCommandHandler` classes (Border's registration/handler split), a `BossFixture.remove(UUID)`
method (the one new method needed), and a selector over the four modes above -- everything else
this ticket calls for already exists (`all()`, `get()`, `create()`, `materialize()`,
`markDefeated()`).

**Permission:** flat `.requires(src -> src.hasPermission(2))`, same as `Border` -- no tiering.

## Log

- 2026-08-28: Ticket opened during Karen playtest. Existing `BossFixture`/`BossAPI` surface and
  `BorderCommands`/`MobTrackCommands` precedent noted above; command shape/scope intentionally
  left open for design.
- 2026-08-29: Scoped after a full Architect design pass (see
  [Boss Command Surface](../wiki/frontiermode/architecture/boss-commands.md)). Build scope set to
  the six items above; selector scope deliberately minimal (no `borderId`, no Border-side
  selector changes needed this pass); permission mimics `Border`'s flat `hasPermission(2)`.

- 2026-08-29: **Closed — scoping done, handed off to [FRO_057](FRO_057_boss-control-commands-build.md)
  for the actual Lead Dev build,** same split as [FRO_044](FRO_044_karen-prep.md) →
  [FRO_045](FRO_045_karen-build.md) for Karen. Readiness confirmed first: Karen resolved, both
  Susan epoch containers checked clean against this build's actual scope. This ticket's own "What
  to build" carries forward unchanged onto FRO_057.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
