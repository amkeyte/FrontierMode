---
id: frontiermode/design/discovery-structures
category: frontiermode/design
slug: discovery-structures
title: Discovery Structures (Draft)
summary: Player-built/environmental boss-locating structures -- a beacon-like Mysterious
  Temple, ley lines, a redstone tracker, and a repurposed vanilla treasure map --
  being workshopped.
keywords: null
status: draft
updated: '2026-09-06'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · frontiermode / design
<!-- bh-header:end -->

# Discovery Structures (Draft)

*First capture, 2026-09-06, workshopped the same day. This is the workshop-in-progress page for a
possible new tier of [Boss Discovery](boss-discovery.md#the-discovery-gradient) tools, sitting
above Special Compasses on the cost/precision end of the gradient: player-built or environmental
structures rather than craftable items, plus the Treasure Map (a portable item, but born from this
same workshop thread). Nothing here is locked. The Treasure Map's original mechanic (an item frame
under the Temple's beam) didn't survive the Temple redesign below and was replaced entirely with a
much simpler reuse of vanilla's own treasure-map system — see its own section.*

## Why these are a different category from the existing six tools

[Boss Discovery](boss-discovery.md#the-discovery-gradient)'s existing gradient is all portable —
an item in a hand or a hotbar. These are the opposite: a Mysterious Temple's central crystal is
explicitly non-collectible once placed, ley lines are terrain the player doesn't build at all, and
the redstone tracker is a base fixture that only works while tended (though it can at least be
rebuilt elsewhere for free — see its own section). That's a meaningful design shift worth naming
up front: these ideas trade portability for either permanence (the Temple's central crystal, ley
lines) or ongoing upkeep (redstone tracker) — a different kind of cost than "spend resources once,"
and one that should probably get its own place on the gradient rather than being folded into the
existing six-tool list as written.

## Shared material: geode components

**Decided (2026-09-06):** geode-sourced materials (amethyst) are a requirement across these
structures, not a one-off ingredient for a single tool — one resource-gathering target unlocks the
whole family instead of unrelated material lists, and it's thematically coherent besides
(Minecraft already treats geodes as "something rare and crystalline is buried here"). **Resolved
by the Temple redesign below:** the Temple's central crystal is confirmed amethyst-based (amethyst
block, quartz-surrounded). The Treasure Map question is moot — see its own note below, it's spun
off this page entirely.

## Mysterious Temple

Reworked 2026-09-06 into something closer to a vanilla **beacon** than the original sunbeam pitch:
still the most accurate tool on the whole gradient, but now time-dependent, immovable, and
expensive in a way that scales continuously with investment, the same way a beacon's pyramid tier
scales its power.

**Structure:** a single rod at the center, topped with a non-collectible crystal. Around it, a
platform hosts a ring of purpose-built pillars — each three blocks tall, capped with a special
block that can glow. When the time is right, a beam runs from the central rod to one pillar, and
that pillar's cap lights up — the lit pillar's position relative to center is the bearing to the
active boss.

**Timing, resolved (2026-09-06): sunrise, modulated by the moon cycle.** Anchoring to sunrise
keeps the trigger itself vanilla-legible — players already understand day/night as a real game
system, no new clock to teach. The moon-phase link is what supplies the "mystery and variation"
this was always meant to have (Minecraft already tracks eight moon phases for its own purposes, so
this rides an existing vanilla system rather than inventing one) — exactly how phase affects the
beam (which sunrises it fires on, how bright/accurate the reading is, something else) is still
open and probably a balance question once there's something to test against.

**Platform pattern, resolved (2026-09-06): glazed terracotta.** Each of the 16 colors carries its
own directional pattern that lines up with its neighbors depending on rotation, so a defined grid
of specific colors/orientations gives the platform a real "recipe" — exact blocks in exact
positions — rather than just a texture choice. Sourcing is deliberately uneven, not a flaw to fix:
found as loot in ruined-Temple partial builds (see "Discovery" below — the same ruins that teach
the build also sometimes supply its floor), or naturally in savanna villages if a player's stumbled
into that biome. **That unevenness is the point, not a gap to patch:** it ties this structure's
buildability directly to the same lever [Progression & Frontier
Mechanics](progression.md#resource-and-reward-density) already names — biome variation as a reward
for pushing the frontier outward — rather than making every material available from day one
regardless of where a player's explored. A player who hasn't found a savanna yet, or gotten lucky
with ruins, genuinely can't build the most accurate version yet, and that's exactly the incentive
structure the whole mode is built around.

**Precision formula: platform size buys pillar slots, filled pillars buy resolution.** A larger
platform has room for more pillars around its ring; the number of pillars actually built determines
how many discrete directions the structure can indicate — more pillars means finer azimuth
resolution, the same "compass gains points as you invest more" idea from the original pitch, now
grounded in an actual buildable mechanic instead of a vague size/elaborateness scale.

**Materials, several options floated for playtest rather than one locked answer:**

- **Central crystal:** an amethyst block surrounded by quartz blocks — deliberately hard to
  gather, matching its non-collectible, one-per-structure role. Not collectible once placed
  (whether silk touch bypasses that is still TBD).
- **Pillar cap blocks:** expensive, but obtainable as a drop rather than gated behind the same
  scarcity as the central crystal. Exact recipe still open — a calcite-based craft is one option;
  another scales off geode-finding directly (a found geode yielding enough material for roughly
  three or four pillars, via some multi-step compacting chain); an emerald block pairing is a
  possible balance lever if either path needs tightening. All three explicitly playtest territory,
  not decided here.

**Discovery and acquisition: rare partial ruins — resolved as the main path, not just a teaching
tool (2026-09-06).** Naturally-generated partial/ruined Temple structures, the same concept as
vanilla's ruined portals but noticeably rarer. They teach the build the way any repeated-exposure
ruin does — found once or twice, they don't mean much; found two or three times, the pattern
becomes legible without a wiki. But given how deliberately hard the central crystal is to craft
from scratch, **a ruin that already has one standing is likely to be most players' actual route to
owning a working Temple at all**, not just a lesson on the way to building one from raw materials.
This falls directly out of the precision formula above rather than needing its own special rule: a
partial ruin is just a small, already-functional Temple — whatever platform/pillars it happens to
have intact work exactly as a deliberately-built structure that size would, at correspondingly low
precision — and "repairing" it is identical to expanding any Temple: add platform and pillars,
gain resolution. No separate partial-function mechanic needed. Ruins may also rarely drop the
special blocks (especially platform glazed terracotta) — finding one in the wild unlocks its
recipe the same way the [Ley Lines](#ley-lines) divining rod's ingredient does, keeping one
consistent "find it before you know what it's for" discovery pattern across this whole page.

**Open questions for workshop:** the exact glazed-terracotta layout and its size-to-pillar-slot
curve; and exactly how moon phase modulates the sunrise trigger (see Timing above) — the mystery
is intentional, but the underlying rule still needs an answer eventually.

## Treasure Map

Originally "place a map in an item frame under the Temple's beam." Spun off into its own mechanic
once the Temple redesign made that placement impossible — the new Temple's beam terminates at a
pillar on the platform rather than shooting an external beam through open space. Picked back up
and resolved much more simply, 2026-09-06.

**Mechanic: vanilla's own treasure-map system, unmodified.** No item frame, no beam, no custom
logic — this just is a treasure map, the same X-marks-the-spot item vanilla already has, retargeted
to mark the active boss's location instead of vanilla loot. Reusing an existing, already-legible
vanilla system outright is the simplest possible version of "gradients, not walls": nothing new
for a player to learn about how the item itself works, only what it points at.

**Two acquisition paths, resolved 2026-09-06:**

- **Buried treasure, not shipwrecks.** Vanilla's own buried treasure maps are normally found in
  shipwrecks and lead you to a buried chest. This mod's version inverts that: it's found as a rare
  bonus item *inside* a buried treasure chest itself — so digging up an ordinary treasure hunt has
  a chance to hand you the lead into a much bigger one. A layered discovery on top of a system
  players already engage with, not a new hunt from scratch.
- **Trade, at a price.** A Cartographer villager (matching their existing maps profession in
  vanilla) has a rare chance to offer this map as a trade once leveled to Master, for a steep
  price — a slower but reliable path for a player who'd rather grind an economy than dig, gated
  behind the same villager-leveling investment vanilla already uses for its best trades. (Assumed
  Cartographer specifically, since they already sell explorer maps — flag if a Wandering Trader
  was meant instead.)

This gives Treasure Map the same "more than one road in" shape as everything else on this page —
craft-it-yourself vs. find-it-in-a-ruin for the Temple's crystal, chest loot vs. general
availability for the divining rod, and now dig-for-it vs. trade-for-it here.

**Resolved (2026-09-06): frozen snapshot.** A map only ever points to wherever the boss was at the
moment the map was created — it does not update if the boss's location later changes (relevant
once [Border Pregeneration](../architecture/border-pregeneration.md)'s bounded-wander movement
ships; bosses don't move today). This is a real, deliberate gap between the map's information and
the boss's current position once movement exists — a stale map is a worse map, not a broken one,
which is arguably the more interesting version anyway. Still assumed but not explicitly confirmed:
it points at whichever boss is currently active (the path tip's) at creation time, matching every
other tool on this page, rather than any historical boss.

## Ley Lines

Concentric rings radiating outward from a boss's location — not built by the player at all, an
environmental feature of the terrain near a boss. Workshopped past the original pitch into shared
infrastructure other tools can key off, not just a standalone crop gimmick.

**Geometry (resolved 2026-09-06):** rings sit at a fixed interval — evenly spaced, not growing
non-linearly — but the interval itself scales with the path tip's Border size, so a small early
Border gets tightly-packed rings and a huge late-game one gets widely-spaced ones, keeping visual
density roughly consistent regardless of how far the frontier's grown. **Gated to mid-to-late game
only** by a minimum path-tip size below which ley lines simply don't generate — the actual
threshold is a tuning number, not decided here.

**Anchoring (resolved 2026-09-06):** tied to the boss's spawn location, exactly like every other
Environmental Tell — not a live-following effect, even though a moving boss would make re-centering
sensible in principle. Bosses don't move today, and "spawn-anchored, same as any Tell" is the
simpler, already-established pattern; if boss movement ever ships, that's a separate future
question, not something this design needs to anticipate now.

**Discovery: the divining rod.** A sword-shaped item (same crafting-pattern silhouette players
already recognize), built from amethyst, whose recipe unlocks via picking up an uncommon chest
drop in existing vanilla structures — the standard "new recipe available" hook vanilla already
uses, so a player finds the ingredient before they know what it's for. Detects nearby ley lines
once crafted. This is the resolution to discovery's chicken-and-egg problem: the rod is a
discovery path that doesn't require already knowing a ley line exists.

**Crops:** kept. Ley-line crops get distinct particles and a harvest bonus (exact bonus TBD). The
payoff worth designing toward explicitly, not leaving as an emergent accident: **a big enough
field basically becomes a compass in its own right** — plant across enough rings and the pattern
of glowing/bonus crops visually traces the boss's direction and rough distance, no rod or reading
required. Costs nothing new to build (just crop density plus the particles above), and gives the
gradient a satisfying top end: enough investment and the land itself tells you everything.

**Shared infrastructure:** other discovery structures can be built anywhere, but get a bonus when
built on a ley line — see [Redstone Tracker Contraption](#redstone-tracker-contraption) below for
the first case. Worth extending to the Mysterious Temple too rather than leaving it
redstone-tracker-specific, so ley lines give every structure on this page a reason to care where
they're built, not just one.

## Redstone Tracker Contraption

A redstone contraption that, once built and powered, periodically fires an item that flies in the
general direction of the active boss.

**Structure, added 2026-09-06:** a dispenser pointed straight up, which must be open to sky above
it — a real site requirement, same category as the Temple needing a place to stand, just lighter.
A distinct signal periodically triggers it to fire an object — a special firework charge is the
working idea — up and out toward the boss's bearing.

**Trajectory, resolved (2026-09-06): aimed-once, not homing.** Confirms the "ender eye, not a
guided missile" option — the charge fires toward the boss's home block's general bearing and
doesn't course-correct in flight, traveling as far as player render distance allows unless it hits
something first. **Beamwidth ties directly into the Ley Lines bonus:** the shot has some angular
spread around that bearing, and building on a ley line narrows it — the closer/better the line, the
tighter the cone, giving the ley-line bonus a concrete mechanical shape instead of a vague
"more accurate" label.

**Firing condition, resolved (2026-09-06):** all three must hold at once — the contraption is
running, the dispenser is loaded (has charges in it), and the amethyst dust (see below) is
actively pulsing, not just steadily powered. **Plain redstone deliberately does nothing special**
— wire it with ordinary redstone instead of amethyst dust and the dispenser just dispenses
normally, no boss-tracking behavior at all. The special behavior is gated behind using the
mod-specific signal type, not available by accident from a vanilla circuit.

**Amethyst dust as a redstone-equivalent, plus a fuel-reading comparator — flagged for Architect
as one combined system, not decided here.** The pitch: a purple, amethyst-based wire carrying
signal the way redstone dust does (potentially shared across the whole geode-material family — see
the Temple's crystal and the Ley Lines divining rod), paired with a new comparator-format block
that reads the furnace/composter fuel state described below and converts it into circuit power on
the same 0-15 scale redstone already uses. Whether either half is a light reskin of existing
redstone behavior or needs new signal-propagation logic entirely is a real feasibility question —
outside what I assess (no source access, and buildability calls are Architect's territory, not
Game Designer's). Worth routing to Architect directly for an actual answer; captured here so the
design intent isn't lost either way. Two tuning questions ride alongside it once it's built: whether
power level at the dispenser should have its own effect (range? accuracy? not yet decided), and
what pulse rate gives the "optimal" firing cadence — both playtest territory, not architecture
questions.

**Chunk-loading requirement, flagged for Architect (2026-09-06):** the contraption should keep
operating as long as its own chunk is loaded — it shouldn't need the boss's chunk, or anything in
between, loaded to fire. Separately, a player chasing the fired charge on foot needs to be able to
follow it all the way to the boss's location without losing it to unloaded terrain in between. This
is the same category of problem [Boss](../architecture/boss.md#the-central-fact-that-shapes-this-whole-design)'s
whole design had to solve for a boss's own chunk usually not being loaded — worth pointing Architect
at that page as prior art rather than treating this as a new class of problem, but the actual
answer for a moving projectile (rather than a stationary boss record) still needs their call.

- **Build:** requires mod-specific craftable components — a one-time construction cost, same
  category as the other tools' upfront price. **Rebuildable without new materials (resolved
  2026-09-06):** unlike the Temple, this one isn't a permanent commitment to a site — a player can
  dismantle and reassemble it elsewhere without paying the craftable cost again.
- **Operation:** requires ongoing fuel — a furnace that has to stay lit, and a composter that has
  to be kept fed so it doesn't run dry. Unlike every other tool on this page and the existing
  gradient, this one has a **running cost** on top of its build cost: the player has to keep
  tending it, not just build it once and walk away.
- **Ley-line bonus (resolved 2026-09-06):** more accurate when built on a ley line than off one —
  see [Ley Lines](#ley-lines) above. This is what makes relocating it (free, per the rebuild rule
  above) an actual decision rather than a pure downgrade-avoidance move: off a line is portable and
  fine, on a line is better but ties the tracker to wherever that line sits.

This upkeep requirement is a genuinely different shape of cost from anything else in the discovery
gradient so far — worth deciding whether that's a feature (it ties a discovery tool into the
base-building/farming loop, which nothing else on this page does) or an added-friction problem for
what should otherwise read as "the reliable, if slow, option."

**Open question for workshop:** confirming the fuel mechanic itself — does the composter need to
be kept *from* finishing (i.e., it must never fill all the way, which is the reverse of how a
composter with a mod-specific twist might work) or does it need to be kept *fed* so it doesn't run
empty, the same shape as furnace fuel? The phrasing this was pitched with reads ambiguously either
way, and the two versions play very differently.

## Related pages

- [Boss Discovery](boss-discovery.md) — the discovery gradient this whole page extends
- [Guardian Mobs](guardian-mobs.md) — the other ambient/environmental discovery mechanism, closest
  in spirit to Ley Lines above
- [Border Pregeneration](../architecture/border-pregeneration.md) — the future bounded-wander boss
  movement Ley Lines' spawn-anchoring decision explicitly chose not to depend on yet
- [Boss](../architecture/boss.md) — prior art for the redstone tracker's chunk-loading question:
  the same "usually not loaded" problem, already solved once for a stationary boss record
- [Frontier Mode Overview](overview.md) — the "teach through play" and "gradients, not walls"
  pillars these ideas are being pressure-tested against
