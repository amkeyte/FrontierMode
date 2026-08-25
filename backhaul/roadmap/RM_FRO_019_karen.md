---
id: RM_FRO_019
uid: RM_FRO
number: 19
kind: work
status: open
title: Boss defeat border-growth caller
owner: Arryn
depends_on:
- RM_FRO_018
created: '2026-08-16'
superseded_by: null
ticket: FRO_045
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_FRO
<!-- bh-header:end -->

## Boss defeat border-growth caller

- 2026-08-24: **Lead Dev build ticket opened: [FRO_045](../tickets/FRO_045_karen-build.md).**
  Architect prep ([FRO_044](../tickets/FRO_044_karen-prep.md)) closed — both design calls ruled,
  `boss.md` reconciled against shipped `BossAPI`. `ticket:` field updated from FRO_044 (prep,
  closed) to FRO_045 (build, open).

*Name note: "Karen" also named RM_FRO_016, a Tier 0 convergence deleted 2026-08-16 — so a bare
"Karen" in older prose may mean either node. Always pair the name with its ID. Reusing a retired
node's persona is no longer done; see [BHRM — Roadmap Conventions](../wiki/meta/bhrm.md).*

- 2026-08-24: **Both open design calls settled, prose reconciled against shipped source —
  Architect ruling ([FRO_044](../tickets/FRO_044_karen-prep.md)), now that
  [RM_FRO_018](RM_FRO_018_shirley.md) ("Shirley") is `resolved` and this node is genuinely
  startable.**

  **1. `growCenteredOn` (option 1) is ruled, not merely recommended.** Verified against real
  source rather than the description below alone: `BordersPathFacet.grow()` delegates to
  `fixture.logic.grow(previous)` (`BorderLogic.grow(Border)`), which builds a proposal via
  `prop.center(rules.chooseNextCenter(level, previous)).radius(rules.chooseNextRadius(level,
  previous)).layerIndex(previous.layer() + 1)` and applies it — and `BorderAPI.grow(Level)` is a
  one-line wrapper over `borders.PATH.grow()`. Three mirrored additions, same shape at each layer:
  `BorderLogic.growCenteredOn(Border previous, BlockPos center)` (identical to `grow(Border)` but
  `prop.center(center)` in place of `rules.chooseNextCenter(...)` — radius and `layer` still come
  from the rules, unchanged), `BordersPathFacet.growCenteredOn(BlockPos center)` (same append/
  `markPathDirty()` shape `grow()` already has), `BorderAPI.growCenteredOn(Level level, BlockPos
  center)` (wrapper, mirroring `grow(Level)`). One case `grow()` doesn't have to handle that this
  method does: `grow()` falls back to `logic.getInitial()` when the path has no tip yet;
  `growCenteredOn` has no such fallback available — a post-defeat call always requires an existing
  tip (a boss can't be defeated on a level with no border), so an absent tip here is a genuine
  data-corruption case, not a bootstrap case. Implement it as a loud log-and-no-op, not a silent
  `getInitial()` substitution, which would silently discard the caller's requested center.

  **2. Defeat-detection race fallback (option b) is ruled.** `MobScope.getFor(mob)` called
  synchronously in the `LivingDeathEvent` handler when the dying entity carries no
  `BossMobFixture`, per the 2026-08-17 entry below's own reasoning — cheaper than a second lookup
  shape, reuses the same fast path spawn-time tagging already relies on. The handler prose below
  already described this as the mechanism; this is the formal ruling that was never separately
  recorded.

  **3. Stale phrasing reconciled: "calls into `BossModule`'s record-creation directly" (below and
  in the 2026-08-18 entry) means `BossAPI.createBoss(level, border)`, confirmed against source.**
  `BossAPI.java` (built by [FRO_043](../tickets/FRO_043_boss-build.md)) has exactly that signature
  — `public static Optional<BossRecord> createBoss(Level level, Border border)` — and
  [Boss § Defeat detection and the border-growth gap](../wiki/frontiermode/architecture/boss.md#defeat-detection-and-the-border-growth-gap)
  already names it explicitly for both the bootstrap and post-defeat call sites (updated during
  FRO_043). Only this node's own older prose still used the generic phrasing; "Closes the loop"
  below is corrected to match rather than left to drift further from the two authoritative
  descriptions.

  **4. Shirley's stale `/kill`-self-heal done-bar wording — this node's own handler likely already
  satisfies it, pending a live check, not a separate mechanism.** `/kill` fires `LivingDeathEvent`
  for a `LivingEntity` in vanilla/Forge (`Entity.kill()` → `hurt(damageSources().genericKill(),
  Float.MAX_VALUE)` → `die()`, same path ordinary combat death takes) — standard Forge/vanilla
  behavior, **not verified against this project's own decompiled source**, per this project's
  standing no-Gradle-in-sandbox constraint. If that holds, this node's own defeat handler (with the
  race fallback above) catches a `/kill`ed boss the same as a melee-killed one: marks the record
  defeated, grows a new border, creates the next boss record — which is the self-heal Shirley's
  done bar wanted, just delivered by this node's mechanism rather than the recurring
  bootstrap-catch-up tick that design walked back from (see
  [RM_FRO_018](RM_FRO_018_shirley.md)'s 2026-08-24 entry). **Recommendation, not yet acted on:**
  add an explicit `/kill`-the-boss case to this node's own done bar's real-play pass (not just an
  ordinary combat kill) to confirm this for real before treating Shirley's wording as satisfied.
  The genuinely distinct, still-open case — a boss removed by something that *never* fires
  `LivingDeathEvent` at all (world-editing, another mod, chunk corruption) — is unaffected by this
  finding either way; that stays tracked on
  [Boss's own "Known gaps"](../wiki/frontiermode/architecture/boss.md#known-gaps), out of this
  node's scope.

- 2026-08-18: **`BossFixture` decoupled from `Border` entirely — see
  [RM_FRO_018](RM_FRO_018_shirley.md)'s 2026-08-18 log entry for the full reasoning; this node's
  own handler changes as a direct result.** Two corrections to the body below: (1) "mark that
  `Border`'s record in `BossFixture` defeated" is now "mark that boss's own record" —
  `BossFixture` records aren't keyed by `Border` UUID anymore, so there's no "that `Border`'s
  record" to reach for; the dying entity's `BossMobFixture`/fallback lookup (see the 2026-08-17
  entry below) already resolves the specific boss record directly, by its own `bossId`, without
  needing the `Border` at all. (2) "Closes the loop: ... calls into RM_FRO_018's spawn logic ...
  same as the bootstrap catch-up path would" is stale — there's no more bootstrap-catch-up tick
  path to mirror. What this handler actually does after `growCenteredOn(deathLocation)` succeeds:
  call `BossModule`'s record-creation directly (the same paired call every border-creation site
  needs, per RM_FRO_018's entry) — extract `position`/`level` from the just-created `Border` and
  write a fresh `BossFixture` record, `bossEntityId: null`. Materialization (actually placing the
  entity) happens later, on `BOSS_JIG`'s own tick, same as any other unmaterialized record — this
  handler doesn't spawn anything itself, it just ensures the record exists.
  **[Vocab note: "level" here is now named `layer` per [Border
  Vocabulary](../wiki/frontiermode/architecture/border-vocabulary.md), fixed on
  [Boss](../wiki/frontiermode/architecture/boss.md) itself via
  [FRO_029](../tickets/FRO_029_border-vocab-conformance.md) — this log entry is left as
  originally written, not restated.]**
  **[Naming note, 2026-08-24: "call `BossModule`'s record-creation directly" above means
  `BossAPI.createBoss(level, border)`, confirmed against `BossAPI.java` — see this node's
  2026-08-24 entry above. Left as originally written, not restated.]**
- 2026-08-17: **Defeat-detection race worth flagging explicitly, found while writing up
  `MobJig`'s poll-based presence model for [RM_SAT_021](../wiki/frontiermode/architecture/boss.md)
  (see [Boss](../wiki/frontiermode/architecture/boss.md)'s "What can actually go wrong" section).**
  `BossMobFixture` is now populated two ways: immediately at spawn time via `MobScope.getFor(mob)`
  (a guaranteed-loaded fast path — see [RM_FRO_018](RM_FRO_018_shirley.md)), or, for a boss whose
  chunk just became loaded some other way, by `MobJig`'s foundation-pulse poll, which runs on a
  cadence (roughly every 20 ticks), not instantly. This handler's own `LivingDeathEvent` listener
  should **not** assume `BossMobFixture` is already attached to every dying entity that is, in
  fact, a tracked boss — a fast load-then-death sequence (player finds and kills a boss within the
  same ~20-tick window its chunk loaded in) could beat the poll to it, and the spawn-time fast path
  doesn't help here since this is a *pre-existing* boss becoming loaded again, not a fresh spawn.
  **Fix, not yet built:** on a `LivingDeathEvent` where the dying entity carries no
  `BossMobFixture`, don't treat that as "definitely not a boss" — fall back to either (a) a direct
  scan of `BossFixture` records for one whose `bossEntityId` matches the dying entity's UUID, or
  (b) calling `MobScope.getFor(mob)` synchronously right there in the handler (the entity is, by
  definition, loaded — it just died) before checking. Either closes the race; (b) is probably
  cheaper since it reuses the same fast path spawn already relies on rather than adding a second
  lookup shape. Recommending (b), but leaving the actual choice to whoever implements this.
- 2026-08-16: Node opened, depends on [RM_FRO_018](RM_FRO_018_shirley.md) ("Shirley") directly —
  not also on [RM_FRO_010](RM_FRO_010_susan.md) ("Susan") in parallel, since Shirley already routes
  through her; naming Susan here too would be the exact convergence-bypass shape
  [BKHL_002](../tickets/BKHL_002_convergence-gate.md) forbids. This is the "new caller" half of
  Tier 1's minimum scope per [FrontierMode Operational
  Tiers](../wiki/plans/operational-tiers.md#the-tiers) — the other half
  ([RM_FRO_018](RM_FRO_018_shirley.md)) has to exist first since there's nothing to detect the
  defeat of otherwise. Full design: [Boss](../wiki/frontiermode/architecture/boss.md).

**The handler itself:** a `LivingDeathEvent` listener (wired the same way
`BorderModule.onBlockPlaced` is — a plain static method added via
`MinecraftForge.EVENT_BUS.addListener`, not an `@SubscribeEvent` instance method) that checks
whether the dying entity carries a `BossMobFixture` (via `RM_FRO_018`'s `MobJig`-scoped record) —
and, per the 2026-08-17 log entry above, doesn't stop there if it doesn't: falls back to
`MobScope.getFor(mob)` (safe here since the entity is loaded by definition — it just died) before
concluding it's genuinely not a tracked boss. If neither finds a match, no-op — most deaths in the
world aren't a tracked boss. If so: mark that boss's own `BossFixture` record defeated (`alive:
false`), then grow.

**Real gap found scoping this — Tier 1's defeat-triggered growth is mostly new-caller work, but
not purely. Settled 2026-08-24 (see the log entry above for the source-verified method shapes).**
`BorderAPI.addBorder()`
does accept an arbitrary center, confirmed — but it only touches the border *list*
(`BordersCrudFacet.applyProposal`), not the canonical `borderPath`; only
`BordersPathFacet.grow()` appends to the path, and `grow()` always computes its own center via
`DefaultBorderRules.chooseNextCenter()` (a random point near the *previous* border, not caller-
supplied) — it has no parameter for "center this on the boss's death location." Progression.md's
"centered on the defeated boss's home block" requirement can't be satisfied by calling `grow()`
alone, and calling `addBorder()` alone leaves the new border off the path (breaking
`getRelevant()`'s path/layerIndex assumptions the same way an unreconciled `fixLayers()` would —
see [RM_FRO_015](RM_FRO_015_margaret.md)).

**Ruled: a new path-aware method**, `BordersPathFacet.growCenteredOn(BlockPos center)` (backed by
`BorderLogic.growCenteredOn(Border previous, BlockPos center)`, exposed via
`BorderAPI.growCenteredOn(Level level, BlockPos center)`) — same two-step shape `grow()` already
has internally (rules-driven proposal via `BorderLogic`, then append to `borderPath`), but taking
an explicit center instead of deferring to `DefaultBorderRules.chooseNextCenter()`. Keeps
"create + path-append" atomic the way every other path-mutating call in `BordersPathFacet` already
is, rather than leaving two calls for every caller to remember to sequence correctly. The
alternative considered and rejected — a two-call sequence from this node's own handler
(`BorderAPI.addBorder(...)` then `borders.PATH.insert(...)`) — would have pushed the atomicity
requirement onto every future "grow to a specific point" caller instead of Border itself
guaranteeing it.

**Radius/layerIndex for the new border** should reuse `DefaultBorderRules.chooseNextRadius()`
(growth-factor curve) and `previous.layerIndex() + 1` exactly as `grow()` does today — only the
center is different, not the rest of the growth rule.
**[Rename note, 2026-08-20: `Border.layerIndex()` is now `Border.layer()` — renamed directly in
code by the project owner. Every `layerIndex` mention on this node (here, the path/layerIndex
assumption above, and the done bar's "`layerIndex` continues the existing sequence" below) means
the same current `layer()` accessor; left as originally written rather than rewritten throughout.]**

**Closes the loop:** after growth succeeds, this handler calls `BossAPI.createBoss(level, border)`
right after — the same paired call every border-creation site needs (see
[Boss § Defeat detection and the border-growth gap](../wiki/frontiermode/architecture/boss.md#defeat-detection-and-the-border-growth-gap)),
not a spawn call. It creates the record; it doesn't place the entity — that's `BOSS_JIG`'s own
tick's job, same as for any other unmaterialized record.

**Done bar:** real build + real play. Kill a tagged boss via ordinary combat, confirm: a new border
is created centered on the death location (not a random point), the new border is on the path
(`getRelevant()`/`@relevant` resolve correctly across old and new territory), `layer` continues the
existing sequence, and a new boss spawns in the new level without a manual trigger. **Also kill a
tagged boss via `/kill`** — per the 2026-08-24 log entry above, this handler's own
`LivingDeathEvent`-plus-`getFor`-fallback design should catch this identically to a combat kill,
which would satisfy Shirley's still-unmet `/kill`-self-heal wording; confirm this live rather than
assuming it from the vanilla/Forge behavior alone.

## Required By

*(computed — nothing depends on this yet)*
