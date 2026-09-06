---
id: RM_SAT_005
uid: RM_SAT
number: 5
kind: work
status: resolved
title: Build the networking layer
owner: Arryn
depends_on:
- RM_SAT_004
created: '2026-08-11'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_SAT
<!-- bh-header:end -->

## Build the networking layer

- 2026-08-11: Node opened, backfilled as resolved history.

Evidenced by `common/net/*` (`SatchelNetwork`, `S2cBundleParcel`, `ParcelInbox`) and the verified
[Networking](../wiki/satchel/architecture/net.md) wiki page. Sequenced after fixtures (RM_SAT_004)
rather than after the bundle model
alone (RM_SAT_002), since the packet model syncs serialized *fixture* data
(`bundle.loadAll(data)`), not raw bundle state — the networking layer has nothing concrete to move
until fixtures exist as the unit of serialization.

## Required By

<!-- required-by:start -->
- [**RM_SAT_007**](RM_SAT_007_charles.md) — Satchel 2.0 persistence model
<!-- required-by:end -->
