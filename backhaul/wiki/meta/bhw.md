---
id: meta/bhw
category: meta
slug: bhw
title: BHW — Wiki Conventions
summary: Wiki page ID scheme, slug convention, and CLI cheatsheet.
keywords: null
status: draft
updated: '2026-08-13'
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

A wiki page describes the thing as it is now, not how it got there. Don't write "Update
(TICKET, date): X changed to Y" inline, don't keep a section around "for history" once it no
longer describes current behavior, and don't narrate that content was "backfilled" or
"reconstructed" — none of that helps a reader who just wants to know the current state, and it's
one more thing to notice has gone stale.

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
