---
id: SAT_034
uid: SAT
number: 34
client: Satchel
status: done
title: 'MobJig: correct + document before build'
context: RM_SAT_021's design lives only in its node log, and runtime.md is factually
  wrong about PlayerJig.
priority: high
opened: '2026-08-21'
closed: '2026-08-21'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Architect ticket for [RM_SAT_021](../roadmap/RM_SAT_021_frank.md) ("Frank," `MobJig`/`MobScope`).
Frank is the next thing anyone should pick up — project owner's ordering call, recorded on
[FRO_030](FRO_030_frank-first.md): Frank lands before
[RM_FRO_018](../roadmap/RM_FRO_018_shirley.md) ("Shirley") and the rest of FrontierMode's Tier 1.

**This ticket is the design/documentation half, not the build.** Frank's node already carries a
decided design, but it lives entirely in that node's log — spread across three entries and two
mid-flight pivots. Nothing in `wiki/` mentions `MobJig` or `MobScope` at all. Handing that to Lead
Dev as-is means asking someone to reconstruct a design by reading a changelog, which is the exact
shape this project spent 2026-08-21 cleaning up out of the wiki.

## 1. `runtime.md` is wrong about `PlayerJig` — fix this first

[Jig & Scope Runtime](../wiki/satchel/architecture/runtime.md)'s jig-kinds section states that
`PlayerJig` is "**entirely commented out**, all seven files, package declaration included... Not a
partial stub... Scaffolding history, not current runtime," and that `LevelJig` is "the only jig kind
with a `JigConfig` subclass and the only one either repo's real consumers (Border,
`TrackingModule`) use."

**Both claims are false**, verified against source on 2026-08-21:

- `common/jig/player/` holds four live files — `PlayerJig`, `PlayerResolver`, `PlayerScope`,
  `PlayerScopeCoupler` — each with an uncommented `package com.arryn.satchel.common.jig.player;`
  declaration.
- `PlayerJigConfig.java` exists (`common/newconfig/newnew/`), so `LevelJig` is not the only kind
  with a config subclass.
- FrontierMode consumes it: `BorderModule` and `BorderAPI` both reference `PlayerScope`.
- [RM_SAT_020](../roadmap/RM_SAT_020_jerry.md) ("Jerry") is `resolved`, confirmed against a real
  dedicated-server login/dimension-change/logout cycle.

The section heading also says "The two jig kinds" while the page's own summary and intro both say
"three."

**Why this is item 1 and not a footnote:** Frank's own build instruction is "build fresh against
`PlayerJig`'s current shape for the scope/config plumbing." An Architect or Lead Dev who reads
`runtime.md` first is told that shape is commented-out scaffolding. The reference this work is meant
to copy from is documented as not existing.

Correcting it needs someone who has actually read `PlayerScopeCoupler` — the surrounding claims
about readiness gates and coupler behavior are stale by association and shouldn't be patched
blind. Flagged rather than fixed by PM for that reason.

## 2. Document `MobJig` as a real jig kind

Frank's design is settled; it just isn't anywhere a reader would find it. Consolidate from
[RM_SAT_021](../roadmap/RM_SAT_021_frank.md)'s log into `runtime.md`'s jig-kinds section, stated as
current fact rather than as a decision history:

- **`Mob`, not `LivingEntity`** — `LivingEntity` includes `Player`, which `PlayerJig` already owns;
  `Mob` excludes it at the type level rather than by convention.
- **No `MobResolver`** — `determineUUID` and the `getFor` factory live on `MobScope` itself. Frank's
  log has the reasoning for why the `LevelResolver`/`PlayerResolver` split wasn't worth mirroring.
- **Poll-driven ingress, not event-driven** — the architectural difference from the other kinds, and
  the part most likely to be re-litigated if it isn't written down. A boss's chunk is essentially
  never loaded at the moment its existence needs checking; that is the normal state, not an edge
  case. Presence is re-verified on a `foundationLifecycle().pulse()` reconciliation step calling
  `Level.getEntity(UUID)` against a per-consumer interest supplier, on roughly a 20-tick cadence.
- **`MobJigConfig` ships no default `sideApplicability`** — each consumer chooses. Shirley's own
  `SERVER` choice is hers, not inherited.

While in that section: it currently opens "Only `LevelJig` is exercised by anything real," which
item 1 already falsifies and a third kind falsifies further.

## 3. Decide whether `MobScope.getFor()` earns a spec page

[BHW — Wiki Conventions](../wiki/meta/bhw.md#spec-pages-a-stricter-sibling-of-architecture-pages)'s
test: does a consumer *outside* the implementing package depend on this surface not changing?

PM's read is yes — FrontierMode's `BossModule` calls `MobScope.getFor(mob)` directly as its
boss-tagging entry point, and [RM_FRO_019](../roadmap/RM_FRO_019_karen.md)'s defeat handler is
designed to call it synchronously inside a `LivingDeathEvent` listener to close a race. That is two
external callers depending on precise behavior — including the `Optional.empty()`-on-`isRemoved()`
contract and the guarantee that `getFor` at spawn time attaches immediately rather than waiting for
the next poll. `satchel/spec/` currently holds exactly one page, which is the intended rarity.

Architect's call, not PM's. Recorded here so it gets decided rather than defaulted.

## Explicitly out of scope

**No `src/` changes.** This is the Architect lane per [Architect](../roles/architect.md) — design
and structure, hand implementation to Lead Dev. There is precedent for the project owner directing
Architect to implement directly ([FRO_029](FRO_029_border-vocab-conformance.md) and
[RM_FRO_015](../roadmap/RM_FRO_015_margaret.md) both did), but that is the owner's call to make
explicitly, not a standing licence.

**Frank's own done bar is unchanged and stays on the node** — a synthetic consumer or
`MobTrackingModule` proving introduce/tick/remove, including a real chunk unload/reload. Note that
it needs the dedicated-server path; integrated/singleplayer won't exercise the thing under test.

## Log

- 2026-08-21: **All three items done.**
  1. `runtime.md`'s jig-kinds section corrected — `PlayerJig` is documented as live (four files,
     `PlayerJigConfig`, consumed by `BorderModule`/`BorderAPI`), the section heading and its
     `LevelJigConfig` "one concrete `JigConfig` subclass" claim (same false-exclusivity error,
     found while fixing the section) both corrected, and the section retitled from "The two jig
     kinds" to "The jig kinds," split into per-kind subsections for direct linking.
  2. `MobJig`/`MobScope` consolidated into `runtime.md`'s jig-kinds section as current design —
     `Mob` typing, no `MobResolver`, poll-driven presence, no default `sideApplicability` — pulled
     from RM_SAT_021's log rather than left there as the only record. While there: the stale
     `ScopeInfo.source()` question RM_SAT_021 had left open was resolved (Architect ruling: the
     poll's own teardown-then-reintroduce cycle already makes a stale source impossible by
     construction, no separate refresh mechanism needed) and recorded on that node directly.
  3. **Spec page created:** [MobScope.getFor() Contract](../wiki/satchel/spec/mobscope-getfor.md)
     — Architect's call was yes, matching PM's read: `BossModule`'s tagging entry point and
     RM_FRO_019's defeat handler are two external callers depending on this method's precise
     contract (fast-path timing, the `Optional.empty()` case). Marked `draft`, not `verified` —
     unlike `forge-integration.md`, this describes a contract for code that doesn't exist yet
     (RM_SAT_021 is still `open`); it moves to `verified` once Lead Dev's build confirms it against
     real source, matching how `boss.md`/`difficulty.md` stay `draft` for the same reason.

  Closing — all three items resolved, nothing carried forward from this ticket. Frank's own build
  and done bar are unchanged and remain on [RM_SAT_021](../roadmap/RM_SAT_021_frank.md).
- 2026-08-21: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
