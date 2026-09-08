---
id: FRO_097
uid: FRO
number: 97
client: FrontierMode
status: open
title: 'New design ready: Exterior (Frontier Sickness, Feral)'
context: Exterior design (Frontier Sickness, Feral) ready. 3 open Qs for Architect
  -- see Summary.
priority: normal
opened: '2026-09-07'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

New design ready: Exterior (Frontier Sickness, Feral)

Game Designer has written up frontiermode/wiki/frontiermode/design/exterior.md -- what lies past the Frontier before it's earned. Covers: Frontier Sickness (an uncapped, distance-driven player debuff that decays per-effect on return), Feral mobs (Guardian-Mobs-style name tag + team glow, distinct color, continuous scaling, no boss tie), FAFO Rule #3 framing (no in-game warning UI, deliberate), three sensory tells, and two new craftable detection items (Edge Stone, Diorite Wick/lantern) on a new common diorite-dust material kept separate from the amethyst family. For Architect: page flags (1) computing distance to the nearest point on the Frontier's irregular multi-Border boundary as real unsolved geometry, (2) Feral's spawn mechanism proposed to start from BossGuardiansFixture's existing spawn-finalization-event hook as a baseline only, (3) a Border Pregeneration interaction question if a player has already altered ground that later falls inside a new Border. Also edited progression.md (removed the now-superseded ambient Frontier marker claim, pointed forward to this page) and nether-and-end.md (added an Exterior-scoping note: Frontier Sickness/Feral are Overworld-only; whether the End gets any equivalent treatment is still an open call). No code access on this end -- routing for Architect/PM triage, not requesting implementation yet.

## Log

- 2026-09-08: PM: minted two RM_FRO nodes for this cluster per the architecture page's own build-sequencing direction -- [RM_FRO_037](../roadmap/RM_FRO_037_brenda.md) ("Brenda," Frontier Sickness core: distance query, Entry Cue, Sick Wildlife, debuff) and [RM_FRO_038](../roadmap/RM_FRO_038_martha.md) ("Martha," Feral). Both wired to [RM_FRO_017](../roadmap/RM_FRO_017_donna.md) directly and both ACTIONABLE now. Edge Stone/Diorite Wick detection items stay parked, not scoped into either node. Not closing this ticket -- item (3) from this ticket's own list (Border Pregeneration vs. player-altered ground) is still untouched, and FRO_098's density-scaling note is folded into the design page but not yet reflected in either new node beyond a mention.
- 2026-09-08: Architect + project owner scoped a build sequence on
  [frontiermode/architecture/exterior.md](../wiki/frontiermode/architecture/exterior.md)'s new
  "Proposed build sequencing" section: first node bundles the distance query with the Entry Cue
  and Sick Wildlife tells plus the Frontier Sickness debuff itself; second node is Feral; Edge
  Stone/Diorite Wick stay parked (not dropped) for a later, art-dependent pass. Opened
  [FRO_098](FRO_098_design-note-for-sick-wildlife-passive-mo.md) to Game Designer with a
  ready-to-stitch note: passive mobs should also spawn more densely with Exterior distance, not
  just glitch once present. Still no `RM_FRO` node minted -- staying wiki-first per the earlier
  call. Item (3) from this ticket's own list (Border Pregeneration vs. player-altered ground) is
  still untouched.
- 2026-09-07: Architect: items (1) and (2) answered in new
  [frontiermode/architecture/exterior.md](../wiki/frontiermode/architecture/exterior.md), draft/proposal
  stage per project-owner direction (Border/Exterior folding into the current Donna epoch, no
  RM_FRO node minted yet). (1) resolved as not actually a hard geometry problem once distance (not
  nearest-point) is the only thing consumed -- see the new page's "distance-to-Frontier query"
  section. (2) Feral's spawn-hook baseline confirmed workable, placed alongside `BordersBundle`
  rather than `BossBundle` since it has no boss to key off. (3), the Border Pregeneration
  interaction question, is **not** addressed by this pass -- still open. Also stitched FRO_085's
  already-ruled paragraph into `boss.md` while in this area (unrelated backlog, done opportunistically).
- 2026-09-07: PM housekeeping: context trimmed to board length standard (was 1356 chars, way over the ~100-char limit in bht.md). Full detail moved into Summary above, unchanged.
- 2026-09-07: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
