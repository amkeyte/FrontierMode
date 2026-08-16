---
id: SAT_032
uid: SAT
number: 32
client: Satchel
status: done
title: isReady() gate replaces silent LevelScope UUID fallback
context: LevelScope now throws instead of silently forking UUIDs pre-token; Satchel.isReady()
  is the new general readiness gate. Architecture-page updates needed on your side.
priority: normal
opened: '2026-08-15'
closed: '2026-08-15'
---

<!-- board:start -->
<!-- board:end -->

## Summary

**To Douglas (Architect): this change has already been made and landed — this ticket is a
heads-up so architecture/spec pages can be brought current, not a request for review.**

Supersedes part of RM_SAT_019's original design. Full context: project owner questioned whether
RM_SAT_019's fallback-based fix was itself a future footgun (it was — see FRO_021, where the
same class of bug bit a second, different call site), which led to replacing the fallback
entirely with a real readiness gate.

**What changed:**

1. **`LogicalFoundation.isReady()` / `Satchel.isReady()`** — new general readiness predicate.
   Server: trivially true once installed. Client: additionally requires the world-identity token
   (RM_SAT_019) to be bound. Intended as *the* thing any current or future Satchel-dependent code
   checks before doing anything, rather than each feature growing its own "am I too early" logic.
2. **`LevelScope`'s UUID derivation no longer has a silent fallback.** Previously: token present →
   fold it in; token absent → quietly compute a dimension-only UUID instead. That fallback is
   exactly what caused FRO_021's `RenderContext` cache-fork bug, and was a live landmine for any
   *other* code that constructs a `LevelScope` directly (bypassing `LevelResolver`) — provably
   safe today only because we happened to audit the two call sites that exist. Now:
   `LevelScope`'s constructor throws `SatchelException.NotReady` (new exception type) if the
   token isn't bound, full stop. `LevelScope` is still directly constructable from anywhere (that
   was deliberate from the start, not something this change revisits) — the precondition is
   enforced structurally instead of silently degraded.
3. **Ticking is now gated too.** `ServerForgeIngress`/`ClientForgeIngress.onExecutionPulse` no-op
   `foundation.foundationLifecycle().pulse()` until `Satchel.isReady()`. The bootstrap chain
   itself (`bindFoundation`, `ensureInstalled`, and client's `reannounceLevelIfTokenJustArrived`,
   which is what actually detects the token arriving) is the deliberate exception — those are the
   paths that make readiness happen, so they run unconditionally.
4. **`LevelResolver.resolveScope`** now checks `Satchel.isReady()` instead of its own inline
   `LogicalSideContext`/`WorldIdentityContext` check — same external behavior (`null` if not
   ready), simpler implementation.
5. **`BorderAPI.borders(Level)`** (FrontierMode) gained a proactive `Satchel.isReady()` check at
   the top, since it constructs its own `LevelScope` via `scope()` internally and is reachable
   from the client render path (`RenderContext.fixture()`) where readiness isn't guaranteed.
6. **Dead-code cleanup, not just this feature:** `LevelResolver.determineUUID(Object)` and
   `LevelScope`'s private `determineUUID(Level)` were two independent, hand-synced
   implementations of the same UUID formula — a real rot risk flagged by the project owner.
   `LevelResolver.determineUUID` now delegates to constructing a real `LevelScope` and reading
   its UUID instead of duplicating the formula; there's only one implementation left. (It's
   reached only via `LevelJig.determineUUID`, itself confirmed unreachable by anything live —
   kept correct rather than deleted since it's a real `@Override` on `SatchelJig`'s contract.)
   Two duplicate `safeCurrentToken()` helpers (one per class) are gone entirely — no longer
   needed now that the precondition is enforced once, structurally, rather than defensively
   re-checked per call site.

**Files touched:** `SatchelException.java` (new `NotReady`), `LogicalFoundation.java`
(`isReady()`), `Satchel.java` (`isReady()`), `LevelScope.java`, `LevelResolver.java`,
`ServerForgeIngress.java`, `ClientForgeIngress.java` (all Satchel); `BorderAPI.java`
(FrontierMode).

**Needs your attention:**
[Forge Integration & Sidedness Contract](../wiki/satchel/spec/forge-integration.md)'s
"Sidedness — the contract, not just the mechanism" section, last bullet ("Client-side scope
recognition can now be legitimately deferred, not just missing") documents the
`tryScopeInfo`-only version of this as current-state fact. It's now incomplete — `tryScopeInfo`
is still correct and still needed (a scope not being *registered* yet is a different, still-real
state from the token not being *bound* yet), but the page doesn't yet mention `Satchel.isReady()`
or that direct `LevelScope` construction can throw `SatchelException.NotReady`. Also worth a look:
[Jig & Scope Runtime](../wiki/satchel/architecture/runtime.md) (no readiness-gate concept exists
there yet at all) and [New Module Checklist](../wiki/satchel/architecture/new-module-checklist.md)
(item 4, `LogicalSideContext` thread discipline, is the natural place to add "and check
`Satchel.isReady()` before touching anything Satchel-dependent from outside its own ingress").

**Not build-tested** in this session (same sandbox limitation as the rest of this pass) — needs a
real `gradlew build` plus a real join before this should be trusted as fully proven, same caveat
as everything else from this session.

## Log

- 2026-08-15: **Doc updates done (Architect/Douglas) — closing.** All three pages flagged above:
  - [Forge Integration & Sidedness Contract](../wiki/satchel/spec/forge-integration.md) — the
    `tryScopeInfo` bullet now distinguishes "does a jig know this scope yet" from the general
    readiness question; added a new bullet for `Satchel.isReady()` as the general gate (what it
    means per side, what it gates, the ingress-class exception), and a new bullet documenting that
    direct `LevelScope` construction now throws `SatchelException.NotReady` instead of silently
    falling back.
  - [Jig & Scope Runtime](../wiki/satchel/architecture/runtime.md) — added a new "Readiness: bound
    to a side vs. ready to use" section right after "Foundations," covering the server/client
    asymmetry, the execution-pulse gating, and both `LevelResolver`/`LevelScope`'s roles in it.
  - [New Module Checklist](../wiki/satchel/architecture/new-module-checklist.md) — extended item 4
    with the `Satchel.isReady()` check and a pointer to `LevelResolver.resolveScope` as the
    prefer-this-over-direct-construction pattern.
  Also closed [FRO_021](../tickets/FRO_021_clear-frontiermode-s-remaining-out-of-sp.md) in the
  same pass (already done by Curtis, confirmed via source read) — its `RenderContext` finding is
  the concrete bug this whole redesign traces back to.
- 2026-08-15: **Confirmed via real play.** Project owner ran a full session, switching worlds
  back and forth repeatedly with no problems. Closes the "not build-tested" caveat this ticket
  opened with.
- 2026-08-15: **Follow-up:** flipped the direction of delegation between `LevelResolver` and
  `LevelScope`. Originally landed with `LevelResolver.determineUUID` constructing a whole
  `LevelScope` just to read its UUID back off (one source of truth, but backwards and mildly
  wasteful). `LevelResolver.determineUUID` is now the one real implementation;
  `LevelScope`'s constructor calls it directly. No behavior change. Also happens to match
  `LevelJigConfig.createPresets()`'s pre-existing `uuidDeterminer = LevelResolver::determineUUID`
  binding better, since that could never have pointed at a private method on `LevelScope` anyway.
- 2026-08-15: Ticket opened, findings above. Lead Dev (Curtis) implemented per the project owner's
  explicit direction after a design discussion (RenderContext's FRO_021 bug prompted questioning
  whether RM_SAT_019's fallback itself was a footgun; project owner proposed the isReady() gate
  directly).
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
