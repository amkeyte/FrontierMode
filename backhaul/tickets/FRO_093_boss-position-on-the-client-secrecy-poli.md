---
id: FRO_093
uid: FRO
number: 93
client: FrontierMode
status: done
title: 'Boss position on the client: secrecy policy + growth-trigger anchor'
context: 'GrowthTriggerRenderer wants to anchor on the boss, not the border center
  -- client has zero boss data today (BOSS_JIG defaults SERVER-only). Architect call:
  is boss location supposed to be secret, and which of three options to build.'
priority: normal
opened: '2026-09-07'
closed: '2026-09-07'
---

<!-- board:start -->
<!-- board:end -->

## Summary

`GrowthTriggerRenderer` (client-only, purely visual -- FRO_055) anchors its particle ring on
`BordersPathFacet.tip().center()`, the path tip's geometric **border center**. Player feedback
mid-playtest: it should anchor on the paired **boss's actual position** instead -- the border
center is frequently far from where the boss actually stands (FRO_072 deliberately biases boss
placement toward its border's outer edge, so successive borders "crawl" outward), and a debug/
admin visual pointing at empty space near the boss's platform isn't doing its job.

This turned out not to be a one-line anchor swap. Investigated in chat first (not yet built):

**The client has zero boss data today, and it's not a deliberate secrecy ruling -- it's an
infrastructure default nobody looked at.** `BOSS_JIG` (`LevelJigConfig`, owns `BossFixture` +
`BossTellFixture`) never calls `.sideApplicability(...)` in `BossModule.registerBossJig()`, so it
silently inherits `LevelJigConfig`'s own class default -- `SERVER` (`LevelJigConfig.java` line 34,
`p.binding.sideApplicability = JigPolicies.SideApplicability.SERVER`). Compare `BORDERS_JIG`,
which explicitly overrides that same default to `BOTH` in `BorderModule.init()` -- that's the only
reason any Border data (and therefore `GrowthTriggerRenderer`) exists on the client at all today.
A `SERVER`-only jig's bundle never compiles/instantiates on the client side in the first place
(`JigConfigCompiler.appliesToSide()`), which is upstream of parceling -- there's no client-side
`BossFixture` instance for any parcel to hydrate into, regardless of `SyncMode`. Separately,
`BOSS_MOB_JIG` (`MobJigConfig`, the actual mob-entity/defeat-detection jig) *does* explicitly set
`SideApplicability.SERVER` with a real comment ("Defeat detection is server-only") -- that one
looks deliberate. `BOSS_JIG`'s own server-only-ness looks like nobody ever asked the question.

Confirmed while investigating: the boss/border association is live from creation, not just at
death -- `BossAPI.createBoss()` always calls `fixture.CRUD.create(border.layer(), border.id())`,
so `BossRecord.borderId()` is set the moment the boss is created, paired with the border that
spawned it. So "does the client know which border a boss belongs to" isn't the gap; "does the
client know anything about the boss at all" is.

## The question this ticket is actually for

**Is boss location supposed to be secret from the client, or did it just never come up?** This
project's discovery-systems design (environmental tells, LOG-shaped intensity curves fading in as
a player nears a boss -- see `boss.md`/FRO_087) reads like it's built around *not* handing players
an exact coordinate up front -- tells are deliberately probabilistic and proximity-gated, not a
"boss is here" ping. If that's an actual design intent, syncing the real `BlockPos` to every
connected client (options 1 and 2 below) hands out exactly what the discovery system is trying to
avoid revealing -- any client-side mod, packet logger, or even a naive dev tool would have the
real answer sitting in memory the instant a boss materializes, tells or no tells. If it's *not* an
actual concern (this is all singleplayer/trusted-multiplayer territory, or "the tell system already
leaks it eventually anyway, precision doesn't matter"), then options 1/2 are both simpler and more
"correct" by ordinary data-ownership standards than option 3's workaround. This ticket can't
resolve itself without that call, and it's a design/security-posture question, not an
implementation one -- hence routing here rather than picking a default and building it.

## Three options

### Option 1 -- sync the boss's position onto its `Border` record

Add a nullable field (e.g. `pairedBossPosition`) to `Border`, set by whichever code path finalizes
a boss's position for that border, riding Border's existing client sync pipe (`BORDERS_JIG` is
already `BOTH`, and today's session just fixed a real duplicate-accumulation bug in that exact
sync path -- see FRO_055's log -- so there's fresh confidence it works correctly right now).

**Assessment:** Mechanically the cheapest of the three -- no jig/bundle restructuring, no new sync
channel. But it's Boss writing into Border's data model, which is the *same category* of coupling
already sitting open and undecided on FRO_085 ("BorderCommandHandler now imports BossAPI --
pendingAttach write inverts Boss-never-reaches-back-into-Border rule"). Building this would either
need FRO_085 resolved first in a direction that allows it, or would add a second, independent
instance of the same architectural exception without a ruling backing either one. Also: zero
secrecy -- the real position is on every connected client, full precision, from the moment the
boss exists.

### Option 2 -- widen Boss's own client visibility (`BOSS_JIG` -> `BOTH`)

Flip `BOSS_JIG`'s `sideApplicability` to `BOTH` (one line, mirrors `BorderModule`'s own pattern
exactly) so `BossFixture` syncs to the client directly -- Boss owns and publishes its own data,
no Border coupling at all.

**Assessment:** Conceptually the "correct" ownership shape of the three, but not actually a
one-liner in practice: `BossFixture` and `BossTellFixture` are declared under the *same*
`bossBundle`/`BOSS_JIG` config (see `BossModule.registerBossJig()`), so flipping this one setting
would also make `BossTellFixture`'s tick logic compile and run on the client -- and
`BossTellFixture.tick()`/`onJigTick()` opens with `Satchel.requireServer()`, which throws
immediately off the server thread. This is real, avoidable-but-nontrivial scope: either split
`BossTellFixture` out into its own `SERVER`-only jig separate from `BossFixture`'s now-`BOTH` data
(structural, touches how Boss's bundle is composed), or add explicit client-side no-op guards
through its tick path (smaller, but still a real behavior change to a fixture this session already
modified heavily for FRO_087). Also fully exposes real boss position (and whatever else
`BossFixture` carries) to every client, same secrecy profile as option 1. The one with the most
"blast radius," and the only one that plausibly touches Satchel-side jig-config conventions rather
than staying inside FrontierMode.

### Option 3 -- client-side deterministic reconstruction, no new sync at all

The client independently computes its own approximation of where the boss is, from data it
*already* has (a synced `Border`'s id/center/radius/layer), with no boss data crossing the wire in
either direction. This is exactly the shape `ClientEffectsAPI`/`SimParity` (FRO_089) was built
for -- that module's own doc names `GrowthTriggerRenderer` as its anticipated first real consumer,
once FRO_055 cleared enough to make this worth building.

**How it would actually work:**

`DefaultBossRules.choosePosition(Level, Border)` today draws from a shared, stateful
`RandomSource` -- candidate points depend on invocation order/timing, not on anything derivable
from the border alone, so client and server can't currently arrive at the same answer even in
principle. Making this reproducible means reseeding *for this purpose specifically* off data both
sides already have -- e.g. a `RandomSource` seeded from `border.id()`'s bits -- so a client holding
the same `Border` can regenerate the identical candidate sequence.

The harder part: the *real* algorithm's candidate scoring (`hazardScore`/`flatnessScore`) reads
actual terrain via `getBlockState`/heightmap lookups (FRO_070's own void/ravine hazard fix lives
here). A client can only reproduce the exact same winning candidate if it has the exact same
chunk/terrain state loaded at evaluation time as the server did -- true most of the time near an
active border, not guaranteed (a chunk the client hasn't loaded/rendered yet, or terrain that's
changed since the server's original placement, e.g. player-built structures). For a purely
decorative debug/admin particle aura, exact terrain-matched parity is probably more precision than
the use case needs.

**Two ways to scope it, cheapest first:**
- **Approximate, no terrain lookups at all:** reuse `BorderMath.randomPointInAnnulus` (already a
  pure center+radius function, no `Border` object required -- see FRO_087's own use of it) with
  the same edge-biased annulus shape `choosePosition`'s design intends, seeded off `border.id()`.
  This lands "in the right neighborhood, biased toward the edge like a real boss would be," not
  the exact spot -- cheap, purely additive to Border's client-render package, zero Boss/Satchel
  touch, and *does* preserve some real ambiguity about the exact position if that turns out to
  matter for the secrecy question above.
- **Exact reproduction:** reseed `choosePosition` itself for parity and have the client re-run the
  real hazard/flatness scoring against its own loaded terrain. Closer to "the client genuinely
  knows where the boss is" (same secrecy profile as options 1/2, just without a packet), for
  roughly the implementation cost of the approximate version plus the terrain-parity risk above.

**Assessment:** Lightest touch of the three -- no Boss/Border coupling, no Satchel changes, no jig
restructuring, stays entirely inside `border/client/render` + a small shared seeding helper. My
recommendation if precision doesn't need to be exact for this use case, which "it's really a
debug/admin thing anyway" suggests it doesn't. Whether to build the approximate or exact variant
depends entirely on this ticket's secrecy question -- approximate is strictly cheaper and adds a
little real ambiguity for free; exact is available at nearly the same cost if secrecy turns out
not to matter, without needing options 1/2's coupling or restructuring at all.

## Note from the project owner

Willing to greenlight a real restructure of `BossTellFixture` (splitting it out of `BOSS_JIG` into
its own jig/bundle), or even a Satchel-side change, if this investigation turns up a genuine
structural deficiency worth fixing properly rather than working around -- not asking for the
cheapest patch by default. Whichever option/variant you land on, flagging that the ceiling here is
"do it right," not "do it quick."

## Architect ruling

**Yes -- boss location is meant to be secret from the client, and this isn't a new call so much as
a fact already on record that this ticket's investigation didn't cross-check.**
[Boss Discovery Systems](../wiki/frontiermode/architecture/discovery-systems.md) is built entirely
around proximity-gated, probabilistic tells (a `LOG`-shaped intensity curve rolling independent
probabilities for particle/sound) precisely so a player never gets an exact position, only an
ambient signal that climbs near and fades. [Border Pregeneration § The exploit this
closes](../wiki/frontiermode/architecture/border-pregeneration.md#the-exploit-this-closes) states
the underlying principle directly, already `verified`: "the entire premise of Boss Discovery is
that finding a boss should only ever leak information through designed signals... An accidental...
tell... defeats that premise for free." Syncing the real `BlockPos` to every client -- whether via
a packet (options 1/2) or a client-side re-derivation of the exact same answer (option 3's "exact
reproduction" variant) -- is a strictly worse version of exactly the leak that page already spent
real design effort closing: not an inferred signal a sharp player might notice, the literal answer
sitting in every client's memory the instant a boss materializes.

**Reject options 1 and 2.** Both hand out full, precise position to every connected client,
unconditionally, from creation -- the opposite of what this project's discovery design requires.
Option 1 also drags in FRO_085's still-open Boss-writes-into-Border coupling question, a second,
independent reason to reject it, not the primary one.

**Reject option 3's "exact reproduction" variant too.** It only avoids a packet -- the client
still ends up holding the real position, same secrecy failure as options 1/2, just computed
instead of synced. It doesn't answer the question this ticket asked.

**Build option 3's approximate variant.** `BorderMath.randomPointInAnnulus`, seeded off
`border.id()`, gives the client a position "in the right neighborhood, edge-biased like a real
boss would be" -- close enough for a decorative debug/admin particle aura, without ever holding
the true answer. This is also the cheapest of all five variants on the table: no Boss/Border
coupling, no Satchel jig changes, stays entirely inside `border/client/render` plus a small shared
seeding helper. The remaining ambiguity isn't a compromise forced by picking the cheap option --
it's the actual point.

**`DefaultBossRules.choosePosition`'s stateful, non-reproducible `RandomSource` stays exactly as
it is.** It only looked like a gap because "exact reproduction" was still on the table; now that
it's rejected, that non-reproducibility is a feature -- the client provably can't reconstruct the
true answer even if someone tried -- not something to "fix" toward parity later. Flagging this
explicitly so a future session doesn't quietly reseed it for a different purpose and reopen this
exact leak.

**On `BOSS_JIG`'s `SERVER` default -- not a structural deficiency, just an undocumented one.** The
project owner's note offered a real restructure if this investigation turned up a genuine gap; it
didn't. `BOSS_JIG` inheriting `SERVER` was the right outcome, just not for a stated reason --
boss.md's own "Module wiring" section previously asserted only "Boss has no client-rendering need
in Tier 1," an assumption this ticket's own investigation shows is no longer complete now that a
real client need (this ticket) has actually surfaced. The right fix is a one-line explicit
`.sideApplicability(SERVER)` call with a real comment, mirroring `BOSS_MOB_JIG`'s own ("Defeat
detection is server-only") -- documentation of an already-correct default, not a behavior change,
and not the restructure being offered.

Wiki updated: [boss.md § Module wiring](../wiki/frontiermode/architecture/boss.md#module-wiring)
(secrecy rationale replaces the stale "no client-rendering need" framing) and [border.md §
Commands and client surface](../wiki/frontiermode/architecture/border.md#commands-and-client-surface)
(`GrowthTriggerRenderer`'s new anchor, described aspirationally -- not yet built). A Lead Dev build
ticket follows for the actual implementation.

## Log

- 2026-09-07: Ticket opened. Investigated in chat (Dev(FrontierMode)) while diagnosing FRO_055's
  growth-trigger anchor request -- no code written against any of the three options; this ticket
  is the write-up and the secrecy question, not a build.
- 2026-09-07: Architect ruling above -- boss location confirmed secret by existing design
  precedent (discovery-systems.md, border-pregeneration.md's "exploit this closes"). Options 1/2
  and option 3's exact-reproduction variant rejected; option 3's approximate variant
  (`BorderMath.randomPointInAnnulus` seeded off `border.id()`) is the build. `BOSS_JIG`'s `SERVER`
  default confirmed correct, ruled a documentation fix (explicit call + comment), not the
  restructure the project owner offered to greenlight. Closing; Lead Dev build ticket follows.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
