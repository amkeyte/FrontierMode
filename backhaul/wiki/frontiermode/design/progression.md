---
id: frontiermode/design/progression
category: frontiermode/design
slug: progression
title: Progression and Frontier Mechanics
summary: 'How the frontier expands: cylinder growth, re-centering, irregular shape,
  and the oldest-ring-wins overlap rule.'
keywords: null
status: draft
updated: '2026-08-12'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · frontiermode / design
<!-- bh-header:end -->

# Progression and Frontier Mechanics

*See [Frontier Mode Overview](overview.md) for the mode's identity and pillars this page builds
on, especially "gradients, not walls."*

## Starting conditions

A new player is allotted a play space defined as a cylinder — full world height, min to max — with
a 20-block radius. This is **level 1**. Play proceeds as normal survival within that space.
Somewhere inside it is a boss: at level 1, something as unthreatening as a rabbit, chosen to be
trivially easy so the ritual (explore, find, fight, expand) gets established with no real risk.

## The core loop

1. A boss exists somewhere within the current level's cylinder.
2. The player finds and defeats it (see [Boss Discovery](boss-discovery.md) for how).
3. The playable area expands: a new, larger cylinder is created, **centered on the defeated
   boss's home block** — not on the original spawn point — with a larger radius (level 2 is
   radius 30; later levels continue to grow).
4. A new boss for the new level spawns at a random coordinate somewhere within the new level's
   area. Normal mobs and bosses both get harder at each level.
5. Repeat indefinitely.

## Why the frontier is irregular

Because each new cylinder centers on wherever the previous boss happened to die — not on spawn —
the combined shape of all cylinders drifts and grows unevenly rather than expanding as a clean
circle around the player's base. This is intentional. The design goal isn't "get farther from
spawn in a straight line," it's meaningful, undirected exploration: the frontier's actual shape is
a record of where the player has actually been and fought, not a distance meter.

The **Frontier** is the outer boundary formed by the union of every level cylinder established so
far. It is marked visually where it currently sits. Past that marker there is no invisible wall —
a player can walk straight through it — but mobs on the other side are ruthlessly, deliberately
too difficult for the player's current level. This is the "gradient, not a wall" pillar made
concrete: nothing stops the player physically, but everything about the far side tells them they
went too far, too fast.

## Persistence and the overlap rule

Once a level's cylinder is established, it **persists at that difficulty permanently.** This is
what keeps expansion meaningful: level 1 ground stays level 1 forever, so there is always a reason
to push the frontier outward in search of harder challenge, rather than a reason to wait for
everything to level up around you.

Because cylinders can overlap (a later, larger cylinder can fully or partially cover an earlier
one's territory), overlap needs a resolution rule: **the oldest level covering a given point is
always the effective ambient difficulty there.** This has two consequences worth calling out
explicitly:

- It creates durable **safe pockets.** A player's level-1 base stays level-1-dangerous no matter
  how far the frontier grows past it, which means it's always viable to live in — and because the
  frontier's shape is irregular, new safe pockets can open up in unexpected places as later
  cylinders wrap around old ones.
- It creates real risk in **exploration**, not just at the frontier's edge: a player can wander
  from a very safe area into a very dangerous one without crossing any marked boundary, simply by
  walking between two overlapping cylinders of different ages. Players are expected to keep their
  head on a swivel; in-game tells (see [Guardian Mobs](guardian-mobs.md) and [Boss
  Discovery](boss-discovery.md)) should give attentive players a chance to notice the shift before
  it costs them, in keeping with the mode's "teach through play" pillar — but there is no hard
  floor guaranteeing a warning every time. That's part of the tension by design.

## Bosses can appear in old territory

A level's boss can spawn anywhere within that level's designated area when it was created —
**including territory that has since become an old, low-difficulty ring** under the overlap rule
above. In other words: build a base in a safe pocket, and there is always a chance a
much-higher-level boss ends up sharing your backyard. This is intentional (target audience is PG;
a player who wants zero chance of this can play Peaceful), but it needs a fairness counterweight
so it reads as risk/opportunity rather than an unfair, untelegraphed ambush:

**Open design item:** when a boss's own level significantly exceeds the ambient difficulty of the
zone it lands in, something should signal that to a nearby player — the working idea is some kind
of drop or marker at the boss's location that communicates roughly how dangerous it is before the
player commits to engaging. Framed correctly, this cuts both ways: a high-level boss showing up
near an established base is bad news (real danger, close to home) and good news (a nearby, findable
challenge, no long trek to the frontier's edge required) — which offsets the risk somewhat. Per
the "gradients, not walls" pillar, this signal should be diegetic (in-world — particle, sound, a
physical marker) rather than a HUD/UI element. Exact mechanic is **TBD** — flagging for design
follow-up, and Architect should weigh in on feasibility before this gets specced further.

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
