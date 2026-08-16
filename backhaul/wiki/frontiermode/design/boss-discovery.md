---
id: frontiermode/design/boss-discovery
category: frontiermode/design
slug: boss-discovery
title: Boss Discovery
summary: The clue gradient players use to find bosses, from ambient guardian mobs
  to expensive tracking tools, plus loot design.
keywords: null
status: draft
updated: '2026-08-12'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · frontiermode / design
<!-- bh-header:end -->

# Boss Discovery

*See [Frontier Mode Overview](overview.md) for the "teach through play" pillar this system is the
primary expression of, and [Progression & Frontier Mechanics](progression.md) for the loop this
feeds into.*

## Design intent

Bosses aren't marked on a map. Finding one is meant to be a real activity, built the same way
Minecraft already builds discovery for ores, structures, and strongholds: environmental and
mechanical clues the player learns to read through play, not through a wiki page (see the
Overview's "teach through play" pillar). This page is the gradient of tools available to do that,
from free and ambient up through rare and costly — matching the mode's general "gradients, not
walls" pillar rather than a single fixed method that either works or doesn't.

## The discovery gradient

Roughly ordered from cheapest/most-ambient to rarest/most-deliberate:

- **Guardian mobs** — stronger, visually distinct hostile variants that cluster more densely near
  a boss. Always available, no cost, requires no special item — just paying attention to mob
  density and toughness while playing normally. See [Guardian Mobs](guardian-mobs.md) for the
  full design; that page also covers why they're introduced later in progression rather than at
  level 1.
- **Environmental tells** — sounds, particle effects, dedicated biomes associated with boss
  presence. Still ambient, slightly more specific than guardian density alone.
- **Beacons and particle trails** — a visible, findable signal placed near (not on top of) a
  boss, cheap enough to be a reasonable early-game aid.
- **Ender-eye-style tracking** — an item the player throws or consumes that points toward the
  boss, modeled on the ender eye's role in locating strongholds: costs resources to use, gives a
  strong directional signal.
- **Special compasses** — a craftable/obtainable item giving persistent directional tracking,
  presumably more expensive than a one-shot ender-eye-style item since it's reusable.
- **Player-built warps** — once a boss's general location is known, the player can build a
  waypoint there, so a hunt that gets interrupted (or a boss that's found but not yet ready to
  fight) doesn't mean re-walking the whole distance from scratch.

Cost and rarity should climb as the tools get more precise — a deliberate design choice so that
"finding harder bosses gets harder" (the pacing goal from [Progression & Frontier
Mechanics](progression.md)) is expressed through the player's tool economy, not just raw mob
difficulty. **Open risk, not yet solved:** if the cost curve on these tools outpaces what a player
can realistically earn at that point in progression, the intended gradient turns into a grindy
wall in practice instead. There's no way to verify the right curve analytically — this needs
play-testing once there's something to test.

## Boss variety and tells (TBD)

One direction worth developing further: different boss types could have different discoverable
signatures — a player who learns to read the tells can identify what boss is nearby before
engaging it, or a player can skip that entirely and just risk the guardian gauntlet blind. This
would give the discovery gradient above a second axis (precision of *information*, not just
distance) but the details are genuinely undecided. **Current placeholder:** in testing, a
server-op-only compass simply points straight at the boss — this is a debug/testing stand-in, not
a proposed player-facing mechanic, and should not be treated as part of the design.

## Reward loot

Boss defeats grant loot from an increasingly rich table as level (and boss difficulty) climbs.
Both which boss appears and what it drops are meant to be randomized, not fixed per level — see
[Progression & Frontier Mechanics](progression.md#resource-and-reward-density) for how reward
richness is also expected to scale with distance from origin more generally, independent of boss
loot specifically.

## Related pages

- [Frontier Mode Overview](overview.md)
- [Progression & Frontier Mechanics](progression.md)
- [Guardian Mobs](guardian-mobs.md)
