---
id: RM_FRO_021
uid: RM_FRO
number: 21
kind: work
status: open
title: Susan epoch review/fix
owner: Arryn
depends_on:
- RM_FRO_020
created: '2026-08-28'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_FRO
<!-- bh-header:end -->

## Susan epoch review/fix

**The standing end-of-epoch container.** Every epoch gets one of these by default, opened alongside
its first maintenance container ([RM_FRO_020](RM_FRO_020_susan-01.md) here) rather than waited on
until it's obviously needed — see
[BHRM — Roadmap Conventions § Epoch maintenance nodes](../wiki/meta/bhrm.md#epoch-maintenance-nodes-containers).
Its job: catch anything found along the way that doesn't need to block whatever's currently being
built (an incidental bug against an unrelated earlier node, something too small to warrant its own
maintenance container) — and be the thing the epoch's next planned node has to clear before it can
open, so nothing carries forward unaddressed.

**Seven real items now, gathered across three build sessions,** none relevant to whatever
[RM_FRO_019](RM_FRO_019_karen.md) ("Karen") or [RM_FRO_022](RM_FRO_022_joyce.md) ("Joyce") are
building, so none block either:

- [FRO_048](../tickets/FRO_048_pathgrow-no-boss.md) — manual `/border path grow` doesn't pair a
  new boss record the way the automatic bootstrap grow does; a pre-existing gap, not a FRO_047
  regression. Low priority, not yet triaged.
- [FRO_051](../tickets/FRO_051_border-load-count-mismatch.md) — client/server `Borders loaded`
  count mismatch on a fresh world; genuinely open investigation, points at Satchel's bundle-sync
  layer rather than Border's own code, confirmed not caused by FRO_047's diff. Normal priority.
- [FRO_054](../tickets/FRO_054_mutation-data-security.md) — project owner's line item: a future
  audit/hardening pass over the mutation-proposal surface (`BorderProposal`, `applyProposal`,
  etc.) for data-security concerns. Not yet triaged or scoped. Low priority.
- [FRO_055](../tickets/FRO_055_growth-particles-stale-tip.md) — found live during Karen's
  playtest: after a boss defeat grows the border, `GrowthTriggerRenderer`'s particle effect stays
  at the previous location instead of the new tip. Client-only, purely visual, no gameplay
  authority. Server-side path (`BordersPathFacet.grow(BlockPos)` appending correctly, `tip()`
  updating) confirmed correct on read-through; root cause still open — client-sync timing vs.
  render-cache staleness, not yet distinguished. Worth checking together with
  [FRO_051](../tickets/FRO_051_border-load-count-mismatch.md) — different symptom, same general
  area (client-side `BordersFixture` state trailing the server's), flagged not confirmed related.
  Normal priority.
- [FRO_058](../tickets/FRO_058_boss-mutation-validation-reconciliation.md) — Lead Dev's read-only
  data-security QA pass over FRO_054 found `BossFixture` has no validation boundary at all
  (`create()`/`materialize()`/`markDefeated()`/`remove()` accept whatever they're given, unlike
  `BordersCrudFacet`'s radius/layer checks), and `BossModule.reconcilePathAgainstBossRecords()`
  only checks one direction. Needs Architect's spec verification/correction before
  implementation. Low priority.
- [FRO_059](../tickets/FRO_059_border-proposal-center-bounds-id-display.md) — same QA pass found
  `BordersCrudFacet.failureReason()` never bounds-checks `center`, and `BorderProposal`'s public
  `id()`/`displayName()` setters are unvalidated (a raw proposal could silently overwrite an
  existing border by UUID collision). Latent, not reachable from any current call site. Needs
  Architect's ruling on the intended `id()`/`displayName()` contract before Lead Dev adds a guard.
  Low priority.
- [FRO_064](../tickets/FRO_064_boss-defeat-cascade-grows-border-level-r.md) — found live during
  playtest: defeating an off-path `/boss add`-ed boss grew the level. Root cause: the
  defeat->grow->next-boss cascade (`BossModule.onLivingDeath`, `BossAPI.forceDefeat`) has no
  concept of path-tip relationship or per-layer boss cardinality -- it fires for any tracked
  boss's defeat, period. This is the real ticket the cardinality question flagged below (2026-08-29,
  "Found while designing Joyce's command surface") said would eventually be needed. Needs
  Architect ruling before Lead Dev builds anything. Normal priority.

**Wired to depend on [RM_FRO_020](RM_FRO_020_susan-01.md) as the default skeleton wiring.** No
longer folded into [RM_FRO_017](RM_FRO_017_donna.md) ("Donna")'s `depends_on` — was, briefly, then
reversed the same day it was added (see log below), project owner's call. This node stays **open
and parked** — seven real items, all real work, none of them currently required by anything
downstream, including [FRO_064](../tickets/FRO_064_boss-defeat-cascade-grows-border-level-r.md)
(see its own log entry below for why that one specifically doesn't change the call even though it's
the sharpest item here). Revisit when there's real appetite for this Border/Boss-side maintenance
backlog, or when something here turns out to be a genuine blocker on something else — at which
point it gets its own `depends_on` edge onto whatever that is, same as any other real dependency.

- 2026-08-28: Node opened, reserved, empty. Kind: work. Owner: Arryn.

- 2026-08-28: `bhrm convergence-bypass` flagged this node against
  [RM_FRO_017](RM_FRO_017_donna.md) ("Donna," `kind: convergence`) — shared ancestor closure
  (both trace back through [RM_FRO_010](RM_FRO_010_susan.md) "Susan"), same benign shape
  [BKHL_002](../tickets/BKHL_002_convergence-gate.md)'s own log already documented twice: this
  node doesn't name Donna in `depends_on` at all, so nothing routes around her gate. Reviewed, no
  change made.

- 2026-08-28: **Third item lands — [FRO_054](../tickets/FRO_054_mutation-data-security.md),
  project owner's own line item from Karen's build-planning session.** A future audit/hardening
  pass over Border's mutation-proposal surface (`BorderProposal`, `BordersCrudFacet.getProposal()`/
  `applyProposal()`, etc.) for data-security concerns — not yet triaged or scoped, same
  not-yet-picked-up shape as [FRO_048](../tickets/FRO_048_pathgrow-no-boss.md)/
  [FRO_051](../tickets/FRO_051_border-load-count-mismatch.md) already sitting here. Low priority,
  doesn't touch [RM_FRO_019](RM_FRO_019_karen.md) ("Karen")'s own build.

- 2026-08-29: **Fourth item lands — [FRO_055](../tickets/FRO_055_growth-particles-stale-tip.md),**
  found live during Karen's playtest. Also: this node folded into
  [RM_FRO_017](RM_FRO_017_donna.md) ("Donna")'s own `depends_on`, alongside
  [RM_FRO_022](RM_FRO_022_joyce.md) ("Joyce," the new Boss-command-surface node also opened this
  session) — both real Tier 1 scope surfaced during the same playtest, folded in together per
  Donna's own standing instruction to gather siblings as they're found rather than let them feed a
  future node and skip her.

- 2026-08-29: **Found while designing Joyce's command surface, not yet ticketed —** a boss
  admin/dev command surface needs to select bosses by level (see
  [Boss Command Surface § Selector scheme](../wiki/frontiermode/architecture/boss-commands.md#selector-scheme)),
  which surfaced a real cardinality question: a level/border can legitimately host more than one
  boss at once (a themed multi-mob encounter), not just the one-boss-per-border shape
  [RM_FRO_019](RM_FRO_019_karen.md) ("Karen")'s now-closed defeat handler assumed. Whether growth
  should wait for every boss at a level to be defeated, fire on the first, or something else is
  open — flagged on that wiki page's own "Open questions" section, not resolved here. Doesn't
  block Joyce's own command-surface design; would need a real ticket once someone picks up
  actually reconciling Karen's defeat/grow trigger against it.

- 2026-08-29: **Second item lands from the same design pass, not yet ticketed —** designing
  Joyce's selector chain (see
  [Boss Command Surface § What happens when a boss's border moves](../wiki/frontiermode/architecture/boss-commands.md#what-happens-when-a-bosss-border-moves))
  surfaced that a `Border` `transform` (repositioning center/radius) has no reconciliation against
  any boss tied to it — `BossRecord.position` is copy-once at creation, same as `layer`, and
  nothing today keeps it in sync with a border that later moves. Settled direction: bosses
  live-follow via delete+recreate, the same immutable-record remove-then-add-back shape
  `materialize()`/`markDefeated()` already use — not settled: the trigger call site (a paired call
  where `transform` is invoked, mirroring the existing boss-creation pairing pattern) and how a
  *materialized* boss's live entity gets handled (needs `mob respawn`'s kill-then-recreate shape,
  not a bare record swap). Doesn't block Joyce's own command-surface design; would need a real
  ticket once someone picks up building it, likely alongside
  [FRO_048](../tickets/FRO_048_pathgrow-no-boss.md)'s own border/boss-pairing territory.

- 2026-08-29: **Fifth and sixth items land — [FRO_058](../tickets/FRO_058_boss-mutation-validation-reconciliation.md)
  and [FRO_059](../tickets/FRO_059_border-proposal-center-bounds-id-display.md),** split out of
  [FRO_054](../tickets/FRO_054_mutation-data-security.md)'s own data-security QA pass (Lead Dev,
  read-only) once its findings landed. FRO_054 itself stays open as the umbrella audit record;
  these two carry the concrete, scoped follow-ups it turned up — Boss's missing validation
  boundary/one-directional reconciliation (FRO_058) and Border's unchecked `center` plus
  unvalidated `BorderProposal.id()`/`displayName()` (FRO_059). Both need Architect spec review
  before Lead Dev implements. Low priority, doesn't block Karen or Joyce.

- 2026-08-29: [FRO_058](../tickets/FRO_058_boss-mutation-validation-reconciliation.md) and
  [FRO_059](../tickets/FRO_059_border-proposal-center-bounds-id-display.md) closed (Architect
  spec review done, rulings on boss.md/border.md); build work tracked on
  [FRO_060](../tickets/FRO_060_boss-border-mutation-validation-build.md) (Lead Dev, code written,
  not yet build-verified -- no Gradle in the agent sandbox, per that ticket's own standing
  constraint).

- 2026-08-29: **Found while build-verifying [FRO_060](../tickets/FRO_060_boss-border-mutation-validation-build.md), not yet ticketed --**
  `BordersFixture`'s `all()` (via `BordersCrudFacet.all()`) returns a live view over its backing
  list (`Collections.unmodifiableList(borders)`), the exact same shape that caused a real
  `ConcurrentModificationException` bug on Boss's side (`/boss transform defeat @all`, fixed on
  FRO_060 by returning a real copy instead). No current Border operation reachable through a
  selector adds a new border as a side effect mid-loop, so this is a structural risk, not a live
  bug, for Border today -- flagged here rather than fixed, since nothing currently triggers it.
  Would need a real ticket if a future Border operation ever grows a border as a side effect of a
  selector-driven mutation the way `transform defeat` does for Boss.

- 2026-08-29: The Border-side `all()` live-view risk flagged just above is now resolved --
  `BordersCrudFacet.all()` copies at the facet boundary (`List.copyOf(fixture.all())`), matching
  `BordersPathFacet.all()`'s own existing precedent. See
  [FRO_060](../tickets/FRO_060_boss-border-mutation-validation-build.md)'s log. Not build-
  verified yet, same standing constraint as the rest of that ticket.

- 2026-08-29: [FRO_061](../tickets/FRO_061_boss-spawned-selector-delete-despawns-it.md) opened --
  a `@spawned` selector mode (maps onto [Boss Command Surface](../wiki/frontiermode/architecture/boss-commands.md)'s
  already-designed, unbuilt "Status filter" section, open question on exactly what "active" means)
  and `/boss delete` despawning its mob rather than orphaning it (resolves the entity-orphaning
  gap [boss.md](../wiki/frontiermode/architecture/boss.md) already flagged, project owner's
  ruling: despawn only, no defeat cascade). Not yet triaged or scoped.

- 2026-08-29: [FRO_062](../tickets/FRO_062_boss-reconciliation-warning-spam-edge-tr.md) opened
  and fixed same-session, live -- a real console-spam bug (identical `[Boss] Reconciliation`
  warning firing every server tick for a persistent mismatch), found during FRO_060's own
  playtest pass but in an unrelated, pre-existing code path. Not build-verified yet.

- 2026-08-29: [FRO_063](../tickets/FRO_063_boss-can-a-path-layer-legitimately-be-bo.md) opened --
  not a scoped item, just a note holding a real design question for a future project owner +
  Architect (Douglas) conversation: can a path layer legitimately have no boss (FRO_062's
  reconciliation warning currently treats any boss-less layer as pure data corruption), and
  should there eventually be a real attach/detach operation for a boss on a path layer, distinct
  from outright deletion (FRO_061)? No ruling, no scope -- deliberately left open for that
  conversation.

- 2026-08-30: [FRO_064](../tickets/FRO_064_boss-defeat-cascade-grows-border-level-r.md) opened --
  live playtest report (defeating an off-path `/boss add`-ed boss caused a real level expansion)
  traced to source and confirmed real: `onLivingDeath`/`forceDefeat`'s grow cascade fires for any
  tracked boss's defeat with no check that it's the path tip's boss, and no gating on the n:1
  boss-per-layer cardinality already flagged above (2026-08-29 entry). This is that flagged item's
  real ticket. No fix applied -- parked for Architect ruling, same discipline as FRO_058/FRO_059.

- 2026-08-30: **Un-folded from [RM_FRO_017](RM_FRO_017_donna.md) ("Donna")'s `depends_on`,
  project owner's explicit call, same day it was folded in.** Checked specifically against
  [FRO_064](../tickets/FRO_064_boss-defeat-cascade-grows-border-level-r.md) before making this
  call, since it's the one item here with real teeth: the off-path scenario it describes is only
  reachable via `/boss add` (Joyce's own build, [FRO_057](../tickets/FRO_057_boss-control-commands-build.md)) —
  it could not occur before Joyce existed. [RM_FRO_019](RM_FRO_019_karen.md) ("Karen")'s own
  verified done bar was ordinary-combat, path-tip defeats only; FRO_064 doesn't contradict anything
  Karen was actually checked against, it's a real gap in how a *new* admin capability interacts with
  an *existing* trigger, not a regression on either one's own scope. Same read as the cardinality
  question that flagged it in the first place (2026-08-29 entry above) — real design debt, parked
  for Architect ruling, not a blocker on what's already built and playtest-verified. Nothing else
  here comes close (display-only bugs, a future audit, latent/unreachable validation gaps, two
  build-verification loose ends already closed out on FRO_060's own log). This node stays open in
  its own right, just not wired as a gate on anything right now. Refined convention going forward
  (also noted on [BHRM — Roadmap Conventions](../wiki/meta/bhrm.md#epoch-maintenance-nodes-containers)):
  a container earns a `depends_on` edge onto a convergence only when it holds a genuine blocker,
  not merely because it's real and open during that epoch.

- 2026-08-30: `bhrm convergence-bypass` re-checked post-unfold — still flags this node against
  [RM_FRO_017](RM_FRO_017_donna.md) ("Donna"), same shared-ancestor-closure shape as the
  2026-08-28 entry above, now with no direct edge to explain away at all (this node depends on
  RM_FRO_020 only). Same benign pattern [BKHL_002](../tickets/BKHL_002_convergence-gate.md)'s own
  log documents repeatedly. Reviewed, no change made.

## Required By

*(computed — nothing depends on this yet)*
