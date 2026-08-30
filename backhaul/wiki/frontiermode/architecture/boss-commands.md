---
id: frontiermode/architecture/boss-commands
category: frontiermode/architecture
slug: boss-commands
title: Boss Command Surface
summary: Command-tree design space for an in-game Boss admin/dev surface (RM_FRO_022
  "Joyce"). Six items shipped and playtest-verified on FRO_057 (info/add/delete/mob
  spawn/transform defeat/debug goto, plus debug distance added mid-playtest); the
  rest of the info/add/delete/transform/mob/debug tree, the borderId-based selector
  chain, and the border-move reconciliation direction remain documented proposal,
  not yet built.
keywords: null
status: draft
updated: '2026-08-29'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · frontiermode / architecture
<!-- bh-header:end -->

# Boss Command Surface

The design space for an in-game admin/dev command surface over `boss/` -- [Border](border.md)
and Satchel's mob-tracking (`MobTrackCommands`) both have one, `boss/` has none, and playtesting
[RM_FRO_019](../../../roadmap/RM_FRO_019_karen.md) ("Karen") surfaced that gap directly. Tracked
as [RM_FRO_022](../../../roadmap/RM_FRO_022_joyce.md) ("Joyce"); first captured on
[FRO_056](../../../tickets/FRO_056_boss-control-commands.md).

**Most of this page is still proposal, not ruling -- but a real slice of it has actually shipped.**
[FRO_057](../../../tickets/FRO_057_boss-control-commands-build.md) built and playtest-verified six
items; see "What's actually built" immediately below for exactly what's real versus what's still
documented design space. Everything past that section describes the wider proposal this build was
drawn from -- category shape, the full selector chain, and the border-move reconciliation
direction -- none of which this build touched."

## What's actually built

[FRO_057](../../../tickets/FRO_057_boss-control-commands-build.md) shipped and playtest-verified
six of the items below, plus one command added mid-playtest that was never on this page at all
until now. Real, live code today -- everything else on this page is still proposal.

- **`info <selector>`, `add <pos\|here> <layer>`, `delete <selector>`, `mob spawn <selector>`,
  `transform defeat <selector>`, `debug goto <selector>`** -- exactly FRO_056's six-item scope,
  each with real playtest evidence (chat log + server log), including `mob spawn`'s hardest case:
  a genuinely unloaded boss, force-chunk-loaded and confirmed spawned.
- **`debug distance <selector>`** -- net-new, not part of the original six. Added mid-playtest to
  make locating an unmaterialized/unloaded boss practical without teleporting blind first; reports
  Euclidean distance from the command source to the boss's stored position (the same position
  `debug goto` teleports to).
- **Selector scope shipped exactly as scoped: four modes only** -- bare position / `@nearest` /
  `@all` / `@none`. No `@id`/`@name`/`@border`, and **no `borderId` field landed on `BossRecord`**
  -- the reversal described in "Selector scheme" below is entirely still proposal, not built.
  `BossSelectorResult`/`BossSelector`/`BossSelectorArgumentType(Info)`
  (`boss/server/commands/`) mirror `BorderSelectorResult`/`BorderSelector`'s shape exactly, sized
  to the four modes.
- **Two real behavioral decisions, confirmed with the project owner before writing code, that
  refine this page's original design:**
  - `transform defeat` runs the **full** defeat-\>grow-\>next-boss cascade, not a bare
    `markDefeated` flag-flip -- new `BossAPI.forceDefeat(Level, UUID)`, mirroring
    `BossModule.onLivingDeath`'s own cascade shape but keyed off a selector-resolved id and the
    record's stored `position()` rather than a live mob. Deliberately not refactored to share code
    with `onLivingDeath` itself -- that's Karen's own already-verified path, left untouched.
  - `mob spawn` on an already-materialized target is a clean no-op, not an error.
- **A real bug caught by playtest, not code review:** `transform defeat`'s success message was
  reporting the newly-grown *border's* UUID labeled as the new *boss's* UUID -- a live test caught
  the mismatch directly (`/boss info` showed the real new boss at a different id than the chat
  line had just named). Fixed via a proper `DefeatOutcome` record (`borderResult` + `nextBoss`)
  replacing a bare `Result` return, so the handler reports the border id and the boss id as the
  two distinct things they are.
- **`/boss info` shows each record's list position** (`BossDisplay.fullInfo` gained a `listIndex`
  param) -- the bare-position selector mode was otherwise unusable without counting list order by
  hand.
- **`BossFixture.remove(UUID)`** -- the one new fixture-level method this build needed; returns
  `boolean` (false = nothing to remove, a plain failure, not an error).
- **`BossModule.forceMaterialize(ServerLevel, UUID)`** returns a `MaterializeOutcome` enum
  (`SPAWNED` / `ALREADY_MATERIALIZED` / `NO_RECORD` / `DECLINED`) rather than a collapsed boolean
  -- playtest found the single generic failure message was masking which of three unrelated
  situations was actually happening. Reuses the exact same `BossRules.materialize`/fixture-write/
  interest-registration steps `materializeUnresolvedBosses` already uses -- one materialization
  codepath, not two, just a new caller that also force-loads the chunk first.

## Precedent this follows

- **A real selector `ArgumentType`, not hand-rolled per command.** `BorderCommands` registers
  `BorderSelectorArgumentType` (`BorderSelectorArgumentType`/`BorderSelectorArgumentTypeInfo`),
  parsed by `BorderSelector.parse()` against a `StringReader` into a `BorderSelectorResult`
  (`RELEVANT | INDEX | INSIDE | COORD | ALL | NONE`). This is a proven pattern in this codebase --
  not the caution it might look like at a glance. `MobTrackCommands`' simpler "nearest `Mob` to
  the command source" convention is a *different*, narrower precedent (no selector algebra, just
  one implicit target) -- worth naming separately since Boss borrows a piece of each, not one
  wholesale.
- **Registration/handler split.** `BorderCommands` (Brigadier tree registration) and
  `BorderCommandHandler` (the logic each leaf calls into) are two classes, not one -- see
  [Border](border.md). Boss's own surface follows the same split.
- **`add`/`delete` at the top level, matching `/border` directly.** The first two drafts tried
  folding creation/deletion into a merged `transform` category and found it didn't hold together
  -- those are lifecycle operations, not mutations of something that already exists. `add`/
  `delete` now mirror `/border`'s own top-level shape exactly. `mob` has no Border analog at all
  (a `Border` is never itself a spawned entity) -- named after `BossMobFixture`'s own concern, the
  live-entity half of this module.
- **Immutable records, remove-then-add-back for any update.** `BossFixture.materialize()` and
  `.markDefeated()` both replace a record in place by removing and re-adding it -- "same
  remove-then-add-back shape `BordersFixture.reassignLayers` uses." This page's border-move
  reconciliation (see below) extends that same pattern to a new trigger, not new machinery.
- **A `name` selector, alongside everything else.** `Border` already carries a nullable
  `displayName()` (Ashring, Dawnmark, ...) -- deduped against currently-live borders when
  auto-generated (`BordersCrudFacet.getDefaultDisplayName()`), though not hard-unique (a
  manually-set name could still collide). Border's own selector has no way to resolve by it
  today, despite it being the one thing a player actually sees -- `@name <name>` is a real gap on
  `/border`'s own side, same shape as the already-flagged `@id` gap. `BossRecord` has no name
  field at all -- a Boss-side `@name` needs that added first, not just a new selector mode; see
  "Open questions."
- **Hand-parsed value tokens where a stock `ArgumentType` doesn't fit.** `BorderSelector.parse()`
  already reads a raw token and switches on it (digit vs. `@`-prefixed word). The health value
  (absolute vs. percent) reuses that same trick rather than splitting into two competing command
  branches.
- **`Result`/permission conventions.** Border's `Result`-checking discipline and
  `.requires(src -> src.hasPermission(2))` baseline are the default assumption for any mutating
  Boss command until stated otherwise -- confirmed flat, no tiering; see "Open questions."

## Existing server-side surface

What's real today, for grounding every command below against actual backing code -- includes
FRO_057's additions now that they've shipped:

- `BossAPI.boss(Level)` -- resolves the level's `BossFixture`.
- `BossFixture.all()` / `get(UUID)` / `unmaterialized()` / `layers()` -- read side.
- `BossFixture.create(BlockPos, int layer)`, `.materialize(UUID, BlockPos, UUID entityId)`,
  `.markDefeated(UUID)`, **`.remove(UUID)`** (FRO_057, returns `boolean`) -- the mutations that
  exist.
- **`BossAPI.forceDefeat(Level, UUID)`** (FRO_057) -- returns `DefeatOutcome { Result
  borderResult, Optional<BossRecord> nextBoss }`. Runs `markDefeated` then the same
  grow-\>createBoss cascade a real death triggers.
- **`BossModule.forceMaterialize(ServerLevel, UUID)`** (FRO_057) -- returns `MaterializeOutcome`
  (`SPAWNED | ALREADY_MATERIALIZED | NO_RECORD | DECLINED`). Force-loads the target chunk, then
  reuses `materializeUnresolvedBosses`' own materialization steps.
- **`BossDisplay.fullInfo(BossRecord, int listIndex)`** (FRO_057) -- presentation-only formatting,
  mirrors `BorderDisplay`'s role.
- `BossRecord` is currently `{bossId, position, layer, bossEntityId, alive}` -- **no Border
  reference, and no name field.** `boss.md`/`BossRecord.java`'s own doc comments state the
  Border decoupling as deliberate, in strong and repeated language. "Selector scheme" below
  proposes reversing it -- **still proposal, not built**; FRO_057 shipped without touching this.
- `Border.displayName()` -- nullable, already real on Border's side. No equivalent on Boss.

Everything else this page describes past what's listed here (and in "What's actually built" above)
is a new method or new command, not a wrapper over something that already exists.

## Selector scheme

A `BossSelectorResult` mirrors `BorderSelectorResult`'s shape, sized to what a boss record
actually is -- but getting there means reversing one thing this design initially treated as
fixed.

**Why the reversal.** `boss.md`/`BossRecord.java` state, more than once, that `BossFixture` is
"not keyed by `Border` UUID, and not a reference to any `Border` at all" -- deliberate, not an
oversight, and specifically what makes a hand-placed boss with no border trivial to create. But
[Border Vocabulary](border-vocabulary.md)'s "Implementation trap" section *already* names the
correct-by-construction boss-difficulty lookup as running through "the boss's own recorded
identity -- Border UUID -- that Border's `layer()`." The code never caught up to that page. So
this isn't a new idea being introduced here -- it's an existing, verified-status wiki page's
already-stated intent, finally getting acted on.

One correction to that page's own wording, though: it names `BossMobFixture` as the holder, but
that fixture is ephemeral -- deliberately not persisted, doesn't exist until an entity
materializes. An *unmaterialized* boss still needs to resolve `@border` before it has an entity
at all, so the reference has to live on `BossRecord`/`BossFixture` (the durable one), not
`BossMobFixture`. See "Follow-up" below.

**`BossRecord` gains `Optional<UUID> borderId`.** `empty()` for a hand-placed boss with no border
at all -- the exact case the original decoupling was protecting, still fully served. Set once at
creation for anything paired with real border-growth, same "copy it once, never re-read the
source" discipline `layer` already uses for its own value.

### Identity modes

`BossSelectorResult = POSITION(int) | NEAREST | ALL | NONE | ID(UUID) | BORDER(BorderSelectorResult)`

| Mode | Resolves to | Notes |
|---|---|---|
| bare `<n>` | the boss at that raw position in `BossFixture.all()` | Not `layer`, not path position -- a true mirror of Border's own bare `INDEX` mode, which is also just a list position (`BordersCrudFacet.all()`), not a semantic value. Covers on-path and off-path bosses identically; same reshuffle caveat Border's own `INDEX` already carries. |
| `@nearest` | nearest materialized boss to the command source | `MobTrackCommands`' convention. |
| `@all` | every boss record | **The default when no selector is given** -- matches Border's own default (`info` with no argument resolves `@all`). |
| `@none` | explicit empty set | -- |
| `@id <uuid>` | the boss with that `bossId` | The one mode guaranteed to work for every boss regardless of path membership, materialization state, or any collision above -- `bossId` is `BossRecord`'s own equality key. |
| `@name <name>` | boss(es) with that name | Needs a new `name` field on `BossRecord` first (see "Open questions") -- Border already has the field, Boss doesn't. |
| `@border <border-selector>` | every boss whose `borderId` is in the resolved Border set | The general primitive -- nests Border's *own* selector grammar wholesale (bare index, `@relevant`, `@all`, `@containing`, `@coord`, `@none`, `@id`/`@name` once those land on Border's side too). A boss with no `borderId` never matches any `@border` query, by construction -- not an error, just an honest empty result. |

**Boss-to-border cardinality is n:1, by design.** A `@border` query resolving to one border
doesn't mean exactly one boss -- nothing above requires it. A level can host a themed multi-mob
encounter (three strong zombies at once, say) as easily as a single mob; each is its own
`BossRecord` sharing the same `borderId`, created by however many `add` calls it takes -- no new
mechanism needed beyond the field already proposed above. `BossFixture.layers()`'s own doc
comment currently frames more-than-one-record-per-layer as itself "a genuine data bug" -- that
assumption doesn't survive this cardinality change. **Tracked on
[RM_FRO_021](../../../roadmap/RM_FRO_021_susan-02.md) ("Susan_02")** as a found-but-not-yet-ticketed
item -- not re-opened as a question on this page.

**Settled: `@border` nests Border's full selector grammar, not a narrower Boss-specific subset.**
Reuses an existing structure (`BorderSelectorArgumentType`/`BorderSelector`) instead of Boss
building and maintaining a second, parallel resolution path for the same lookups.

### Considered and dropped: `@level`/`@layer`

An earlier pass proposed `@level <n>`/`@layer <n>` as named shorthands over `@border` (path
position and `Border.layer()` value, respectively). Dropped: once `@border` can already nest
`@id`/`@name` and every other Border selector mode directly, a dedicated shorthand only saved
typing, not capability -- and dropping it also removes the two Border-side selector modes
(path-position, layer-value) that only existed to support it. The one real Border-side gap that
survives on its own merits is just `@id`/`@name` -- both independently motivated (see "Precedent
this follows"), neither tied to level or layer at all. If `@level`/`@layer` turn out to be missed
in practice, they can come back later as pure syntactic sugar over `@border` -- no data-model
impact either way.

### Status filter

`@status <alive|dead|spawned|notspawned>` -- narrows whatever an identity mode above already
resolved, composable with any of them (`@all @status alive`, `@border @relevant @status
notspawned`, etc.). No Border equivalent exists (borders don't have alive/dead state) -- this is
net-new to Boss specifically.

### Follow-up: `border-vocabulary.md` correction owed

Once this design is settled and built, [Border Vocabulary](border-vocabulary.md#implementation-trap-worth-flagging-now)'s
"Implementation trap" section needs a dated correction note. Its text currently says the lookup
runs through "`BossMobFixture`'s Border UUID"; the field actually lands on `BossRecord`/
`BossFixture` instead (see above for why). Per this project's own correction-note convention,
that's a forward-pointing bracket note added to the existing text, not a silent rewrite. Tracked
here now so it doesn't get lost between this page settling and whenever the build actually lands.

## What happens when a boss's border moves

A real gap this design surfaced, worth its own section rather than burying in "Open questions" --
this one has a settled direction, just not a scoped implementation.

**The actual mechanism, stated precisely (an earlier pass here got this wrong).** `Border.layer()`
is immutable and only ever changes via `BordersFixture.reassignLayers()`, called from
`fixLayers()` after a *path reorder* (`moveUp`/`moveDown`) -- a completely different operation
from `transform` (repositioning a border's center/radius). So a border move doesn't create "the
same kind of drift layer already tolerates" -- there is no existing precedent for it. `boss.md`'s
own data model already treats `position` as copy-once, exactly like `layer` ("picked once,
immediately, at creation... this record never looks at a `Border` again") -- but nothing today
defends against that stored position drifting outside its border's bounds after a `transform`
move. That's a new problem, not an inherited one.

**Settled: bosses live-follow their border by deleting and recreating, not by patching position
in place.** Extends the existing immutable-record, remove-then-add-back pattern
`materialize()`/`markDefeated()` already establish -- not new machinery, the same shape applied to
a new trigger. Two implementation details still need scoping, not yet done here:

- **Trigger mechanism.** The existing precedent for Boss reacting to a Border mutation is a
  *paired call at the call site* -- whoever calls `BorderAPI.grow()` also calls
  `BossAPI.createBoss()` right after, in the same handler; Boss never listens for Border events,
  and Border still has no idea Boss exists. Consistent with that, whoever calls Border's
  `transform()` would need to make a paired Boss-side call afterward -- not a new event-hook
  mechanism, the same coupling direction this codebase already commits to everywhere else.
- **Materialized vs. unmaterialized.** An unmaterialized record is a clean delete+recreate --
  nothing else references it. An already-spawned boss needs `mob respawn`'s own shape instead
  (kill the live entity, then recreate) -- a bare fixture-level swap would orphan a live mob with
  no record pointing at it anymore.

**Tracked on [RM_FRO_021](../../../roadmap/RM_FRO_021_susan-02.md) ("Susan_02")** alongside the
cardinality item above -- found during this design pass, not yet ticketed.

## Command tree

`<selector>` below is the full identity chain from "Selector scheme" above, optionally followed
by a `@status` filter. **[BUILT]** marks a leaf FRO_057 actually shipped -- those currently only
support the four-mode selector (bare position/`@nearest`/`@all`/`@none`), not the full chain the
rest of this section describes; see "What's actually built" above.

```
/boss
├── info <selector>              [BUILT] default @all, matching Border's own default. Full dump
│                                 of the matched boss(es): position, layer, entityId, alive,
│                                 materialized, home border, current mob HP if live.
│
├── add <pos|here> <layer>      [BUILT] plant a boss record off the normal border-creation
│                               pairing -- hand-placed/event boss, or a bare test fixture. No
│                               borderId (that field doesn't exist yet -- see "Selector scheme").
│                               <pos> accepts `here`, same as Border's own `add here`.
│
├── delete <selector>           [BUILT] delete a record outright -- undo a mistake, clear test
│                               debris
│
├── transform                    record-state mutations -- work even without a live entity
│   ├── defeat <selector>       [BUILT] force-defeats without combat and runs the full
│   │                           defeat -> grow -> next-boss cascade on demand (not a bare
│   │                           markDefeated flag-flip -- see "What's actually built"). The
│   │                           single most concretely wanted item here -- what the playtest
│   │                           session that opened Joyce was actually reaching for.
│   ├── fastforward <n>         auto-defeat N bosses in sequence -- avoids grinding to layer
│   │                           12 just to test layer-12 behavior
│   └── reset                   wipe every boss record on this level and reseed from layer 0
│
├── mob                          live-entity operations -- these wrap vanilla's own /kill,
│                                 /damage, /tp rather than pointing admins at them directly, so
│                                 nobody has to work out vanilla targeting syntax by hand
│   ├── spawn <selector>        [BUILT] force materialization now, regardless of chunk-loaded
│   │                           state. A no-op, not an error, if the target is already
│   │                           materialized.
│   ├── respawn <selector>      kill + recreate in place: same layer/position, fresh entity
│   ├── damage <selector> <value>   <value> is a bare int (absolute HP) or int+`%` (percent of
│   │                               max health) -- one hand-parsed token, not two command
│   │                               branches, same trick as BorderSelector.parse()
│   └── heal <selector> <value>     same value grammar as damage
│
└── debug
    ├── validate                run the layer-reconciliation check (path layers vs.
    │                           BossFixture layers) on demand instead of waiting for
    │                           BOSS_JIG's own tick to maybe log it
    ├── tick                    force one BOSS_JIG cycle immediately -- skip the ~20-tick wait
    ├── simulate-death <selector>   fire the LivingDeathEvent path against a boss without
    │                               actually killing anything live -- repeat-test the
    │                               grow/create chain without a real mob each time
    ├── goto <selector>         [BUILT] teleport yourself to a boss's stored position, spawned
    │                           or not
    ├── distance <selector>     [BUILT, net-new -- not in the original six] Euclidean distance
    │                           from the command source to the boss's stored position (same
    │                           position `goto` teleports to) -- pairs with `goto` the way "how
    │                           far" pairs with "go there," added mid-playtest to make locating
    │                           an unmaterialized/unloaded boss practical
    ├── setmob <selector> <type>    override which vanilla mob a layer materializes as --
    │                               bypass DefaultBossRules for balance testing without waiting
    │                               on real tuning work. Testing/tuning-flavored, same class of
    │                               tool as validate/tick/simulate-death -- not a general admin
    │                               action, so it lives here rather than under transform.
    └── dump                    raw NBT/state dump of BossFixture -- lowest-level escape hatch
```

## What's real vs. net-new

**Shipped (FRO_057) -- see "What's actually built" for the full detail:** `info`, `add`, `delete`,
`mob spawn`, `transform defeat`, `debug goto`, `debug distance`. `BossFixture.remove(UUID)`,
`BossAPI.forceDefeat`/`DefeatOutcome`, `BossModule.forceMaterialize`/`MaterializeOutcome`, and
`BossDisplay` are all real methods/types now, not proposals.

Everything below this line is still proposal, not built:

- **Backed by an existing method today, once built:** nothing left in this category -- the two
  items that used to live here (`info`, `transform defeat`) shipped above.
- **A real new mechanism, not just a wrapper:** `debug tick` (exposing what's currently a private
  tick-handler concern), `debug validate` (same, for the reconciliation check), `debug setmob`
  (no per-record override slot exists on `DefaultBossRules` today), `debug simulate-death` /
  `transform fastforward` (drive the `LivingDeathEvent` handler programmatically rather than via
  a real event).
- **Net-new on Boss's side:** `Optional<UUID> borderId` on `BossRecord`/`BossFixture` -- reverses
  a stated, deliberate decoupling; a `name` field, if `@name` is adopted; the `@status` filter,
  which has no Border equivalent at all; the border-move reconciliation handler (see above);
  `mob respawn`/`damage`/`heal`.
- **Net-new on Border's side:** two new `BorderSelectorResult` modes, `@id` and `@name` -- neither
  exists today. `/border` itself gains more precise targeting as a side effect of `/boss` needing
  it.
- **Not `BossFixture` operations at all:** `mob`'s unbuilt leaves (`respawn`/`damage`/`heal`) --
  these touch the entity or the player, not the fixture.

## Open questions

Both items this section used to carry are settled:

- **Which of these are actually wanted first** -- narrowed to a concrete six-item build scope on
  [FRO_056](../../../tickets/FRO_056_boss-control-commands.md) (`info`, `add`, `delete`,
  `mob spawn`, `transform defeat`, `debug goto`); everything else on this page remains documented
  design space, not yet scoped to a build.
- **Permission** -- flat `hasPermission(2)`, same as `Border`, no tiering. Confirmed to cover the
  Dev player as-is.

Nothing else currently open on this page.

## Related pages

- [Boss](boss.md) -- the data model this surface would expose, and the decoupling "Selector
  scheme" above reverses
- [Border](border.md) -- `BorderCommands`/`BorderCommandHandler`'s registration/handler split,
  `displayName()`, and `BorderSelectorArgumentType`/`BorderSelector`, the direct model for this
  page's selector
- [Border Vocabulary](border-vocabulary.md) -- Level vs. Layer vs. Path, and the "Implementation
  trap" section this page's selector chain fulfills (and owes a correction note to)
- [RM_FRO_021](../../../roadmap/RM_FRO_021_susan-02.md) ("Susan_02") -- tracks the multi-boss
  defeat/grow ripple and the border-move reconciliation gap this page surfaced
- [RM_FRO_022](../../../roadmap/RM_FRO_022_joyce.md) ("Joyce") -- the roadmap node this feeds
- [FRO_056](../../../tickets/FRO_056_boss-control-commands.md) -- first capture of the gap
- [FRO_057](../../../tickets/FRO_057_boss-control-commands-build.md) -- the actual build, six items plus `debug distance`, real playtest evidence
