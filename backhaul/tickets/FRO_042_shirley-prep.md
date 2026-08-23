---
id: FRO_042
uid: FRO
number: 42
client: FrontierMode
status: open
title: Architect prep for Shirley (RM_FRO_018)
context: boss.md describes a MobJig interest API that shipped differently, and is
  still draft.
priority: high
opened: '2026-08-22'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

Architect-lane work standing between now and Lead Dev opening
[RM_FRO_018](../roadmap/RM_FRO_018_shirley.md) ("Shirley," boss entity/spawn system) — the next
node, now genuinely startable since [RM_SAT_021](../roadmap/RM_SAT_021_frank.md) ("Frank") resolved.

Four items. Item 1 is the one that would otherwise bite mid-build.

## 1. `boss.md` describes a `MobJig` interest API that shipped differently

[Boss](../wiki/frontiermode/architecture/boss.md) was written against Frank's *design*. Frank
shipped with a different registration shape, for a reason recorded in
[SAT_035](SAT_035_mobjig-build.md)'s log: there was no extension point in the
`JigConfig`/`Presets`/`CompiledJigConfig` framework to hang interest on, and `JigInfo` — what
`reconcile` actually receives — has no back-reference to the originating `MobJigConfig` instance.

- **`boss.md` says** `BossModule` registers "a `MobJig` interest supplier" through `MobJigConfig`
  (see its module-wiring section, and "Three questions, three different mechanisms").
- **What shipped** is a standalone `MobInterestRegistry`, deliberately outside that framework:
  `MobInterestRegistry.register(JigKey<?> key, MobInterestSupplier supplier)` / `.unregister(key)`.
- **`MobInterestSupplier.interestedMobs()` returns `Map<ServerLevel, Set<UUID>>`** — and **this
  signature is being reversed**. Project owner ruled 2026-08-22 that `MobJig` should not be
  server-bound; see [RM_SAT_022](../roadmap/RM_SAT_022_roger.md) ("Roger") and
  [SAT_037](SAT_037_mobjig-sidedness.md). Shirley's own `sideApplicability` stays `SERVER` either
  way, but `BossModule` implements this interface, so **whether `interestedMobs()` is typed against
  `ServerLevel` or something wider should be settled before Shirley is written, not after.** Treat
  the current signature as provisional.
- **`MobTrackingModule` is the worked example** — `watch(ServerLevel, UUID)` /
  `unwatch(ServerLevel, UUID)` / `currentInterests()`, with `init()` called from `SatchelMod`'s
  constructor. `BossModule` should follow that shape, including the `init()` call: SAT_035 found
  `MobTrackingModule` compiled but was never installed because that call was missing, so `MobJig`
  was never a registered jig at all. Easy to repeat.

`boss.md` is the page Shirley builds from, and Shirley's own standing note now says to build
against [Jig & Scope Runtime § MobJig](../wiki/satchel/architecture/runtime.md#mobjig) rather than
Frank's log — so this is the one place the two can disagree without anyone noticing until code is
being written.

## 2. `boss.md` is still `status: draft`

It has been through six substantial revisions and reads settled, but the status field says
otherwise, and it is the design of record for the next node. Either promote it or say plainly what
is still open — the current state gives Lead Dev no signal either way. Worth doing in the same pass
as item 1, since that pass is what would justify the promotion.

## 3. Trace `getInitial()`'s actual call site

Shirley's 2026-08-18 entry states that record creation is a direct call paired at every real
border-creation site — `growCenteredOn()`'s post-defeat caller (Karen's) and `getInitial()` for a
level's first border — and flags that "the latter's actual call site wasn't traced this pass,
flagged for Lead Dev to confirm."

That is an Architect trace question, not a Lead Dev one: it determines where the paired
`BossFixture` record-creation call goes, and getting it wrong means a level's first border has no
boss and the reconciliation check reports corruption on a perfectly normal world. Settle it before
implementation rather than during.

## 4. Promote `mobscope-getfor.md`

[MobScope.getFor() Contract](../wiki/satchel/spec/mobscope-getfor.md) is still `draft`.
[SAT_035](SAT_035_mobjig-build.md) named promoting it a deliverable, and Lead Dev deliberately
declined on lane grounds — Architect's call, not his. Reasonable, and it left the item without an
owner; this is that owner.

The contract is now real, shipped, and dedicated-server verified, including the specific guarantee
that turned out to matter: SAT_035 found and fixed a `reconcile()` bug where a `getFor`-attached
scope with no matching interest entry was torn down one cycle later regardless of resolvability —
a direct violation of this page's "stays scoped while resolvable" wording. The page caught a real
bug by being written first. Worth noting when deciding whether it has earned `verified`.

`BossModule` is one of the two external callers this contract exists for, so Shirley's implementer
will be reading it either way.

## Not in scope, mentioned so it isn't lost

- **[Bundle](../wiki/satchel/architecture/bundle.md) still defines a bundle as a "named, keyed
  container of facets"**, contradicting [Fixture](../wiki/satchel/architecture/fixture.md) and
  [Border](../wiki/frontiermode/architecture/border.md), which both say bundles contain fixtures and
  facets are views onto a fixture. Recorded on [FRO_041](FRO_041_arch-dehistory.md); it is a real
  Satchel-side design call and doesn't block Shirley.
- **[SAT_036](SAT_036_log-noise-followup.md) item 2** asks whether either of SAT_035's two
  shared-framework log changes deserves a wiki line. Also Architect's, also not blocking.

## Log



- 2026-08-22: Ticket opened.

- 2026-08-23: **Items 1 and 2 done (Architect).** [Boss](../wiki/frontiermode/architecture/boss.md)
  corrected: "Module wiring" step 3 now describes the real, shipped registration mechanism —
  `MobInterestRegistry.register(key, supplier)`, a call separate from configuring `MobJigConfig`,
  mirroring `MobTrackingModule`'s worked pattern (`MobInterestRegistry.register(JIG, ...)` alongside
  `Satchel.registerJigConfig(config)`) — and the `init()`-must-actually-be-called gotcha SAT_035
  found is now called out explicitly, so it isn't repeated. A new "Known gaps" section (mirroring
  [Jig & Scope Runtime](../wiki/satchel/architecture/runtime.md)'s own) consolidates the three
  things still genuinely open: `getInitial()`'s untraced call site (item 3, project owner's own),
  `interestedMobs()`'s map-key type being provisional pending
  [RM_SAT_022](../roadmap/RM_SAT_022_roger.md) ("Roger")'s not-yet-built `ForgeEgress`, and the
  expected-but-absent reconciliation question already in "What can actually go wrong." Promoted
  `draft` → `verified` — six revisions in, this is the design of record Lead Dev builds Shirley
  against, and `runtime.md` is the live precedent that `verified` + an honest "Known gaps" section
  aren't in tension. Item 4 (promoting `mobscope-getfor.md`) not part of this pass — separate page,
  separate decision, still open.

- 2026-08-23: **Item 3 done (project owner).** `getInitial()` traced to its one real call site —
  `BordersPathFacet.grow()`'s own empty-path branch, which already falls through to it correctly.
  The actual gap was never a hidden call site; it's that nothing auto-bootstraps a fresh level's
  first border. Settled design: a persisted `seeded` flag on `BordersFixture` plus a `BorderModule`
  subscription to `ScopeEvent.Loaded` calling `BorderAPI.grow(level)` for an unseeded level — keeps
  the admin in control, per the project owner's own framing. Written up on
  [Border](../wiki/frontiermode/architecture/border.md#known-gaps),
  [Boss](../wiki/frontiermode/architecture/boss.md#known-gaps), and
  [RM_FRO_018](../roadmap/RM_FRO_018_shirley.md)'s log. Design only, not yet built. All of items 1-3
  are now done; item 4 remains open.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
