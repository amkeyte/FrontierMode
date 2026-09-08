---
id: RM_SAT_012
uid: RM_SAT
number: 12
kind: work
status: resolved
title: Consolidate BundleFactories into schema
owner: Arryn
depends_on:
- RM_SAT_011
created: '2026-08-14'
superseded_by: null
ticket: SAT_028
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_SAT
<!-- bh-header:end -->

## Consolidate BundleFactories into schema

- 2026-08-15: **Confirmed by real `runClient` — project owner reports a clean run.** The
  jig-identity fix in `TrackingModule`'s three handlers holds; world creation no longer crashes.
  Closing this out for real. Still worth a normal join/play/quit cycle at some point to shake out
  anything downstream, but the specific crash this node tracked is resolved and verified, not
  just reasoned through.
- 2026-08-15: **Root-caused and fixed.** The `[diag]` logging paid off on the very next
  reproduction — the failure-point log line showed `TrackingModule`'s `create()` call running
  against `ScopeEngine_Server@1473816536`, `schemaFrozen=true`, with exactly one registered key:
  `frontiermode:borders_bundle` — **Border's own key, not Tracking's**, on the engine instance
  that was actually asked for `satcheltracker:tracker_bundle`.
  - **Real root cause:** `ScopeEvent` is posted onto the ONE shared per-side `SatchelEventBus`,
    not a per-jig bus. Both `TrackingModule`'s `LevelJig` and Border's `LevelJig` are scoped to
    `minecraft:overworld`, so both post `ScopeEvent.Loaded`/`Tick`/`Unloaded` on that same bus.
    `TrackingModule`'s three handlers (`onScopeLoaded`/`onScopeUnloaded`/`onScopeTick`) never
    checked *whose* event they'd received — they did `info.jigInfo().jig` unconditionally and
    proceeded. When Border's own `onLoad()` posted its event, Tracking's handler received it too
    (event-type subscription, not jig-scoped), correctly resolved `info.jigInfo()` to **Border's**
    JigInfo (that part was never broken), and then tried to fetch `TrackingModule.BUNDLE` through
    Border's coupler/engine — which never registered that key.
  - **Why this is a genuinely pre-existing bug, not something this node's fix introduced:** this
    jig-identity gap in `TrackingModule`'s handlers has always existed. It was invisible before
    because `BundleFactories` was one flat *global* static registry — `entryFor(key)` found
    Tracking's entry regardless of which jig's engine you went through it via. This node's fix
    (per-engine `bundleDecls`, scoped to whichever jig actually registered it) is what made
    "wrong jig" a real, loud failure instead of a silent non-issue. Border itself never hit this
    because its own event handlers deliberately go through `BorderAPI` instead of
    `event.info().jigInfo()` (see [Jig & Scope Runtime](../wiki/satchel/architecture/runtime.md)'s
    account of the same `info.jigInfo().jig` pattern, previously flagged there only in the
    SAT_020 null-jigInfo context — this is a second, distinct hazard from the same pattern).
  - **Fix:** each of `TrackingModule`'s three handlers now checks
    `JIG.equals(info.jigInfo().key)` and returns early if the event wasn't posted by Tracking's
    own jig, before touching `info.jigInfo().jig` at all.
  - All `[diag]` logging (in `ScopeEngine_Server.registerBundleSchema`/`create()` and
    `LogicalFoundation.installConfigs`) removed now that it served its purpose.
  - The fresh-engine-per-jig anomaly noted below is **not** the cause of this bug (each jig
    correctly gets and keeps its own engine instance) — it remains a real, separate inconsistency
    against the SAT_023 comment's "shared singleton" assumption, still unresolved, still worth a
    look, just not urgent relative to this crash.
  - Still not build-tested against a real `gradlew build` in this sandbox — the user reproduced
    this via their own `runClient` and supplied the log; recommend one more real repro to confirm
    the fix actually resolves the crash before treating this as fully closed.
- 2026-08-15: **(superseded by the entry above) Reopened — live crash found in
  `FrontierMode/run/logs/latest.log`.** A real
  `runClient` session crashed the integrated server on world creation:
  `IllegalStateException: No BundleDecl registered for BundleKey[...satcheltracker:tracker_bundle...]`,
  thrown from `ScopeEngine_Server.create()` — the exact line this node's fix touches. This is
  `TrackingModule`'s own jig failing to find its own schema entry, not a Border/FrontierMode
  interaction (Border's registration is fully separate — verified by reading `BorderModule.java`
  in full).
  - Traced the entire registration chain exhaustively by static reading (`TrackingModule.init()`
    → `registerBundleSchema`/`freezeBundleSchema` inside `JigConfigCompiler.instantiateCoupler`
    → coupler/engine binding → `JigInfo`/`ScopeInfo` lookup → `create()`). Every link checks out
    on paper; couldn't reproduce the failure through reading alone.
  - **One real, independently-confirmed anomaly found along the way, not yet proven to be the
    cause:** `ServerFoundationBooter.engine()`/`ClientFoundationBooter.engine()` both do
    `return new ScopeEngine_Server()`/`new ScopeEngine_Client()` — a **fresh instance every
    call** — while `ScopeEngine_Server.resolveServerLevel`'s own SAT_023 comment explicitly
    assumes "this engine is a single per-side singleton shared by every jig." Those two
    statements contradict each other. Worth fixing regardless of whether it's this bug's cause.
  - **Action taken (project owner's call): added temporary diagnostic logging**, not a blind
    fix — `OUT.debug`/`OUT.error` calls in `ScopeEngine_Server.registerBundleSchema()` (dumps
    incoming/registered keys + engine identity), `LogicalFoundation.installConfigs()` (dumps
    which engine instance each jig gets), and `ScopeEngine_Server.create()`'s failure path
    (dumps engine identity, `schemaFrozen`, requested key, and every currently-registered key,
    all with `System.identityHashCode` so registration-time and failure-time engine/key identity
    can be directly compared from one log capture). All marked `[diag]` and commented as
    temporary — remove once root-caused. **Next step: reproduce and capture a fresh
    `latest.log`**, then compare the `[diag]` lines to actually pin this down instead of
    guessing further from source alone.
- 2026-08-15: **Resolved by Lead Dev (Curtis).** `ScopeEngine_Server.create()` and
  `ScopeEngine_Client.create()` now read `bundleDecls.get(key)` (the schema) instead of
  `BundleFactories.entryFor(key)`, exactly per the suggested direction below. Both engines'
  `applyFixture` helper now takes a `JigBundles.FixtureDecl<F>` and wires its `FixtureFactory`
  in via `decl.factory()::create` instead of the old `FixtureRegistration`'s `Supplier`.
  `TrackingModule.init()` no longer makes the now-genuinely-redundant
  `BundleFactories.registerFactory(...)` call — its schema alone is sufficient, and its stale
  "both registrations are required" comment is corrected.
  - **Scoped to Satchel only, by design.** `BundleFactories`/`BundleFactoryEntry`/
    `BundleFactoryEntries`/`BundleFactoryBuilder`/`FixtureRegistration` are deliberately **not
    deleted** this pass — `FrontierMode`'s `BorderModule.init()` still calls
    `BundleFactories.registerFactory(...)`, and deleting the class now would break FrontierMode's
    compile without FrontierMode's own migration pass ever happening. Verified this is safe to
    leave: `BorderModule.init()` already builds its own `JigBundles.Schema` for the exact same
    duality reason `TrackingModule` did, so it needs zero changes to keep working under the new
    `bundleDecls`-driven read path — its bundle construction picks up the fix automatically.
    `BorderModule.java`'s own stale duality comment ("ScopeEngine.create() goes through
    BundleFactories, not bundleDecls. Keep both.") is now inaccurate but intentionally untouched;
    flagging for whoever picks up FrontierMode's own hardening pass to correct alongside deleting
    its `registerFactory` call and, once every module is migrated, the `BundleFactories` class
    itself.
  - No FrontierMode files were read for editing purposes beyond confirming the above via grep —
    only `BorderModule.java`'s existing `registerFactory`/schema block was inspected, not modified.
- 2026-08-14: Node opened, promoted from the deferred-todo ticket
  [SAT_028](../tickets/SAT_028_consolidate-bundlefactories-into-schema.md) onto the roadmap ahead
  of the next module, per [SAT_031](../tickets/SAT_031_handoff-retrospective-recommendations.md)'s
  recommendation #2.

`JigBundles.BundleDecl` already carries a real `Factory`/`FixtureDecl` list — functionally
identical to what `BundleFactories.registerFactory`/`.registerFixture` store in a separate static
registry. `ScopeEngine.create()`/`.get()` only ever read the `BundleFactories` half; the schema's
`bundleDecls` map is populated but never consulted for construction. Not a design gap — user
confirmed mid-refactor: "that config was supposed to do a lot of work, and it isn't doing it all
yet."

**Why this is roadmap work, not just a ticket:** the duality has already caused the same bug
twice, independently — [SAT_022](../tickets/SAT_022_tracker-bundlefactory-missing.md)
(`TrackingModule`) and the identical shape in `BorderModule` (avoided only because of a defensive
comment, not a structural guard). Every future Satchel module inherits this footgun fresh until
it's fixed once, centrally. [RM_SAT_016](RM_SAT_016_kenneth.md) (the new-module checklist)
depends on this landing first, so the checklist documents the real pattern rather than the
duality.

**Suggested direction** (from SAT_028, not a committed plan): point `ScopeEngine.create()`/`.get()`
at `bundleDecls.get(key)` instead of `BundleFactories.entryFor(key)`; once proven out, delete
`BundleFactories` and every module's separate `.registerFactory(...)` call.

## Required By

<!-- required-by:start -->
- [**RM_SAT_016**](RM_SAT_016_kenneth.md) — Write new-Satchel-module checklist
<!-- required-by:end -->
