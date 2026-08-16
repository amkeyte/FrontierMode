---
id: RM_SAT_002
uid: RM_SAT
number: 2
kind: work
status: resolved
title: Establish the bundle/facet state model
owner: Arryn
depends_on:
- RM_SAT_001
created: '2026-08-11'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_SAT
<!-- bh-header:end -->

## Establish the bundle/facet state model

- 2026-08-11: Node opened, backfilled as resolved history.

**Reconstructed, not literally dated** — placed here as the plausible first architectural layer:
the scope → bundle → facet mental model described in
[Bundle](../wiki/satchel/architecture/bundle.md) (scopes own bundles, bundles
group state, facets/fixtures hold it). The name "Satchel 1.0" is invented for sequencing purposes;
no version-1 artifact survives, but a leftover trace of this era's vocabulary is still live in
`SatchelFixture`'s field-registration code, which throws `"Duplicate facet field: " + name` —
"facet" language baked into an error string years after the concept was renamed to "fixture" (see
RM_SAT_004). This node represents `SatchelBundle`, `BundleKey`, and the identity/lifecycle
contract described in [Bundle](../wiki/satchel/architecture/bundle.md) existing before the
heavier jig/scope-coupling runtime
(RM_SAT_003) was layered on top.

## Required By

*(computed — nothing depends on this yet)*
