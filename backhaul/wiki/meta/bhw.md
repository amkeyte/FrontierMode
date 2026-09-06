---
id: meta/bhw
category: meta
slug: bhw
title: BHW — Wiki Conventions
summary: Wiki page ID scheme, slug convention, and CLI cheatsheet.
keywords: null
status: draft
updated: '2026-09-06'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../BACKHAUL.md) · [Wiki Index](../../WIKI_INDEX.md) · meta
<!-- bh-header:end -->

# BHW — Wiki Conventions

Wiki page ID scheme, slug convention, and CLI cheatsheet.

BHW (`bhw`) is the category-based wiki — no numbering, the path itself is the identity.

## ID scheme

A page's ID is `<category>/<slug>` (e.g. `reference/conventions/wiki-style`). Categories can be
nested (`reference/conventions`) — each segment becomes a real subfolder. There's no registry
and no counter, unlike BHT: two pages never collide unless they'd share the exact same
category + slug.

## Filenames and --slug

By default the slug is the title, slugified. Pass `--slug <code>` for a short, hand-picked
filename instead:

```
bhw new --category reference --title "How the Cartridge Mechanism Actually Works" --slug cartridge
```

## Status

`draft` -> `verified` / `published` — informational only. Unlike BHT's ticket status, nothing
here gates whether a page shows up in `WIKI_INDEX.md`; all statuses are listed.

## Title length

`WIKI_INDEX.md` renders each page's title as a table column — same reasoning as BHT's length
standard (see `meta/bht.md`). Target title length ≤ ~40 characters; let `summary` carry the
detail instead.

## No changelog content on wiki pages

**"Describes the thing as it is" doesn't mean "as already shipped in code."** `<mod>/architecture/*`
pages are frequently the spec a page's own account commits to *before* anything is built against
it -- that's the whole point of `draft` status, and there's real precedent for writing this way
throughout the project: `border-curve.md`, `boss-commands.md`, and `discovery-systems.md` were
all worked out in the wiki ahead of minting any roadmap node (see `border-pregeneration.md`'s own
account of the pattern: "worked out in the wiki ahead of minting any RM_FRO node, because getting
the shape wrong before it's in the graph is the expensive mistake"), and Dev roles are told
outright to build *against* `*/architecture/*` pages as the spec -- not to treat them as a status
report on code that already exists. So "current" means the design the page is committing to right
now, not "already shipped." Describing an intended structure ahead of the code is not a violation
of this rule -- it's a large part of what an architecture page is *for*. Use `draft` -> `verified`
to carry the shipped-or-not distinction; don't hedge about it in prose instead.

What this rule actually forbids is narrating how the page's *own account* of a design changed over
time -- the page's history, not the code's maturity. Don't write "Update (TICKET, date): X changed
to Y" inline, don't keep a section around "for history" once it no longer describes the current
design, and don't narrate that content was "backfilled" or "reconstructed" — none of that helps a
reader who just wants to know the current state (whether that state is shipped code or a
committed-to design not yet built), and it's one more thing to notice has gone stale.

History belongs in the place that's actually built to hold it: a ticket's log, a roadmap node's
status trail (`superseded`/`done` nodes stay on record deliberately — see `meta/bhrm.md`), or
`wiki/plans/*` for a specific initiative's writeup. If a wiki page needs to point at *why*
something is the way it is, link to the ticket or roadmap node rather than restating the story
inline — e.g. "known debt, see FRO_004" rather than a paragraph recapping what FRO_004 found.

Before deleting a historical section, check it isn't the only path to something else (a ticket, a
sibling page) — either drop that link too, or move it somewhere that still resolves. Nothing
should go unreachable; `WIKI_INDEX.md` lists every page regardless, but a page's own body should
still route a reader to what it depends on.

## No status either — tickets and roadmap own that

Same principle, one level further: a wiki page doesn't say whether something is open, resolved,
in progress, or the only actionable item in some graph. That's what BHT (`bht`) and BHRM (`bhrm`)
are for, and they're the only place it can be trusted, since a wiki page has no mechanism forcing
someone to update it the moment a ticket closes. If a page needs to gesture at outstanding work,
name the ticket or roadmap node (`see FRO_004`) and stop there — don't also characterize its
state in prose, since that's the part that goes stale first.

If something is a real fact worth tracking and doesn't have a ticket yet, open one — that's the
"ticket out the fix" half of this: don't leave a note-to-self in the wiki saying a fix is needed
"but there's no ticket for it yet." The wiki should describe the thing as it actually is today
(including known-imperfect states, like a placeholder package name); whether someone's planning
to change that is BHT/BHRM's job to track, not the wiki's.

An open design question is different from a tracked status and can stay in prose if that's what
the page is actually about (e.g. a reconciliation doc's list of things not yet decided) — the
line is whether it duplicates a ticket/roadmap field or is substantive content the page exists to
capture.

## Spec pages: a stricter sibling of architecture pages

`<mod>/architecture/*` describes how something is built — the conceptual/structural layer,
narrative-tolerant in the sense of explaining reasoning, but subject to the same "no changelog
content" rule above. `<mod>/spec/*` is a narrower, stricter sibling: the current, precise
contract for one boundary surface — registration/method contracts, invariants, lifecycle
ordering, guarantees, forbidden behavior. No dated narrative at all, not even the light amount
architecture pages sometimes carry — a spec page is meant to be the stable thing other content
points at, not a record of how it got that way.

**Test for whether something gets a spec page: does a consumer *outside* the implementing
package/mod actually depend on this surface not changing?** If yes, it's a boundary and qualifies.
If the answer is "nothing outside this package calls it directly," it stays architecture-page-only
(or isn't documented at that granularity at all) — internal machinery doesn't need a promise made
about it, because nothing external is trusting the promise. This keeps spec pages rare and
load-bearing rather than one-per-class.

Placement mirrors architecture: `<mod>/spec/*`, same cross-reference directionality (link in the
direction of the actual code dependency). Cross-cutting spec content that isn't cleanly
mod-scoped follows the same "goes under FRO for now" convention already established for
cross-cutting tickets/roadmap items (see `plans/strip-down.md`'s "New rule" section) —
FrontierMode is the ecosystem-facing repo, Satchel stays the dependency.

**Not tracked as a roadmap initiative or a bounded plan.** `bhrm` models dependency-graph-shaped
forward work; this isn't that. And unlike `plans/doc-coverage.md` or `plans/strip-down.md`, this
deliberately has no finish line — it's a standing practice, not a project. Built opportunistically:
when a ticket already requires opening the relevant architecture page, split out whatever
contract-shaped content has accumulated into a sibling spec page, and trim the historical
narrative out of the architecture page in the same pass. Don't go looking for untouched areas to
backfill speculatively.

## Cross-references: use real relative links, not double brackets

**Always link with standard markdown — `[Title](relative/path.md)` — resolved relative to the
file doing the linking.** That's the only form guaranteed to be clickable, everywhere, no matter
what renders the file.

Earlier in this project, a double-bracket convention (wrapping a wiki page's slug in two
open-brackets and two close-brackets, borrowed from Obsidian-style wikilinks) spread across
wiki pages, tickets, and roadmap nodes as a lightweight way to reference a wiki page by its
`slug`. It was treated as acceptable on the theory that it was a non-clickable "semantic marker"
by design. In practice this was wrong: it doesn't render as a link in any plain markdown viewer
(it isn't standard Markdown at all), so every one of these ended up reported as "not linking
reliably" regardless of whether the slug it named actually existed. All instances found in
`wiki/` have been converted to real relative links; instances in `tickets/`/`roadmap/` may still
need the same conversion — check before trusting one you find there.

A second, sharper failure mode from the same convention: two independent sessions used it to
reference a **role** (the PM role, the Architect role) rather than a wiki page. Roles live in a
different content root (BHRole) with their own slugs, so that didn't just fail to render — it
pointed at nothing. A real relative link (e.g. `[PM](../../roles/pm.md)`) doesn't have this
failure mode, because it's an actual path, checkable by existence, not a name looked up in a
slug table that may or may not be the right one.

If you genuinely want a non-navigational tag (rare — most of the time you want the reader to be
able to click through), just say so in prose rather than using bracket syntax that looks like a
broken link.

## CLI cheatsheet

```
bhw new --category <cat> --title "..." [--slug code] [--summary "..."] [--status draft]
bhw index [--output PATH] [--category <prefix>] [--title "..."]   # --category scopes to one subtree
bhw refresh                 # recompute breadcrumbs + rebuild the index
bhw seed-meta [--category meta]   # install this project's canonical module-usage pages into another project
bhw projects
```

`--project <name>` / `--config <path>` selects the project, same as BHT.

Set `host_root` in `config.local.json` if `bhw` might run somewhere other than the real machine
(see BHT's meta page and `bhrole`'s) so Edit links stay correct regardless. That fixes links
only — to make `bhw` itself able to read/write real content from a sandbox, export
`BACKHAUL_LOCAL_ROOT` (see the README's "BACKHAUL_LOCAL_ROOT" section) before running any
command.

## Related pages

- [BHT — Ticket Conventions](../meta/bht.md)
- [BHRM — Roadmap Conventions](../meta/bhrm.md)
- [BHRole — Agent Role Conventions](../meta/bhrole.md)
