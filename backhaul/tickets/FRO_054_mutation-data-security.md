---
id: FRO_054
uid: FRO
number: 54
client: FrontierMode
status: open
title: 'Data security pass: mutation proposals'
context: '[Susan_02] Polish/audit: BorderProposal, applyProposal, etc. for defensive
  hardening -- findings split into FRO_058/FRO_059, stays open as umbrella record.'
priority: low
opened: '2026-08-28'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

Project owner's line item, parked on [RM_FRO_021](../roadmap/RM_FRO_021_susan-02.md) ("Susan
epoch review/fix"): a future audit/hardening pass over Border's mutation-proposal surface --
`BorderProposal`, `BordersCrudFacet.getProposal()`/`applyProposal()`, and whatever else in that
family, for defensive/data-security concerns (e.g. input validation on proposal fields beyond the
existing radius/layer bounds check, what a caller could hand a proposal that shouldn't be
possible, whether anything reachable from a client-issued command can shape a proposal in a way
server-side validation doesn't catch).

**Triaged** -- Lead Dev's read-only QA pass (see Log) found concrete issues, split out to
[FRO_058](FRO_058_boss-mutation-validation-reconciliation.md) and
[FRO_059](FRO_059_border-proposal-center-bounds-id-display.md) (both closed). This ticket stays
open as the umbrella audit record rather than closing, since the QA pass's own scoping
recommendation below (a lighter Boss-side validation function) hasn't been picked up as its own
ticket yet. Low priority: general polish, not a known live vulnerability or a blocker for
[RM_FRO_019](../roadmap/RM_FRO_019_karen.md) ("Karen").

## Log

- 2026-08-28: Ticket opened, parked on [RM_FRO_021](../roadmap/RM_FRO_021_susan-02.md) per the
  project owner's direct instruction during Karen's build-planning session.
- 2026-08-29: Lead Dev read-only QA pass over `BorderProposal`/`BordersCrudFacet`/`BossFixture`/
  `BossAPI` and the `/border`, `/boss` command trees, requested by the project owner ahead of
  scoping. Findings for Architect's triage:
  - No client-to-server packet channel exists anywhere in the codebase (`SatchelNetwork`
    registers two S2C packets only, no C2S). The entire client-reachable mutation surface for
    both Border and Boss is Brigadier commands, and both `/border` and `/boss` require
    `hasPermission(2)` -- an untrusted player cannot reach either surface at all under normal
    server permission setup.
  - `BordersCrudFacet.failureReason()` validates `radius` (`BorderConstants` bounds) and
    `layerIndex` (non-negative) only. `center` has no bounds check at all, and `BorderProposal`'s
    public `id()`/`displayName()` setters are unvalidated -- a caller building a raw proposal
    directly (bypassing `BorderAPI.addBorder`/`transformBorder`, which never touch either field)
    could set `id()` to an existing border's UUID and silently replace it via
    `BordersFixture.accept()`'s remove-then-add, or set an unbounded `displayName`. No current
    call site does this, so it's latent, not live.
  - `BossFixture` has no validation layer at all (no counterpart to
    `BordersCrudFacet.failureReason()`): `create()`/`materialize()`/`markDefeated()`/`remove()`
    accept whatever they're given. `/boss add <pos> <layer>` lets an op create a record at any
    position with any non-negative layer, including one that matches no real `Border`.
    `DefaultBossRules` clamps `layer` defensively at spawn/stat-scaling time
    (`mobTypeForLayer`/`applyStatScaling` both `Math.max(layer, 0)`), so this can't crash
    materialization, but the raw stored value isn't clamped, and
    `BossModule.reconcilePathAgainstBossRecords()` only checks one direction (a path layer
    missing a boss record) -- a boss record whose layer matches no real border's layer is never
    flagged.
  - Recommendation for scoping: a full `BorderProposal`-style proposal-object system for Boss
    looks like more ceremony than Boss's current two-argument `create()` needs -- Border's
    proposal exists to support rules-driven defaulting, display-name dedup, and partial-update
    `transformBorder()` semantics, none of which Boss has yet. A lighter validation function at
    `BossFixture`'s mutation boundary (bounds-check `layer`, sanity-check `position`) would close
    the concrete gap above without the added class. Final call on scope is the Architect's per
    this ticket's own text.

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
