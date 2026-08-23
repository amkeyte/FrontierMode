---
id: RM_SAT_022
uid: RM_SAT
number: 22
kind: work
status: open
title: Make MobJig side-agnostic
owner: Arryn
depends_on:
- RM_SAT_021
created: '2026-08-22'
superseded_by: null
ticket: SAT_037
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_SAT
<!-- bh-header:end -->

## Make MobJig side-agnostic

**Project owner's ruling, 2026-08-22: `MobJig` should not be server-bound. The `ServerLevel`
coupling [RM_SAT_021](RM_SAT_021_frank.md) shipped with gets reversed.**

Frank built the poll against `ServerLevel.getEntity(UUID)` and typed the public interest API to
match, on the reasoning that UUID-keyed entity lookup is a server-only capability in vanilla
Minecraft/Forge. That reasoning is correct about the *vanilla API* and wrong as a constraint on
`MobJig` — which is why this is a new node rather than a defect on Frank: the code does what it was
designed to do, and the design is what changed.

## Why this is a design problem, not a type widening

`Level` has no `getEntity(UUID)`. `ServerLevel` does, backed by its own entity index; `ClientLevel`
tracks entities by network ID and has no equivalent UUID lookup. So widening the signatures from
`ServerLevel` to `Level` produces code that doesn't compile, and forcing it produces a poll that
silently never resolves anything client-side. **What's needed is a side-appropriate resolution
strategy behind one interface** — the same shape `Satchel` already uses elsewhere to keep consumers
side-agnostic while the two engines differ underneath.

## The four coupling points

Verified against source 2026-08-22:

1. **`MobInterestSupplier.interestedMobs()` returns `Map<ServerLevel, Set<UUID>>`** — the public
   API surface. This is the one that reaches consumers: `BossModule`
   ([RM_FRO_018](RM_FRO_018_shirley.md)) implements this interface, so its signature is load-bearing
   for FrontierMode's Tier 1.
2. **`MobJig.reconcile()` Phase 1** iterates `Map.Entry<ServerLevel, ...>` and calls
   `level.getEntity(uuid)` directly.
3. **`MobJig.reconcile()` Phase 2** — the second-chance re-resolution — guards on
   `if (!(mobScope.mob().level() instanceof ServerLevel serverLevel)) continue;`. See the latent
   bug below.
4. **`MobTrackingModule`** — `watch(ServerLevel, UUID)`, `unwatch(ServerLevel, UUID)`,
   `currentInterests()`, and its `Map<ServerLevel, Set<UUID>>` interest store. The verification-aid
   consumer, and the worked example any new consumer copies.

## Latent bug this already causes

`MobJigConfig` deliberately ships **no default `sideApplicability`** and forces each consumer to
state one — `JigConfigValidator` rejects a config that doesn't. So a consumer registering `CLIENT`
or `BOTH` is legal today and passes validation.

It then misbehaves silently. Phase 2's `instanceof ServerLevel` guard `continue`s for a client-side
`MobScope`, so that scope never lands in `resolvedThisCycle`, so `computeTeardowns` treats it as
unresolved and tears it down — **every cycle, roughly once per second, regardless of whether the mob
is still standing there.**

That is the identical failure Frank found and fixed for `getFor`-attached scopes
([SAT_035](../tickets/SAT_035_mobjig-build.md), 2026-08-22), reintroduced on the client side and
still latent because nothing client-side consumes `MobJig` yet. Worth fixing on its own merits even
if the sidedness ruling had gone the other way: the config layer promises a choice the runtime
doesn't honor.

## Consumer impact

`BossModule` is the first real consumer and is written against nothing yet, so the window to change
the interface cheaply is now. [RM_FRO_018](RM_FRO_018_shirley.md)'s own `sideApplicability` choice
is `SERVER` (defeat detection is server-only), which stays correct either way — the question is
whether it implements `interestedMobs()` against `ServerLevel` or `Level`, and that answer should
be settled before it is written, not after.

## Done bar

Same standard as Frank's: compiling clean is necessary and not sufficient. A client-side consumer
(or a `BOTH`-applicability one) registers interest, gets a scope introduced, sees ticks, and gets a
clean teardown on unresolvability — verified against a real dedicated server plus a connected
client, not integrated/singleplayer, since the whole point is that the two sides behave the same.
Confirming the latent bug above is gone is part of it: a client-scoped mob must survive more than
one reconcile cycle.

## Related pages

- [Jig & Scope Runtime § MobJig](../wiki/satchel/architecture/runtime.md#mobjig) — currently states
  the server-only constraint as settled design; corrected as part of this node's work
- [MobScope.getFor() Contract](../wiki/satchel/spec/mobscope-getfor.md)
- [Universal Sidedness Facade](../wiki/satchel/architecture/facade-vision.md) — this node is a
  concrete instance of that vision's argument, worth reading alongside
  [RM_SAT_018](RM_SAT_018_edward.md)

- 2026-08-22: Node opened by PM off the project owner's ruling. Architect ticket:
  [SAT_037](../tickets/SAT_037_mobjig-sidedness.md).

- 2026-08-22: **Design settled — [SAT_037](../tickets/SAT_037_mobjig-sidedness.md) closed, full
  reasoning there.** Shape: a new `MobEntityLookup` interface (`Optional<Mob> resolve(Level level,
  UUID uuid)`, no sided type on it), chosen per side at foundation boot exactly the way
  `ScopeEngine` already is — `ASatchelFoundationBooter.mobEntityLookup()`, implemented by
  `MobEntityLookup_Server`/`MobEntityLookup_Client`. `MobJig.reconcile()`'s both phases and
  `MobInterestSupplier.interestedMobs()` (→ `Map<Level, Set<UUID>>`) resolve through it instead of
  touching `ServerLevel` directly. Fixes the latent client-side teardown bug above as a consequence
  of unifying the mechanism, not a separate patch. Ruled a neighbour of
  [RM_SAT_018](RM_SAT_018_edward.md) ("Edward"), not a piece of it — no `depends_on` edit. This
  node's own `depends_on`/scope are unchanged; it now has a settled design to build against.

## Required By

*(computed — nothing depends on this yet)*
