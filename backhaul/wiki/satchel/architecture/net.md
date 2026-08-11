---
id: satchel/architecture/net
category: satchel/architecture
slug: net
title: Networking
summary: Satchel's transport-only networking layer -- packet model, guarantees, forbidden
  behavior.
keywords: null
status: verified
updated: '2026-08-11'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · satchel / architecture
<!-- bh-header:end -->

# Networking

*Migrated from `Satchel/src/main/java/com/arryn/satchel/common/net/notes.md` as part of
[SAT_003](../../../tickets/SAT_003_md-migration.md). Content unchanged — no drift or artifacts
found when reviewed.*

The `net` package defines Satchel's **transport-only networking layer**. It exists solely to
move serialized bundle data and explicit client requests between sides. Networking does not
define state, policy, or gameplay behavior.

All packet handlers are explicitly registered. Packet decode is side-effect free, input is
validated, and server-side execution is authoritative. Mods may use Satchel bundles without
using the network layer at all.

### Packet Model

**Server → Client**

* Bundle state is synchronized using a unified parcel format.
* Parcels contain bundle identity and serialized facet data.
* Client application is limited to `bundle.loadAll(data)`.

**Client → Server**

* Client requests are wrapped, validated, and routed through registered handlers.
* No server state is mutated during packet decode.

Transport variants (world-, player-, or scope-specific) are conveniences only and do not alter
the bundle model.

### Guarantees

The network layer guarantees:

* Explicit registration of all client → server traffic
* Server-authoritative sync only
* No gameplay logic during encode or decode
* No mutation of server state on the client
* Isolation of packet handler failures
* Tolerance of unknown or missing bundles on the client

### Forbidden Behavior

The following are explicitly disallowed:

* Reflection-based packet discovery
* Gameplay logic during packet handling
* Bi-directional or client-authored state sync
* Persistence during network handling
* Dependence on server-only classes
* Sending live objects instead of serialized data

Violations indicate architectural leaks.

### Error Handling

Packet handling must be defensive: malformed data is dropped, failures are logged, and execution
is isolated. Network errors must never crash or corrupt game state.

### Summary

The network layer moves bytes — nothing more.

Bundles define state, facets define structure, and other systems decide *when* sync occurs. By
keeping networking explicit, minimal, and side-safe, Satchel avoids hidden coupling and
cross-thread bugs.

## Related pages

- [Bundle](bundle.md)
- [Fixture](fixture.md)
- [Satchel mod summary](../../mods/satchel.md)
