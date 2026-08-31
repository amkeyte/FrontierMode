---
id: frontiermode/design/guardian-mobs
category: frontiermode/design
slug: guardian-mobs
title: Guardian Mobs
summary: Stronger, visually distinct hostile mob variants that cluster near bosses
  as a passive discovery aid and tension ramp.
keywords: null
status: draft
updated: '2026-08-31'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · frontiermode / design
<!-- bh-header:end -->

# Guardian Mobs

*See [Boss Discovery](boss-discovery.md) for where this fits in the wider discovery gradient, and
[Progression & Frontier Mechanics](progression.md) for the loop it supports.*

## What they are

Guardian mobs are varieties of normal hostile mobs — stronger than the ambient standard for their
Border, and visually distinct so a player can recognize one on sight. They spawn more commonly the
closer the player gets to a boss.

## Why they exist (two jobs at once)

1. **Discovery aid.** Rising guardian density gives the player a natural, always-available, no-cost
   way to tell "I'm getting closer" without needing a special item or reading a wiki — the same
   kind of environmental signal Minecraft already uses for ore veins or structure proximity. This
   is a direct expression of the mode's "teach through play" pillar: the rule ("more/tougher
   guardians means the boss is near") is meant to be learned by noticing it, not by being told.
2. **Tension ramp.** Beyond pure information, guardians double as an escalating gauntlet — a
   climactic build-up that coordinates with the longer arc of frontier growth, so approaching a
   boss feels like a ramp-up rather than a flat walk followed by a sudden fight.

## When they're introduced

Guardian mobs are **not** part of the starting Border (level 1, to the player). Early Borders are
small enough (20-block radius at the start) that there's no meaningful "getting warmer" gradient
to build — the boss is already close to everything. Guardians are introduced starting at a later
Border, once the play space is large enough for density-based signals to mean something. This
lines up with a broader intent for the mode: complexity should ramp up alongside physical space,
not be front-loaded.

**Design obligation this creates:** whichever Border first introduces guardian mobs inherits the
same responsibility the starting Border would have had — that Border needs to overcommunicate the
"guardians mean boss nearby" association clearly and repeatedly, so the player actually learns the
heuristic the first time it's relevant, rather than encountering subtlety before the underlying
rule was ever taught.

[Boss Discovery Systems](../architecture/discovery-systems.md#guardian-mobs), the
architecture counterpart to this page, has `BossGuardiansFixture` and its placement/difficulty
curves built out already, and names this as one of two open questions still owed to Game
Designer. An actual Border number (or a rule for picking one) is needed here, not just "later."

## Open question: guardians in old territory

[Progression & Frontier Mechanics](progression.md#bosses-can-appear-in-old-territory) flags an
open design item: when a boss lands in an already-old, low-difficulty ring (via the oldest-ring-wins
overlap rule), something needs to signal that danger, ideally in-world rather than via UI. Guardian
mobs appearing somewhere they normally wouldn't (an old, low-difficulty Border — a safe pocket to
the player — suddenly showing tougher, visually distinct mobs) is a plausible candidate for part
of that signal — noted here as a possible answer, not a locked one. Needs to be resolved alongside
the rest of that open item. [Boss Discovery Systems § Guardian
Mobs](../architecture/discovery-systems.md#guardian-mobs) names this as the second of the two
questions still owed to Game Designer, alongside the introduction-Border question above.

## Related pages

- [Boss Discovery](boss-discovery.md)
- [Progression & Frontier Mechanics](progression.md)
- [Frontier Mode Overview](overview.md)
- [Border Vocabulary](../architecture/border-vocabulary.md) — why "Border" replaces "level" here
  as the mechanic term.
- [Boss Discovery Systems](../architecture/discovery-systems.md) — `BossGuardiansFixture`'s data
  model and spawn-time mechanism; names both open questions on this page as owed to Game Designer.
- [Border Curve](../architecture/border-curve.md) — the placement/difficulty intensity curves this
  mob's spawn rate and stat scaling read from.
