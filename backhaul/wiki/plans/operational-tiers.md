---
id: plans/operational-tiers
category: plans
slug: operational-tiers
title: FrontierMode Operational Tiers
summary: The experience-tier framework FrontierMode's roadmap convergence nodes are
  organized around, instead of one convergence per module.
keywords: null
status: published
updated: '2026-08-16'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../BACKHAUL.md) · [Wiki Index](../../WIKI_INDEX.md) · plans
<!-- bh-header:end -->

# FrontierMode Operational Tiers

Goal: give FrontierMode's roadmap convergence nodes a legible, player-facing meaning instead of
"module X is done." A module-by-module convergence (Border done, Guardian Mobs done, ...) answers
whether a piece of code exists; it doesn't answer whether the game is actually playable yet — and
the design itself doesn't cut cleanly along module lines. Border is explicitly the ecosystem's
proof-of-concept module, and nearly everything else FrontierMode's design describes (bosses,
guardian mobs, discovery tools, loot density, Nether/End gating) is defined in terms of *level* or
*distance from origin* — one continuous experience gradient, not a set of independent features
(see [Frontier Mode Overview](../frontiermode/design/overview.md) and [Progression & Frontier
Mechanics](../frontiermode/design/progression.md)).

Individual `work`/`convergence` roadmap nodes for actual implementation stay exactly as they are —
module-scoped nodes are still the right shape for real work (see [BHRM — Roadmap
Conventions](../meta/bhrm.md)). What this page changes is which nodes a *tier* convergence
gathers: a tier convergence crosses module lines on purpose, pulling together whatever work —
regardless of which module it technically lives in — is needed to cross that experience threshold.

## Why this page, not just the roadmap

Unlike [Full Documentation Coverage Plan](doc-coverage.md), this isn't an alternative to roadmap
tracking — the tiers themselves *are* real `bhrm` convergence nodes, gaining real `depends_on`
edges as the underlying work gets scoped. What lives here is the rationale: why these particular
boundaries, in this order, so a future tier convergence node's own body doesn't have to re-derive
or re-argue the framework from scratch each time one opens. Same relationship `meta/bhrm.md` has
to any individual roadmap node — the node cites the convention, it doesn't restate it.

## The tiers

**Tier 0 — Substrate operational.** The underlying runtime and data model work end-to-end and are
hardened: Satchel's foundation/jig/scope runtime, and Border's world-border mechanics built on it.
This is infrastructure, not gameplay — a player dropped into a fresh world at this tier has
nothing to actually do yet, since nothing currently calls `BorderAPI.grow()`/`addBorder()` except
an admin command and a debug trigger (see [Border-Frontier
Reconciliation](../frontiermode/architecture/frontier-reconciliation.md)'s "missing caller, not a
missing capability" finding).

**Tier 1 — Core loop operational.** The loop [Frontier Mode
Overview](../frontiermode/design/overview.md) describes is actually playable, even crudely: a boss
exists somewhere in the level, can be found (by exploring — no discovery aids required yet), can
be killed, and killing it grows the border and spawns the next one. This is the single most
legible milestone in the project — the point FrontierMode stops being infrastructure and becomes a
game someone could sit down and play. Needs work that doesn't exist as roadmap nodes yet: a boss
entity/spawn system, and a defeat-detection caller into `BorderAPI.addBorder()` (already able to
accept an arbitrary center, per the reconciliation finding above — new-caller work, not
new-capability work).

**Tier 2 — Guided loop operational.** [Boss Discovery](../frontiermode/design/boss-discovery.md)'s
clue gradient and [Guardian Mobs](../frontiermode/design/guardian-mobs.md) exist, so finding a
boss is a designed, teach-through-play experience (ambient density, environmental tells, beacons,
ender-eye-style tracking, compasses) instead of blind wandering. Also where guardian-mobs.md's two
open questions — which level first introduces them, how they signal a boss that landed in old
territory — get resolved.

**Tier 3 — Full progression curve operational.** Reward/loot density scaling with distance
([Progression & Frontier
Mechanics](../frontiermode/design/progression.md#resource-and-reward-density)), the still-open
"boss significantly exceeds ambient difficulty" fairness signal, [Nether and
End](../frontiermode/design/nether-and-end.md)'s highest-attained-level gating fully wired, and
boss variety/tells (boss-discovery.md's TBD second discovery axis). This is where the mode's
economy and pacing are real, not just the bare loop.

**Tier 4 — Social operational.** Multiplayer. Explicitly parked — see [Multiplayer Sketch
(Parked)](../frontiermode/design/multiplayer-sketch.md) — not scoped into a real convergence node
until "whose frontier is it" gets unparked. Named here only so the sequence has a placeholder and
isn't rediscovered from scratch later.

## Per-player evaluation doesn't map to one tier

[RM_FRO_006](../../roadmap/RM_FRO_006_sandra.md) (per-player border evaluation, blocked on
Satchel's [RM_SAT_020](../../roadmap/RM_SAT_020_jerry.md)) is Tier-0-shaped *work* — it's building
a capability, not a player-facing feature — but its actual payoff (a buff-modified applicable
layer, a border-compass's attunement) likely doesn't get exercised until Tier 2 or 3 content
exists to use it. A capability and its payoff don't have to land in the same tier; RM_FRO_006 sits
in Tier 0's convergence because that's where the *work* belongs, not because that's where it
becomes meaningful to a player.

## Where each tier is tracked

- **Tier 0**: [RM_FRO_014](../../roadmap/RM_FRO_014_shirley.md), gathering
  [RM_FRO_010](../../roadmap/RM_FRO_010_susan.md) (hardening) and
  [RM_FRO_006](../../roadmap/RM_FRO_006_sandra.md) (per-player).
- **Tiers 1-3**: no convergence node yet — no work exists under `RM_FRO` for a boss, discovery, or
  progression module, so there's nothing real to gather. Per this project's existing practice for
  exactly this situation (see [Universal Sidedness
  Facade](../satchel/architecture/facade-vision.md)'s own "real work nodes get inserted as pieces
  of this get scoped, not invented wholesale now"), these convergences get created once real
  sibling work exists, not as empty placeholders now.
- **Tier 4**: not scoped; see above.

## Related pages

- [Frontier Mode Overview](../frontiermode/design/overview.md)
- [Progression & Frontier Mechanics](../frontiermode/design/progression.md)
- [Border architecture](../frontiermode/architecture/border.md)
- [BHRM — Roadmap Conventions](../meta/bhrm.md)
