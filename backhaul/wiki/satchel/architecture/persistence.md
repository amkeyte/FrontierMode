---
id: satchel/architecture/persistence
category: satchel/architecture
slug: persistence
title: Persistence
summary: Server-side per-bundle persistence architecture (Satchel 2.0) -- BundleSavedData,
  identity rules, dirty propagation.
keywords: null
status: verified
updated: '2026-08-13'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · satchel / architecture
<!-- bh-header:end -->

# Persistence

`com.arryn.satchel.common.persistence` is where the persistence/hydration plumbing sits —
`BundleSavedData` included. There is no `server/persistence` package. `common.persistence` is where
Satchel 2.0's persistence/hydration classes live (`BundleSavedData`, `FixtureHydrator`,
`FixtureHydrationSource`, `ParcelEgressSink`, `ParcelHydrationSource`, `SavedDataEgressSink`,
`SavedDataHydrationSource`) — the name now matches this page's own title, rather than the
placeholder it replaced.

This package defines all **server-side persistent storage** for Satchel bundles. In Satchel
2.0, persistence moves from "one SavedData per owner type" (world/player) to **one SavedData
file per bundle**, each identified by its `BundleKey`.

Persistence ensures:

* Stable bundle identity across server restarts
* Deterministic fixture save/hydrate
* Dirty-state propagation to Minecraft's save system
* Reconstruction of bundles *before* they become active

No scanning, no auto-discovery, and no side effects during hydrate. Persistence is fully
explicit and backend-driven.

## Architectural Principles

### 1. Identity is per-bundle

Each `BundleKey` corresponds to exactly **one SavedData record**.

SavedData stores:

* `key.uuid` — **canonical, authoritative identity**
* `key.name` — mandatory symbolic identifier
* `fixtures` — full bundle fixture state

The UUID and name are **deterministically matched** by the `BundleKey` construction rules.

On hydrate:

1. Read UUID + name from SavedData
2. Reconstruct the `BundleKey`
3. Validate name ↔ UUID consistency
4. Attach the key to the bundle
5. Hydrate fixtures via the common-layer protocol

Persistence never derives, guesses, or repairs identity. Any mismatch is treated as corruption.

This fully decouples persistence from owner type and supports **multiple bundles per world or
player**.

### 2. Persistence hydrates, but does not drive lifecycle

Persistence restores **state**, not behavior.

The effective lifecycle for a 2.0 bundle is:

```
construct bundle
→ attach owner
→ attach key
→ SavedData hydration
→ loadAll(fixtures)
→ backend-controlled lifecycle
```

Persistence:

* Restores fixture data
* Restores bundle identity
* Performs **no gameplay actions**
* Invokes **no lifecycle callbacks**

Persistence must never call:

* `onCreated`
* `onLoaded`
* `onRemoved`

Lifecycle sequencing is owned entirely by backend systems (e.g. `WorldBundles`).

This boundary held up correctly even through
[SAT_027](../../../tickets/SAT_027_server-hydrate-bypasses-lifecycle.md): `SatchelBundle.
hydrateFrom(FixtureHydrationSource)` (persistence's actual entry point into a bundle) only does
the `CREATED → HYDRATED` transition and marks `isHydrated()` true — it still never calls
`onLoaded()` itself, matching the rule above. The bug SAT_027 fixed was one level up:
`ScopeEngine_Server` (the backend, correctly the one place `onLoaded()` *is* called from) was
calling `FixtureHydrator` directly instead of going through `hydrateFrom`/`hydrateAll` at all, so
`isHydrated()` never flipped true and the backend's own `onLoaded()` call never fired — a backend
wiring bug, not a persistence-layer violation of this rule.

### 3. Dirty propagation is one-way

```
Fixture → DataBundle → BundleSavedData → Minecraft
```

Rules:

* Fixtures call `markDirty()`
* Bundles mark themselves dirty
* Backend layers detect dirty bundles
* `BundleSavedData` marks itself dirty so Minecraft persists it

Nothing flows *into* the bundle from persistence. Persistence reflects runtime state; it never
mutates it.

### 4. Saving must remain deterministic

Persistence must obey strict determinism rules:

* Fixture ordering must not matter
* Missing fixture classes are ignored
* Invalid fixture data is logged and skipped
* Saving must never mutate runtime state
* Loading must never create fixtures implicitly
* All fixture creation must occur via bundle accessors (`getOrCreateFixture`)

This guarantees forward and backward compatibility across mod updates.

## Core Component

### `BundleSavedData` (Satchel 2.0)

The universal, per-bundle `SavedData` implementation.

Responsibilities:

* Persist bundle identity (`BundleKey`)
* Write fixture state via `bundle.saveAll()`
* Hydrate fixture state during hydrate
* Track dirty state for Minecraft persistence
* Perform **no gameplay or lifecycle actions**

All previous SavedData variants (`PlayerBundleSavedData`, `ServerWorldBundleSavedData`) are
deprecated and replaced by this single mechanism.

## Error Handling

| Scenario | Behavior |
|---|---|
| Missing fixture class | Log + skip |
| Invalid fixture NBT | Log + reset |
| UUID / name mismatch | **Fail fast** |
| UUID collision | **Fatal error** |
| Load exception | Log + continue |

Persistence must never crash the server, but identity violations are treated as hard errors.

## Save / Load Contract

**Saving:**

```
tag.putUUID("key.uuid")
tag.putString("key.name")
tag.put("fixtures", bundle.saveAll())
```

**Loading:**

```
UUID id = tag.getUUID("key.uuid")
String name = tag.getString("key.name")

BundleKey key = new BundleKey(id, name, bundleType)
bundle.setKey(key)

bundle.loadAll(tag.getCompound("fixtures"))
```

Persistence never invokes lifecycle hooks.

## Future-Proof Goals

The per-bundle persistence model is fully generic and supports:

* World bundles
* Player bundles
* Entity bundles
* Block bundles
* Virtual or system bundles

Any owner capable of supplying a `BundleOwner` and a `BundleKey` can persist bundles without
architectural changes.

## Do Not Break

* UUID is the canonical source of identity
* Name ↔ UUID mismatches must fail fast
* Persistence must not create bundles
* Persistence must not invoke lifecycle hooks
* Save/hydrate must round-trip perfectly
* Persistence must remain backend-driven

## Summary

`com.arryn.satchel.common.persistence` (renamed from `common.newstuff`, see
[SAT_004](../../../tickets/SAT_004_newstuff-rename.md)) defines the durable storage layer for
Satchel bundles.

In Satchel 2.0:

* Persistence is **per-bundle**
* Identity is **canonical and validated**
* Lifecycle is **backend-owned**
* Persistence is **state-only and side-effect free**

This guarantees stable, deterministic bundle state across world loads, server restarts, and mod
evolution.

## Related pages

- [Bundle](bundle.md)
- [Fixture](fixture.md)
- [Satchel mod summary](../satchel.md)
