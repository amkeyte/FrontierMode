---
id: frontiermode/architecture/special-compass
category: frontiermode/architecture
slug: special-compass
title: Special Compass
summary: 'Dedicated technical shape for the attunement-aware compass item (RM_FRO_033,
  Marilyn): formalizing BorderPathCompass into a self-refreshing Item, the item-NBT
  attunement wrapper, and TargetRef::Dynamic tip-pointing.'
keywords: null
status: draft
updated: '2026-09-07'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · frontiermode / architecture
<!-- bh-header:end -->

# Special Compass

The architecture counterpart to [Boss Discovery](../design/boss-discovery.md#the-discovery-gradient)'s
special-compass entry, and a dedicated page graduated from [Boss Discovery
Systems](discovery-systems.md#special-compass)'s own Special Compass section, the same move
[Border Curve](border-curve.md) and [Border Pregeneration](border-pregeneration.md) each made
once their own proposals firmed up enough to warrant a page of their own. Feeds
[RM_FRO_023](../../../roadmap/RM_FRO_023_kathleen.md) ("Kathleen") via
[RM_FRO_033](../../../roadmap/RM_FRO_033_marilyn.md) ("Marilyn").

**RM_FRO_033 already exists on the roadmap, but stays blocked on the attunement game-rules
pass — see that node's own Blocker section.** Everything below is proposal, not ruling: a
concrete-enough plan to build against the moment that blocker clears, not a description of
anything built yet. The hub page's own Special Compass section remains the shorter, cluster-wide
summary; this page is where the item's own shape gets worked out in full.

## Not hypothetical: the existing coded compass

There's a real, shipped compass to formalize, not a green-field item. `BorderPathCompass` — a
server-only static utility, not an `Item` subclass — builds a vanilla `Items.COMPASS` stack
tagged `FrontierCompass=true` and repoints it by rewriting the vanilla lodestone NBT fields
(`LodestonePos`/`LodestoneDimension`, with `LodestoneTracked=false` so vanilla doesn't try to
manage it) whenever the border tip moves. `BordersTriggers.updateFinderItems` drives it on a
throttled poll — every 5 ticks, via `BordersTriggers.FINDER_ITEMS_UPDATE_MONITOR` — calling
`BorderPathCompass.giveOrUpdate`, which does both jobs today in one call: hand a player a compass
if they don't have one, and rewrite its lodestone fields if they do. It's an externally-triggered
snapshot rebind, not a self-refreshing item — the poll does the work, not the item.

This already went through one robustness pass: [RM_FRO_013](../../../roadmap/RM_FRO_013_judy.md)
("Judy") found `BorderPathCompass.find()` only scanned the main inventory, so a compass sitting in
the offhand slot read as absent and `giveOrUpdate` handed out a duplicate on every poll. Fixed by
scanning offhand alongside the main-inventory loop. Worth knowing before formalizing this into a
real `Item` — the offhand case is exactly the kind of thing a self-refreshing item's own
`inventoryTick()` has to keep working correctly, not just the poll that grants it.

## Proposed: fold into a self-refreshing `Item` subclass

**Settled on the hub page, restated here as the concrete target: a real, self-refreshing custom
`Item` subclass, reusing vanilla's own compass-needle rendering entirely for free** — the needle
already points at whatever `LodestonePos` says, so nothing about rendering changes. On a
throttled `inventoryTick()`, the item reads a small persisted NBT discriminator off its own
stack, reconstructs the matching `TargetRef` fresh (see "TargetRef::Dynamic and Navigator
dispatch" below), resolves it, and rewrites the lodestone fields. One item class, distinguished
only by which discriminator a given stack carries — this is what unifies tip-pointing and
boss-pointing under one roof instead of two parallel item types.

**Throttle cadence: propose reusing Satchel's existing `TickThrottler`** rather than a bespoke
counter, matching the "reuse rather than reinvent" list [Boss Discovery
Systems](discovery-systems.md#navigation-lives-in-border) already names for this cluster
(`BorderSelector`, Boss's validation idiom, `BorderDisplay`/`BossDisplay`'s presentation
pattern). Exact interval is a tuning number, not settled here — same "safe baseline, replace
later" territory as [Environmental Tells](discovery-systems.md#environmental-tells)'s own tick
cadence, though the existing 5-tick `FINDER_ITEMS_UPDATE_MONITOR` interval is a reasonable
starting point precisely because it's already proven not to feel laggy in play.

**What this does to the existing poll: `updateFinderItems`'s job narrows, it doesn't disappear.**
A player without a compass yet still needs one placed in their inventory — that half of
`giveOrUpdate` stays the poll's responsibility. But once the item repoints itself on its own
`inventoryTick()`, the poll no longer needs to *update* an already-held compass — that becomes
this item's own job, not something driven from outside it. The likely shape is
`giveOrUpdate` splitting into a give-only path once this ships, with the update half deleted
rather than left redundant. Flagged here so the two mechanisms' overlap doesn't get missed at
build time — the actual split is Lead Dev's call, not decided on this page.

## Attunement discriminator schema (NBT)

**Illustrative shape, not ratified — the hub page is explicit that this is Lead Dev's call at
build time:** `AttunedTo: "PATH_TIP"` for the tip case, or `AttunedTo: "BOSS", TargetUuid: <uuid>`
for the boss case, read and rewritten by the wrapper below.

**Added here: group these fields under one nested compound tag** (e.g. `frontier:Attunement`)
rather than flat top-level keys on the stack's NBT. Two reasons: it keeps this item's own state
from ever colliding with vanilla's `LodestonePos`/`LodestoneDimension`/`LodestoneTracked` fields
living on the same stack, and it gives the wrapper below exactly one subtree to read, write, and
validate, instead of several independent top-level keys that could partially exist. Still
illustrative — the exact key name and whether `TargetUuid` needs a type discriminator of its own
if a third `AttunedTo` case is ever added (a `Structure` target, say) is open, not resolved here.

## The item-NBT attunement wrapper

**The first concrete built instance of [Universal Sidedness
Facade](../../satchel/architecture/facade-vision.md)'s argument**, per the hub page's own framing
— a thin managed wrapper around this item's NBT access, not raw `ItemStack.getOrCreateTag()`
calls scattered through item-use and tick handlers. No Scope, no Satchel registration for the
storage itself: this is the item-carried half of Attunement (see [Boss Discovery Systems §
Attunement](discovery-systems.md#attunement-item-state-vs-world-state)), which lives in the
stack's own tag, not a fixture.

**Proposed surface**, living alongside the item class rather than in a Satchel fixture:

- `CompassAttunement.read(ItemStack) -> Optional<Discriminator>` — parses the nested compound
  above into a small in-memory shape (which case, plus its payload), or empty if the stack carries
  no valid attunement yet.
- `CompassAttunement.write(ItemStack, Discriminator)` — rewrites the compound. Called wherever a
  player (re-)attunes a compass — the actual trigger (right-click on a target, a crafting/binding
  station, something else) is game-rules territory, not decided here.
- `CompassAttunement.resolve(Discriminator) -> TargetRef` — reconstructs the matching `TargetRef`
  fresh each time it's asked, per "TargetRef::Dynamic and Navigator dispatch" below. Never cached
  across ticks — a `Dynamic` target's embedded supplier can't be persisted, so there's nothing to
  cache safely.

**Not this wrapper's problem: process/world-anchored attunement.** The hub page's Attunement
split names a second lifecycle — a `LevelScope`-hosted fixture for attunement with no item to
carry it (a placed trail effect, say). That's a different consumer's infrastructure (plausibly
[Beacons](../../../roadmap/RM_FRO_031_joan.md), "Joan," which shares this node's own attunement
blocker) — this wrapper only ever manages one `ItemStack`'s own tag.

## `TargetRef::Dynamic` and Navigator dispatch

Recapping the hub page's mechanism, concretely against this item: `TargetRef` gained a `Dynamic`
variant specifically for the tip-pointing case, because the path tip isn't owned by any
registered `TargetType` the way a boss or a border is — it's a live value read straight off
Border's own path-tip tracking, nothing to look up in the resolver registry by UUID. Navigator
special-cases this one variant: if a `TargetRef` is `Dynamic`, it calls the embedded supplier
directly (`() -> BordersPathFacet.getTipCenter()`); otherwise it dispatches through the
`TargetType -> resolver` registry as normal.

**Applied here:** `AttunedTo: "PATH_TIP"` reconstructs `TargetRef.Dynamic(() ->
BordersPathFacet.getTipCenter())` fresh, every `inventoryTick()` — never stored as a `Dynamic`
value, only ever rebuilt from the tag. `AttunedTo: "BOSS", TargetUuid: <uuid>` reconstructs
`TargetRef.Boss(uuid)` and resolves it through the ordinary registry path, the same route any
other `TargetType.BOSS` consumer uses. **This is exactly the corollary the hub page names: a
`Supplier<BlockPos>` cannot be serialized, so `Dynamic` is a pure runtime-only convenience, and
the consumer — this item, via the wrapper above — is responsible for its own lightweight
persisted discriminator and for reconstructing the right `TargetRef` fresh every time, rather than
expecting Navigator to remember a `Dynamic` reference across a save/reload.**

## Distinct from the Tracker

Restated from the hub page since it's this node's own reason for existing as a separate roadmap
item: the Compass is not the [Ender-eye-style
Tracker](discovery-systems.md#ender-eye-style-tracker) wearing a different shell. The Tracker is
stateless and one-shot — filter to relevant candidates, resolve `direction()` once at the moment
of use, keep no state afterward. The Compass holds a real, persisted attunement (the discriminator
above) and keeps resolving it tick after tick. That's a structural difference in what state each
item carries, not a cosmetic one — the argument [RM_FRO_033](../../../roadmap/RM_FRO_033_marilyn.md)
and [RM_FRO_032](../../../roadmap/RM_FRO_032_elizabeth.md) ("Elizabeth") mint as two nodes rather
than one.

## Distinct from Border's own compass-attunement concept

Also restated, because it's easy to conflate given both are called "compass attunement" in
conversation: Border already has its own, differently-shaped attunement concept —
[RM_FRO_006](../../../roadmap/RM_FRO_006_sandra.md)/[RM_SAT_020](../../../roadmap/RM_SAT_020_jerry.md)
describe "which border ID a border-compass is currently attuned to" as per-player,
`PlayerScope`-hosted identity-tied state — one canonical attunement the player carries, not one
per item. This page's item-carried version is a different mechanism for a different reason: N
independent per-item locks (a player can hold more than one compass, each attuned to something
else) versus one canonical per-player lock. **Not this page's call to reconcile.** Whether a
player ends up holding both kinds of compass as genuinely separate mechanics, or the two get
unified later, is part of the same project-owner/Game-Designer attunement pass this whole node is
blocked on.

## Open questions

- **The attunement game-rules pass — the literal blocker on this node.** How attunement is
  granted, whether/how it can change, and cost are all undecided; nothing below moves toward a
  done bar until that pass lands. See [RM_FRO_033](../../../roadmap/RM_FRO_033_marilyn.md)'s own
  Blocker section.
- **The NBT discriminator schema (including the nested-compound proposal above) is illustrative,
  not ratified** — Lead Dev's call once this node is actually ticketed.
- **How `updateFinderItems` actually splits once the item self-refreshes** — give-only poll plus
  self-updating item is the proposed shape; the mechanical split (does the poll shrink in place,
  does `giveOrUpdate` get replaced outright) is Lead Dev's call, not resolved here.
- **The re-attunement trigger itself** — right-click on a target, a crafting/binding station, a
  command, something else — is squarely part of the game-rules pass above, not an architecture
  question.
- **Whether the wrapper needs to guard against a foreign or hand-edited compass stack** — a
  vanilla compass a player crafted that happens to collide with this item's own tag namespace —
  isn't addressed yet; probably a non-issue once this is a real custom `Item` class rather than a
  tag glued onto vanilla `Items.COMPASS`, but worth confirming once it's built.

## Related pages

- [Boss Discovery Systems § Special Compass](discovery-systems.md#special-compass) — the hub
  page's own summary, and the cluster-wide Attunement/Navigator context this page builds on
- [Boss Discovery](../design/boss-discovery.md) — the design intent this page gives further
  technical shape to
- [RM_FRO_033](../../../roadmap/RM_FRO_033_marilyn.md) ("Marilyn") — this page's own roadmap node,
  and its Blocker section
- [RM_FRO_032](../../../roadmap/RM_FRO_032_elizabeth.md) ("Elizabeth") — the Tracker, the sibling
  this item is structurally distinct from
- [RM_FRO_031](../../../roadmap/RM_FRO_031_joan.md) ("Joan") — Beacons, sharing this node's own
  attunement blocker, and the likely home for the process/world-anchored half of Attunement
- [RM_FRO_006](../../../roadmap/RM_FRO_006_sandra.md) / [RM_SAT_020](../../../roadmap/RM_SAT_020_jerry.md)
  — Border's own, differently-shaped compass-attunement concept
- [RM_FRO_013](../../../roadmap/RM_FRO_013_judy.md) ("Judy") — the existing `BorderPathCompass`
  offhand-duplication fix, prior art for keeping this item's own `inventoryTick()` correct
- [Universal Sidedness Facade](../../satchel/architecture/facade-vision.md) — the managed-touch-point
  argument the attunement wrapper is a concrete instance of
- [Utilities](../../satchel/architecture/utilities.md) — `TickThrottler`, proposed reuse for this
  item's throttled `inventoryTick()` cadence
