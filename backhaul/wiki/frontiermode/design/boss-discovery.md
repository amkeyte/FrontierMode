---
id: frontiermode/design/boss-discovery
category: frontiermode/design
slug: boss-discovery
title: Boss Discovery
summary: The clue gradient players use to find bosses, from ambient guardian mobs
  to expensive tracking tools, plus loot design.
keywords: null
status: draft
updated: '2026-08-31'
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

Roughly ordered from cheapest/most-ambient to rarest/most-deliberate. Guardian mobs,
environmental tells, and beacons/trails are the tools [Boss Discovery
Systems](../architecture/discovery-systems.md) (this page's architecture counterpart) builds out
for Tier 2; the ender-eye-style tracker and player-built warps are sequenced later rather than cut
from scope, with warps in particular pushed out to a later epoch. All six stay listed below so
this reads as the full intended gradient.

- **Guardian mobs** — stronger, visually distinct hostile variants that cluster more densely near
  a boss. Always available, no cost, requires no special item — just paying attention to mob
  density and toughness while playing normally. See [Guardian Mobs](guardian-mobs.md) for the
  full design; that page also covers why they're introduced later in progression rather than in
  the starting Border (level 1, to the player).
- **Environmental tells** — sounds, particle effects, dedicated biomes associated with boss
  presence. Still ambient, slightly more specific than guardian density alone.
- **Beacons and particle trails** — a visible, findable signal placed near (not on top of) a
  boss, cheap enough to be a reasonable early-game aid.
- **Ender-eye-style tracking** *(sequenced later, see above)* — an item the player
  throws or consumes that points toward the boss, modeled on the ender eye's role in locating
  strongholds: costs resources to use, gives a strong directional signal.
- **Special compasses** — a craftable/obtainable item giving persistent directional tracking,
  presumably more expensive than a one-shot ender-eye-style item since it's reusable. Genuinely
  different from the tracker above, not the same tool with a different shell — it holds a real,
  persistent attunement rather than firing once and forgetting.
- **Player-built warps** *(sequenced later, see above)* — once a boss's general
  location is known, the player can build a waypoint there, so a hunt that gets interrupted (or a
  boss that's found but not yet ready to fight) doesn't mean re-walking the whole distance from
  scratch.

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
distance) but the details are genuinely undecided.

The current placeholder — a server-op-only compass that simply points straight at the
boss — is a debug/testing stand-in, not a proposed player-facing mechanic. [Boss Discovery
Systems § Special Compass](../architecture/discovery-systems.md#special-compass) proposes folding
that existing op tool (`BorderPathCompass`) into a real, self-refreshing player item that unifies
path-tip-pointing and boss-pointing under one class — but that's still today's flat "points
straight at the boss" behavior, not the tiered, tell-based boss-variety system described above.
This section's actual open question (different boss types having different discoverable
signatures) remains unresolved and is still mine to design.

## Reward loot

Boss defeats grant loot from an increasingly rich table as Border difficulty climbs. Both which
boss appears and what it drops are meant to be randomized, not fixed per Border — see
[Progression & Frontier Mechanics](progression.md#resource-and-reward-density) for how reward
richness is also expected to scale with distance from origin more generally, independent of boss
loot specifically.

## Related pages

- [Frontier Mode Overview](overview.md)
- [Progression & Frontier Mechanics](progression.md)
- [Guardian Mobs](guardian-mobs.md)
- [Border Vocabulary](../architecture/border-vocabulary.md) — why "Border" replaces "level" here
  as the mechanic term.
- [Boss Discovery Systems](../architecture/discovery-systems.md) — the architecture counterpart to
  this entire page: data model and build sequencing for every tool in the gradient above.
- [Border Pregeneration](../architecture/border-pregeneration.md) — closes an accidental discovery
  signal this page's "Design intent" section didn't anticipate (differential chunk-load hitching)
  before it could ever undermine the designed gradient above.
