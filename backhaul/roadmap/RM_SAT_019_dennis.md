---
id: RM_SAT_019
uid: RM_SAT
number: 19
kind: work
status: resolved
title: Sync a Satchel world-identity token
owner: Arryn
depends_on:
- RM_SAT_014
created: '2026-08-14'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_SAT
<!-- bh-header:end -->

## Sync a Satchel world-identity token

- 2026-08-15: **Confirmed via real play, isReady()-gated design.** Project owner ran a full
  session switching worlds back and forth repeatedly, no problem — no `ScopeNotFound`, no
  `SatchelException.NotReady`, no repeat of FRO_021's cache-fork symptom. Closes out the "not
  build-tested" caveat on both this entry and the direction-of-delegation follow-up below.
- 2026-08-15: **Follow-up on the entry below: flipped the direction of delegation.** Project
  owner pointed out the entry below had `LevelResolver.determineUUID` delegate to constructing a
  `LevelScope` just to read its UUID back off — technically one source of truth, but backwards
  and mildly wasteful. `LevelResolver.determineUUID` is now the one real implementation;
  `LevelScope`'s constructor calls it directly (`super(LevelResolver.determineUUID(level))`).
  Also matches pre-existing intent better: `LevelJigConfig.createPresets()`'s `uuidDeterminer`
  binding already pointed at `LevelResolver::determineUUID` (unused today, but a private method
  on `LevelScope` could never have satisfied that method-reference regardless). No behavior
  change, purely a direction-of-delegation fix. [SAT_032](../tickets/SAT_032_isready-gate.md)
  updated to match.
- 2026-08-15: **Design superseded, not just patched: the fallback itself is gone.** After
  FRO_021 found a second, independent bug from the same root cause (`RenderContext`'s cache
  forking on the pre/post-token UUID split), the project owner questioned whether the
  fallback-based design below was a standing footgun rather than a one-off — correctly. Replaced
  with a real readiness gate rather than another defensive patch: `Satchel.isReady()` /
  `LogicalFoundation.isReady()` (new — client requires the token bound, server trivially ready
  once installed), and `LevelScope.determineUUID` now throws `SatchelException.NotReady` (new
  exception type) instead of silently computing a dimension-only UUID when the token isn't bound.
  `LevelScope` stays directly constructable from anywhere (never revisited that) — the
  precondition is enforced structurally in the one real UUID-derivation method instead of
  degraded silently. Both ingress classes' tick pulses (`onExecutionPulse`) now no-op until
  `Satchel.isReady()`, except the bootstrap chain that makes readiness happen in the first place
  (`bindFoundation`/`ensureInstalled`/client's `reannounceLevelIfTokenJustArrived`). Also fixed,
  flagged separately by the project owner as its own rot risk: `LevelResolver.determineUUID` and
  `LevelScope`'s own `determineUUID` were two independent, hand-synced implementations of the
  same formula — collapsed to one (`LevelResolver.determineUUID` now delegates to constructing a
  real `LevelScope`), and the two duplicate `safeCurrentToken()` helpers are gone. Full writeup
  and file list: [SAT_032](../tickets/SAT_032_isready-gate.md), opened as a heads-up to the
  Architect since this touches [Forge Integration & Sidedness Contract](../wiki/satchel/spec/forge-integration.md)'s
  documented contract. Not build-tested this session — same caveat as everything else in this
  pass.
- 2026-08-15: **Confirmed via real play, after the `ScopeNotFound` fix below.** Project owner ran
  a full session: new world + border path, exit, new world (confirmed no bleed-in — see
  [RM_SAT_014](RM_SAT_014_joseph.md)'s own log for that half), new path with no problem, full
  Minecraft exit and relaunch, re-entered the world, everything worked as expected. No repeat of
  the `ScopeNotFound` crash across a join, a world switch, or a full client restart (the three
  cases most likely to exercise the token round-trip's timing). Closes the "not build-tested" gap
  this node's log otherwise still carried.
- 2026-08-15: **Real crash found and fixed, via an actual `latest.log`, not inferred.** Project
  owner reported the first real `runClient` join after this node's implementation crashed on the
  first render frame:
  ```
  com.arryn.satchel.common.jig.guts.SatchelException$ScopeNotFound: No Jig scopeInfo was found:
  Jig JigKey[...frontiermode:borders_jig...] does not know scope LevelScope[minecraft:overworld]...
    at LogicalFoundation.requireScopeInfo
    at BorderAPI.borders (BorderAPI.java:88/81)
    at RenderContext.fixture / .standby
    at WorldBordersRenderer.render → Rendering.onRenderLevel
  ```
  **Root cause:** a direct, predictable consequence of this node's own block/defer design (the
  project owner's explicit choice, over the "eager dimension-only UUID, upgrade silently" option).
  `ClientForgeIngress` now withholds registering the client's `LevelJig` scope until the
  world-identity token round-trips from the server — a real window (~0.85s in the observed log)
  during which the scope legitimately isn't registered yet. `RenderContext.java:59` constructs its
  own `LevelScope` directly (bypassing `LevelResolver` entirely, same as noted earlier in this
  log) and immediately asked `LogicalFoundation.requireScopeInfo` for it — which throws hard on an
  unregistered scope, with no fallback, unlike the `BundleNotFound`-tolerant path one layer down.
  Before this node existed, that window couldn't happen at all (registration was synchronous with
  level-load), so nothing had ever needed to handle it.
  **Fix, project owner explicitly authorized crossing into FrontierMode for this** ("we can
  absolutely update the FRO side to conform to the Satchel, that's by convention"):
  - Satchel: `LogicalFoundation.tryScopeInfo(JigKey<?>, SatchelScope)` — non-throwing sibling of
    `requireScopeInfo`, returns `Optional<ScopeInfo>` instead of throwing `ScopeNotFound`. Purely
    additive; `requireScopeInfo` itself is untouched, so nothing relying on its throw changes
    behavior. Confirmed via grep this is the *only* call site anywhere in either repo reached from
    a context where the scope might legitimately not exist yet — `Satchel.get()`/`getOrCreate()`'s
    own `requireScopeInfo` calls and `ASatchelJig`'s are only ever reached from already-tracked
    `ScopeEvent`/tick dispatch, never from a freshly-constructed scope on the query side.
  - FrontierMode: `BorderAPI.borders(Level)` now calls `tryScopeInfo` instead of `requireScopeInfo`
    and treats an empty result exactly like the pre-existing `!isReady()` branch a few lines below
    it (log at debug, return `Optional.empty()`) — no new code path, reuses the file's own existing
    pattern. Traced the full call chain first (`RenderContext.fixture()`/`.standby()` →
    `WorldBordersRenderer.render()`) and confirmed every caller above `BorderAPI.borders()` already
    treats an empty `Optional` as "standby, don't crash" — this was the one and only throw site in
    the whole chain, so no other file needed to change.
  Not build-tested in this session (same sandbox limitation as everywhere else in this pass) —
  needs the same real `gradlew build` + real join cycle already owed to this node before treating
  it as fully proven, same caveat as below.
- 2026-08-15: **Resolved by Lead Dev (Curtis).** Implemented exactly per the proposal below, plus
  the client-side block/defer design the project owner chose explicitly (over the alternative
  "dimension-only fallback, upgrade silently" option) when this work was scoped:
  - `common/persistence/WorldIdentitySavedData.java` — new server-only `SavedData`, one record per
    world, always anchored on the overworld's `DataStorage` regardless of which dimension triggers
    the lookup. `getForWrite` generates a random UUID on first-ever access, same
    `createNew`/`loadExisting` shape as `BundleSavedData`.
  - `common/identity/WorldIdentityContext.java` — new side-bound token holder, same shape as
    `LogicalSideContext`: `bindServerToken`/`bindClientToken`/`clearClientToken`/`current()`
    (`Optional<UUID>`, branches on `LogicalSideContext.require()`).
  - `common/net/S2cWorldIdentityToken.java` + `SatchelNetwork` — new S2C packet (encode/decode/
    handle, mirroring `S2cBundleParcel`'s shape), registered in `SatchelNetwork.register()`, plus a
    new `SatchelNetwork.sendToken(ServerPlayer, UUID)` using `PacketDistributor.PLAYER` (per-player,
    not per-dimension — this is world-level state, not scope-level).
  - `ServerForgeIngress.onLevelDiscover` now binds the server token from
    `WorldIdentitySavedData.getForWrite(overworld)` **before** calling `introduceSource(level)` —
    the server must never defer, since it owns the persisted record and can always read it
    synchronously. New `onPlayerLoggedIn`/`onPlayerChangedDimension` handlers push the token to
    that player via `SatchelNetwork.sendToken`.
  - `LevelResolver.resolveScope` implements the chosen **block/defer** design: on the client, if no
    token is cached yet, it returns `null` (reuses this method's existing "unrecognized source"
    contract rather than inventing a new state) instead of constructing a token-less `LevelScope`.
    Server-side never hits this branch, by construction of the ordering above.
  - `ClientForgeIngress` gained `onLoggingOut` (clears the cached client token — session-scoped by
    design, never persisted) and a tick-driven `reannounceLevelIfTokenJustArrived()` — since
    `introduceSource` is only ever called from level load/unload events, a source deferred at
    `onLevelDiscover` would otherwise never be retried once the token actually arrives.
    `introduceSource` re-announcement is idempotent (established fact from RM_SAT_014's
    investigation), so this is a cheap, one-time-per-token re-trigger, not a repeated no-op.
  - `LevelScope`'s own private static `determineUUID(Level)` — the actually load-bearing UUID
    derivation (the constructor's `super(determineUUID(level))` resolves to this overload, not the
    instance-level one) — folds the token in **opportunistically and non-blocking**: present → fold
    it in (`nameUUIDFromBytes(token + ":" + dimension)`); absent → falls back to the old
    dimension-only UUID. Deliberately non-throwing, unlike `resolveScope`'s block/defer: two
    FrontierMode call sites (`BorderAPI.java:60`, `RenderContext.java:59`) construct `LevelScope`
    directly, bypassing `LevelResolver` entirely, and are out of this pass's scope to touch or
    verify — this keeps them working exactly as before when no token is bound yet, rather than
    throwing or silently blocking construction they don't expect. `LevelResolver.determineUUID`
    (confirmed dead/unused code, see RM_SAT_014-era investigation) got the identical fallback for
    consistency, so it doesn't rot into a misleading duplicate of the real logic.
  - **Two things flagged, not resolved here:**
    1. **Unverifiable in this sandbox.** No cached Forge 47.4.10 jars or network access — the exact
       Forge event class names used (`PlayerEvent.PlayerLoggedInEvent`,
       `PlayerEvent.PlayerChangedDimensionEvent`, `ClientPlayerNetworkEvent.LoggingOut`) and
       `PacketDistributor.PLAYER`/`MinecraftServer.overworld()` are based on training knowledge of
       the Forge 1.20.1 API, not compiled against real jars. Same caveat as RM_SAT_014's log: needs
       a real `gradlew build` plus one real join/dimension-change/disconnect cycle before this is
       trusted the way RM_SAT_012's crash fix now is (confirmed via a real `runClient` run).
    2. **Assumes the overworld loads before any other dimension can.**
       `ServerForgeIngress.onLevelDiscover` calls `level.getServer().overworld()` unconditionally —
       correct under vanilla/Forge's normal boot order (overworld always loads first at server
       start), but not defensively guarded if that assumption is ever wrong for some other reason
       (a datapack-driven dimension setup, etc.). Worth a second look during the real build/test
       pass above, not a blocker for landing this.
- 2026-08-14: Node opened, "nice to have" per the project owner — defense-in-depth on top of
  [RM_SAT_014](RM_SAT_014_joseph.md)'s teardown fix, not a substitute for it.

**First, the question that prompted this: could two dimensions collide within one running server?**
No — `Level.dimension()` is a registry key (`ResourceKey<Level>` over a `ResourceLocation`), and
Forge/vanilla's dimension registry is structurally keyed by that value; two simultaneously-loaded
dimensions can't share one registration. The gap
[RM_SAT_014](RM_SAT_014_joseph.md) found is purely **cross-session** — the *same* key recurring
across two different worlds loaded sequentially in one JVM, not a collision within one running
world. Confirms RM_SAT_014's fix (proper teardown before rescope) is the actual required fix;
this node is additional hardening on top, not an alternative to it.

**Proposal:** Satchel generates a random, persistent per-world token the first time any dimension
in a world loads (anchored on the overworld's `DataStorage` via a small dedicated `SavedData` —
same mechanism `BundleSavedData` already uses, just world-scoped instead of per-bundle), and syncs
it to the client at join (and cheaply on each dimension change, for robustness against a missed
initial packet) via a new Satchel packet. `LevelScope.determineUUID` then folds the token in
alongside the existing dimension key (`nameUUIDFromBytes(token + ":" + dimension().toString())`
or equivalent), so two different worlds sharing dimension names can no longer compute the same
scope UUID even if a teardown edge case is ever missed.

**Why client-side needs the sync, not just a server-side token:** `ClientLevel` doesn't reliably
carry enough server-world identity on its own — the world seed isn't networked to the client by
default, and the save-folder identity is server-filesystem-only. The dimension key is currently
the only thing guaranteed symmetric between `ServerLevel` and `ClientLevel` without adding new
plumbing, which is exactly why `LevelScope.determineUUID` only uses that today. A Satchel-issued
token pushed to the client at the right moment closes that gap deliberately, rather than
discovering another ambient signal to lean on.

**Same philosophy as [Universal Sidedness Facade](../wiki/satchel/architecture/facade-vision.md):**
Satchel becomes the authority that issues an explicit identity value, rather than every consumer
(or in this case, every side) re-deriving one from whatever ambient signal happens to be
available. This is the first concretely scoped piece of that vision.

- 2026-08-15: Originally wired directly into RM_SAT_018's `depends_on`,
  in parallel with RM_SAT_018's dependency on [RM_SAT_017](RM_SAT_017_paul.md) — that routed
  around RM_SAT_017's own convergence gate. Moved: this node is now one of RM_SAT_017's
  prerequisites instead, so RM_SAT_018 reaches it by depending on RM_SAT_017 alone. The
  relationship this page describes to the facade vision is unchanged.

## Required By

<!-- required-by:start -->
- [**RM_SAT_017**](RM_SAT_017_paul.md) — Prototype hardening
<!-- required-by:end -->
