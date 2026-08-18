---
id: frontiermode/design/progression
category: frontiermode/design
slug: progression
title: Progression and Frontier Mechanics
summary: 'How the frontier expands: cylinder growth, re-centering, irregular shape,
  and the oldest-ring-wins overlap rule.'
keywords: null
status: draft
updated: '2026-08-18'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · frontiermode / design
<!-- bh-header:end -->

# Progression and Frontier Mechanics

*See [Frontier Mode Overview](overview.md) for the mode's identity and pillars this page builds
on, especially "gradients, not walls."*

## Terminology: "level" is player-facing only

[Border Vocabulary](../architecture/border-vocabulary.md) retires "level" from internal
architecture language — a Border has a Layer (a sort key), a position in the Path (progression
order), and a Difficulty (a separate question from either) — because conflating those already
caused real ambiguity once code needed to be precise about which one a value meant. **Ruling
(design call, 2026-08-18; tightened 2026-08-18): "level" is strictly a player-facing term.** It
names what a player is told or sees — "level 1," "level 2," and so on — and nothing else. Every
Frontier Mode design page, this one included, says **Border** when describing the underlying
mechanic (a Border is what a player experiences as one level), and **Path**, **Layer**, or
**Difficulty** specifically when one of those narrower concepts is what's actually meant — the
same discipline Border Vocabulary already applies internally, not a separate design-side
exception to it. This isn't pedantry: loose use of "level" across both design and code is exactly
what created the ambiguity Border Vocabulary exists to fix, and these pages had the same habit.

This doesn't block individual Borders from also carrying flavor `displayName`s (Ashring,
Dawnmark, ...) for place-identity — a level number says *how far*, a place name says *which one*;
they're not competing for the same job.

## Starting conditions

A new player is allotted a play space defined as a cylinder — full world height, min to max — with
a 20-block radius. This is the starting **Border**, which the player sees as **level 1**. Play
proceeds as normal survival within that space. Somewhere inside it is a boss: in this first
Border, something as unthreatening as a rabbit, chosen to be trivially easy so the ritual (explore,
find, fight, expand) gets established with no real risk.

## The core loop

1. A boss exists somewhere within the current **Border**.
2. The player finds and defeats it (see [Boss Discovery](boss-discovery.md) for how).
3. The playable area expands: a new, larger **Border** is created, **centered on the defeated
   boss's home block** — not on the original spawn point — with a larger radius (the next Border,
   level 2 to the player, is radius 30; later Borders continue to grow).
4. A new boss for the new Border spawns at a random coordinate somewhere within its area. Normal
   mobs and bosses both get harder with each new Border.
5. Repeat indefinitely.

## Why the frontier is irregular

Because each new cylinder centers on wherever the previous boss happened to die — not on spawn —
the combined shape of all cylinders drifts and grows unevenly rather than expanding as a clean
circle around the player's base. This is intentional. The design goal isn't "get farther from
spawn in a straight line," it's meaningful, undirected exploration: the frontier's actual shape is
a record of where the player has actually been and fought, not a distance meter.

The **Frontier** is the outer boundary formed by the union of every Border established so far. It
is marked visually where it currently sits. Past that marker there is no invisible wall — a player
can walk straight through it — but mobs on the other side are ruthlessly, deliberately too
difficult for the player's current Border. This is the "gradient, not a wall" pillar made
concrete: nothing stops the player physically, but everything about the far side tells them they
went too far, too fast.

## Persistence and the overlap rule

Once a **Border** is established, it **persists at that difficulty permanently.** This is what
keeps expansion meaningful: the starting Border stays exactly as easy forever (level 1 ground
stays level 1, in player terms), so there is always a reason to push the frontier outward in
search of harder challenge, rather than a reason to wait for everything to level up around you.

Because Borders can overlap (a later, larger Border can fully or partially cover an earlier one's
territory), overlap needs a resolution rule: **the oldest Border covering a given point is always
the effective ambient difficulty there.** This has two consequences worth calling out explicitly:

- It creates durable **safe pockets.** A player's starting-Border base stays just as easy no
  matter how far the frontier grows past it (level-1-dangerous stays level-1-dangerous, to the
  player), which means it's always viable to live in — and because the frontier's shape is
  irregular, new safe pockets can open up in unexpected places as later Borders wrap around old
  ones.
- It creates real risk in **exploration**, not just at the frontier's edge: a player can wander
  from a very safe area into a very dangerous one without crossing any marked boundary, simply by
  walking between two overlapping Borders of different ages. Players are expected to keep their
  head on a swivel; in-game tells (see [Guardian Mobs](guardian-mobs.md) and [Boss
  Discovery](boss-discovery.md)) should give attentive players a chance to notice the shift before
  it costs them, in keeping with the mode's "teach through play" pillar — but there is no hard
  floor guaranteeing a warning every time. That's part of the tension by design.

## Bosses can appear in old territory

A Border's boss can spawn anywhere within that Border's area when it was created — **including
territory that has since become an old, low-difficulty ring** under the overlap rule above. In
other words: build a base in a safe pocket, and there is always a chance a boss from a much newer,
harder Border (a "high-level boss," to the player) ends up sharing your backyard. This is
intentional (target audience is PG; a player who wants zero chance of this can play Peaceful), but
it needs a fairness counterweight so it reads as risk/opportunity rather than an unfair,
untelegraphed ambush:

**Trigger condition, formalized (2026-08-18):** [Border Vocabulary](../architecture/border-vocabulary.md#difficulty)
draws a real architecture distinction that gives this item a concrete, checkable condition instead
of a vague "significantly exceeds" feeling — ambient difficulty at a point and a boss's own
difficulty are two different lookups (`Point → Relevant Border → Difficulty` vs. `Boss's own home
Border → Difficulty`) that are allowed to disagree by design; that disagreement *is* this rule. The
fairness signal should fire whenever `difficultyOf(bossHomeBorder) > difficultyOf(getRelevant(bossSpawnPos))`
— i.e., whenever the boss's own difficulty outranks whatever's ambient at the point it actually
spawned. That resolves *when* the signal should fire.

**Still open — the signal itself:** something should communicate that mismatch to a nearby player
before they commit to engaging — the working idea is some kind of drop or marker at the boss's
location that communicates roughly how dangerous it is. Framed correctly, this cuts both ways: a
boss from a far newer Border showing up near an established base is bad news (real danger, close
to home) and good news (a nearby, findable challenge, no long trek to the frontier's edge
required) — which
offsets the risk somewhat. Per the "gradients, not walls" pillar, this signal should be diegetic
(in-world — particle, sound, a physical marker) rather than a HUD/UI element. Exact mechanic is
still **TBD** — the trigger condition is settled, the presentation isn't.

## Resource and reward density

Rather than gating resources hard behind the frontier (e.g. "ore only spawns at the edge," which
would fight the safe-pocket incentive above by pulling players away from the exact places the
overlap rule just made appealing to live in), the working idea is **density scaling with
distance**: a player can find diamonds close to spawn on day one, same as vanilla, but richer and
rarer rewards become statistically more likely the farther out they go. Mob loot tables could
scale the same way. Biome variation is another available dial. Exact curve is explicitly **not**
locked — this is a play-testing question, not something to spec from first principles.

## Related pages

- [Frontier Mode Overview](overview.md)
- [Boss Discovery](boss-discovery.md)
- [Guardian Mobs](guardian-mobs.md)
- [Nether and End](nether-and-end.md)
- [Border architecture](../architecture/border.md) — the existing implementation
  this design is expected to build on (ordered border path, rule evaluation, client-side
  rendering); Architect should confirm how closely it already matches this model.
- [Border Vocabulary](../architecture/border-vocabulary.md) — the Relevance/Layer/Path/Difficulty
  split this page's "level" language and old-territory trigger condition are now stated in terms
  of.
- [Boss](../architecture/boss.md) — Tier 1's implementation design, already building against this
  page's core loop and "level 1 is a rabbit" starting condition.
