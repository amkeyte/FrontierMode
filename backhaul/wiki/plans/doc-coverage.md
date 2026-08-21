---
id: plans/doc-coverage
category: plans
slug: doc-coverage
title: Full Documentation Coverage Plan
summary: Plan to bring FrontierMode and Satchel's wiki up to full design/spec coverage
  of existing code before content work starts.
keywords: null
status: published
updated: '2026-08-13'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../BACKHAUL.md) · [Wiki Index](../../WIKI_INDEX.md) · plans
<!-- bh-header:end -->

# Full Documentation Coverage Plan

Goal: full design and spec documentation in the wiki for all existing code in both mods, before
work shifts to new content. This is maintenance/foundation work, not forward feature planning —
tracked here and via ordinary tickets, deliberately **not** as roadmap nodes (see "Why not the
roadmap" below).

Shaped after [Bare-Necessity Strip-Down Plan](strip-down.md), the project's existing precedent
for a wide-ranging, non-roadmap initiative: one plan page holding the goal, the inventory, and the
decision trail, with the actual work done as small individual tickets that link back here.

## Why not the roadmap

`bhrm` models dependency-graph-shaped forward work — features with `depends_on` edges, where
`frontier` computes what's actionable next. A documentation-coverage sweep isn't shaped like
that: it's closer to a checklist across existing surface area than a chain of prerequisite work.
Tracking it as tickets (small, closeable) plus this plan page (durable narrative) keeps the
roadmap's history focused on actual code/feature work, per your call.

## Scope

"Design and spec documentation" here means the `architecture/*` pages (how the code is actually
built — the technical reference a developer would read) for both mods. FrontierMode's
`design/*` pages (game-design vision, owned by the Game Designer role, no source access) are a
separate track — see "Design docs" below; they already substantially exist and aren't part of
this sweep's gap analysis.

## Coverage inventory (as of 2026-08-13)

### FrontierMode

| Area | Page | Status | Notes |
|---|---|---|---|
| Border (the whole `border/*` package — data model, runtime wiring, commands, client rendering) | [border.md](../frontiermode/architecture/border.md) | verified | Comprehensive — re-read in full during this survey. Accurately documents even the unfinished per-player eval gap (`RM_FRO_006`). No gap found. |
| Mod landing/identity | [frontiermode.md](../frontiermode/frontiermode.md) | verified | [FRO_013](../../tickets/FRO_013_frontiermode-draft.md) — identity/dependency claims checked against `mods.toml`/`build.gradle`, all held; one link-text typo fixed. |
| Border↔Frontier design reconciliation | frontier-reconciliation.md *(retired 2026-08-21, FRO_036)* | draft (deliberate) | [FRO_013](../../tickets/FRO_013_frontiermode-draft.md) — re-verified against the rewritten border.md, one stale claim corrected (the "commands vs. levers" gap). Stays `draft` on purpose: this page's job is to hold live, still-open design questions (permanent-region overlap rule, `BorderPlayerBundle`'s design mapping, geometry model, boss-warning feasibility) — not a documentation gap, a genuine open-question tracker. |
| Top-level entrypoint (`FrontierMode.java`, `Config.java`, `FrontierKeys.java`, `Rendering.java`) | — | n/a | Small registration/entrypoint files, already covered in context by border.md. Not treated as a gap. |

### Satchel

| Area | Page | Status | Notes |
|---|---|---|---|
| Bundle | [bundle.md](../satchel/architecture/bundle.md) | verified | No gap found. |
| Fixture | [fixture.md](../satchel/architecture/fixture.md) | verified | No gap found. |
| Networking | [net.md](../satchel/architecture/net.md) | verified | No gap found. |
| Persistence | [persistence.md](../satchel/architecture/persistence.md) | verified | [SAT_017](../../tickets/SAT_017_persistence-followup.md) — the page's false "already deleted" claim about a dead file is fixed, and the file (`server/persistence/ServerPersistenceContext.java`) is now actually deleted. |
| Jig/scope/foundation runtime (`LogicalFoundation`, dispatchers, `SatchelEventBus`, `JigConfig`/`JigConfigCompiler`) | [runtime.md](../satchel/architecture/runtime.md) | verified | [SAT_015](../../tickets/SAT_015_runtime-arch.md) — new page, written and checked directly against source. Covers the scope-broadened items too (jig kinds, bundle lifecycle). Found a real runtime bug in the process — see SAT_020 below, out of scope for this plan. |
| Concrete jig kinds — `LevelJig`, `ModelJig`, `PlayerJig` | [runtime.md](../satchel/architecture/runtime.md) | verified | Folded into SAT_015's page rather than a separate ticket. `LevelJig` is the only one with a real consumer; `ModelJig` is complete but unreachable (no `JigConfig` subclass registers it); `PlayerJig` is fully commented out. |
| Jig & Strap Registration (history) | [jig-registration-break.md](../satchel/architecture/jig-registration-break.md) | verified | [SAT_016](../../tickets/SAT_016_jig-break-stale.md) — retitled to "— History", `draft` → `verified`, cross-linked both directions with `runtime.md`. |
| Mod landing/identity | [satchel.md](../satchel/satchel.md) | verified | [SAT_019](../../tickets/SAT_019_satchel-status.md) — identity spot-checked against `gradle.properties`, Architecture list brought current (added `runtime.md`, fixed the break-page's now-stale draft note). |
| Dead code: `common/exp/*` | — | resolved | [SAT_018](../../tickets/SAT_018_exp-dead-code.md) — PM call: delete (zero references confirmed in either repo). Deleted, including the now-empty directory. |
| Dead code: `server/persistence/ServerPersistenceContext.java` | — | resolved | Deleted as part of [SAT_017](../../tickets/SAT_017_persistence-followup.md), alongside the persistence.md false-claim fix. |
| **New, out of scope for this plan:** `ScopeInfo.jigInfo()` unconditionally `null` | — | real bug | Found while writing `runtime.md`. NPEs `TrackingModule`'s handlers on the first `ScopeEvent` any level posts — reachable now, not latent. Ticketed [SAT_020](../../tickets/SAT_020_jiginfo-null.md), high priority, **left open** — this is Lead Dev implementation work, not a documentation gap, and out of this plan's scope. |

### Areas checked and found already covered (no ticket needed)

Confirmed during this survey rather than assumed — `common/stitch/*`, `common/bundle/builder/*`,
and `common/identity/*` (key types) all already have real coverage inside `bundle.md`/
`fixture.md`/`persistence.md`. Utility packages (`common/util/*`) are implementation detail, not
treated as needing dedicated architecture pages.

### Design docs (separate track, not this sweep's gap analysis)

FrontierMode's `design/*` category (overview, progression, boss-discovery, guardian-mobs,
nether-and-end, two parked pages) already has substantive content — seven pages, all `draft`.
Owned by the Game Designer role. Whether they need a verification pass is a separate call from
this plan, which is scoped to code-architecture coverage.

## Tickets (opened)

**Satchel:**
- [SAT_015](../../tickets/SAT_015_runtime-arch.md) — jig/scope/foundation runtime reference page — **done**
- [SAT_016](../../tickets/SAT_016_jig-break-stale.md) — fix stale title/status on jig-registration-break.md — **done**
- [SAT_017](../../tickets/SAT_017_persistence-followup.md) — fix persistence.md's false "already deleted" claim + actually delete the dead file — **done**
- [SAT_018](../../tickets/SAT_018_exp-dead-code.md) — common/exp/* — delete or document as scratch — **done** (deleted)
- [SAT_019](../../tickets/SAT_019_satchel-status.md) — re-verify satchel.md status — **done**
- [SAT_020](../../tickets/SAT_020_jiginfo-null.md) — `ScopeInfo.jigInfo()` always null, real NPE bug found while writing SAT_015 — **open, deliberately not closed by this plan** (Lead Dev work, not documentation)

**FrontierMode:**
- [FRO_013](../../tickets/FRO_013_frontiermode-draft.md) — frontiermode.md + frontier-reconciliation.md still draft — **done**

## Status: executed (2026-08-13)

All six original tickets closed. `frontiermode.md` and `satchel.md` are both `verified`; every
`architecture/*` page in both mods is `verified` except `frontier-reconciliation.md`, which stays
`draft` deliberately — its job is holding genuinely open design questions, not reporting a
completed state (see the inventory table above and the "no status either" carve-out in
`meta/bhw.md` for why that's not the same thing as a stale page).

One real finding fell out of scope on purpose: writing `runtime.md` surfaced an actual runtime
bug (`ScopeInfo.jigInfo()` always `null`, NPEs on the first tick). That's ticketed as
[SAT_020](../../tickets/SAT_020_jiginfo-null.md) and left **open** — fixing code isn't this plan's
job, only documenting it accurately was, and this plan does that.

FrontierMode's `design/*` pages (game-design vision) were explicitly out of this sweep's scope
from the start (see "Design docs" above) and remain untouched — a separate call for whoever picks
that up next, not a gap this plan left behind.
