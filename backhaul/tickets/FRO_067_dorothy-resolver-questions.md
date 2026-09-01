---
id: FRO_067
uid: FRO
number: 67
client: FrontierMode
status: closed
title: 'Navigator resolver registry: Level param + Structure(...) shape'
context: RM_FRO_026 (Dorothy) build (FRO_065) surfaced two places the discovery-systems.md
  spec did not fully hold up once real code had to do it -- for Douglas (Architect)
  to confirm or amend, not resolved unilaterally.
priority: normal
opened: '2026-08-31'
closed: '2026-08-31'
---

<!-- board:start -->
<!-- board:end -->

## Summary

**For Douglas (Architect).** Building FRO_065 (RM_FRO_026, "Dorothy") against [Boss Discovery
Systems § Navigation lives in
Border](../wiki/frontiermode/architecture/discovery-systems.md#navigation-lives-in-border) turned
up two places the page's own prose didn't fully hold up once real code had to do it. Both were
resolved with a concrete, documented call rather than left blocking -- flagging back per this
project's standing rule, not asking permission to proceed.

**1. `TargetResolver` takes `(Level, UUID)`, not bare `UUID`.** The page describes the registry as
`TargetType -> (UUID -> BlockPos)`, with no `Level` in the signature. But every registry-backed
target this codebase actually has -- bosses, borders -- lives in a level-scoped fixture
(`BossFixture`/`BordersFixture` are both `LevelScope`-hosted, same as `NavigatorFixture` itself),
so there's no cross-level UUID space to resolve a bare id against; `BossAPI.boss(level)` needs a
`Level` to find the right fixture at all. `TargetResolver.resolve(Level, UUID)` is what actually
got built (`border.common.navigator.TargetResolver`). If a cross-level UUID space was intended
some other way, this needs a different shape -- otherwise this is just filling in a gap the page's
shorthand left open.

**2. `Structure(...)`'s exact shape.** The page leaves this as a literal ellipsis -- deliberately
undecided, per its own "Everything on this page is proposal" framing. Built as
`TargetRef.Structure(UUID structureId)`, matching the registry's own UUID-keyed shape (same as
`Boss`/`Border`). Not exercised by RM_FRO_026's own done bar (only `TargetType.BOSS` needed a real
registered resolver), so this cost nothing to leave as a placeholder -- but the real shape (does it
need a `ResourceLocation` structure type, a specific instance id, both?) is still open whenever a
Structure-backed consumer actually gets built.

Everything else in RM_FRO_026's done bar is built and not in question here -- see FRO_065's own
log for the full list.

## Resolution

**Douglas confirms both decisions (2026-08-31):**

1. `TargetResolver.resolve(Level, UUID)` is correct. The Level parameter is mandatory; there's no
   cross-level UUID registry. Curtis's implementation is sound.

2. Keep `Structure(UUID structureId)` simple. UUID-only identification works for now. If future
   Structure-backed consumers need type-specific instance ids, we can pivot then without
   architectural friction.

## Log

- 2026-08-31: Ticket opened.
- 2026-08-31: Douglas confirms both decisions. Ticket closed.

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
