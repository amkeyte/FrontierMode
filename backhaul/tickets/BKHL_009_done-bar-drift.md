---
id: BKHL_009
uid: BKHL
number: 9
client: Backhaul
status: done
title: done-bar text drifts from the architecture page it's supposed to be checked
  against
context: FRO_043's done bar (copied from RM_FRO_018) still promised a self-heal mechanism
  boss.md had already walked back to an open question. Nothing caught it until a session
  happened to cross-check by hand.
priority: normal
opened: '2026-08-24'
closed: '2026-08-24'
---

<!-- board:start -->
<!-- board:end -->

## Summary

A roadmap node's "Done bar" is meant to be the pass/fail bar Lead Dev builds against, and per this
project's own convention (stated directly in [FRO_043](FRO_043_boss-build.md)) an architecture wiki
page is meant to be authoritative over a node's prose the moment the design settles. Nothing ties
those two facts together. When a wiki page's design narrows after the done bar was written, the
done-bar text doesn't get flagged — it just sits there promising something the settled design no
longer delivers on its own, until someone happens to reread both side by side.

That happened for real this cycle, not hypothetically.

## What actually happened

[RM_FRO_018](../roadmap/RM_FRO_018_shirley.md) ("Shirley")'s log, bullet 4 (written 2026-08-16),
proposed a **recurring** `BORDERS_JIG`-tick check — "does the path-tip Border have a live boss? If
not, spawn one" — and claimed directly that this would self-heal a `/kill`ed boss even without a
`LivingDeathEvent` listener. That claim made it into the node's own **Done bar** line verbatim: "a
`/kill`ed boss triggers the bootstrap catch-up to respawn one."

[Boss](../wiki/frontiermode/architecture/boss.md), promoted to `verified` during
[FRO_042](FRO_042_shirley-prep.md) (architect prep), settled on a **different, narrower** mechanism:
a one-shot `seeded`-flag bootstrap that only fires once, on a level's first-ever border. `boss.md`'s
own "What can actually go wrong" section explicitly calls the `/kill` self-heal case "an open design
question, not an assumed answer" and punts it to
[RM_FRO_019](../roadmap/RM_FRO_019_karen.md) ("Karen," not yet built).

Nothing about promoting `boss.md` to `verified` touched RM_FRO_018's done-bar line. **FRO_043 was
written and opened with that stale done-bar text copied in unchanged**, and Lead Dev only caught the
mismatch mid-build, by independently reading both `boss.md` and the node's own bullet-4 history
side by side and noticing they no longer agreed. That comparison isn't performed by any tool in this
project — it happened because a session was asked, after the fact, "are we ready to close this
ticket," and went looking.

FRO_043 closed with the gap accepted and logged rather than blocking on it (the correct call here),
and [FRO_044](FRO_044_karen-prep.md) — opened afterward — now carries "reconcile this" as one of its
own open items. So the drift did get caught, twice over, but only because two separate sessions each
independently chose to cross-check prose against prose. Neither check was structural.

## Why this is a different class of problem than BKHL_007

[BKHL_007](BKHL_007_lint-routine.md) is about `backhaul lint` catching broken links and orphaned
pages — structural drift. This is semantic drift: every link involved here was live and valid the
whole time. `boss.md` correctly links to `RM_FRO_019`, RM_FRO_018's done bar correctly quotes itself
— nothing was broken in the sense lint checks for. The thing that went stale was a factual claim
("this respawns after `/kill`") that a separate page had already superseded. No amount of link
validation catches that; it needs some notion of a done-bar clause being tied to the specific design
section it depends on, so that a later edit to that section can at least surface "this done bar
references design that just changed" for a human to judge.

## Suggested direction, not a committed design

1. **A lightweight linking convention**, not a smart checker: a done-bar bullet that depends on a
   specific wiki section could cite it inline (as this project's prose already does everywhere
   else — `boss.md#known-gaps`-style anchors), and `backhaul lint` (once BKHL_007 exists) could flag
   *any* done-bar bullet with zero such citations as worth a second look, without trying to parse
   whether the cited section still agrees.
2. **Cheaper and maybe sufficient on its own: a written rule.** Add to
   [BHW — Wiki Conventions](../wiki/meta/bhw.md) or
   [BHRM — Roadmap Conventions](../wiki/meta/bhrm.md): promoting a design page to `verified` after a
   done bar already exists for the same node is the specific moment to reread that done bar, the
   same way this project already has a named moment for "update the architecture page right after
   you implement something." A rule without a mechanism is what BKHL_007 already found doesn't hold
   up by itself (see its own "re-applied by hand every few weeks" finding for `bhw`'s two rules) —
   worth being honest that this suggestion has the identical weakness, not a reason to skip stating
   it.
3. **Don't try to auto-detect semantic contradiction.** A checker that tried to decide whether
   `boss.md`'s prose still supports a given done-bar claim would be guessing at meaning, not
   verifying structure — exactly the kind of judgment call BKHL_007 already flags as belonging to a
   person, not lint.

## Log

- 2026-08-24: **Closed.** No mechanism warranted here, per this ticket's own conclusion — added the
  citation convention and the "reread on verified-promotion" rule directly to
  `backhaul/wiki/meta/bhrm.md`. No upstream Backhaul ticket needed; nothing here requires a code
  change, and the ticket's own point 3 argues explicitly against building an auto-detector.

- 2026-08-24: Ticket opened, off the Lead Dev session that built and closed
  [FRO_043](FRO_043_boss-build.md) and hit this gap directly.

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/backhaul)
<!-- bh-header:end -->
