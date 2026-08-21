---
id: satchel/architecture/fixture
category: satchel/architecture
slug: fixture
title: Fixture
summary: 'The fixture/facet package -- Satchel''s modder-facing unit of persistent
  state: lifecycle, field registration, save/load contract, isolation rules.'
keywords: null
status: verified
updated: '2026-08-11'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · satchel / architecture
<!-- bh-header:end -->

# Fixture

**Fixture and facet are two different things**, at different levels of the hierarchy — not
synonyms, and not a rename half-applied:

- **Fixture** is the persisted-state unit this page describes: it extends `SatchelFixture`
  (`common/fixture/SatchelFixture.java`), is keyed by `FixtureKey` (`common/identity/FixtureKey.java`),
  is attached to exactly one bundle, and owns the field-registration/save-load contract.
- **Facet** is a *sub-view onto a fixture* — a small accessor class that groups
  a related slice of a fixture's read/query API, not a persisted unit in its own right and not
  registered with a bundle. Satchel itself defines no `Facet`-named classes (facets are a pattern
  for fixture authors, not a Satchel-provided type). The clearest live example is FrontierMode's
  `BordersFixture` (`FrontierMode/.../border/common/fixture/BordersFixture.java`), a single
  `SatchelFixture` that exposes four facets as public fields — `PATH` (`BordersPathFacet`), `CRUD`
  (`BordersCrudFacet`), `RULES` (`BordersRulesFacet`), `INFO` (`BordersInfoFacet`) — each a small
  class constructed with a reference back to the owning fixture (see `BordersInfoFacet`, which
  just wraps read-only accessors onto `BordersFixture`). None of the four facet classes extend
  `SatchelFixture` or register their own persisted fields; the fixture is the one thing that does.

In short: **fixture** is Satchel's term for the atomic persisted-state unit. **Facet** is a
mod-author convention for a grouped-accessor slice of a fixture's API — demonstrated in
FrontierMode, not a Satchel-provided type.

## Fixture

The `fixture` package defines Satchel's **primary modder-facing abstraction**. Fixtures are the
smallest meaningful units of persistent state in Satchel, and most mod-authored code is expected
to work directly with them. A fixture represents a single, focused concern within a bundle: it
owns its data, defines how that data is serialized, and exposes behavior derived from that
state.

Fixtures live inside bundles and are always managed by them. A fixture has exactly one instance
per bundle and never exists independently. Fixtures are **explicitly composed**, not discovered,
and Satchel makes no assumptions about which fixtures exist beyond what the bundle creates.

### What Is a Fixture?

A fixture represents one logical subsystem of a bundle.

Examples include:

* Progression or unlock state
* World or player flags
* Cooldowns, counters, or timers
* Region, chunk, or area metadata

Each fixture:

* Owns all of its persisted fields
* Registers its persistent state explicitly
* Serializes and deserializes itself deterministically
* Receives lifecycle callbacks from its bundle
* Is agnostic to what kind of scope ultimately owns the bundle

Fixtures do not depend on whether the bundle belongs to a world, player, entity, or any future
owner type.

### Fixture Lifecycle

Fixture lifecycle is **entirely controlled by the parent bundle** and is strictly ordered and
deterministic:

```
constructor
→ setBundle(bundle)
→ onCreated(owner)
→ loadFromNBT(tag)
→ onLoaded(owner)
→ runtime
→ onRemoved(owner)
```

Lifecycle guarantees:

* `onCreated` runs before any persisted data is applied
* `onLoaded` runs after all registered fields are hydrated
* `onRemoved` is always called when a fixture is discarded
* Fixtures must not create or remove other fixtures during lifecycle callbacks

Fixtures should treat lifecycle methods as **state transitions**, not gameplay triggers.

### Persistent Field Registration

Fixtures declare all persistent state using explicit registration helpers.

Example:

```java
registerInt("ticksRemaining",
    () -> ticksRemaining,
    v -> ticksRemaining = v
);
```

Registration helpers:

* Bind field names to getter/setter logic
* Guarantee deterministic save/hydrate behavior
* Encapsulate all serialization concerns
* Allow fixtures to remain plain Java objects

All field registration must occur during construction, before any lifecycle callbacks are
invoked.

Supported field types include primitives, UUIDs, strings, collections via helpers, and custom
encoded objects.

### Save / Load Contract

Fixtures must obey a strict persistence contract:

* Save writes all registered fields
* Load reads only registered fields
* Missing fields default cleanly
* Unknown fields are ignored

Fixtures must never:

* Mutate runtime state during save
* Depend on field ordering
* Assume the presence of other fixtures

This guarantees safe round-tripping across reloads, upgrades, and mod changes.

### Dirty Propagation

Fixtures may request persistence or synchronization by marking their parent bundle dirty.

Fixtures should mark dirty when:

* Mutable state changes during runtime
* Registered values are invalidated
* Server-side logic updates internal state

Fixtures must not mark dirty:

* In the constructor
* During hydrate
* During `onLoaded`

Only runtime state changes should propagate dirtiness.

### Isolation Rules

Fixtures are intentionally isolated:

* No cross-bundle references
* No direct world or player access (only via the provided owner)
* No networking code
* No persistence infrastructure access
* No implicit ticking unless driven externally

This isolation keeps fixtures portable, predictable, and easy to reason about.

### Error Handling

Fixture failures must never compromise bundle integrity:

* Missing fixture classes are skipped
* Invalid or malformed data resets the fixture to defaults
* Exceptions during hydrate or save are logged and isolated

Bundles always recover deterministically.

### Invariants (Do Not Break)

* Field registration precedes lifecycle callbacks
* Save/hydrate must round-trip deterministically
* No gameplay effects during hydrate
* No state mutation during save
* Lifecycle order must remain stable

### Summary

Fixtures are the **workhorse abstraction** of Satchel.

They are small, explicit, isolated units of persistent state that mod authors interact with
directly. Bundles organize them, scopes own them, and stitches enable optional capabilities —
but fixtures are where mod-defined behavior actually lives.

They are intentionally boring, explicit, and strict — and that is their strength.

## Related pages

- [Bundle](bundle.md)
- [Satchel mod summary](../satchel.md)
