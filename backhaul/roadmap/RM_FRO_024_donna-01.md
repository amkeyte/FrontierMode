---
id: RM_FRO_024
uid: RM_FRO
number: 24
kind: work
status: open
title: Donna epoch maintenance 1
owner: Arryn
depends_on:
- RM_FRO_017
created: '2026-08-30'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_FRO
<!-- bh-header:end -->

## Donna epoch maintenance 1

**Maintenance container, not a feature deliverable.** Gathers unplanned rework/health work found
during the "Donna epoch" — the span opened by [RM_FRO_017](RM_FRO_017_donna.md) ("Donna") reaching
— that doesn't belong on a persona-named node because it isn't building toward a design goal, it's
paying down something a ruling or a build turned up along the way. See
[BHRM — Roadmap Conventions § Epoch maintenance nodes](../wiki/meta/bhrm.md#epoch-maintenance-nodes-containers)
for the full convention. No `ticket:` field on purpose — a container can gather more than one
ticket over its life; what actually landed here is tracked in this log, not a single frontmatter
pointer. Empty at open, same as [RM_FRO_020](RM_FRO_020_susan-01.md) ("Susan epoch maintenance 1")
was — opened alongside the epoch's start rather than waited on until something obviously needs it.

- 2026-08-30: A third item flagged for review, surfaced during the `BorderCurve`
  ([border-curve.md](../wiki/frontiermode/architecture/border-curve.md)) design pass -- not
  actioned, just parked here: Boss's existing reference-by-ID relationship to Border has no
  documented explicit-delete guarantee, so a Border can be deleted out from under a Boss and leave
  it pointing at nothing. `BorderCurveFixture` was designed with an explicit delete-cascade
  instead, and the project owner isn't excited that Boss's own pattern allows the weaker,
  orphan-permitting case. Worth a review of whether Boss's pattern should tighten to match -- not a
  ruling.

- 2026-08-30: Two items flagged for review, surfaced during Kathleen's
  ([RM_FRO_023](RM_FRO_023_kathleen.md)) discovery-systems scoping pass -- neither actioned, just
  parked here so they aren't lost:
  - [Boss](../wiki/frontiermode/architecture/boss.md)'s/[Border](../wiki/frontiermode/architecture/border.md)'s
    "one bundle per concern" discipline (the reasoning `BossFixture` was kept out of
    `BordersBundle`, and `BossMobFixture` kept in its own bundle again rather than folded into
    `BossFixture`'s) is believed obsolete by the project owner, who recalls it already being
    removed elsewhere. Needs a review pass across wherever it's still cited as a live rule before
    anyone relies on it again.
  - Project owner is separately musing on merging `BossFixture` and `BossMobFixture` into a single
    bundle instead of two -- not a ruling, just recorded here so it isn't lost.

- 2026-08-30: Node opened, alongside [RM_FRO_025](RM_FRO_025_donna-02.md) ("Donna epoch
  review/fix") and Donna's own `reached` flip — see [RM_FRO_017](RM_FRO_017_donna.md)'s own
  2026-08-30 log entry. Nothing held yet.

- 2026-08-31: **Correction to the 2026-08-30 entry above, from the project owner directly, not
  just "believed":** there never was an actual one-fixture-per-bundle discipline to become
  obsolete. `BossFixture` and `BordersFixture` each starting out alone in their own bundles was
  coincidental, not a by-design constraint — nothing was ever removed or retired, because nothing
  was ever ruled in the first place. Wherever this cluster's wiki pages cited it as a live
  precedent under review has been corrected (see
  [Boss Discovery Systems § Navigation lives in Border](../wiki/frontiermode/architecture/discovery-systems.md#navigation-lives-in-border)).
  The separate, still-genuinely-open item from that same entry stands unchanged: whether
  `BossFixture` and `BossMobFixture` are one concern or two, i.e. whether they should share a
  bundle — not resolved, not blocked on anything above.

- 2026-08-31: A fourth item flagged for review, surfaced during the Special Compass /
  `BorderPregenFixture` design pass on
  [Border Pregeneration](../wiki/frontiermode/architecture/border-pregeneration.md) -- **the wiki
  does not adequately document Satchel's own per-fixture lifecycle surface.** `fixture.md` covers
  `onCreated`/`onLoaded` but never mentions the per-fixture-instance `isReady()` or `onJigTick()`
  hooks that design leans on directly, and `runtime.md`'s own documented `isReady()` is a
  different, foundation-level check (`Satchel.isReady()`/`LogicalFoundation.isReady()`), not the
  per-fixture one — a same-name collision a reader could easily miss. Out of scope for any
  FrontierMode page to fix -- now tracked properly as
  [RM_SAT_024](RM_SAT_024_raymond-01.md) ("Raymond epoch maintenance 1") on Satchel's own
  roadmap, since it's Satchel's documentation debt, not FrontierMode's.

- 2026-09-07: A fifth item flagged for review, project owner's own musing while closing out [FRO_094](../tickets/FRO_094_boss-client-secrecy-anchor-build.md) (boss client-secrecy anchor build) -- not actioned, just parked here so it isn't lost: a broader **boss-location security** pass, sometime later. Not scoped yet -- FRO_094 itself already keeps the real `BossRecord`/`bossEntityId()` off the client for the growth-trigger ring specifically, but the project owner is thinking about whether that same discipline needs auditing more widely (other client-visible surfaces, commands, or packets that might carry a real boss position). No ruling, no ticket -- revisit if/when the project owner picks it back up.

- 2026-09-07: A sixth item, project owner's direct call while ruling Guardian Mobs' visible-marker
  question -- not actioned, just parked here so it isn't lost: guardian glow (the new `guardian`
  scoreboard team on [Effects](../wiki/frontiermode/architecture/effects.md#team-assignment-persistent-visual-state))
  is currently always-on for every player, built that way deliberately for now. At some point this
  should sit behind an admin/debug command instead of being a permanent player-facing visual --
  not scoped, not ticketed, revisit when it's actually picked up.

## Required By

<!-- required-by:start -->
- [**RM_FRO_025**](RM_FRO_025_donna-02.md) — Donna epoch review/fix
<!-- required-by:end -->
