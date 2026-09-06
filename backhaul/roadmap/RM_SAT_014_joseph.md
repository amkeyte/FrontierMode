---
id: RM_SAT_014
uid: RM_SAT
number: 14
kind: work
status: resolved
title: Fix LevelJig unload/rescope leak
owner: Arryn
depends_on:
- RM_SAT_011
created: '2026-08-14'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_SAT
<!-- bh-header:end -->

## Fix LevelJig unload/rescope leak

- 2026-08-15: **Confirmed via real play, the exact reported symptom.** Project owner ran the
  precise repro from the "Ruled" entry below on a real build: new world, created a border path,
  exited, started a second new world, confirmed the first world's path did **not** bleed into it,
  created a new path in the second world with no problem, exited Minecraft entirely, relaunched,
  re-entered the world, everything still worked. This is the fix working end-to-end, not just
  reviewed-by-tracing as the entry below caveats — closing the "not build-tested" gap for this
  node specifically.
- 2026-08-15: **Resolved by Lead Dev (Curtis).** Both parts implemented exactly as scoped below:
  1. `ServerForgeIngress.onLevelUnload`/`ClientForgeIngress.onLevelUnload` now call
     `foundation.tryRemoveSource(level)` instead of the no-op `introduceSource(level)`
     re-announce.
  2. `ScopeEngine_Server.unload(ScopeInfo)` now does `active.remove(validatedScopeId(info))`
     instead of reading the map via `bundlesFor(info)` (which would `computeIfAbsent` a fresh
     empty map rather than evicting the stale one). `ScopeEngine_Client.unload()` needed no
     change — confirmed it already evicted correctly.
  Both parts landed together since the node called out that (1) alone wouldn't fix the reported
  symptom. Not build-tested in this session (sandboxed environment, no Forge/Mojang maven
  access) — reviewed by tracing the call chain against
  [Jig & Scope Runtime](../wiki/satchel/architecture/runtime.md) rather than a real
  `gradlew build`; flagging for a real compile + one join/new-world/reconnect cycle before
  calling this closed for real.
- 2026-08-14: Node opened, per
  [SAT_031](../tickets/SAT_031_handoff-retrospective-recommendations.md) recommendation #4, as an
  open decision ("either document as intentional or scope the real fix").
- 2026-08-14: **Ruled — implement the real fix.** Not a latent gap: user confirmed a live symptom
  — loading a new world without restarting Minecraft reloads the *previous* world's border rings
  instead of starting blank. Traced to source (not inferred) before ruling; this is a two-part fix,
  not the single `tryRemoveSource` wire-up the gap first looked like.

**Root cause, precisely:** `LevelScope`'s UUID is `UUID.nameUUIDFromBytes(dimension().toString())`
(`LevelScope.determineUUID`) — deterministic from the dimension ID alone, with no regard to which
actual world/save it is. Any two worlds both containing an `overworld` dimension resolve to the
*identical* scope UUID. That's correct and necessary for one persistent world reconnecting across
server restarts (same world → same bundle, so persistence reattaches) — but it means "new world,
same dimension names" is indistinguishable from "same world, reloaded" *unless old scope state is
actually torn down first*. Two independent gaps currently prevent that teardown:

1. **`LevelEvent.Unload` never calls `LogicalFoundation.tryRemoveSource`.**
   `ServerForgeIngress`/`ClientForgeIngress`'s unload handlers just re-announce the source via
   `introduceSource(level)` — a no-op re-registration, not a removal. `tryRemoveSource` is real and
   correctly wired downstream (`jig.onUnload` → `signalScopeUnloaded` → `JigInfo.removeScope`), it's
   just never called. Fix: call `foundation.tryRemoveSource(level)` from both `onLevelUnload`
   handlers instead of `introduceSource(level)`.
2. **Even with (1) fixed, `ScopeEngine_Server.unload(ScopeInfo)` still leaks.** It calls
   `bundle.onDestroyed()` on every bundle in the scope's map but never evicts that map from its own
   `active: Map<UUID, Map<BundleKey<?>, SatchelBundle>>` registry — contrast with
   `ScopeEngine_Client.unload()`, which correctly does `active.remove(info.scopeId())`. Since
   `bundlesFor(info)` is `active.computeIfAbsent(scope.uuid(), ...)`, the next time a same-UUID
   scope gets created (i.e. the "new" world, same dimension name), `computeIfAbsent` finds the old,
   already-`onDestroyed()`'d bundle map still sitting there and hands back the stale bundles instead
   of building fresh ones — this is what actually reproduces "reloads old rings," even after (1) is
   fixed. Fix: `ScopeEngine_Server.unload()` needs the same `active.remove(info.scopeId())` (or
   equivalent) `ScopeEngine_Client.unload()` already has.

Both parts are required; (1) alone would still exhibit the reported symptom. Client-side only needs
(1) — its engine's `unload()` was already correct.

Concretely related, not the same bug: [FRO_016](../tickets/FRO_016_null-level-on-exit-crash.md)
(client tick reaching a dead-context render after disconnect) is a *symptom* of gap (1) too — the
client jig's tick subscription never gets torn down on unload since `tryRemoveSource` never fires.
FRO_016's fix was a defensive skip at the consumer; this fix addresses the actual cause.

## Required By

<!-- required-by:start -->
- [**RM_SAT_017**](RM_SAT_017_paul.md) — Prototype hardening
- [**RM_SAT_019**](RM_SAT_019_dennis.md) — Sync a Satchel world-identity token
<!-- required-by:end -->
