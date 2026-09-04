---
id: satchel/spec/mobscope-getfor
category: satchel/spec
slug: mobscope-getfor
title: MobScope.getFor() Contract
summary: 'The static-factory boundary contract BossModule and Karen''s MobDied-triggered
  defeat handler depend on: fast-path semantics, the Optional.empty() removed-reference
  case, and what it guarantees about poll-cycle timing.'
keywords: null
status: verified
updated: '2026-09-03'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · satchel / spec
<!-- bh-header:end -->

# MobScope.getFor() Contract

`MobScope.getFor(Mob mob)` is the sanctioned entry point for tagging a live `Mob` into Satchel's
`MobJig` scope machinery outside of the poll's own reconciliation cycle. Two FrontierMode callers
depend on this contract directly: `BossModule`'s boss-tagging entry point, and Karen's defeat
handler, which calls it synchronously from within its `MobDied` handler ([Mob Lifecycle
Signals](../architecture/mob-lifecycle-signals.md) — `SatchelEventBus.post()` is itself synchronous,
so this still runs in the same call stack, same tick, as the `LivingDeathEvent` that triggered it;
just one layer removed from being that raw listener directly, since `ServerForgeIngress` owns the
raw registration now) to close a race against the entity's own teardown. Doesn't re-derive
`MobJig`'s poll-driven presence model — see [Jig & Scope Runtime](../architecture/runtime.md#mobjig)
for that; this page is the boundary contract for the one static factory method itself,
current-state, no history.

## Signature and call sequence

`static Optional<MobScope> getFor(Mob mob)` — no `MobResolver`; the factory and UUID derivation
both live on `MobScope` itself (see [Jig & Scope Runtime](../architecture/runtime.md#mobjig) for
why the resolver split `LevelJig`/`PlayerJig` use wasn't mirrored). In order:

1. Check `Satchel.isReady()` for the calling side. Cheap and side-agnostic — it resolves against
   whichever foundation is bound on the calling thread, so this one check guards both sides with
   no `Mob`-specific logic.
2. Reject an already-`isRemoved()` reference — returns `Optional.empty()`, not a throw. This is the
   "bad reference" case: a caller holding a `Mob` object that has already left the world.
3. Call `Satchel.require().introduceSource(mob)`.
4. Return `Optional.of(new MobScope(mob))`.

## What `Optional.empty()` means

`getFor` returns `Optional.empty()` in exactly two cases, both "not usable right now," neither an
error:

- The calling side's foundation isn't ready yet (`Satchel.isReady()` false) — the same
  "not recognized yet, not an error" contract `LevelResolver.resolveScope` and other pre-readiness
  call sites already use elsewhere in Satchel (see
  [Jig & Scope Runtime](../architecture/runtime.md#readiness-bound-to-a-side-vs-ready-to-use)).
- The `Mob` reference passed in is already `isRemoved()`.

Neither case throws. A caller that gets `Optional.empty()` back has nothing further to retry
synchronously — a mob that becomes resolvable later is the poll's job to pick up, not this call's.

## Fast path vs. the poll — what's guaranteed

`getFor` is a fast path onto the same scope machinery the poll's own reconciliation feeds, not a
substitute for it and not the primary ingress. It doesn't replace registering interest with a
`MobJigConfig` interest supplier, and it does nothing for a mob nobody currently holds a live
reference to.

- **Guarantee: immediate attachment.** Calling `getFor` at a moment a live `Mob` reference already
  exists — a boss freshly spawned this tick — attaches the scope immediately. It does not wait for
  the next foundation-pulse reconciliation cycle. This is the specific property the defeat handler
  depends on: calling `getFor` synchronously from within its `MobDied` handler closes a race that
  waiting on the next poll cycle would not — same guarantee as calling it directly inside a raw
  `LivingDeathEvent` listener, since the synchronous chain from that event to this call is unbroken
  either way.
- **Guarantee: idempotent under overlap with the poll.** Calling `getFor` for a `Mob` that's already
  scoped — whether by an earlier `getFor` call or by the poll's own reconciliation reaching it
  first — is safe. `introduceSource`'s `hasScope(scope)` guard means a second call never creates a
  duplicate `ScopeInfo`. Because `ASatchelScope.equals()`/`hashCode()` are UUID-based, this holds
  even across two different `Mob` Java objects that happen to share the same persistent UUID (e.g.
  one from before a chunk reload, one from after).

## What this contract does not guarantee

- **The returned `MobScope` is not a handle safe to hold across ticks.** Once a mob's chunk becomes
  unresolvable, the poll tears the scope down — treated identically to a genuine removal (see
  [Jig & Scope Runtime](../architecture/runtime.md#mobjig)) — and a previously-returned `MobScope`
  refers to state that no longer exists. A consumer needing the current, guaranteed-live entity
  re-resolves it fresh via `Satchel.require().egress().getEntity(level, uuid)` (`ForgeEgress`, the
  side-resolved lookup [RM_SAT_022](../../../roadmap/RM_SAT_022_roger.md) ("Roger") shipped — see
  [Jig & Scope Runtime § MobJig](../architecture/runtime.md#mobjig)) rather than caching the object
  `getFor` returned. Note the return type is `Optional<Entity>`, not `Optional<Mob>` — a caller
  narrowing back to `Mob` is responsible for its own check.
- **`getFor` carries no domain meaning of its own.** `introduceSource` only makes the `Mob` visible
  to Satchel's scope machinery — it doesn't make a mob a boss, or tracked, or anything else. Domain
  meaning (a `BossMobFixture`, defeat handling) is entirely the calling module's responsibility,
  layered on top of the scope this call establishes.

## Threading

Same rule as every other Satchel entry point — see
[Forge Integration & Sidedness Contract](forge-integration.md#sidedness--the-contract-not-just-the-mechanism):
the calling thread must already be bound via `LogicalSideContext`. `getFor`'s own `isReady()` check
guards foundation readiness only, not thread binding — a thread that was never bound still fails
inside `introduceSource`'s call to `Satchel.require()`, not here.

## Related pages

- [Jig & Scope Runtime](../architecture/runtime.md) — `MobJig`'s poll-driven presence model this
  contract sits on top of
- [Forge Integration & Sidedness Contract](forge-integration.md)
- [Satchel mod summary](../satchel.md)
