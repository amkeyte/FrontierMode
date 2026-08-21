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

The `net` package defines Satchel's **transport-only networking layer**. It exists solely to
move serialized bundle data and explicit client requests between sides. Networking does not
define state, policy, or gameplay behavior.

All packet handlers are explicitly registered — via `SatchelNetwork.register()`, which must
actually be called for that to be true. It wasn't, from whenever this page's "explicit
registration" language was written until
[SAT_026](../../../tickets/SAT_026_network-register-never-called.md): `S2cBundleParcel`'s
encoder/decoder/handler were fully implemented but never added to `SatchelNetwork.CHANNEL`, so
every `SatchelNetwork.send()` call was sending a message type the channel didn't know about.
Client-side bundles have never received real hydration data as a result — the guarantee this
paragraph describes was the intended design the whole time, just not wired up. Fixed by calling
`SatchelNetwork.register()` from `SatchelMod`'s constructor. Packet decode is side-effect free,
input is validated, and server-side execution is authoritative. Mods may use Satchel bundles
without using the network layer at all.

A second, independent bug sat right next to the first and only surfaced once registration and
[FRO_018](../../../tickets/FRO_018_border-executionpulse-disabled.md)'s execution-pulse fix let
`SatchelNetwork.send()` actually run: it did `ServerLevel level = info.scopeAs();`, an unchecked
generic cast that compiled fine but threw `ClassCastException` at runtime — the scope object
behind a `LevelJig` is a `LevelScope`, never a `ServerLevel` directly. Fixed in
[SAT_029](../../../tickets/SAT_029_send-sidedness-classcast.md) by going through
`LevelScope.level()` first, then casting the real `Level` to `ServerLevel`. `send()` is currently
LevelScope-only by design (see its own doc comment); a future scope type would need its own
distribution logic, not a fix to this cast.

### Packet Model

**Server → Client**

* Bundle state is synchronized using a unified parcel format.
* Parcels contain bundle identity and serialized facet data.
* Client application goes through `SatchelBundle.hydrateAll(data)` for a bundle's first-ever
  parcel (pairs with the one-time `onLoaded()` transition) or `SatchelBundle.refreshFrom(source)`
  for every parcel after that (updates fixture data in place, no lifecycle transition). Until
  [SAT_030](../../../tickets/SAT_030_client-refresh-single-shot-hydrate.md), every parcel past the
  first for a given bundle called `hydrateAll` again, which throws once the bundle has already
  left `CREATED` — client bundles were permanently frozen at their first snapshot, silently
  dropping every real update after it.

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
- [Satchel mod summary](../satchel.md)
