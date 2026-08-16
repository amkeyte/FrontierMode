---
id: frontiermode/design/nether-and-end
category: frontiermode/design
slug: nether-and-end
title: Nether and End
summary: How the Nether and End tie into frontier progression via the player's highest
  attained level rather than their own spatial frontier.
keywords: null
status: draft
updated: '2026-08-12'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · frontiermode / design
<!-- bh-header:end -->

# Nether and End

*See [Progression & Frontier Mechanics](progression.md) for the overworld frontier system this
page rides on instead of duplicating.*

## Difficulty axis: highest attained level, not their own geometry

The Nether and the End do not get their own spatial frontier — building an equivalent
cylinder/expansion system for two more dimensions isn't the goal. Instead, their difficulty is
tied to **the player's highest attained frontier level so far**, tracked as a single value. In
effect they ride along with overworld progression as a difficulty gate rather than a place, which
keeps them "part of the frontier" conceptually without needing their own geometry.

## Portals

The original instinct was to force nether portals to be built only from the Overworld side and
make them indestructible, closing off the classic vanilla trick of using nether travel as a
shortcut to skip overworld exploration. That's been reconsidered: it cut a well-liked, ordinary
vanilla utility (using the Nether to fast-travel between two Overworld points), and it's not
actually necessary once Nether/End difficulty rides on the player's highest attained level instead
of the frontier's shape.

The current thinking: portals can be built more freely, including from the Nether side, and the
"don't let this bypass exploration" job is handled by risk instead of a rule. A portal built in
the Nether links back to an Overworld location the normal vanilla way — if that location happens
to fall outside the player's actual established frontier (i.e., somewhere their progress hasn't
earned yet), stepping through is just an ordinary, self-inflicted death trap: the world there is
exactly as dangerous as its real frontier level says it is, no different from walking there on
foot. No hard constraint is needed to "keep portals inside the border" — the border's own danger
gradient does that job. This is consistent with the mode's "gradients, not walls" pillar.

**Resolved (2026-08-12):** portals are vanilla — buildable and destructible from either side, no
special rule. The original indestructible/overworld-only restriction is fully superseded by the
death-trap-risk framing above; the border's own danger gradient does the job that restriction was
trying to do, so no portal-specific behavior is needed at all.

## Related pages

- [Progression & Frontier Mechanics](progression.md)
- [Frontier Mode Overview](overview.md)
