---
id: mods/satchel
category: mods
slug: satchel
title: Satchel
summary: Core data & utility mod for FrontierMode; also feeds an API dump used by
  DocletProject.
keywords: null
status: draft
updated: '2026-08-11'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../BACKHAUL.md) · [Wiki Index](../../WIKI_INDEX.md) · mods
<!-- bh-header:end -->

# Satchel

Core data & utility mod for FrontierMode.

## Identity

Pulled from `Satchel/gradle.properties` (the actual source of truth — `Satchel/mods.toml` is
templated off these properties, and `README.txt` is stock Forge MDK installation boilerplate
that doesn't describe the mod; see [SAT_001](../../tickets/SAT_001_readme-boilerplate.md)).

- **Mod ID**: `satchel`
- **Display name**: Satchel
- **Version**: 0.0.2
- **Group**: `com.arryn`
- **Author**: Arryn
- **License**: MIT
- **Minecraft**: `[1.20.1,1.21)` on Forge `[47,)`

## Dependencies

- Depends only on `minecraft` directly. **FrontierMode depends on Satchel** (mandatory, ordering
  `AFTER`) — see [FrontierMode](frontiermode.md).

## Tooling: DocletProject — removed

**Update ([SAT_002](../../tickets/SAT_002_strip-bare.md), 2026-08-11):** the `apiDumpSatchel`
task, its `gradle/apiDump.gradle` module, and DocletProject itself were all removed as part of
the bare-necessity sanitization pass. This section is kept for history — there is no longer a
concrete link between Satchel and DocletProject in this workspace. See
[backhaul/wiki/plans/strip-down.md](../plans/strip-down.md).

## Architecture

Deeper docs migrated from in-source `.md` files ([SAT_003](../../tickets/SAT_003_md-migration.md)):

- [Bundle](../satchel/architecture/bundle.md) — primary unit of state aggregation
- [Fixture](../satchel/architecture/fixture.md) — modder-facing unit of persistent state
- [Networking](../satchel/architecture/net.md) — transport-only networking layer
- [Persistence](../satchel/architecture/persistence.md) — server-side per-bundle persistence

All four are `verified`. `Fixture` and `Persistence` were initially migrated as `draft` with a
flagged discrepancy each (facet/fixture terminology drift; a package/location mismatch) —
resolved by the Architect role, see `RM_SAT_004` and `RM_SAT_007` in the roadmap.

## Design docs

Existing diagrams under `Satchel/design/`:

- `satchelArchitectureOverview.svg` / `.png`
- `satchelDataAvailabilty.svg` / `.png`
- `Satchel external event flow.png`

## Roadmap

Tracked under `RM_SAT`. Backfilled 2026-08-11 with reconstructed history
(`RM_SAT_001`–`RM_SAT_010`) covering the bundle/facet model, the jig/scope/foundation runtime,
the facet→fixture rename, networking, the (now-removed) DocletProject integration, the Satchel
2.0 persistence convergence, the jig-config rewrite, the strip-down pass, and the doc migration —
see [backhaul/wiki/plans/strip-down.md](../plans/strip-down.md)'s deferred "roadmap backfill"
note. No node is currently open — the graph reflects completed history, not forward planning;
real next steps are deferred to a future PM pass rather than invented here.

## Related pages

- [FrontierMode](frontiermode.md)
