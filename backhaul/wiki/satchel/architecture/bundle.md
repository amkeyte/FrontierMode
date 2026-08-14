---
id: satchel/architecture/bundle
category: satchel/architecture
slug: bundle
title: Bundle
summary: Satchel's primary unit of state aggregation -- identity, lifecycle, and the
  scope/bundle/facet/stitch mental model.
keywords: null
status: verified
updated: '2026-08-11'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · satchel / architecture
<!-- bh-header:end -->

# Bundle

*Migrated from `Satchel/src/main/java/com/arryn/satchel/common/bundle/notes.md` as part of
[SAT_003](../../../tickets/SAT_003_md-migration.md). Content unchanged except: a leftover
editorial artifact (an assistant-style response fragment that had been pasted into the source
file and never cleaned up) was removed between the intro and "Bundle Identity" below.*

The `bundle` package defines Satchel's primary unit of state aggregation. A bundle is a
**named, keyed container of facets** that represents a coherent body of data attached to a
scope. Bundles provide identity, lifecycle boundaries, and a synchronization surface, but do not
prescribe how or when persistence, networking, or ownership mechanics occur.

Bundles are identified by a `BundleKey`, which provides stable, global identity independent of
any particular scope instance. This allows bundles to be referenced symbolically by mod code
while remaining unambiguous and deterministic within Satchel's internals. A scope may host
multiple bundles simultaneously, each representing an independent concern.

Bundles are intentionally **passive**. They own facets, sequence facet lifecycles, and track
whether their state has changed, but they do not directly perform persistence, networking, or
side checks. Those concerns are enabled through capability interfaces (stitches) and external
orchestration. This separation allows bundles to remain simple, testable, and reusable across
different environments.

The bundle package defines *what* a bundle is and *how* it is identified and composed — not
*where* it lives or *what systems act upon it*. Satchel trusts the surrounding environment to
interpret and support bundle capabilities correctly.

---

## Bundle Identity

Every bundle is identified by a `BundleKey`, which provides **stable, global identity**
independent of any particular scope instance. A bundle key represents *what the bundle is*, not
*where it lives* or *who owns it*. This allows bundles to be referenced symbolically by mod code
while remaining deterministic and unambiguous within Satchel's internals.

A `BundleKey` always represents a single logical bundle type. Identity is stable across loads,
persistence cycles, and synchronization boundaries. How a bundle is stored, transmitted, or
reconstructed is an implementation concern external to the bundle itself. Satchel relies on
bundle identity to remain consistent and does not attempt to repair or reinterpret mismatches.

Bundle identity exists to decouple **mod intent** from **system mechanics**.

---

## Mental Model

* A **scope** defines *where* state lives.
* A **bundle** defines *what body of state* exists within that scope.
* A **facet** defines *how that state is structured and behaves*.
* A **stitch** defines *what capabilities that state participates in*.

Scopes provide ownership and lifetime boundaries. Bundles group related state within a scope.
Facets hold the actual data and logic. Stitches declare optional capabilities—such as
persistence, side awareness, or synchronization—without dictating how those capabilities are
implemented.

None of these layers assume the others' mechanics. Each plays a single role, and Satchel's job
is to let them cooperate without collapsing into a monolith.

## Related pages

- [Fixture](fixture.md)
- [Networking](net.md)
- [Persistence](persistence.md)
- [Satchel mod summary](../satchel.md)
