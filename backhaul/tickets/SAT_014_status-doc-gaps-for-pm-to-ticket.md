---
id: SAT_014
uid: SAT
number: 14
client: Satchel
status: done
title: Status + doc gaps for PM to ticket
context: Compile-fix saga closed out. Lists what's left to reach a fully documented
  Satchel/FrontierMode.
priority: normal
opened: '2026-08-13'
closed: '2026-08-13'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Status + doc gaps for PM to ticket

### For PM (Walter)

Status report on the "repo doesn't compile" investigation, and the doc-gap list to turn into real
tickets. Architect doesn't have scope authority — this is a proposal for you to prioritize and
ticket, not a commitment.

### Status: where things stand

Both repos build clean — `Satchel` and `FrontierMode`, `BUILD SUCCESSFUL`, zero errors, confirmed
against real `gradlew build` output, not static analysis alone. Full chain that got us here:
SAT_008 (Satchel's own compile-blocking façade drift) → SAT_005 (duplicate, closed alongside) →
SAT_011 (`EventHandlers.install(bus)` wiring gap) → FRO_011 (`BorderAPI` bad method call) →
FRO_012 (the actual port of Border off the dead `SatchelJigRegistrar`/`SatchelStrap` pattern onto
`JigConfig`/`EventHandlers`) → SAT_006 (inverted `requireClient()` guard) → SAT_009
(`validateTypes` argument order). All closed. SAT_012 turned out to be a misdiagnosis (traced to a
real `ScopeEngine_Server` bug instead, split into SAT_013, still open — not compile-blocking,
a runtime persistence-capability bug). SAT_004 (rename `common.newstuff` → `common.persistence`)
has the naming decided but not yet executed; SAT_007 folded into it as a duplicate.

Wiki brought current with all of the above: `border.md`, `jig-registration-break.md`,
`jig-registration-recovery-plan.md`, `persistence.md`, and roadmap nodes `RM_FRO_003`/`RM_FRO_005`
(both had "flagging for PM, may not still be `resolved`" notes from earlier in this investigation
— both now confirmed genuinely resolved and updated accordingly).

### Doc gaps — proposed tickets, in rough priority order

1. **No dedicated jig/scope/foundation runtime architecture page.** `bundle.md`/`fixture.md`
   cover state aggregation and the persisted-unit model; `persistence.md` covers disk save/load;
   `net.md` covers transport. But the actual runtime machinery underneath all of Satchel's
   tick/event delivery — `LogicalFoundation`, `LogicalSideContext`, `ScopeInfo`, `JigInfo`,
   `SatchelJig`/`ASatchelJig`, `FoundationLifecycleDispatcher`, `ScopeLifecycleDispatcher`,
   `SatchelEventBus`, and the `JigConfig`/`JigConfigCompiler`/`CompiledJigConfig` declarative
   config layer on top of it — has no standalone reference page. It currently only exists as
   narrative inside `jig-registration-break.md`/`jig-registration-recovery-plan.md` (a
   break-and-fix story, not meant to be the long-term doc surface). This is the single biggest gap
   and the best time to close it is now, while the mechanism is freshly re-verified end to end.
2. **`jig-registration-break.md`'s title and status frontmatter are stale.** Title still reads
   "(currently broken)" and `status: draft`, even though its own "Resolved — 2026-08-13" section
   says otherwise. Cheapest fix: fold its content into the new runtime page from (1) as historical
   record, retire this page or retitle it plainly (e.g. "Jig & Strap Registration — History").
3. **`persistence.md` follow-up once SAT_004 executes.** Small, mechanical, but worth its own
   ticket so it doesn't get forgotten — drop the "(rename decided, execution pending)" caveat and
   confirm all class references match the new `common.persistence` package.
4. **`common/exp/*` (`BaseConfig`, `CategoryAConfig`, `HomeCategoryAConfig`, `HomeConfig`,
   `UseCase`) is confirmed dead/unreferenced scratch code**, but nothing tracks it — no ticket to
   delete it, no doc marking it as intentional sandbox space. PM call: delete it, or document it
   explicitly as scratch so it stops looking like an oversight.
5. **`satchel.md` itself is still `status: draft`** despite its child pages (`bundle.md`,
   `fixture.md`, `persistence.md`, `net.md`) all being `verified`. Worth a re-verification pass
   once (1) exists, to bring the mod-level summary current with its own sub-pages.
6. **Lower priority, not blocked by anything above:** `frontiermode.md` and
   `frontier-reconciliation.md` are both still `draft`. `frontier-reconciliation.md` doesn't
   reference any of the wiring this investigation touched, so it isn't stale because of that —
   just an open loop from the Sasha design-vocab work, worth a priority call whenever that's back
   in scope.

### Log

- 2026-08-13: All 6 proposed doc gaps ticketed as agreed with PM: item 1 ->
  [SAT_015](SAT_015_runtime-arch.md) (high priority), item 2 -> [SAT_016](SAT_016_jig-break-stale.md),
  item 3 -> [SAT_017](SAT_017_persistence-followup.md), item 4 -> [SAT_018](SAT_018_exp-dead-code.md),
  item 5 -> [SAT_019](SAT_019_satchel-status.md), item 6 -> filed under FrontierMode as
  [FRO_013](FRO_013_frontiermode-draft.md) since both pages live in the frontiermode wiki
  category. Closing this ticket — its job (surface the gaps for PM to ticket) is done; the work
  itself now lives in the 6 new tickets.
- 2026-08-13: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
