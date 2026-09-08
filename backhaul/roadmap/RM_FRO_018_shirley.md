---
id: RM_FRO_018
uid: RM_FRO
number: 18
kind: work
status: resolved
title: Boss entity/spawn system
owner: Arryn
depends_on:
- RM_FRO_010
created: '2026-08-16'
superseded_by: null
ticket: FRO_043
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_FRO
<!-- bh-header:end -->

## Boss entity/spawn system

**Prerequisite satisfied — [RM_SAT_021](RM_SAT_021_frank.md) ("Frank," `MobJig`/`MobScope`) is
`resolved` as of 2026-08-22, so this node is now genuinely startable, not just graph-actionable.**
Everything this design leans on Satchel for now exists and is dedicated-server verified:
`MobScope.getFor(mob)` as the boss-tagging entry point (contract on
[MobScope.getFor() Contract](../wiki/satchel/spec/mobscope-getfor.md)), `MobJig`'s foundation-pulse
presence poll, and the `MobJig`-scoped fixture shape `BossMobFixture` needs. Build against
[Jig & Scope Runtime § MobJig](../wiki/satchel/architecture/runtime.md#mobjig), not Frank's own log.

**Architect prep: [FRO_042](../tickets/FRO_042_shirley-prep.md), items 1-3 done.**
[Boss](../wiki/frontiermode/architecture/boss.md) now describes `BossModule`'s real registration
mechanism (`MobInterestRegistry.register(...)`, a call separate from `MobJigConfig`), is promoted to
`verified`, and the `getInitial()` call site this node's own 2026-08-18 entry flagged as untraced is
now traced with its bootstrap gap resolved to a settled design — see the 2026-08-23 entry below.
Item 4 (promoting the `getFor` spec page) is still open, not blocking this node.

*Name note: "Shirley" also named RM_FRO_014, a Tier 0 convergence deleted 2026-08-16 — so a bare
"Shirley" in older prose may mean either node. Always pair the name with its ID. Reusing a retired
node's persona is no longer done; see [BHRM — Roadmap Conventions](../wiki/meta/bhrm.md).*

- 2026-08-24: **Resolved by PM, after verifying FRO_043's close claims directly against
  source.** Read `BorderModule.java`/`BossModule.java`/`FrontierMode.java` and the reobf
  build log on the project owner's own machine, not just the ticket's own account: the
  client-side guard (`Satchel.require().side() == LogicalSide.CLIENT`, right after the
  jig-key check in `onBordersScopeLoaded`) is really there, matching the crash-fix
  claim exactly; `BossModule.init()` is really called from `FrontierMode.java` (the
  SAT_035-style gotcha didn't repeat); `MobInterestRegistry.register(BOSS_MOB_JIG, ...)`
  is really wired alongside `Satchel.registerJigConfig(config)` with an explicit
  `sideApplicability(SERVER)`; the reobf jar's own log lists the compiled `boss/`
  classes. Real, not read-through-only.

  Marking `resolved` rather than leaving `open`: three of the done bar's four bullets
  are real-playtest-verified (fresh-level bootstrap, recognizable tagged mob,
  `BossFixture`/`BossMobFixture` agreement), and the fourth (`/kill` self-heal) is
  accepted as unmet by the project owner's own explicit call, with the gap owned
  outright by [RM_FRO_019](RM_FRO_019_karen.md) ("Karen")'s own scope rather than left
  dangling — same shape [FRO_031](../tickets/FRO_031_betty-donebar.md) used to resolve
  Betty with two unconfirmed items tracked separately. No new tracking ticket opened
  for the gap here since Karen already owns it by design, per `boss.md`'s own "Defeat
  detection and the border-growth gap" section — a second ticket would just duplicate
  that ownership.

  **Note on the done bar's own wording, added closing out FRO_043:** "bootstrap
  catch-up" below still describes bullet 4's original design above — a recurring
  `BORDERS_JIG` tick check against the path-tip border, claimed there to self-heal a
  `/kill` even without a `LivingDeathEvent` listener. `boss.md` walked that back to the
  one-shot `seeded`-flag bootstrap FRO_043 actually built, explicitly punting self-heal
  to [RM_FRO_019](RM_FRO_019_karen.md) as an open design question rather than inventing
  a heuristic. Nothing updated this done bar's text to match that walk-back at the time
  — it still promises more than the settled design delivers on its own. Left as-is here
  rather than silently rewritten, per this project's "don't rewrite history" convention;
  treat the gap as known, not as a regression, until either RM_FRO_019 closes it for
  real or the done bar itself is deliberately revised.

- 2026-08-24: **Lead Dev build ticket opened: [FRO_043](../tickets/FRO_043_boss-build.md).**
  Architect prep ([FRO_042](../tickets/FRO_042_shirley-prep.md)) closed — `boss.md` and the
  `getFor` spec page both `verified` — and both Satchel prerequisites this node leans on
  ([RM_SAT_021](RM_SAT_021_frank.md) "Frank", [RM_SAT_022](RM_SAT_022_roger.md) "Roger") are
  `resolved` and live-verified. `ticket:` field set to FRO_043.

- 2026-08-23: **`getInitial()` traced, and the real gap resolved: nothing auto-bootstraps a fresh
  level's first border — not a hidden call site.** `getInitial()` has exactly one call site in the
  entire codebase — `BordersPathFacet.grow()`'s own empty-path branch, which already falls through
  to it correctly (restored by [RM_FRO_015](RM_FRO_015_margaret.md)'s "pathGrow's backwards
  empty-path bootstrap guard" fix). So the 2026-08-18 entry below's "flagged for Lead Dev to
  confirm" is answered: there's no separate level-bootstrap call site to find, because nothing
  currently calls `grow()` on level load at all — and `isEmpty()` alone can't tell "fresh world"
  from "admin removed every border," so an automatic hook can't just check emptiness.
  **Settled design (project owner):** a new persisted `seeded` boolean on `BordersFixture`, set once
  inside `BordersPathFacet.grow()`'s own append (covering every caller uniformly, cleared by
  nothing), plus a new `BorderModule` subscription to Satchel's own `ScopeEvent.Loaded` (not a raw
  Forge listener — `LevelJig` already fires it, via `ServerForgeIngress.onLevelDiscover`), filtered
  to `LevelJig`'s key and the overworld dimension, calling `BorderAPI.grow(level)` only when
  `!seeded`. This delivers exactly the property project owner asked for directly: "a Path may become
  empty when all its borders are removed, but that does not immediately call for a `getInitial()`
  call in and of itself... this leaves the admin in control of the situation" — removal never
  re-triggers auto-creation; only a later real `grow()` call (organic or admin) repopulates the
  path. **This is also where this node's own paired boss-record-creation call for a level's first
  border belongs** — the same `ScopeEvent.Loaded` hook, right after `BorderAPI.grow(level)`
  succeeds for a level's very first border, extracting `position`/`layer` from the `Border` it
  returns exactly as the 2026-08-18 entry below already describes for `growCenteredOn()`'s
  post-defeat case. Design only, not yet built. Full detail on both pages:
  [Border § Known gaps](../wiki/frontiermode/architecture/border.md#known-gaps) and
  [Boss § Defeat detection and the border-growth gap](../wiki/frontiermode/architecture/boss.md#defeat-detection-and-the-border-growth-gap).
- 2026-08-22: **Frank landed — this node is unblocked.**
  [RM_SAT_021](RM_SAT_021_frank.md) is `resolved`, built and verified against a real dedicated
  server (see [SAT_035](../tickets/SAT_035_mobjig-build.md)). The standing note above is updated to
  match; the ordering entry below is left as written.
- 2026-08-21: **Ordering ruled: Frank first.** Project owner's call — Satchel's `MobJig`/`MobScope`
  lands before any FrontierMode-side boss work starts. Recorded as the standing note above rather
  than only here, and mirrored onto [RM_SAT_021](RM_SAT_021_frank.md) so the scheduling is visible
  from the Satchel side too. Nothing about this node's design changes as a result; it is a
  sequencing decision, not a technical one.
- 2026-08-18: **Largest correction yet: `BossFixture` is fully decoupled from `Border` — not keyed
  by `Border` UUID, no live reference at all — driven by project owner's own correction of their
  own wording mid-discussion: "the boss is created at the same time as a border, but not tied to
  it."** Previously `BossFixture` was described as "keyed by `Border` UUID," and record creation
  rode `BORDERS_JIG`'s tick asking "does the path-tip have a boss" — both implied a standing
  structural coupling to `Border`/the path that project owner rejected on two grounds: not every
  `Border` is on the path, so nothing about boss logic should reference path position at all
  (only `layerIndex`, for stat scaling); and a live coupling makes a future hand-placed
  special-event boss (fixed position, fixed level, no real `Border` involved) needlessly hard,
  when it should just be "insert the same record shape a different way."
  - **New shape:** `BossFixture` is a self-contained collection of records,
    `{bossId, position, level, bossEntityId, alive}` — `level` a plain copied number from
    `layerIndex()` at creation, never re-read from a `Border` afterward.
    **[Vocab note: "level" here is now named `layer` per [Border
    Vocabulary](../wiki/frontiermode/architecture/border-vocabulary.md), fixed on
    [Boss](../wiki/frontiermode/architecture/boss.md) itself via
    [FRO_029](../tickets/FRO_029_border-vocab-conformance.md) — this log entry is left as
    originally written, not restated.]**
  - **New trigger:** creation is **not tick-driven at all** anymore — it's a direct call, paired at
    every real border-creation call site (`growCenteredOn()`'s post-defeat caller in
    [RM_FRO_019](RM_FRO_019_karen.md), and `getInitial()` for a level's first border — the latter's
    actual call site wasn't traced this pass, flagged for Lead Dev to confirm). Project owner's own
    reasoning for why this is safe rather than fragile: "the path tip is created by growing it...
    if not we'd be suffering a significant data bug, not an edge case" — i.e. a missing boss record
    is no longer something to eventually-consistently paper over, it's something to detect and log
    as corruption, via a reconciliation check correlating the path's `layerIndex` set against
    `BossFixture`'s `level` set (level number as the join key, still no live `Border` reference).
  - **Materialization is now on `BossFixture`'s own tick** (`BOSS_JIG`, a dedicated
    `LevelJigConfig`), not borrowed from `BORDERS_JIG` — a direct consequence of the bundles being
    fully independent per the 2026-08-17 `BossBundle` entry below.
  Full detail: [Boss](../wiki/frontiermode/architecture/boss.md#data-model--a-persisted-record-and-a-live-view-not-two-peers)
  and [Boss § Defeat detection and the border-growth gap](../wiki/frontiermode/architecture/boss.md#defeat-detection-and-the-border-growth-gap).
- 2026-08-18: **Real design fix, not just a wording correction: the previous entry's `isLoaded`
  retry-with-a-fresh-candidate loop was solving the wrong problem — caught by project owner asking
  directly why chunk-loading needs to be checked at all when picking a boss's position, since "the
  boss mob gets a random spot in the new border disk" and that has nothing to do with spawning a
  mob.** Correct: choosing *where* (a random XZ column in the disk) and choosing *when* (waiting
  for that column to be loaded so the entity can actually be placed) are two separate steps, not
  one fused "keep re-rolling until something's loaded" loop — the re-rolling version silently
  biased the boss toward already-loaded (i.e. already-near-a-player) territory, which isn't what
  "random coordinate somewhere within the new level's area" means. `BossFixture` now carries
  `position` (chosen once, instantly, no chunk state involved) alongside a **nullable**
  `bossEntityId` (null until the fixed position happens to load and the entity actually gets
  placed there — never re-picked). Y still can't be resolved until the chunk loads, which is
  fine — it's resolved at materialization time, not at position-choosing time.
  Full detail: [Boss](../wiki/frontiermode/architecture/boss.md#data-model--a-persisted-record-and-a-live-view-not-two-peers).
- 2026-08-18: **Deeper correction to the two entries below: post-growth spawn was never a
  distinct case at all — checked against [Progression & Frontier
  Mechanics](../wiki/frontiermode/design/progression.md#the-core-loop)'s actual wording and the
  real `DefaultBorderRules.chooseNextCenter()`/`BordersPathFacet.grow()` source, at project
  owner's prompting.** The design doc says the new boss spawns "at a random coordinate somewhere
  within the new level's area" — not at or near the death location. The death location only
  decides where the new `Border` itself is centered (confirmed via `chooseNextCenter()`: a
  geometry-only concern, already tracked separately as the `growCenteredOn()` gap above) — it has
  nothing to do with where the boss mob ends up. So post-growth has exactly the same positioning
  problem bootstrap catch-up does, not a special guaranteed-loaded case. Also simplified the
  trigger condition itself: not "bootstrap vs. self-heal vs. post-growth" as three scenarios, just
  one rule — any time `BossFixture` says a border should have a live boss and doesn't, spawn one;
  those three are occasions that satisfy it, not separate rules. One position algorithm, one
  trigger condition, called from two places (the tick check, and an eager direct call after
  growth). Full detail: [Boss](../wiki/frontiermode/architecture/boss.md#spawn-algorithm--a-pluggable-strategy-mirroring-borderrules).
- 2026-08-17: **Position algorithm's `isLoaded` check (previous entry) was written with a "post-growth spawn doesn't need it" carve-out — that carve-out was wrong, caught by project owner
  pointing out the post-growth case still samples a random point across the *new* border's disk,
  not the exact death-location point itself.** The center being where the player just stood only
  guarantees that one point is loaded — a uniform sample elsewhere in the new disk isn't
  automatically loaded too, since growth radius isn't bounded by view/simulation distance.
  Corrected: both spawn triggers now run the identical sample-then-check-then-retry loop, no
  special case. Post-growth should converge in one or two attempts in practice (the area right
  around the death location is definitionally loaded) but doesn't get to skip verifying it.
  Full detail: [Boss](../wiki/frontiermode/architecture/boss.md#spawn-algorithm--a-pluggable-strategy-mirroring-borderrules).
- 2026-08-17: **Real gap found: the bootstrap catch-up spawn had no answer for "spawn it where,
  exactly," given the design's own central fact that a candidate position is usually unloaded.**
  Project owner asked directly: "checking if it exists will always return 'no' unless we trigger
  the spawn — how are we telling Forge to spawn the mob when it's in range?" Correct on both
  counts — nothing spawns a boss but `BossModule` itself calling the vanilla spawn API explicitly
  (Forge doesn't do this for us), and the original "Position" strategy (uniform sample across the
  whole border disk) would usually pick a point in an unloaded chunk with nothing to place an
  entity into. **Fix:** sample only from the intersection of the level's currently loaded/ticking
  chunks and the border disk; an empty intersection is a normal no-op, retried next
  `BORDERS_JIG` tick, not an error. Doesn't apply to the post-growth spawn trigger — that border is
  centered on the just-defeated boss's death location, guaranteed near the triggering player.
  Full detail: [Boss](../wiki/frontiermode/architecture/boss.md#spawn-algorithm--a-pluggable-strategy-mirroring-borderrules).
- 2026-08-17: **`BossFixture` gets its own bundle, not a slot on `BordersBundle` — caught by
  project owner asking "is `BordersBundle` still the right name for this."** Earlier drafts of
  [Boss](../wiki/frontiermode/architecture/boss.md) described `BossFixture` as "sibling to
  `BordersFixture` on `BordersBundle`." That's the exact anti-pattern this project already ruled
  out for per-player border state — see [RM_SAT_020](RM_SAT_020_jerry.md)/
  [RM_FRO_006](RM_FRO_006_sandra.md)'s explicit rejection of hosting unrelated state on
  `BordersBundle` as "the dumping ground every future module reaches for," with that same
  discussion naming "mob bosses" specifically as a case `Scope`'s generalization exists to cover.
  Corrected: a new level-scoped `BossBundle`, registered by `BossModule.init()` itself, the same
  "one module, more than one bundle" shape `BorderPlayerBundle` already establishes for
  `BorderModule.init()`. Full detail: [Boss](../wiki/frontiermode/architecture/boss.md#data-model--a-persisted-record-and-a-live-view-not-two-peers).
  Promoted to a standing item on [New Module Checklist](../wiki/satchel/architecture/new-module-checklist.md)
  (item 7) since this is the second time it's come up against the same target — future modules
  shouldn't have to rediscover it a third time.
- 2026-08-17: **Follow-up question, answered: `BossMobFixture` needs a bundle wrapper too
  (`BossMobBundle`, `MobScope`) — but for an ordinary reason, not the checklist-item-7 mistake
  above.** `JigConfigValidator` requires the `FixtureDecl` → `BundleDecl` → `Schema` shape for
  every fixture regardless of how many fixtures end up in one bundle (`TrackingModule`'s single
  `TRACKER` fixture gets its own dedicated `BUNDLE` the same way). The difference from the
  `BossFixture`/`BordersBundle` case above: there's no pre-existing bundle on `MobScope` to be
  tempted to fold into — `MobJig` is new infrastructure with nothing else registered against it —
  so this is just the normal registration shape, not a second near-miss.
- 2026-08-17: **`MobJig`'s design pivoted a second time since the entry below — see
  [RM_SAT_021](RM_SAT_021_frank.md)'s 2026-08-17 log entry for the full reasoning.** The trigger:
  a boss's chunk is essentially never loaded at the moment its existence needs checking — that's
  the normal state, not an edge case — so `MobJig` ingress is now poll-driven (a
  `foundationLifecycle().pulse()` reconciliation step calling `Level.getEntity(UUID)` against a
  per-consumer interest supplier), not event-driven. Two corrections that land directly on this
  node's own points below:
  - **Point 5's persistence bullet was wrong — only `BossFixture` needs persistence.**
    `BossMobFixture` is the *live* view, populated by the poll (or by `getFor` as a spawn-time fast
    path) only while the entity is confirmed present, and is deliberately **not** persisted — same
    "cheap to recompute, don't bother saving it" shape `BorderPlayerStatusFixture` already
    established. `BossFixture` alone is the durable record and is the only one needing
    `capabilities(true, ...)` + `policies().persistence(...)`.
  - **Point 4's "tick-driven check" and `MobJig`'s new presence poll are two different mechanisms
    on two different tick paths, not one thing.** The bootstrap catch-up in point 4 below ("does
    the path-tip `Border` have a live boss recorded in `BossFixture`") rides the *existing*
    `ScopeEvent.Tick` for `BORDERS_JIG` — it's a question about the record, answerable without
    touching any entity. `MobJig`'s presence poll is a separate question ("is the recorded boss's
    entity actually here right now") and is new Satchel-side machinery, not something this node
    builds itself. Full detail: [Boss](../wiki/frontiermode/architecture/boss.md#three-questions-three-different-mechanisms)
    (renamed from "two different tick-driven questions" per the 2026-08-18 entry above — record
    creation is no longer tick-driven at all).
- 2026-08-16: **`MobJig`'s design refined since this node opened — see
  [RM_SAT_021](RM_SAT_021_frank.md)'s own log.** Nothing here changes as a result (this node never
  named `LivingEntity` or a specific Satchel-level API), but two points worth carrying forward:
  the entry point for tagging a boss is `MobScope.getFor(mob)`, not a `Satchel`-level method (see
  [Boss](../wiki/frontiermode/architecture/boss.md)'s "Spawn algorithm" section), and
  `MobJigConfig` ships with no default `sideApplicability` — `SERVER` here is this node's own
  explicit choice, not inherited.
- 2026-08-16: Node opened, sibling of [RM_FRO_019](RM_FRO_019_karen.md) under
  [RM_FRO_017](RM_FRO_017_donna.md) ("Donna," Tier 1), depends on
  [RM_FRO_010](RM_FRO_010_susan.md) ("Susan," Tier 0) directly per the convergence-gate convention
  (see [BKHL_002](../tickets/BKHL_002_convergence-gate.md)). No boss code exists anywhere in
  FrontierMode source today (confirmed via grep) — genuinely greenfield, not a hardening pass over
  something half-built. Full design: [Boss](../wiki/frontiermode/architecture/boss.md).

**Scope, per [FrontierMode Operational Tiers](../wiki/plans/operational-tiers.md#the-tiers):** a
boss exists somewhere in the current level's cylinder and can be found by ordinary exploration —
no discovery aids (Tier 2) required yet. Concretely:

**[Vocab note: the "Scope" section below (points 1-5) predates [Border
Vocabulary](../wiki/frontiermode/architecture/border-vocabulary.md) (2026-08-18) and still uses
"level" in several places where the vocabulary now says `layer` (a Border's immutable sort key)
or plain `Border`. Left as originally written per this project's "don't rewrite history"
convention — [Boss](../wiki/frontiermode/architecture/boss.md) itself already reflects the
corrected terms; treat that page as authoritative over this node's own prose. See
[FRO_029](../tickets/FRO_029_border-vocab-conformance.md).]**

**[Rename note, 2026-08-20: separately, `Border.layerIndex()` is now `Border.layer()` — renamed
directly in code by the project owner, after everything on this node was written (including the
2026-08-18 log entries above and point 3 below, both of which say `layerIndex`).
[Boss](../wiki/frontiermode/architecture/boss.md) already reflects the current name; build against
that page, not this node's own wording.]**

1. **Vanilla mob reuse, not new entity types** (project owner's call, 2026-08-16) — a boss is a
   tagged instance of an ordinary vanilla mob, stats scaled by level. Progression.md's own level-1
   example ("something as unthreatening as a rabbit") already assumes this. Boss variety/tells is
   explicitly Tier 2/3 scope, not this node's.
2. **Data model — two fixtures, not one**, using the new [RM_SAT_021](RM_SAT_021_frank.md)
   ("Frank," `MobJig`) alongside the existing `LevelJig`: a level-scoped `BossFixture` (canonical
   per-`Border` record — which entity, alive/defeated) for "what's the boss for level N" queries,
   and a `MobJig`-scoped `BossMobFixture` on the boss entity itself (reverse pointer — which
   `Border` this entity belongs to) so [RM_FRO_019](RM_FRO_019_karen.md)'s defeat handler can
   answer "is this dying entity a tracked boss" in O(1) from the entity alone, without scanning
   every border. See [Boss](../wiki/frontiermode/architecture/boss.md) for the full shape.
3. **Spawn algorithm as a pluggable strategy**, mirroring `BorderRules`/`DefaultBorderRules`'
   existing shape exactly (`BossRules` interface, `DefaultBossRules` "safe baseline, replace
   later" implementation) — position: uniform-in-disk sample within the target `Border`'s
   `center()`/`radius()`, snapped to a valid ground position; mob type/stat scaling by
   `layerIndex`: a placeholder table, not a locked balance curve — real curve-tuning is Game
   Designer/playtest territory, same as `DefaultBorderRules.GROWTH_FACTOR`'s own "safe baseline"
   framing.
4. **Bootstrap catch-up, not a precise creation hook — and a distinct mechanism from `MobJig`'s own
   presence poll (see the 2026-08-17 log entry above).** Rather than trying to hook every possible
   border-creation call site (block-placement growth, command growth, and RM_FRO_019's new
   defeat-triggered growth), a check riding `BORDERS_JIG`'s existing `ScopeEvent.Tick` — "does the
   path-tip `Border` have a live boss recorded in `BossFixture`? If not, spawn one" — covers level
   1's bootstrap and every subsequent level uniformly, and self-heals if a boss entity is ever lost
   by other means (e.g. `/kill`) without a matching `LivingDeathEvent`.
   [RM_FRO_019](RM_FRO_019_karen.md) marks a boss defeated *and* triggers this spawn for the
   newly-created next border in the same handler, so the catch-up check is a safety net, not the
   primary path. This is separate from, and doesn't replace, `MobJig`'s own foundation-pulse
   presence poll — that one answers whether the *entity* for an already-recorded boss is currently
   resolvable, not whether a record should exist at all.
5. **Persistence footgun to preempt, not rediscover: only `BossFixture` needs it.** `BossFixture`
   holds identity state that can't be cheaply recomputed and must survive a restart — needs
   `capabilities(true, ...)` **and** the matching `policies().persistence(...)` call, the exact
   two-call requirement [Border](../wiki/frontiermode/architecture/border.md#runtime-wiring)'s own
   "Runtime wiring" section documents Border having gotten wrong once already
   ([FRO_014](../tickets/FRO_014_border-persistence-crash.md)). `BossMobFixture`, by contrast,
   should **not** be persisted — it's the live, poll-fed view (see point 2 and
   [Boss](../wiki/frontiermode/architecture/boss.md#data-model--a-persisted-record-and-a-live-view-not-two-peers)),
   the same "cheap to recompute, don't bother saving it" shape `BorderPlayerStatusFixture` already
   established. An earlier version of this bullet said both fixtures needed persistence — that was
   wrong; corrected 2026-08-17.

**Done bar:** real build + real play. At minimum: a fresh level gets a boss within its cylinder
without any command/trigger, the boss is a recognizable tagged vanilla mob, `BossFixture`/
`BossMobFixture` agree with each other (no orphaned record on either side after a spawn), and a
`/kill`ed boss triggers the bootstrap catch-up to respawn one (proving the self-heal path, not just
the happy path) — full defeat → growth confirmation is [RM_FRO_019](RM_FRO_019_karen.md)'s own
done bar, not this node's.

## Required By

<!-- required-by:start -->
- [**RM_FRO_019**](RM_FRO_019_karen.md) — Boss defeat border-growth caller
<!-- required-by:end -->
