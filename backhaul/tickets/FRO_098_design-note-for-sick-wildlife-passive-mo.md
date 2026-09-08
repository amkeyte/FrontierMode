---
id: FRO_098
uid: FRO
number: 98
client: FrontierMode
status: done
title: 'Design note for Sick Wildlife: passive mob density scales with Exterior distance'
context: 'Project owner note for Game Designer: small passive animals (rabbits etc.)
  should spawn more commonly the deeper into the Exterior, not just glitch cosmetically
  -- see body.'
priority: low
opened: '2026-09-08'
closed: '2026-09-08'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Project owner note, routed to Game Designer to fold into
[Exterior](../wiki/frontiermode/design/exterior.md)'s "Sick wildlife" bullet under Sensory design:
small passive animals (rabbits, and whatever else that section names) should also spawn *more
commonly* the farther into the Exterior a player goes, not only glitch cosmetically once present.
Today's design describes their behavior (poison particles, hurt sounds, no real stakes) but says
nothing about their population -- this adds a second, purely ambient axis to the same tell: not
just "the wildlife here looks wrong" but "there's more of it than there should be." No new
infrastructure implied -- this reads as a distance-scaled spawn-rate multiplier on the same
passive-mob spawns already covered by that bullet, the same distance value [Exterior
architecture](../wiki/frontiermode/architecture/exterior.md) already proposes exposing for
Frontier Sickness and Feral. Exact curve/threshold is Game Designer's call, same as everything
else in that section.

Ready-to-stitch addition, for wherever Game Designer judges it fits best in that bullet:

> Passive mobs also spawn more densely the farther into the Exterior a player is -- more wrong-looking
> wildlife underfoot is itself part of the tell, on top of each individual mob's own glitching.

## Log

- 2026-09-08: Ticket opened.
- 2026-09-08: Stitched into Exterior's Sick wildlife bullet (design/exterior.md) -- passive-mob density now scales with the same Exterior distance value as everything else on the page, alongside the existing per-mob cosmetic glitching.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
