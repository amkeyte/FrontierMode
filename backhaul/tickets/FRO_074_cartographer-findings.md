---
id: FRO_074
uid: FRO
number: 74
client: FrontierMode
status: open
title: Cartographer findings (running list)
context: Running log of drift/design findings from Cartographer's diagramming pass,
  appended to over time.
priority: normal
opened: '2026-09-03'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

Running log of drift/design findings surfaced by Cartographer's diagramming pass over
FrontierMode/Satchel. Each finding is a ruling request for the Architect (cross-module shape,
event flow), not something Cartographer edits or fixes directly. PM to split a finding into its
own ticket/roadmap node once it's scoped as real work, same pattern FRO_054 used for its own
sub-findings (FRO_058/059).

## Findings

### 1. Border/Boss level-bootstrap ownership is inverted relative to the codebase's own pattern

Surfaced by: `frontiermode/diagrams/border-module-shape.md` (module map) and a follow-up
signal-flow diagram of `BorderModule` (Scrapyard, not yet promoted).

`BorderModule.onBordersScopeLoaded` (subscribed to `BORDERS_JIG`'s `ScopeEvent.Loaded`,
server + overworld only) is the sole place a fresh level's first Border gets created *and* paired
with a Boss record -- it calls `BorderAPI.grow(level)` itself, then hands the result straight to
`BossAPI.createBoss(level, border)`. Border reaching into Boss's own API.

This is the *only* one of Border's three growth triggers where the calling direction goes this
way. The other two both have the triggering module reaching into Border, not the reverse:

- Block-placed trigger: `BordersTriggers.growPath` (Border's own code) calls `BorderAPI.grow()`.
- Boss-defeat trigger: `BossModule.onLivingDeath` calls `BorderAPI.grow(level, deathLocation)`
  then `BorderAPI.startPregeneration()` -- Boss reaching into Border via the facade, matching
  `border.md`'s stated direction ("Boss depends on Border, never the reverse").

Checked `BossModule.init()` directly: `BOSS_JIG` is wired only to `ScopeEvent.Tick`
(`onBossJigTick`), nothing to `Loaded`. There is no competing/redundant bootstrap hook on Boss's
side today -- this is a live, single-owner inversion, not a duplication bug, and not yet load-
bearing anywhere else, which is exactly the window to correct it before more code (Tier 2
discovery tools, per the roadmap) builds on the current direction.

**Ruling requested:** should `BossModule` register its own `ScopeEvent.Loaded` hook on
`BOSS_JIG` (or `BORDERS_JIG`, whichever scope is right) that calls `BorderAPI.grow(level)` and
creates its own Boss record from the result -- matching the direction both other growth triggers
already use -- rather than `BorderModule.onBordersScopeLoaded` reaching into `BossAPI` directly?

### 2. BorderPlayerBundle/BorderPlayerStatusFixture has no facet-resolver discipline

Surfaced by the same `BorderModule` signal-flow diagram.

`BordersFixture` (world-scoped) gets the full treatment `border.md`'s "Data model" section
documents: four named facets (`PATH`/`CRUD`/`RULES`/`INFO`), doc-comment discipline, "the fixture
itself is never handed to another module." `BorderPlayerBundle`/`BorderPlayerStatusFixture`
(player-scoped, `BORDER_PLAYER_JIG`) has no equivalent. Both the producer
(`BorderModule.onPlayerScopeTick`) and the reader (`BorderAPI.playerStatus()`) independently call
`jig.getOrCreate(scope, BORDER_PLAYER_BUNDLE)` / `bundle.getOrCreateFixture(...)` directly --
there's no dedicated resolved-facet accessor the way the world-scoped side has one.

**Ruling requested:** is this an accepted exception (derived, non-persisted, non-networked state,
per `border.md`'s "Known gaps" section already calling it "cheap to recompute"), or should it get
an equivalent facet treatment for consistency with `BordersFixture`?

### 3. Deprecate gold-block-placement path growth entirely -- superseded by boss-defeat growth

Surfaced by the same `BorderModule` signal-flow diagram, this time by what it shows already
sitting alongside the mechanism finding 1 is about.

The raw Forge listener wired in `BorderModule.init()`
(`MinecraftForge.EVENT_BUS.addListener(BorderModule::onBlockPlaced)`) still fires on every
`BlockEvent.EntityPlaceEvent`, calling `BordersTriggers.growPath(event)` ->
`DefaultBorderRules.growPathCriteria(level, pos, placed)` (true only for a placed
`Blocks.GOLD_BLOCK` within `GROWTH_RING_RADIUS + 1` of the path tip, or unconditionally true on an
empty path) -> `BorderAPI.grow(level)` on a pass.

`border-vocabulary.md`'s own "Path" section already calls this out as provisional, not a design
fixture: "the live growth trigger today is gold-block placement near the path tip
(`BordersTriggers.growPath`) -- **a debug-shaped stand-in**, not player progression in the sense
this definition means... They become the same thing once Boss (RM_FRO_018/019) lands and growth is
actually centered on a boss's death location, **not before**."

Boss has now landed -- finding 1 above confirms `BossModule.onLivingDeath` calls
`BorderAPI.grow(level, deathLocation)` on defeat, live and working. The "not before" condition the
wiki itself set for retiring gold-block growth has been met. Per the project owner: deprecate the
entire feature, not just de-prioritize it.

**Removal surface, for whoever picks this up** (not prescribing the fix, just naming what's
reachable from this trigger and nothing else): the `MinecraftForge.EVENT_BUS.addListener(...)`
call in `BorderModule.init()`, `BorderModule.onBlockPlaced`, `BordersTriggers.growPath`, and
`growPathCriteria` on both `BorderRules` (interface) and `DefaultBorderRules` (impl) --
`GROWTH_RING_RADIUS` likely goes with it if nothing else reads it. `border-vocabulary.md`'s own
"Aspirational vs. actual" paragraph about this trigger would need updating alongside the removal
-- Cartographer doesn't edit it, flagging so whoever does the removal knows the wiki reference
exists.

**Ruling requested:** confirm removal (project owner already has, via this ticket) and route to PM
for scheduling as Lead Dev work; not a design question the way findings 1-2 are.

### 4. BorderPregenFixture reaches back into BorderAPI.CRUD() from inside its own bundle -- verify if intentional

Surfaced by: `frontiermode/diagrams` follow-up, `BorderAPI`'s high-level IO diagram (Scrapyard,
not yet promoted).

`BorderPregenFixture` lives inside `BordersBundle`, as a sibling to `BordersFixture` itself --
same bundle, same scope, no facade needed to reach a sibling fixture in Satchel's own bundle
model. It nonetheless calls back out through `BorderAPI.CRUD()` (the public facade) rather than
any direct sibling-to-sibling bundle access. Everywhere else in this codebase, a fixture inside a
bundle either stays self-contained or is reached from *outside* the bundle via the facade --
`BorderPregenFixture` is the only case found so far of a fixture calling the facade from
*inside* its own bundle.

Flagging this at the "just verify" level, not a call for a ruling -- it's plausibly intentional
(pregen touching its sibling only through the same seam every external caller uses, so there's
one code path either way), but it's also exactly the shape a debugging-driven auto-connect or
copy-paste from an external caller would leave behind. Worth a quick confirm.

**Ruling requested:** is `BorderPregenFixture` calling `BorderAPI.CRUD()` from inside its own
bundle intentional (single code path for sibling access), or should it reach `BordersFixture`
directly since it's already inside the same bundle?

### 5. Add BorderMath to BorderAPI's surface, for consistency -- per project owner

Surfaced by: the same bypass-check diagram Finding 4 came from (Scrapyard, not yet promoted).

`BorderMath` (pure geometry helpers -- containment, distance, `randomPointInAnnulus`, all X/Z-only
per its own doc comment) is stateless, so reaching it directly isn't a facade violation the way
touching a fixture or bundle would be -- confirmed clean when checked for finding 4's diagram.
Even so, it's currently the one thing a cross-module consumer (`DefaultBossRules`) reaches in the
Border package without going through `BorderAPI` at all -- everything else Boss touches in Border
(`PATH`, `CRUD`, growth, pregen) has a `BorderAPI` entry point.

Per project owner: add `BorderMath`'s operations to `BorderAPI`'s own surface, so every
cross-module touch point into Border -- stateful or not -- goes through the one facade
consistently, rather than "state goes through BorderAPI, math doesn't."

**Ruling requested:** none -- this is a stated decision, not an open question. Route to PM for
scheduling as Lead Dev work: decide whether `BorderAPI` wraps/delegates to the existing
`BorderMath` methods or `BorderMath` moves under `BorderAPI` outright, and whether `BorderMath`
stays public afterward or drops to package-private once nothing external calls it directly.

### 6. Deprecate BorderCommandHandler.debugCreate() -- per project owner

Surfaced by: the same bypass-check diagram findings 4-5 came from (Scrapyard, not yet promoted).

`debugCreate()` (registered as `/border debug create`, alongside the ordinary `/border debug` ->
`debug()`) is the one place anything reaches past `BorderAPI`'s facet-resolver ready gate to the
raw `FrontierKeys.BORDERS_BUNDLE`/`BORDERS` jig entry -- through `BorderAPI.levelJig()` and
`BorderAPI.scope(level)`, both public `BorderAPI` methods with no other caller anywhere in the
codebase (confirmed by search). Self-documented in its own doc comment as deliberate: the normal
gated accessors collapse "bundle exists but Borders facet ABSENT" and "not ready yet" into one
`Optional.empty()`, and this command needed to tell those two apart.

Per project owner: deprecate it.

**Removal surface, for whoever picks this up** (not prescribing the fix, just naming what's
reachable from this command and nothing else): the `.then(Commands.literal("create")....)` node
under `debug()` in `BorderCommands.java`, `BorderCommandHandler.debugCreate()` itself, and --
since `debugCreate()` is currently their only caller -- `BorderAPI.levelJig()` and
`BorderAPI.scope(Level)` become dead surface once it's gone; whoever removes this should decide
whether those two go with it or stay as a lower-level accessor for future debug tooling. The
plain `/border debug` -> `BorderCommandHandler.debug()` command is untouched by this -- it goes
through the ordinary `BorderAPI.CRUD()` gate and isn't part of this finding.

**Ruling requested:** none -- stated decision. Route to PM for scheduling as Lead Dev work.

### 7. Deprecate BorderAPI.grow(Level) in favor of grow(Level, BlockPos) -- per project owner, fallout not yet resolved

Surfaced by: the `forceDefeat` -> materialized-boss trace (Scrapyard, not yet promoted) and a
follow-up read of `BorderAPI.java` directly.

`BorderAPI` carries two `grow` overloads: the no-center `grow(Level)` (a thin wrapper over
`BordersPathFacet.grow()`) and `grow(Level, BlockPos)` (an explicit-center overload, added for
RM_FRO_019/"Karen" -- Boss's defeat handler was its first consumer). Per project owner: deprecate
the no-center form, standardize on the explicit-center one.

**Real callers of `grow(Level)` today** (grep-verified, all three real, none comment noise):

- `BorderCommandHandler.pathGrow` (`/border grow` command) -- **no natural center available at
  this call site today.** The command takes no coordinate argument and there's no player-position
  or path-tip value being passed in already; migrating this caller means either adding an argument
  to the command, defaulting to something (the path's current tip? the issuing player's position?
  project owner's call), or leaving this one call site as a sanctioned exception to the
  deprecation.
- `BorderModule.onBordersScopeLoaded` -- the level-bootstrap call site finding 1 (above) is
  already asking the Architect to rule on. If that ruling moves this call into `BossModule`
  instead (or removes it from `BorderModule` entirely), its `grow(Level)` call moves or disappears
  with it -- **this finding shouldn't be actioned independently of finding 1's ruling landing
  first**, or it risks migrating a call site that's about to move anyway.
- `BordersTriggers.growPath` -- finding 3 (above) already calls for removing this entire trigger.
  Once that lands, this call site is gone, not migrated. **Also shouldn't be actioned before
  finding 3 resolves**, same reasoning.

Net: once findings 1 and 3 land, `pathGrow` may be the *only* real remaining `grow(Level)` caller
-- but that's not confirmed, since neither ruling has landed yet. Sequencing, not just fallout,
is the open question here.

**Ruling requested:** none on the deprecation itself -- stated decision. What needs deciding
before execution: (a) whether this is sequenced after findings 1 and 3 land, per the reasoning
above, and (b) what `pathGrow` passes as a center once the no-arg form is gone. Route to PM for
scheduling once those are settled.

### 8. BossModule has become a grab-bag -- find real homes for most of what's hanging off it

Surfaced by: reading `BossModule.java` in full while tracing finding 7 and the `forceDefeat` ->
materialized-boss diagram (Scrapyard, not yet promoted). Same shape of concern the original
`BorderModule` signal-flow diagram raised about `BorderModule` itself, at the start of this
ticket -- Boss has now grown into it too.

707 lines, 18 methods, spanning at least seven genuinely separate concerns, all sitting as static
methods on one class:

- **Two independent jig lifecycles wired here**: `registerBossJig()` (`BOSS_JIG`, `LevelScope`,
  persisted `BossFixture`) and `registerBossMobJig()` (`BOSS_MOB_JIG`, `MobScope`, live
  `BossMobFixture`) -- different scopes, different policies, different event shapes, both
  registered and handled in the same class.
- **The `BOSS_JIG` tick pipeline**: `onBossJigTick`, `resolveHomeBorder`,
  `finalizeUnpositionedBosses`, `materializeUnresolvedBosses` -- position-selection and
  materialization *orchestration* (gating, sequencing, fixture writes) all live here, even though
  the actual algorithms are correctly already split out into `DefaultBossRules`.
- **Defensive reconciliation/logging**: `reconcilePathAgainstBossRecords` -- a background data-
  integrity watchdog, logically unrelated to either the tick pipeline or the mob-scope handling it
  sits next to.
- **Interest-registry bookkeeping**: the `INTERESTS` map, `addInterest`, and the
  `MobInterestRegistry.register(...)` call in `registerBossMobJig` -- plumbing for a different
  subsystem (mob presence polling) than anything else in the class.
- **`BOSS_MOB_JIG` scope-attach handling**: `onBossMobScopeLoaded`/`onBossMobScopeUnloaded`.
- **The defeat-detection cascade**: `onLivingDeath`, `resolveBossId`, `existingBossId` -- a raw
  Forge event listener plus its own resolution helpers.
- **An admin escape hatch**: `forceMaterialize`, mirroring the tick pipeline's own materialize
  step on demand for `/boss mob spawn`.
- Plus the navigator-resolver registration (finding-adjacent, already covered by the bypass-check
  diagram) and command-registration delegation.

Per project owner: most of this needs a real home elsewhere rather than continuing to accumulate
on `BossModule`. Not naming destinations here -- that's the ruling being asked for, not something
Cartographer prescribes.

**Ruling requested:** which of the above groupings warrant their own class/file (a tick-pipeline
orchestrator, a mob-scope handler, a defeat-cascade listener, interest-registry plumbing kept
separate from jig registration, etc.), versus which are fine staying put. Same caution finding 1
already raised applies here too -- whatever lands for finding 1's bootstrap-ownership ruling may
itself relocate some of this (e.g. if `BossModule` gains a `ScopeEvent.Loaded` hook on `BORDERS_JIG`
per that finding, that's more surface added to the same class this finding is asking to shrink).

## Log

- 2026-09-03: Ticket opened; findings 1-2 filed from the Border module-shape and signal-flow
  diagrams.
- 2026-09-03: Finding 3 added -- deprecate gold-block path growth entirely, per project owner.
- 2026-09-03: Finding 4 added -- verify BorderPregenFixture's use of BorderAPI.CRUD() from inside its own bundle.
- 2026-09-03: Finding 5 added -- add BorderMath to BorderAPI's surface for consistency, per project owner.
- 2026-09-03: Finding 6 added -- deprecate BorderCommandHandler.debugCreate(), per project owner.
- 2026-09-03: Finding 7 added -- deprecate BorderAPI.grow(Level) in favor of the BlockPos overload, per project owner; fallout/sequencing against findings 1 and 3 flagged, not yet resolved.
- 2026-09-03: Finding 8 added -- BossModule has become a grab-bag (707 lines/18 methods/7+ concerns), find real homes for most of it, per project owner.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
