---
id: RM_FRO_022
uid: RM_FRO
number: 22
kind: work
status: resolved
title: Boss control commands
owner: Arryn
depends_on:
- RM_FRO_019
created: '2026-08-29'
superseded_by: null
ticket: FRO_057
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_FRO
<!-- bh-header:end -->

## Boss control commands

A real feature node, not a maintenance container — an in-game admin command surface for
`BossFixture`/`BossAPI`, requested by the project owner during [RM_FRO_019](RM_FRO_019_karen.md)
("Karen") playtesting. Depends on Karen directly: the command surface wraps
`BossFixture.markDefeated(UUID)` (new this build) alongside the pre-existing
create/materialize/lookup surface, so it can't start before that exists.

**Genuine gap, not scope creep.** [Border](../wiki/frontiermode/architecture/border.md) and
mob-tracking (`server/commands/MobTrackCommands.java`) both have a command layer already; `boss/`
has none — no hands-on way to exercise or debug boss state without real combat or a code change.
`MobTrackCommands`'s own class doc names exactly this as its reason for existing ("so the done
bar's checklist can be exercised from chat without code access or a debugger"). See
[FRO_056](../tickets/FRO_056_boss-control-commands.md) for the existing server-side surface this
would wrap and the precedent (`BorderCommands`/`BorderCommandHandler`'s split,
`MobTrackCommands`'s nearest-to-source targeting convention) — command shape and scope
(list/info/force-defeat/spawn/remove, naming, permission level) deliberately left open, Architect's
call when this is picked up.

**Folded into [RM_FRO_017](RM_FRO_017_donna.md) ("Donna," Tier 1 convergence) directly** —
real Tier 1 scope surfaced during Karen's playtest, same pattern Donna's own text already
prescribes for exactly this situation ("fold those siblings into this node's own `depends_on` ...
don't let them feed some future Tier 2 node directly and skip this one"). This node's own
`depends_on` (Karen only) is unaffected — Donna gathering it as a sibling doesn't create a
convergence-bypass shape, since this node never names Donna itself.

- 2026-08-29: Node opened. First scope named in [FRO_056](../tickets/FRO_056_boss-control-commands.md),
  captured during Karen's playtest session. Folded into [RM_FRO_017](RM_FRO_017_donna.md)'s own
  `depends_on` as a new Tier 1 sibling, per that node's own standing instruction.

- 2026-08-29: **Build scope set, after a full Architect design pass.** Full design space now
  documented on [Boss Command Surface](../wiki/frontiermode/architecture/boss-commands.md)
  (`info`/`add`/`delete`/`transform`/`mob`/`debug`, the selector chain, a border-move
  reconciliation gap) -- deliberately wider than one build. Actual build scope narrowed to six
  items (`info`, `add`, `delete`, `mob spawn`, `transform defeat`, `debug goto`) on
  [FRO_056](../tickets/FRO_056_boss-control-commands.md)'s own "What to build" section --
  selector scope kept deliberately minimal for this pass, permission mirrors `Border`'s flat
  `hasPermission(2)`. Everything else on the wiki page stays documented design space, not this
  node's current scope.

- 2026-08-29: **Checked against real content whether [RM_FRO_020](RM_FRO_020_susan-01.md)
  ("Susan_01")/[RM_FRO_021](RM_FRO_021_susan-02.md) ("Susan_02") hold anything this node's build
  scope actually depends on -- cleared, no new `depends_on` edge needed.** Susan_01 is fully
  `resolved` with nothing outstanding. Susan_02's four open tickets
  ([FRO_048](../tickets/FRO_048_pathgrow-no-boss.md),
  [FRO_051](../tickets/FRO_051_border-load-count-mismatch.md),
  [FRO_054](../tickets/FRO_054_mutation-data-security.md),
  [FRO_055](../tickets/FRO_055_growth-particles-stale-tip.md)) are all Border-side (path-grow
  pairing, client/server count sync, a future security audit, growth-particle rendering) --
  none touch `BossFixture`'s `all()`/`get()`/`create()`/`materialize()`/`markDefeated()`, the only
  methods this build's six items actually call. Susan_02's own two un-ticketed design notes
  (multi-boss cardinality, border-move reconciliation) are both `borderId`-driven, and this pass's
  own scoping already rules `borderId` out for this build -- less relevant now than when logged,
  not more. This node's `depends_on` stays [RM_FRO_019](RM_FRO_019_karen.md) only.

- 2026-08-29: **Built and playtest-verified, six-for-six -- [FRO_057](../tickets/FRO_057_boss-control-commands-build.md)
  `status: done`.** All six scoped items (`info`/`add`/`delete`/`mob spawn`/`transform defeat`/
  `debug goto`) confirmed via real chat/server-log evidence, including `mob spawn`'s hardest case
  (a genuinely unloaded boss, force-chunk-loaded). One command added mid-playtest beyond the
  original scope, `debug distance`. Architect reconciliation done on
  [Boss Command Surface](../wiki/frontiermode/architecture/boss-commands.md) and
  [boss.md](../wiki/frontiermode/architecture/boss.md) -- see FRO_057's own log for the detail.
  Node `status: resolved`, `ticket:` repointed to FRO_057 -- the six-item build is this node's
  full scope, same as Karen's own resolution didn't wait on every documented idea being built. The
  wider design space on the wiki page (`@id`/`@name`/`@border`, `borderId`, `transform
  fastforward`/`reset`, `mob respawn`/`damage`/`heal`, `debug validate`/`tick`/`simulate-death`/
  `setmob`/`dump`, border-move reconciliation) stays documented, unbuilt proposal -- picked up
  again only if a future node scopes it.

- 2026-08-29: **Ready to build — verified.** [RM_FRO_019](RM_FRO_019_karen.md) ("Karen")
  `resolved` with real playtest evidence; the two prior log entries above already confirm
  [RM_FRO_020](RM_FRO_020_susan-01.md)/[RM_FRO_021](RM_FRO_021_susan-02.md) hold nothing this
  build's scope touches, and both non-blocking design notes on Susan_02 are re-confirmed as such.
  Ticket pointer moved from [FRO_056](../tickets/FRO_056_boss-control-commands.md) (scoping,
  now `done`) to [FRO_057](../tickets/FRO_057_boss-control-commands-build.md) (Lead Dev build,
  opened) — same handoff shape as [FRO_044](../tickets/FRO_044_karen-prep.md) →
  [FRO_045](../tickets/FRO_045_karen-build.md) for Karen.

- 2026-08-29: **Resolved.** [FRO_057](../tickets/FRO_057_boss-control-commands-build.md) closed
  against real playtest evidence, not self-report: all six built commands (`info`, `add here`,
  `delete`, `mob spawn`, `transform defeat`, `debug goto`) confirmed via matching server+client
  log evidence across a live playtest session. `transform defeat` verified end-to-end -- the full
  defeat -> border-grow -> next-boss-queued cascade, not a bare flag-flip, per this node's own
  "i just killed the boss" requirement -- with a border-id/boss-id reporting bug caught and fixed
  mid-session (new `BossAPI.DefeatOutcome`). `mob spawn`'s two outcomes both confirmed: a clean
  no-op against an already-materialized boss, and the force-chunk-load `SPAWNED` path (the one
  item untested for most of the session -- every earlier boss checked had already been in loaded
  territory by the time anyone looked) confirmed once a boss landed genuinely unmaterialized and
  unloaded, both server log lines (`forcing chunk load` -> `spawned bossId=...`) and matching
  client chat feedback present. A seventh command, `debug distance`, was added mid-build at the
  project owner's request (not in FRO_056's original six-item scope) and confirmed working the
  same way -- not yet reflected on the wiki page, flagged there as owed to the Architect. Scope
  otherwise stayed exactly what FRO_056/this node's own log fixed -- no `@id`/`@name`/`@border`
  selector modes, no `borderId` field; everything else stays documented design space on the wiki
  page, not this node's scope.

## Required By

<!-- required-by:start -->
- [**RM_FRO_017**](RM_FRO_017_donna.md) — Tier 1: Core loop operational
<!-- required-by:end -->
