---
id: RM_FRO_020
uid: RM_FRO
number: 20
kind: work
status: resolved
title: Susan epoch maintenance 1
owner: Arryn
depends_on:
- RM_FRO_010
created: '2026-08-28'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_FRO
<!-- bh-header:end -->

## Susan epoch maintenance 1

**Maintenance container, not a feature deliverable.** Gathers unplanned rework/health work found
during the "Susan epoch" — the span opened by [RM_FRO_010](RM_FRO_010_susan.md) ("Susan") reaching
— that doesn't belong on a persona-named node because it isn't building toward a design goal, it's
paying down something a ruling or a build turned up along the way. See
[BHRM — Roadmap Conventions § Epoch maintenance nodes](../wiki/meta/bhrm.md#epoch-maintenance-nodes-containers)
for the full convention (naming, wiring, when a node like this gets to reopen something already
`resolved`). No `ticket:` field on purpose — a container can gather more than one ticket over its
life; what actually landed here is tracked in this log, not a single frontmatter pointer.

**Held [FRO_047](../tickets/FRO_047_border-interface-refactor.md)** — the general Border
external interface refactor (BorderLogic elimination, Result type, facet-resolver conversion,
BorderAuthority removal) split out of [FRO_045](../tickets/FRO_045_karen-build.md) once
[FRO_046](../tickets/FRO_046_growcenteredon-proposal-contract.md)'s ruling turned out to reach well
beyond Karen. [RM_FRO_019](RM_FRO_019_karen.md) ("Karen") depends on this node directly as a
result — real, blocking: Karen's own build assumes the facet/`Result` shapes this container's work
has to land first.

**Worth flagging, not yet acted on.** FRO_046's own "Known blast radius" section names
`BorderModule` among the callers migrating off `BorderAPI.borders(Level)` — and
`BorderModule.onBordersScopeLoaded()` is exactly what
[RM_FRO_018](RM_FRO_018_shirley.md) ("Shirley")'s `resolved` done bar rests on. Whether FRO_047's
refactor actually threatens that behavior hasn't been checked. If it does, per this project's
regression convention, Shirley reopens and that gets logged here, not silently — not warranted on
evidence alone yet, just named so it isn't missed once FRO_047 is further along.

- 2026-08-28: Node opened. Holds [FRO_047](../tickets/FRO_047_border-interface-refactor.md);
  [RM_FRO_019](RM_FRO_019_karen.md) ("Karen") depends_on updated to include this node. First real
  instance of this project's new epoch-maintenance-container convention.

- 2026-08-28: **Resolved — [FRO_047](../tickets/FRO_047_border-interface-refactor.md) closed,
  built and playtest-verified twice.** Karen unblocked from this side (confirmed on
  [FRO_045](../tickets/FRO_045_karen-build.md)'s own log too). Two logged deviations from FRO_047's
  original spec, neither Karen-relevant: `BordersFixture` stayed a public Java type rather than
  package-private (Satchel's `FixtureKey` requires it — ruled final, not revisited, per
  [FRO_050](../tickets/FRO_050_remove-borders-doc-guard.md)); `bordersContaining()` throws
  `SatchelException.ScopeNotReady` rather than returning `Result`, since `Result` only ever carries
  a single `Border` and doesn't fit a query. [FRO_049](../tickets/FRO_049_border-wiki-fro047-catchup.md)
  (wiki catch-up for both) already closed same day.

  **Also gathers [FRO_050](../tickets/FRO_050_remove-borders-doc-guard.md)** (see the "Deferred,
  non-blocking" callout above) — not reopening this node's `resolved` status for it, per this
  project's container-status convention.

- 2026-08-28: Retrofitted with the "Deferred, non-blocking" callout at the top of this node's own
  body, per [BHRM — Roadmap Conventions](../wiki/meta/bhrm.md#epoch-maintenance-nodes-containers)'s
  newly-formalized convention — this node's own FRO_050 mention was the case that prompted writing
  the convention down in the first place, so it's the first one retrofitted to follow it.

- 2026-08-28: **Regression-doubt flag checked against real source, cleared -- Shirley is not at
  risk.** The "worth flagging, not yet acted on" concern above (`BorderModule` migrating off
  `BorderAPI.borders(Level)` under FRO_047, and whether that threatens
  [RM_FRO_018](RM_FRO_018_shirley.md) "Shirley"'s `resolved` done bar) is resolved on evidence, per
  [BHRM's regression-doubt convention](../wiki/meta/bhrm.md#epoch-maintenance-nodes-containers):
  read `BorderModule.onBordersScopeLoaded()` directly. It resolves through `BorderAPI.INFO(level)`
  (the new facet resolver, not the deleted `borders(Level)`), correctly unwraps `BorderAPI.grow(level)`'s
  new `Result` (checks `result.isSuccess()` before calling `BossAPI.createBoss(level, result.border())`,
  warns and returns on failure instead of assuming success) -- same bootstrap behavior as before
  FRO_047, just reading a `Result` instead of a bare `Border`. Shirley's done bar stands; no
  reopen. One cosmetic leftover found in the same read, not a behavior risk: the method's own doc
  comment still says `BorderAPI.borders(level)` by name (a deleted method) -- folded into
  [FRO_050](../tickets/FRO_050_remove-borders-doc-guard.md) as a drive-by, since that ticket is
  already touching doc comments in this exact area.

- 2026-08-28: **Ruling's fallout, folded into existing tickets, no new one opened.** Curtis's
  build-planning session for [FRO_045](../tickets/FRO_045_karen-build.md) surfaced a project-owner
  correction to `growCenteredOn`'s no-tip behavior (bootstraps like `grow()`, not a failed
  `Result`) that in turn collapsed `growCenteredOn` into a `grow(BlockPos center)` overload instead
  of a separately-named method. Formalized on [RM_FRO_019](RM_FRO_019_karen.md)'s own 2026-08-28
  log entry, closed out via [FRO_052](../tickets/FRO_052_growcenteredon-no-tip.md); FRO_045's "What
  to build" rewritten to match. Logged here per this project's epoch-container convention --
  early-epoch rework belongs on the start container even when it's small enough to close same-day
  without its own maintenance ticket.

- 2026-08-29: **[FRO_050](../tickets/FRO_050_remove-borders-doc-guard.md) closed for real** --
  built (all four "What to build" items plus a drive-by retargeting a dangling `BordersBundle`
  javadoc `{@link}`), then confirmed against a real `build.log` from the project owner's machine
  (`compileJava` fresh, `BUILD SUCCESSFUL`, only pre-existing unrelated deprecation warnings).
  `status: done`, closed `2026-08-28`. This was the only item this node had marked "Deferred,
  non-blocking" -- that callout is removed now that nothing here is still open; this node currently
  has no deferred, non-blocking content.

## Required By

*(computed — nothing depends on this yet)*
