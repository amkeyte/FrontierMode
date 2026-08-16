---
id: frontiermode/design/overview
category: frontiermode/design
slug: overview
title: Frontier Mode Overview
summary: Core identity, design pillars, and the gradient-not-walls philosophy for
  FrontierMode's game-mode concept.
keywords: null
status: draft
updated: '2026-08-12'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · frontiermode / design
<!-- bh-header:end -->

# Frontier Mode Overview

*First design pass, 2026-08-12 — captured from a brainstorming session with PM/Ziltoid. This is
the primary reference for FrontierMode's creative intent; see [Progression & Frontier
Mechanics](progression.md), [Boss Discovery](boss-discovery.md), [Guardian Mobs](guardian-mobs.md),
and [Nether and End](nether-and-end.md) for the systems that implement this vision.*

## What Frontier Mode is

Frontier Mode sits alongside Survival, Creative, and Adventure as its own selectable game mode —
not a replacement for any of them, not "Minecraft 2.0," and explicitly not an attempt to fix
everything players complain about in vanilla. It keeps the standard survival gameplay loop and
flow intact (punch trees, mine, build, fight) and layers a structured exploration/difficulty
system on top of it. The pitch in one line: a different set of challenges that grow in both
explorable area and difficulty as the player progresses, built from Minecraft's own systems
rather than imported wholesale from somewhere else.

The Nethack influence is real but deliberately secondary right now. Ideas like secret shops are
worth keeping in mind precisely so the core loop doesn't have to be band-aided later to fit them
— see [Nethack Ideas (Parked)](nethack-ideas-parked.md) — but they are not part of the mode's
first buildable shape.

## Core loop (summary)

The player starts in a small area and hunts a boss hidden somewhere inside it. Defeating the
boss expands the playable area and raises the difficulty ceiling, and the loop repeats
indefinitely. Full mechanics — cylinder growth, re-centering, the irregular frontier shape, and
the overlap rule that lets old territory stay safe — live in [Progression & Frontier
Mechanics](progression.md). How players actually find a hidden boss lives in [Boss
Discovery](boss-discovery.md) and [Guardian Mobs](guardian-mobs.md).

## Design pillar: gradients, not walls

Nearly every limit in Frontier Mode should be a gradient the player experiences through play, not
a hard rule enforced against them. The frontier's edge is a visual marker, not an invisible wall —
a player can walk past it, and the world gets ruthlessly harder on the other side, but nothing
stops them physically. Difficulty ramps by distance and by level-age, not by a switch. Discovery
tools scale from ambient and free (guardian mobs, environmental tells) to rare and expensive, not
from "nothing" to "everything" at a fixed point. This principle should be treated as a constraint
on every future mechanic proposed for this mode, not just the ones designed so far.

## Design pillar: teach through play

The mode's discovery systems are built on the belief — associated with Shigeru Miyamoto's design
of Super Mario Bros. World 1-1 — that a level should let players "gradually and naturally
understand what they're doing" well enough to "start to play more freely," and that "creating all
kinds of different reactions to player experimentation" is the real core of good game design. In
practice: a player should be able to learn Frontier Mode's rules by experimenting inside it,
without reading a wiki or a walkthrough. That matters beyond onboarding — it's also what lets a
spoiler-averse player experience the mode's exploration and discovery loop as intended, the same
way a first-time Mario player learns to jump without a tutorial screen. (Note on sourcing: this
framing is recalled from memory and attributed to Miyamoto's discussion of World 1-1's design;
the exact wording above is the closest independently verifiable phrasing found, not a pinned
direct quote — flag if a more precise source turns up later.)

## Difficulty and death

Frontier Mode does not override Minecraft's existing difficulty settings or death consequences.
Peaceful, Normal, Hard, and Hardcore all remain valid ways to play, and players should be free to
set their own comfort level — Hardcore included, for players who want that tension. If a future
mechanic wants to tie a consequence to the frontier itself (losing progress, being pushed back a
level, etc.), that would be a deliberate, separate design decision, not an assumed default.

## Audience

Target audience is PG. The mode should read as legible and fair to a player who's never touched
Nethack, while still rewarding the kind of obsessive, thorough play that Nethack veterans enjoy —
see [Progression & Frontier Mechanics](progression.md) for where that tension shows up concretely
(the "boss can spawn in old territory" rule). A player who doesn't want that tension at all has an
out: Peaceful difficulty, or simply not chasing the frontier's edge. The hard line for any
proposal under this mode: if understanding a mechanic requires already knowing Nethack, it needs a
Minecraft-native reframe, not a glossary entry.

## Scope

This design is being built single-player first, deliberately. Multiplayer raises real open
questions — whose frontier is it, what happens when players expand it in different directions at
once — that would compromise the single-player design if solved prematurely. Early multiplayer
thinking is captured separately and is **not** part of the mode's current design scope; see
[Multiplayer Sketch (Parked)](multiplayer-sketch.md).

## Related pages

- [Progression & Frontier Mechanics](progression.md)
- [Boss Discovery](boss-discovery.md)
- [Guardian Mobs](guardian-mobs.md)
- [Nether and End](nether-and-end.md)
- [Multiplayer Sketch (Parked)](multiplayer-sketch.md)
- [Nethack Ideas (Parked)](nethack-ideas-parked.md)
- [FrontierMode mod summary](../frontiermode.md)
- [Border architecture](../architecture/border.md)
