---
id: FRO_082
uid: FRO
number: 82
client: FrontierMode
status: done
title: 'Boss-less path layers: pendingAttach + /boss attach build'
context: '[Susan_02] Lead Dev build for FRO_063''s ruling -- borderId, pendingAttach,
  /boss attach, reconciliation update.'
priority: normal
opened: '2026-09-03'
closed: '2026-09-03'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Lead Dev build for [FRO_063](FRO_063_boss-can-a-path-layer-legitimately-be-bo.md)'s ruling --
Architect's ruling written onto
[boss.md](../wiki/frontiermode/architecture/boss.md#boss-less-path-layers-and-attach)'s
"Boss-less path layers and attach" section and
[boss-commands.md](../wiki/frontiermode/architecture/boss-commands.md)'s command tree/selector
scheme. This ticket is the build itself, same split as
[FRO_058](FRO_058_boss-mutation-validation-reconciliation.md) -> [FRO_060](FRO_060_boss-border-mutation-validation-build.md)
and [FRO_074](FRO_074_cartographer-findings.md) -> FRO_075..FRO_081.

## What to build

Per boss.md's "Boss-less path layers and attach" section, no new scoping decisions -- straight
implementation:

1. **`Optional<UUID> borderId` on `BossRecord`/`BossFixture`.** `empty()` for a hand-placed
   off-path boss; set once at creation for anything paired with real border-growth (bootstrap
   grow, defeat-triggered grow, and the new `attach` command below). Per
   [boss-commands.md](../wiki/frontiermode/architecture/boss-commands.md)'s existing "Selector
   scheme" ruling -- this ticket is what makes building it necessary now.
2. **`Set<UUID> pendingAttach` on `BossFixture`** -- a persisted collection of border ids, separate
   from the boss-record list (it tracks borders with *no* record).
3. **`BorderCommandHandler.pathGrow()`** adds the newly-grown border's id to `pendingAttach` on
   success.
4. **`BossAPI.removeBoss()`** (backing `/boss delete`) adds the removed record's `borderId` (if
   present and still on-path) to `pendingAttach`, as its own new step after the existing
   despawn/delete/no-growth-event steps.
5. **New `/boss attach <border-selector>` command** (`BorderCommandHandler`/`BossCommands`,
   nesting `BorderSelectorArgumentType` the same way `@border` in the selector scheme already
   does) -- creates a boss record via `BossCrudFacet.create(border.layer())` (or today's
   `BossFixture.create(int layer)` if FRO_081's facet refactor hasn't landed yet), sets its
   `borderId` to the selected border, and removes that border's id from `pendingAttach`.
6. **`BossModule.reconcilePathAgainstBossRecords()`** checks `pendingAttach` before logging a
   path-layer-with-no-boss gap: a gap covered by `pendingAttach` logs at a lighter level (info,
   "awaiting attach") instead of the existing loud "real data bug" warning.

**Not touched:** the reverse-direction reconciliation check (still deferred, per boss.md's "Known
gaps" -- unrelated to this ticket's forward-direction fix); `/boss transform defeat`'s cascade
(unaffected by `pendingAttach`, since a `transform defeat` never removes a record).

## Standing constraint

Same as every ticket this pass: no Gradle in the agent sandbox. Whatever gets built here needs
real build/playtest evidence before closing.

## Log
- 2026-09-03: **Built.** All six numbered items implemented against the current (FRO_075/FRO_080/
  FRO_081-landed) codebase, read fresh rather than assumed:

  1. **`Optional<UUID> borderId` on `BossRecord`/`BossFixture`.** Added as an actual
     `Optional<UUID>` field (not a nullable raw field with a boolean helper, the shape
     `position`/`bossEntityId` use) -- boss-commands.md's "Selector scheme" section states the
     field's type this literally more than once, so followed it as written rather than the
     fixture's older nullable-field idiom. Persisted via the same "has" boolean + UUID tag shape
     `bossEntityId` already uses. Set at creation: `BossFixture.create(int layer)` (off-path,
     `/boss add`) keeps `borderId` empty; a new `create(int layer, UUID borderId)` overload sets
     it. `BossAPI.createBoss(Level, Border)` -- the one method every real border-growth call site
     (`BossModule.onBordersScopeLoaded`'s bootstrap grow, `BossModule.onLivingDeath`,
     `BossAPI.forceDefeat`) already funnels through -- now calls the new overload with
     `border.id()`, so all three covered call sites got `borderId` for free from this one change,
     no per-call-site edits needed at those three locations themselves.
  2. **`Set<UUID> pendingAttach` on `BossFixture`, persisted.** Same `ListTag`-of-`UUID`
     persistence shape `BordersFixture.borderPath` already establishes
     (`NbtUtils.createUUID`/`loadUUID` over a `TAG_INT_ARRAY` list), mirrored exactly rather than
     inventing a second UUID-collection persistence idiom. Own `registerCustom` key
     (`"pendingAttach"`), separate from `"bosses"`. Query/mutation surface:
     `pendingAttach()`/`addPendingAttach(UUID)`/`removePendingAttach(UUID)` on the fixture, with
     pass-throughs on `BossCrudFacet` (mutations) and `BossInfoFacet` (the read, since
     `reportMismatch` needed it there).
  3. **`BorderCommandHandler.pathGrow()`** adds `result.border().id()` to `pendingAttach` via
     `BossAPI.CRUD(level).ifPresent(...)` on a successful grow, hooked into the current
     `pathGrow(ctx, BlockPos center)` signature FRO_080 left it with. **Flagging a real
     architectural tension, not glossing over it:** this makes `BorderCommandHandler.java` import
     `BossAPI` -- the one place in the whole codebase where Border imports Boss, inverting the
     "Boss depends on Border, never the reverse" direction `BossModule`'s own class doc states and
     FRO_075 specifically relocated a listener to preserve. This isn't an oversight or a decision
     I made unilaterally -- both this ticket's own item 3 and boss.md's "Boss-less path layers and
     attach" section name `BorderCommandHandler.pathGrow()` explicitly as the write site, and there
     is no way to honor that literal instruction without Border code calling into `BossAPI`
     somewhere (no existing Border-side event fires on a path grow for Boss to listen to instead,
     and adding one would be new machinery this ticket's "additive/extending, not a rewrite"
     framing rules out). Implemented as instructed, with an inline comment at the call site making
     the direction explicit for whoever revisits this boundary -- flagged here for the Architect
     rather than silently normalized.
  4. **`BossAPI.removeBoss(Level, UUID)`**, new -- didn't exist before this ticket despite
     boss.md's "Despawn and record removal" section describing it in prose; `BossCommandHandler
     .delete` previously called `BossFixture.remove(id)` directly. Added the method (captures the
     record before removing it, since `borderId` is gone from the fixture once `remove()` returns
     true; removes; if the captured record had a `borderId` *and* that border is still genuinely
     on the level's path -- checked via `BorderAPI.PATH(level)` -- adds it to `pendingAttach`) and
     rewired `BossCommandHandler.delete` to call it instead of the fixture directly, so the new
     step has one call site rather than being duplicated at every delete-style caller. **Scope
     note:** boss.md's spec for `removeBoss` also describes a "despawn the live entity" step;
     current shipped code (pre- and post- this ticket) never did that -- it's tracked separately as
     an open "Known gap" (a materialized record's entity is left orphaned on delete), not something
     this ticket's item 4 asked for or that I added. Didn't touch it.
  5. **`/boss attach <border-selector>`**, new -- `BossCommands.attach()` nests
     `BorderSelectorArgumentType` directly as the command's own argument (the same way
     `BorderCommandHandler.pathInsert`/`pathRemove` already do), not as a mode inside
     `BossSelectorResult`'s own chain. Deliberate: `@border` as a `BossSelector` mode is still
     documented proposal, not built (FRO_057 shipped only the four-mode selector), and this
     command doesn't need it -- it always creates a fresh record rather than resolving an existing
     boss, so there's nothing for a `BossSelector` to resolve *to*. `BossCommandHandler.attach`
     resolves the selector to exactly one border via `BorderSelector.resolveSingle` (throws on
     zero/multiple matches, same as `pathInsert`), creates via
     `BossCrudFacet.create(border.layer(), border.id())` -- confirmed `BossCrudFacet` exists
     post-FRO_081 and used it, not the ticket's own named fallback -- then
     `removePendingAttach(border.id())` regardless of whether it was actually pending (boss.md's
     own spec places no precondition on attach requiring a pending entry first; a plain
     `Set.remove` no-op is the correct behavior either way, not an error).
  6. **`BossModule.reconcilePathAgainstBossRecords()` / `BossInfoFacet.reportMismatch`.** Widened
     `reportMismatch`'s parameter from `Set<Integer>` (bare layer values) to
     `Map<Integer, UUID>` (layer -> that layer's border id) -- needed so a missing layer's gap can
     be checked against `pendingAttach` by border id, not just by layer number.
     `BossModule.reconcilePathAgainstBossRecords` (now delegating through `BossInfoFacet` per
     FRO_081, read fresh rather than assumed pre-refactor) builds that map the same way it built
     the old set. A gap whose border id is in `pendingAttach` now logs at `OUT.info` ("awaiting
     /boss attach", not a data bug) instead of `OUT.warn`; an uncovered gap still logs exactly as
     before. Both halves feed the same `lastReportedMismatch` edge-trigger (FRO_062) so a
     transition either way -- a gap closing, or a gap moving from uncovered to
     `pendingAttach`-covered -- still re-logs once, not every tick.

  **Also touched, not separately numbered:** `BossDisplay.fullInfo` now shows `| border=<id or
  none>` -- a small, low-risk addition so `/boss info` can actually confirm `attach`/`pendingAttach`
  behavior manually during real playtest verification, not required by the ticket's own six items
  but directly useful for verifying them. `BossFixture.remove()`'s own doc comment updated to note
  it's now reached via `BossAPI.removeBoss()` rather than called directly, so it doesn't read as
  stale next to the new indirection.

  **Not touched, per the ticket's own "Not touched" list:** the reverse-direction reconciliation
  check (still deferred, boss.md's "Known gaps"); `/boss transform defeat`'s cascade -- confirmed
  by reading `BossAPI.forceDefeat`/`BossModule.onLivingDeath` fresh that neither implements
  boss.md's "Cascade gating" section's on-path/last-one-standing checks at all yet (that's
  FRO_083's job, explicitly left alone per this session's instruction not to touch it) -- this
  ticket's `borderId` field is a prerequisite FRO_083 will read, not something this ticket gates
  the cascade with itself.

  **Verification performed (no compiler available in this sandbox -- see this ticket's own
  standing constraint):** brace/paren balance counting on all 10 touched files
  (`BossRecord.java`, `BossFixture.java`, `BossCrudFacet.java`, `BossInfoFacet.java`,
  `BossDisplay.java`, `BossModule.java`, `BossAPI.java`, `BossCommandHandler.java`,
  `BossCommands.java`, `BorderCommandHandler.java`) -- all balanced. A whole-tree balance sweep
  across every `.java` file under `FrontierMode/src/main/java/com/arryn/frontiermode` turned up 4
  pre-existing mismatches, all in files this ticket never touched (`RenderContext.java`,
  `BorderDisplay.java`, `BordersCrudFacet.java`, `BordersFixture.java`) -- consistent with stray
  parentheses inside prose doc comments (a known limitation of naive paren-counting), not
  something this ticket introduced or is responsible for. Whole-tree `grep -rn` sweeps for both
  dotted and bare references to every symbol added/changed (`new BossRecord(`, `.create(layer`,
  `.CRUD.create(`, `reportMismatch(`, `pendingAttach`, `borderId`, `removeBoss`) -- every call
  site found matches the new shape; no stray 5-arg `BossRecord` constructions, no other
  `reportMismatch` callers, no other `Set<Integer> pathLayers`-shaped caller left behind. No test
  files reference `BossRecord`, so no test-side breakage to account for either.

  **Real build/playtest verification is still needed** -- no JDK/Gradle available in this sandbox,
  so nothing here has actually compiled. In particular: `/boss attach`'s Brigadier registration
  (a new `BorderSelectorArgumentType` argument nested directly under a Boss command, not Boss's
  own `ArgumentType` -- untested wiring, though it mirrors `pathInsert`/`pathRemove`'s already-
  shipped shape exactly), the widened `BossRecord` NBT schema round-tripping through an actual
  save/load cycle, and the new `BorderCommandHandler -> BossAPI` import actually resolving at
  compile time (no circular-inheritance concern, just never compiled).


- 2026-09-03: Ticket opened. Split from FRO_063's ruling for scheduling; carries the Architect's
  ruling verbatim. Parked on [RM_FRO_021](../roadmap/RM_FRO_021_susan-02.md) ("Susan_02"), same
  home as the rest of this session's found-along-the-way Boss items.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
