---
id: frontiermode/design/exterior
category: frontiermode/design
slug: exterior
title: Exterior
summary: 'What lies past the Frontier before it''s earned: nothing to find, and a
  mounting cost (Frontier Sickness, and the corrupted wildlife called Feral) for staying
  anyway -- both scaling with raw distance and never capping.'
keywords: null
status: draft
updated: '2026-09-07'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · frontiermode / design
<!-- bh-header:end -->

# Exterior

*See [Frontier Mode Overview](overview.md#design-pillar-gradients-not-walls) for the "gradients,
not walls" and "teach through play" pillars this page is built to express, and [Progression &
Frontier Mechanics](progression.md#why-the-frontier-is-irregular) for the Frontier's own
definition — the outer boundary formed by the union of every Border established so far — and the
"no wall, but ruthless" language this page turns into an actual mechanism.*

## What this page answers

The **Exterior** is everything past the Frontier line. No Border exists there yet — the next one
isn't created until the current edge's boss falls (see [Progression & Frontier
Mechanics](progression.md#the-core-loop)) — so there's no boss, no `BorderRules` record, no
pregenerated terrain, nothing placed. This page settles what a player actually finds if they walk
out anyway: **nothing.** No hidden boss to fight early, no richer loot, no shortcut. **Frontier
Sickness** is the reason going into the Exterior stays a bad idea without needing a wall to make
it one.

Vocabulary note: **Frontier** names the line itself (unchanged, existing usage). **Exterior**
names the land past it. The two aren't interchangeable — this page introduces the second term
specifically so the first one keeps meaning only what it's always meant.

## Frontier Sickness

Frontier Sickness is what the Exterior does to anything spending time in it — the player
included. Both of its expressions below key off the same single value: **raw distance past the
nearest point on the Frontier's boundary.** No Border, no Layer, no boss difficulty feeds this —
those all describe *claimed* land. Out here there's only distance, and Frontier Sickness is a
direct, uncapped function of it: the farther out, the worse it gets, with no designed ceiling.

### The player's affliction

Severity works as a target-and-catch-up: current distance sets how bad it's *trying* to get, and
the player's actual condition climbs toward that target the longer they stay, rather than
snapping straight to a fixed value. A quick dash past the edge to scout stays cheap; camping out
there is what actually costs you. It stacks along both axes at once — farther out raises the
target, staying longer closes the gap to it.

Coming back inside the Frontier doesn't clear everything at once: each active effect runs down
and clears independently, after its own short randomized delay, rather than a single instant full
reset. The sickness is tied to standing in unclaimed land, not to scarring — turn back, and it
lifts, on its own schedule, with nothing lingering once every effect's timer runs out.

The exact effects, their thresholds, and the climb/decay rates are genuinely a playtest question,
not something to spec from first principles here — Lead Dev owns picking sane initial numbers to
playtest against, with this page setting the shape those numbers need to honor (continuous,
uncapped, uncoupled from Border/Layer machinery). An illustrative, non-binding sketch of the
shape: nausea shows up first and costs nothing but discomfort; weakness and mining fatigue follow,
denting combat and digging without stopping either; hunger drain and slowness turn a long stay
into a resource cost; and past that, blindness and direct damage make it an active killer rather
than an inconvenience.

### Feral

Guardian Mobs already cover "a mob variant that signals proximity to a specific boss" (see
[Guardian Mobs](guardian-mobs.md)) — that mechanic stays exactly what it is and doesn't extend
into the Exterior, because there's no boss out here for anything to guard. Unclaimed land needs
its own signal, for a different reason: not "a boss is near," but "you've left everywhere the game
has vetted for you."

**Feral** mobs are corrupted, visibly twisted variants of ordinary hostiles, native to the
Exterior rather than clustered around any one point. They scale continuously with the same
distance value driving the player's own sickness — no placement/difficulty curve tied to a boss
record, no bucketed tiers picked from a table, just steadily more twisted and more dangerous the
farther out they're found. One throttle, two expressions: the wildlife getting visibly worse and
the player feeling worse are reading off the same number.

Visually, Feral should get the same category of always-on tell Guardian Mobs already use — a name
tag and a scoreboard-team glow (see [Guardian Mobs — architecture](../architecture/guardian-mobs.md)
for the precedent: `GM-<tier>` tags plus a `guardian` team glowing `DARK_PURPLE`, deliberately
distinct from Boss's own unteamed white glow) — but on its own team and its own color, so a
through-wall glow reads unambiguously as "boss nearby" (purple) versus "you're deep in the
Exterior" (Feral's color), never both at once. Proposing a sickly green for that team color, tying
the glow to the same poison-particle language the sick passive-mob tell below already uses — open
to revision, not locked. Exact tag format is Lead Dev's call, same as Guardian's.

For an initial build, Feral's spawn mechanism can start from exactly the same mob-spawn-finalization
event hook Guardian Mobs already use (see [Guardian Mobs —
architecture](../architecture/guardian-mobs.md)) rather than inventing a separate mechanism from
scratch — a known-working pattern to build on, not a promise the two will stay identical if it
doesn't fit once it's actually built.

Whether Feral replaces ordinary hostiles entirely at distance or spawns alongside them is
undecided (see Open Questions).

## Why nothing rewards going out here

[Progression & Frontier Mechanics](progression.md#resource-and-reward-density) already scales
loot and resource richness with distance — but that pillar is scoped to distance *from origin
within already-claimed ground*, rewarding a player for pushing the Frontier outward the normal way
(defeat a boss, earn the next Border). It was never meant to extend into the Exterior, and this
page draws that line explicitly: there is no loot table, no ore bonus, and no boss out there that
isn't also available, more safely, by playing the core loop forward. Going out early doesn't skip
a queue — there's nothing in that queue to skip yet.

## Fairness: FAFO Rule #3

Minecraft already has its own player-taught folklore for "the game won't stop you, but it also
won't warn you": don't dig straight down, don't dig straight up. The Exterior gets a third —
**don't push into the Exterior** — and it's meant to propagate the same way the first two do,
player to player, not through a tutorial or a UI warning. There is deliberately no clean
"last warning" moment built into this design. The atmospheric tells below (the entry cue, the sick
wildlife, the climbing debuff) are the warning, exactly the way gravel and sand physics warn a
player before they bury themselves. Rules 1 and 2 both turn out to have real nuance once a player
knows more (dig straight down safely by placing a block first; straight up is fine with the right
setup) — Rule 3 has the same shape: the blunt version for a newcomer is "don't," the version a
veteran actually plays by is "a quick look is fine, staying is what kills you," per the
target-and-catch-up shape above.

## Sensory design

Three layers, each doing a different job, all diegetic rather than UI (per [Frontier Mode
Overview](overview.md#design-pillar-gradients-not-walls)'s "gradients, not walls" commitment to
in-world signals over HUD elements):

- **Entry cue.** A time-throttled, low-frequency descending tone — a bass drop — plays as a
  player crosses into the Exterior. Cheap, unambiguous, needs no explanation the first time it
  happens.
- **Sick wildlife (cosmetic only).** Passive mobs found in the Exterior — rabbits, and other small
  animals that read as out of place this deep — emit poison-colored particles and periodically
  make hurt sounds without ever actually taking damage or dying. They're otherwise entirely
  normal: harmless, no aggression, no real stakes. They're only ever "defeated" — despawning — by
  crossing back into the Frontier themselves. They also spawn more densely the farther into the
  Exterior a player is, on the same distance value driving everything else on this page — more
  wrong-looking wildlife underfoot is itself part of the tell, on top of each individual mob's own
  glitching. A free, no-cost tell: a player who's paying attention notices something's wrong with
  the local wildlife well before anything is actually dangerous.
- **Hallucination layer, gated strictly to deep distance.** Past a threshold placed well beyond
  where the first two tells have already fired, the player starts hearing random mob sounds and
  encountering a rising number of decoy 1-hit-point mobs, visually and audibly indistinguishable
  from anything actually dangerous. Deep enough, this is meant to become genuinely unplayable —
  surrounded by mobs the player can't parse, unable to tell a real threat from a decoy, milk
  buckets buying only temporary relief from the debuff stack piling up underneath it. This is a
  deliberate, effectively unwinnable dead end, not content meant to be beaten — and it stays fair
  specifically because the actual cost of losing here is an ordinary Minecraft death, nothing
  extra layered on top (see [Frontier Mode Overview](overview.md#difficulty-and-death)). A player
  who never goes looking for this never encounters it and loses nothing for it.

## Detection

[Progression & Frontier Mechanics](progression.md#why-the-frontier-is-irregular) currently
describes the Frontier as "marked visually where it currently sits" — that ambient, always-on
render is being retired in favor of active, craftable discovery tools, consistent with "teach
through play": a passive render hands a player the answer, a cheap tool makes them go earn it.
(Owed edit: remove that line from Progression and point it here instead.)

Both tools below share one new material, **diorite dust** (a 1:1 craft from a diorite block —
common, no rarity gate), deliberately kept separate from the amethyst family [Discovery
Structures](discovery-structures.md) already committed to boss-location tools. The split is
meant to read as two different questions with two different materials: diorite answers "where's
the edge," amethyst answers "where's the boss." Nothing here changes Discovery Structures itself;
it just confirms amethyst stays boss-only.

- **Edge Stone.** Crafted from flint and diorite dust. A cheap, stackable, consumable throwable —
  ordinary enough that carrying a stack of them should feel routine, the way carrying torches
  does. Thrown toward the Frontier, it poofs the instant it crosses the line, in flight — not on
  landing, so it reads clearly at a distance even over canopy or water. The reference point for
  the effect: the snap of an egg breaking, just happening in mid-air instead of against a surface.
- **Diorite Wick and the Frontier lantern.** Glass and diorite dust craft into a Diorite Wick — a
  diorite rod topped with a glass orb, shaped like a torch. Slotted into vanilla's lantern recipe
  in place of the torch (same surrounding iron-nugget pattern), it produces a lantern that lights
  up whenever it's within 10 blocks of the Frontier line, and goes dark once the Frontier's growth
  carries that line past it. Placeable and permanent, this doubles as a passive expansion
  tracker — a player who lines a base's perimeter with them can watch which lanterns go dark over
  time and read their own Frontier's growth at a glance, without re-scouting. Ten blocks of range
  is deliberately generous — it opens up real build possibilities (a ring, a wall, a watchtower
  pattern) beyond the minimum needed to just confirm a line.

## Open questions

- **Exact debuff effects, thresholds, and climb/decay rates.** The illustrative sketch above is
  not a spec. Lead Dev sets initial placeholder values; real numbers are a playtest question.
- **Feral population mix.** Whether Feral mobs replace ordinary hostiles entirely at distance or
  spawn alongside them is undecided.
- **Feral's team color and name-tag format.** Sickly green is proposed, tying to the sick-wildlife
  particle language above, but not locked; exact tag string is an implementation call.
- **Rule #3 advancement(s).** Worth doing — Minecraft's own joke-advancement tradition around
  rules 1 and 2 is a natural fit — but explicitly deferred to a later polish pass, not this one.
- **Nether and End.** See Related pages — Frontier Sickness and Feral are Overworld-only; how (or
  whether) the Nether guards against becoming a fast-travel bypass, and whether the End gets any
  special treatment at all, is being tracked on that page, not here.

## Flagged for Architect

- **The irregular-boundary distance problem.** "Raw distance past the nearest point on the
  Frontier's boundary" is simple to state and not simple to compute — the Frontier is the union of
  every Border established so far, an irregular, overlapping shape by design (see [Progression &
  Frontier Mechanics](progression.md#why-the-frontier-is-irregular)), not a single circle.
  Finding the nearest boundary point from an arbitrary player position is real geometry work this
  page doesn't attempt to solve.
- **Feral's spawn mechanism.** Proposed to start from the same mob-spawn-finalization event hook
  Guardian Mobs already use (see [Guardian Mobs —
  architecture](../architecture/guardian-mobs.md)) as a known-working baseline, not a requirement
  that the two stay mechanically identical.
- **Pregeneration interaction.** [Border Pregeneration](../architecture/border-pregeneration.md)
  validates terrain among sampled candidates before a boss spawns. If a player has already
  explored, built on, or altered ground that later falls inside a newly-created Border, this page
  doesn't know whether that needs special handling in candidate validation or falls out naturally
  the way it would for any other pre-existing player build. Flagging only — unclaimed land isn't
  guaranteed untouched land by the time a Border actually claims it.

## Parked

**Outposts.** Building a Nether portal that links to a point in the Exterior may create a small,
player-made safe pocket there — a genuinely interesting idea, entirely unworked. It will need
reconciling with [Nether and End](nether-and-end.md)'s existing "portals are vanilla, no
special rule" position when it's picked up — noted here so it isn't lost, not designed.

## Related pages

- [Frontier Mode Overview](overview.md) — the "gradients, not walls" and "teach through play"
  pillars this page is built to express
- [Progression & Frontier Mechanics](progression.md) — the core loop and the Frontier's own
  definition as the union of established Borders
- [Guardian Mobs](guardian-mobs.md) — the boss-proximity mob signal this page deliberately does
  not reuse or extend
- [Guardian Mobs — architecture](../architecture/guardian-mobs.md) — the name-tag/team-glow and
  spawn-hook precedent Feral builds on
- [Boss Discovery](boss-discovery.md) — the wider discovery gradient; the Exterior isn't part of
  it, since there's nothing here to discover
- [Discovery Structures](discovery-structures.md) — the amethyst-family boss-location tools this
  page's diorite family is deliberately kept separate from
- [Nether and End](nether-and-end.md) — Nether/End scoping for Frontier Sickness and Feral
- [Border Pregeneration](../architecture/border-pregeneration.md) — terrain validation a future
  Border forming over previously-unclaimed ground would need to reconcile with
