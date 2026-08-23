---
id: SAT_037
uid: SAT
number: 37
client: Satchel
status: done
title: Design MobJig's side-agnostic resolution
context: Project owner reversed the server-only call. Needs a resolution abstraction,
  not a type widening.
priority: high
opened: '2026-08-22'
closed: '2026-08-22'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Architect ticket for [RM_SAT_022](../roadmap/RM_SAT_022_roger.md) ("Roger"). The project owner has
reversed [RM_SAT_021](../roadmap/RM_SAT_021_frank.md)'s server-only call: `MobJig` should not be
server-bound.

The node has the full picture — the four coupling points, the latent client-side teardown bug, and
the done bar. This ticket is the design question that has to be answered before any of it can be
built, and it is genuinely a design question rather than a mechanical widening.

## The question

`Level` has no `getEntity(UUID)`. `ServerLevel` does, backed by its own entity index. `ClientLevel`
tracks entities by network ID with no UUID-keyed equivalent. So the two sides cannot share one
lookup call, and `MobJig`'s whole presence model is built on exactly that call.

What shape does the resolution abstraction take? Some options, none of them decided:

- **A side-resolved strategy behind one interface**, chosen at foundation boot the same way the two
  `ScopeEngine` implementations already are — arguably the most consistent with how Satchel already
  handles side divergence.
- **A client-side UUID index maintained by `MobJig` itself**, populated from entity add/remove
  events, trading memory for a lookup the vanilla API doesn't give.
- **Client-side resolution by iteration** over the tracked entity list, which is correct but O(n)
  per polled UUID per cycle, and the cycle already runs ~every 20 ticks.

Whichever it is, it also has to answer what `MobInterestSupplier`'s signature becomes, since that is
the part consumers see.

## Why this is urgent rather than merely open

**`BossModule` implements `MobInterestSupplier`.** [RM_FRO_018](../roadmap/RM_FRO_018_shirley.md)
("Shirley") is the next FrontierMode node and has not been written yet — so the window to change
that interface at zero cost is open right now and closes the moment Lead Dev starts.

Shirley's own `sideApplicability` stays `SERVER` either way; defeat detection is server-only. The
question is only whether her `interestedMobs()` is typed against `ServerLevel` or something wider,
and that is cheaper to answer now than to migrate later.

## Also in scope

- **[Jig & Scope Runtime § MobJig](../wiki/satchel/architecture/runtime.md#mobjig) currently states
  the server-only constraint as settled design** — "the poll itself is server-side only today; a
  future client-side `MobJig` consumer would need its own resolution mechanism." That sentence is
  now a description of a bug rather than a design, and should not be left standing while the fix is
  pending.
- **[Universal Sidedness Facade](../wiki/satchel/architecture/facade-vision.md)** argues exactly
  this case in the abstract, and RM_SAT_018 ("Edward") is the
  actionable convergence tracking it. Worth deciding whether Roger is a piece of Edward or a
  neighbour — if the former, Edward's `depends_on` should say so per
  [BKHL_002](BKHL_002_convergence-gate.md)'s convergence-gate convention.

## Not in scope

The build. That is Lead Dev's, off whatever this ticket concludes, tracked on
[RM_SAT_022](../roadmap/RM_SAT_022_roger.md)'s own done bar.

## Log



- 2026-08-22: Ticket opened.

- 2026-08-22: **Design ruling (Architect).** Shape: the first option — a side-resolved strategy
  behind one interface, chosen at foundation boot. Verified against source rather than assumed:
  `ASatchelFoundationBooter` already defines exactly this hook once, for `ScopeEngine` —
  `public abstract ScopeEngine engine()` — and `LogicalFoundation.installConfigs()` pulls it via
  `booter().engine()`; `ServerFoundationBooter`/`ClientFoundationBooter` each return their own
  concrete implementation (`ScopeEngine_Server`/`ScopeEngine_Client`). That's a load-bearing
  precedent to mirror exactly, not a second mechanism to invent.

  **New interface, `MobEntityLookup`** (`common/jig/mob/`, beside `MobInterestSupplier`/
  `MobReconcileLogic` — this is `MobJig`-specific, not generic infrastructure the way `ScopeEngine`
  is, so it doesn't belong in `common/jig/guts/`):
  ```
  Optional<Mob> resolve(Level level, UUID uuid);
  ```
  Only `Level`/`Mob`/`Optional` on the signature — no sided type anywhere on it. That's the whole
  point: nothing in `common/jig/mob/` needs to import `ServerLevel` or `ClientLevel` again.

  **Wiring mirrors `engine()` exactly.** `ASatchelFoundationBooter` gains
  `public abstract MobEntityLookup mobEntityLookup();`. `ServerFoundationBooter.mobEntityLookup()`
  returns a new `MobEntityLookup_Server` (`server/jig/mob/`) whose `resolve` casts to `ServerLevel`
  and calls `getEntity(uuid)` — today's exact logic, relocated, not rewritten. `ClientFoundationBooter
  .mobEntityLookup()` returns `MobEntityLookup_Client` (`client/jig/mob/`) — since `ClientLevel` has
  no UUID-keyed lookup, its `resolve` casts to `ClientLevel` and iterates the tracked entity list for
  a UUID match (the Summary's third option). That cost and that correctness are both now fully
  local to one class nothing else ever sees. The Summary's second option — a `MobJig`-maintained
  client index fed by entity add/remove events — stays available as a drop-in replacement for
  `MobEntityLookup_Client`'s internals alone, zero change to the interface or its callers, if
  iteration cost ever actually shows up in profiling. Not worth building against a cost nobody has
  measured, and it would mean new entity add/remove event wiring through `ClientForgeIngress` for a
  problem that may not exist.

  **`MobJig.reconcile()`** calls `Satchel.require().booter().mobEntityLookup().resolve(level, uuid)`
  in both phases — Phase 1's `ServerLevel.getEntity(uuid)` call and Phase 2's
  `instanceof ServerLevel` guard + cast alike. Neither `MobJig` nor `MobReconcileLogic` needs a
  `ServerLevel`/`ClientLevel` import afterward.

  **`MobInterestSupplier.interestedMobs()`** widens from `Map<ServerLevel, Set<UUID>>` to
  `Map<Level, Set<UUID>>` — the consumer-facing answer the Summary asked for.
  `MobTrackingModule`'s `INTERESTS` map and `watch`/`unwatch`/`currentInterests()` widen the same
  way. Nothing about this forces any existing consumer to become side-agnostic itself:
  `MobTrackingModule` stays `SERVER`-applicability by its own unchanged choice, `BossModule`/
  Shirley's stays `SERVER` per RM_FRO_018's own text, and a real `ServerLevel` is always legal
  wherever a `Level` is asked for. This design changes what the poll's mechanism is *capable* of,
  not what side any current consumer runs on.

  **Fixes RM_SAT_022's latent bug for free, not as a separate patch.** Phase 2's re-resolution
  today `continue`s outright for a client-scoped `MobScope` because of its `instanceof ServerLevel`
  guard — exactly why a `CLIENT`/`BOTH` config's scopes were being torn down every cycle regardless
  of the mob's real state. Once Phase 2 calls through `MobEntityLookup` instead of casting, a
  client-side scope re-resolves through `MobEntityLookup_Client` exactly like a server-side one
  resolves through `MobEntityLookup_Server` — the bug disappears as a consequence of unifying the
  mechanism.

  **Deliberately not generalized beyond `MobJig`.** No other jig kind polls today —
  `LevelJig`/`PlayerJig` stay event-driven — so `MobEntityLookup` is scoped to what `MobJig` actually
  needs now, not parked in `common/jig/guts/` against a hypothetical future poll-driven jig kind. If
  one shows up later, promoting the pattern is a mechanical relocation, not a redesign.

  **Edward/Roger: neighbour, not a piece.** Checked [BKHL_002](BKHL_002_convergence-gate.md)'s rule
  directly: a node naming a convergence in `depends_on` can't also name a sibling alongside it
  unless that sibling is already an ancestor of the convergence. RM_SAT_022 genuinely and directly
  depends on RM_SAT_021 (Frank) — that edge is real and shouldn't be dropped to satisfy a structural
  rule. RM_SAT_021 is not an ancestor of RM_SAT_018 (Edward descends from RM_SAT_017/RM_SAT_019
  instead), so adding RM_SAT_018 alongside it would be exactly the bypass BKHL_002 exists to catch.
  Separately, on the merits: Edward's own text is explicit that "real work nodes get inserted... as
  pieces of this get scoped" — meaning pieces of its own still-open questions (a generic
  side-bound event-forwarding facade, a frame-driven dispatch path), not any node that happens to
  rhyme with its philosophy. RM_SAT_019 is the precedent for exactly this distinction: called out in
  `facade-vision.md` as "the first real piece" evidencing the vision, yet it was never folded into
  RM_SAT_018's own `depends_on` — it sits behind RM_SAT_017 instead. Roger is the same shape of
  precedent-in-spirit, not a decomposition of Edward's own scope, and it's urgent on a timeline
  (Shirley) that has nothing to do with Edward's deliberately-unscoped one. No `depends_on` edit to
  either node.

  **`runtime.md`'s stale sentence:** already carries an interim correction pointing at this node
  ("That is a defect under repair, not settled design" — see the page's MobJig section) — adequate
  for now. The fuller rewrite describing `MobEntityLookup` as built belongs to whoever closes
  RM_SAT_022, once it exists to describe — same SAT_034→SAT_035 sequencing Frank used, not
  something to write against unbuilt behavior.

  Design concludes here. Handing off to RM_SAT_022's own done bar for the build.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
