---
id: RM_SAT_011
uid: RM_SAT
number: 11
kind: convergence
status: reached
title: Foundation runtime verified end-to-end
owner: Arryn
depends_on:
- RM_SAT_010
created: '2026-08-14'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_SAT
<!-- bh-header:end -->

## Foundation runtime verified end-to-end

- 2026-08-14: Node opened, backfilled as **reached** history (convergence) — same pattern as
  [RM_SAT_007](RM_SAT_007_charles.md).

Marks the point where the jig/scope/foundation runtime stopped being "compiles clean, passes
`JigConfigValidator`" and became "actually runs correctly," confirmed by a real
runtime-verification pass rather than static reading. Ten previously-invisible bugs surfaced and
got fixed in this one pass, eight of them Satchel-side, all downstream of code that had already
built clean:
[SAT_022](../tickets/SAT_022_tracker-bundlefactory-missing.md) (schema/`BundleFactories` duality —
missing factory registration), [SAT_023](../tickets/SAT_023_scopeengine-shared-config.md) (shared
`ScopeEngine` singleton read the wrong jig's config),
[SAT_024](../tickets/SAT_024_client-engine-wrong-exception.md) (client engine's missing-bundle
fallback never engaged), [SAT_025](../tickets/SAT_025_client-tick-missing-active-guard.md)
(non-`ACTIVE` client bundles ticked anyway),
[SAT_026](../tickets/SAT_026_network-register-never-called.md) (network channel never registered —
client bundles never actually received hydration data),
[SAT_027](../tickets/SAT_027_server-hydrate-bypasses-lifecycle.md) (server hydrate path bypassed
the `isHydrated`/`onLoaded` contract), [SAT_029](../tickets/SAT_029_send-sidedness-classcast.md)
(unchecked `ServerLevel` cast in `send()`), and
[SAT_030](../tickets/SAT_030_client-refresh-single-shot-hydrate.md) (every parcel after the first
crashed instead of updating). Evidenced in the wiki by
[Jig & Scope Runtime](../wiki/satchel/architecture/runtime.md) and
[Networking](../wiki/satchel/architecture/net.md), both `verified`.

Cross-linked with [RM_FRO_008](RM_FRO_008_sharon.md) — that node marks the same pass's
FrontierMode-side half (Border proving the runtime end to end as its first real consumer).
Deliberately modeled as **two paired convergence nodes, one per graph**, not one node with a
cross-UID edge: `bhrm` graphs are independent per UID, and this pairing is a convention for right
now, not permanent structure — expect it to stop once Satchel has a second real consumer and the
two mods' milestones stop moving in lockstep.

See [SAT_031](../tickets/SAT_031_handoff-retrospective-recommendations.md) for the full
retrospective this pass closed with, including the recommendations that became
[RM_SAT_017](RM_SAT_017_paul.md) below.

## Required By

*(computed — nothing depends on this yet)*
