---
id: frontiermode/design/multiplayer-sketch
category: frontiermode/design
slug: multiplayer-sketch
title: Multiplayer Sketch (Parked)
summary: Early, unscoped ideas for how the frontier loop might work in multiplayer
  -- fixed/summoned bosses, gear-based scaling. Not in active design scope.
keywords: null
status: draft
updated: '2026-08-12'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · frontiermode / design
<!-- bh-header:end -->

# Multiplayer Sketch (Parked)

**Status: parked.** FrontierMode's design is being built single-player first, deliberately — see
[Frontier Mode Overview](overview.md#scope). This page captures early multiplayer thinking so it
isn't lost, not because it's ready to build against.

## Why multiplayer needs a different shape, not just a bigger frontier

The single-player loop relies on a boss existing somewhere unknown within its level, found through
ambient and deliberate discovery tools (see [Boss Discovery](boss-discovery.md)). That doesn't
translate cleanly once multiple players can search simultaneously in different directions, and
raises questions the single-player design doesn't have to answer — whose frontier is it, what
happens when two players expand it in different directions at once. Rather than force-fit the
solo model, the early thinking here treats multiplayer as its own variant with real structural
differences:

- **Boss locations are fixed**, not randomized within the level's area — and maintain the
  appropriate difficulty/depth for wherever they sit, rather than roaming.
- **Bosses must be summoned.** They don't simply exist ambiently in the world waiting to be
  stumbled on the way a solo-mode boss does.
- Boss difficulty might scale off **the highest level of boss loot equipment a player is
  carrying** when they summon it — a gear-check rather than a location-check.

## Design goal: protect the ramp-up for new players

A known problem in shared Minecraft worlds: an established player hands a newcomer end-game gear
("here's your diamond kit, have fun"), which is generous but flattens the very progression that
makes the mode interesting, hurting replayability for that new player. The multiplayer variant of
Frontier Mode should actively work against that. The current thinking accepts that **spoilers are
normal in multiplayer** — new players will get help, and veteran players will be out expanding the
frontier — but wants a structure where a newer player still gets a real guardian/boss
difficulty/loot ramp-up to enjoy on their own terms, even inside a world where the "answer" is
already known by others. Gear-based boss scaling (above) is one lever toward that; how well it
actually protects the ramp-up is untested.

## Related pages

- [Frontier Mode Overview](overview.md)
- [Progression & Frontier Mechanics](progression.md)
- [Boss Discovery](boss-discovery.md)
