---
id: satchel/architecture/fixture
category: satchel/architecture
slug: fixture
title: Fixture
summary: 'The fixture/facet package -- Satchel''s modder-facing unit of persistent
  state: lifecycle, field registration, save/load contract, isolation rules.'
keywords: null
status: verified
updated: '2026-08-31'
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

### Per-Fixture Lifecycle API Reference

**Note:** These per-fixture-instance lifecycle overrides are distinct from foundation-level readiness
checks documented in [Jig & Scope Runtime § Readiness](runtime.md#readiness-bound-to-a-side-vs-ready-to-use).
A fixture's `isReady()` method is a separate contract from `LogicalFoundation.isReady()` and
`Satchel.isReady()` — this section documents the fixture-level hooks only.

#### `onCreated(SatchelBundle owner)`

**Signature:** `protected void onCreated(SatchelBundle owner)`

**When it fires:** After bundle construction, before `loadFromNBT()` and before any persisted data is
hydrated. Fires once per fixture instance per bundle lifecycle.

**State guarantees:**
- The fixture is already attached to its bundle via `setBundle()`
- All field registrations from the constructor are complete
- No persisted data has been loaded yet — all fields hold their constructor-initialized values
- The bundle's parent scope exists but is not yet in a fully-ready state

**What it's for:**
- Initialize transient runtime state that depends on bundle setup
- Prepare caches or indices that will be populated during `onLoaded()`
- Set up initial state for fixtures that need to coordinate with other fixtures in the same bundle
- **Not** for gameplay effects, world mutations, or anything that depends on persisted data being
  present

**Example:** A fixture managing cooldowns might initialize its cooldown timer values to safe defaults
in `onCreated()`, then restore persisted values in `onLoaded()`.

**Important:** Do not read or rely on persisted field values in this method — they have not been
loaded yet. Use `onLoaded()` for logic that depends on hydrated state.

---

#### `onLoaded(SatchelBundle owner)`

**Signature:** `protected void onLoaded(SatchelBundle owner)`

**When it fires:** After `loadFromNBT()` completes and all registered persistent fields are hydrated.
Fires once per fixture instance per bundle lifecycle.

**State guarantees:**
- The fixture is already attached to its bundle
- All registered persistent fields have been deserialized from NBT and hold their stored values
- All other fixtures in the same bundle have also completed their `onLoaded()` callbacks
- The bundle is entering its runtime phase and may begin handling events/ticks

**What it's for:**
- Post-load validation and consistency checks (e.g., verifying cross-fixture invariants)
- Rebuilding transient indices or caches from the now-hydrated persisted data
- Coordinating post-load state with other fixtures in the bundle
- Emitting events or signals that downstream consumers might listen for
- **Not** for immediate gameplay mutations — save those for tick or event handlers

**Example:** A fixture managing a collection of tracked entities might rebuild its lookup indices
in `onLoaded()` after the stored entity set is deserialized.

**Important:** This is your primary opportunity to act on persisted state. Use it for any logic that
requires hydrated fields to be present and consistent.

---

#### `onRemoved(SatchelBundle owner)`

**Signature:** `protected void onRemoved(SatchelBundle owner)`

**When it fires:** When a fixture is being discarded — typically when its parent bundle is being
unloaded or the scope it belongs to is being removed. Fires exactly once per fixture instance per
bundle lifecycle.

**State guarantees:**
- The fixture is still attached to its bundle
- All other fixtures in the same bundle have not yet been removed (or have been, but in a
  deterministic order driven by bundle teardown)
- The bundle is transitioning out of its runtime phase
- Persisted state is stable and available if needed

**What it's for:**
- Cleanup of external resources (file handles, network connections, subscriptions)
- Clearing or resetting transient state before the fixture is discarded
- Removing event listeners or unregistering callbacks
- **Not** for saving data — persisted state is handled by the bundle's own save cycle, not by
  individual fixture cleanup

**Example:** A fixture that registered listeners on a global event bus should unregister them in
`onRemoved()` to prevent stale listeners from persisting.

**Important:** Do not attempt to mutate bundle state or create new fixtures during this callback.
Use it to clean up only the fixture's own resources.

---

#### `onJigTick(SatchelBundle owner)`

**Signature:** `protected void onJigTick(SatchelBundle owner)`

**When it fires:** Once per tick, every server/client tick (depending on the jig configuration),
after the jig's scope has been loaded and is in the `LOADED` phase. Does not fire during the
`NEW` phase (before the scope converges to ready).

**State guarantees:**
- The fixture is already loaded (all persisted fields are hydrated)
- The bundle's scope is in `LOADED` phase and actively participating in ticks
- Other fixtures in the same bundle are also ticking (in a deterministic order)
- Persisted state is consistent and can be safely read

**What it's for:**
- Periodic updates: decrementing counters, incrementing timers, aging stale entries
- Checking conditions that drive gameplay effects (e.g., "is this timer expired?")
- Broadcasting state changes via dirty-marking (`bundle.markDirty()`) for persistence/sync
- Iterating over collections and applying time-dependent logic
- **Not** for initial state setup — use `onLoaded()` for that

**Example:** A cooldown fixture might decrement active cooldown timers in `onJigTick()` and fire a
game event when a cooldown reaches zero.

**Important:** This method fires every tick. Keep it fast. If you need to check a condition less
frequently, implement your own throttling (e.g., check a counter and only act every N ticks).

---

#### `boolean isReady()`

**Signature:** `protected boolean isReady()`

**When it's called:** Repeatedly, at the discretion of the bundle or consuming code. This is not a
lifecycle callback fired at a deterministic time — it's a *query method* that external code calls
to ask if the fixture considers itself ready to use.

**What it returns:**
- `true` if the fixture is in a state where downstream consumers can safely read its data and
  expect consistent, validated results
- `false` if the fixture is in a transitional state (e.g., waiting for external data, performing
  asynchronous validation) where consumers should defer their queries

**State guarantees when returning `true`:**
- The fixture has been loaded (`onLoaded()` has fired)
- Persisted data is hydrated and validated
- Any dependent state has been initialized
- Downstream code can safely depend on the fixture's current state

**What it's for:**
- Allowing fixtures to signal "not ready yet" to consumers without raising an exception
- Deferring dependent logic (e.g., "I can't tell you the result yet, check back later")
- Coordinating readiness across multiple fixtures (fixture A might return `false` until fixture B
  returns `true`)
- Throttling or rate-limiting dependent logic

**Important distinction:** This is *not* the same as `LogicalFoundation.isReady()` or
`Satchel.isReady()`, which track whether the foundation and its scopes are initialized at the
JVM/game level. A fixture-level `isReady()` is a per-fixture-instance readiness check, independent
of foundation-level readiness.

**Example:** A fixture managing a border's pregeneration might return `false` from `isReady()` until
the disk generation is complete, allowing Boss code to poll this query and defer placement until
generation finishes (see [Border Pregeneration](../../frontiermode/architecture/border-pregeneration.md)
for a real use case).

**Default implementation:** The base `SatchelFixture` provides a default `isReady()` that returns
`true`. Override only if your fixture has a meaningful "not ready yet" state.

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

### External Access Is Not Compiler-Enforced

A fixture's own package-private members are protected the ordinary Java way -- only same-package
code compiles against them. But the fixture *class itself*, and the path to reach one from
outside its owning module, generally aren't: `FixtureKey<T extends SatchelFixture>` requires `T`
accessible everywhere its key is built and consumed, which routinely reaches beyond the fixture's
own package (a module's top-level key registry, its bundle-wiring class, and its `JigConfig`
registration all typically need the concrete type by name) -- so a fixture class is very often
not, and often cannot be, a literal package-private type, whatever a mod author's original intent
was. And `SatchelBundle.get(FixtureKey<T>)` -- the generic retrieval mechanism every module's own
API class is built on -- is `public` and unguarded, and has to stay that way; there's no way to
restrict it to "only this module's own API class" without breaking the mechanism every module
depends on.

The practical consequence: any code holding both a bundle reference and a fixture's `FixtureKey`
can pull the raw fixture out directly, reaching whatever that fixture exposes as public (facet
fields, in the pattern this page describes above) without going through whatever
readiness/access gating the owning module's own API class wraps around it. Satchel provides no
compiler mechanism to close this off -- it's a known, accepted property of the fixture/bundle
model, not a bug to fix module-by-module. The mitigation is doc-comment discipline on the fixture
itself (state plainly what the real, sanctioned access path is, and that facet methods assume the
caller already went through it), not a runtime guard -- see FrontierMode's
[Border § Data model](../../frontiermode/architecture/border.md#data-model) for the worked example
and the ruling this reasoning backs.

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
