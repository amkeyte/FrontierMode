---
id: meta/bhrm
category: meta
slug: bhrm
title: BHRM — Roadmap Conventions
summary: Roadmap node ID scheme, why short slugs matter here, and CLI cheatsheet.
keywords: null
status: draft
updated: '2026-08-30'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../BACKHAUL.md) · [Wiki Index](../../WIKI_INDEX.md) · meta
<!-- bh-header:end -->

# BHRM — Roadmap Conventions

Roadmap node ID scheme, why short slugs matter here, and CLI cheatsheet.

BHRM (`bhrm`) is the dependency-graph roadmap module — optional, gated by `enabled_modules`.
Every unit of roadmap-load-bearing work is a node (`work` or `convergence`) with explicit
`depends_on` edges; `frontier` computes what's actionable instead of that being a memorized
position in a list.

## ID scheme

A node's ID is `RM_<uid>_<NNN>` (e.g. `RM_ARR_001`). The UID is `RM_` + a client's short code —
the *same* code BHT uses for that client (they share one `client-uids.md`), so `ARR` means the
same client whether it's a ticket or a node. **Each UID is its own fully independent graph** —
`validate`/`frontier`/`downstream`/etc. are always scoped to one UID, and a `depends_on` entry
naming a node under a different UID is a hard error, not a cross-project link. This is how one
shared `content_roots.roadmap` folder hosts multiple, unrelated roadmaps side by side (e.g. one
project hosting both FrontierMode's and Satchel's graphs).

## Filenames and --slug — use a short code here, not the description

A node's filename is `<ID>_<slug>.md`. **For roadmap nodes specifically, always pass
`--slug <code>` with a short, one-word mnemonic** (e.g. `alma`, `scaffold`) rather than letting
it default to the slugified title:

```
bhrm new --client FrontierMode --title "Set up the initial mod scaffolding and build pipeline" --owner Arryn --slug scaffold
```

This matters more here than for BHT/BHW: `depends_on` edges reference IDs directly
(`RM_FRO_003`), and a node's own body/render output is meant to be skimmed as a dependency
graph, not a flat list — a long descriptive slug makes both harder to eyeball and
tab-complete than a short code does. The ID itself never contains the slug (only the filename
does), so this is purely about keeping files easy to work with, not about identity.

## Persona names are not reusable

Nodes in this project carry a persona name alongside their ID, drawn from
[Top Baby Names of 1945](../reference/baby-names-1945.md). **A retired node's name does not go back
in the pool.** Reissuing one was tried as a convenience and turned out to be a bad idea: RM_FRO_014
("Shirley") and RM_FRO_016 ("Karen") were deleted, their names handed to RM_FRO_018 and RM_FRO_019,
and every bare-name reference in the project's prose became ambiguous — including in the log entries
that explain *why* the originals were deleted, which necessarily still name them.

The ID is the identity; the persona name is a handle for talking about a node out loud. A handle
that points at two things is worse than no handle. Take the next unused name instead — the list is
long, and the cost of skipping one is nothing.

## Title length

`ROADMAP_INDEX.md` renders each node's title as part of a one-line list entry — same reasoning
as BHT's length standard (see `meta/bht.md`). Target title length ≤ ~40 characters; put the
detail (specific classes/files/packages involved) in the node's body instead of stuffing it into
the title. Several nodes from the initial roadmap backfill run well over this — worth trimming
next time they're touched, not urgent enough to warrant a dedicated pass on its own.

## Status vocabulary (kind-dependent)

- **work**: `open` -> `resolved` | `superseded` (terminal once left `open`).
- **convergence**: `WIP` <-> `reached` (reversible — a milestone can un-converge on real
  evidence of a gap, with a `ReachedLog` recording every time it was reached, never erased).

## Epoch maintenance nodes (containers)

An **epoch** is the span of work between two convergence nodes, named for the one that opens it —
e.g. "the Susan epoch" for everything after [RM_FRO_010](../../roadmap/RM_FRO_010_susan.md)
("Susan") reached. Real work inside an epoch isn't always feature-shaped: a ruling's blast radius
turns out to reach past the ticket that provoked it, a build turns up a bug against something
unrelated, a later node casts doubt on an earlier one's done bar. That work still needs a place to
live on the graph — otherwise it only exists by being found in the tickets folder, which is
exactly the "search to find it" problem this convention exists to remove.

**A maintenance node (a "container") is an ordinary `kind: work` node that doesn't represent a
design goal.** It doesn't get a persona name — persona names are reserved for feature work (see
above) — it gets a slug keyed to the epoch instead: `<epoch>-01`, `<epoch>-02`, etc.,
e.g. `susan-01`. It doesn't carry a `ticket:` field, the same way a convergence node doesn't —
`ticket: null`, and what actually landed on it is tracked in its own log, since a container can
gather more than one ticket over its life and a single frontmatter pointer would misrepresent that.
No new `kind` value for these — they render in the graph like any other `work` node for now. A
dedicated `kind: maintenance` (distinct color/shape in `bhrm index`'s HTML output) is worth
revisiting once the pattern has proven out; deliberately not built yet.

**Every epoch gets two standing containers by default: a start container and an end container,**
opened together rather than waited on until something obviously needs one — cheap to have sitting
there empty, expensive to reconstruct after the fact once nobody remembers the epoch had a start.
The start container is where early-epoch rework tends to land (a ruling's fallout, the first real
thing found). The end container is the epoch's review/fix gate: things too small or too unrelated
to block whatever's currently being built get dropped there instead of stalling it, and the epoch's
next planned node depends on the end container clearing — so nothing carries forward unaddressed
into the next epoch. **Additional containers get inserted in the middle only when a set of tickets
is big enough to warrant its own marker** — a judgment call each time, not an automatic trigger.

**Wiring is real, not decorative.** A `depends_on` edge onto a container means the dependent is
genuinely blocked on it clearing, the same as any other node. Wire a container to whatever it
actually gates, wherever that lands — that can mean a node several persona-names back, if that's
what the container's contents actually touch, not just the node currently in flight.

**Corollary, learned the hard way on the first real end-container (`susan-02`, 2026-08-29/30): a
container earns a `depends_on` edge onto a convergence only when it holds — or is reasonably
suspected to hold — something that actually blocks that convergence, not merely because it's real,
IDed, logged scope that happens to be open during the epoch.** The first pass at this container got
folded into the epoch's Tier-1 convergence on a too-literal reading of "fold real siblings in as
they're found" — and even after the container grew to seven real items (including one genuine,
sharp-edged bug), none of them turned out to actually threaten what the convergence was claiming.
Reversed the same day it was added. A container can be real, open, and completely un-wired to
anything — that's the normal state for "side quest" work being deliberately parked, not a gap in
the graph. Wire it when something on it is an actual blocker; until then it just sits there,
tracked and findable, waiting.

**Reopening a `resolved` node on real evidence is the intended mechanism, not a violation of this
project's history.** Rework sometimes means re-proving an earlier node's done bar, not just noting
that it might be affected — that's real QA work, and a maintenance container is where it gets
tracked in order rather than happening ad hoc. Earlier caution around ever flipping a `resolved`
node's status back was circumstantial, not structural: the original roadmap was written while
learning and stabilizing an inherited codebase, not planning one being built forward. Now that the
codebase is owned outright, reopening on real evidence is expected practice — logged as a new dated
entry on the reopened node itself (never a silent edit of what's already there, still the rule for
the log itself), cross-referenced from the container that forced it.

**Regression doubt gets chased back one hop, for now.** If a node's work casts real doubt on the
node immediately before it, that node reopens. Doubt about something further back than one hop gets
named and logged as accepted risk rather than triggering a deeper sweep — the graph is still
churning enough, this early, that a full downstream audit would likely be re-litigating ground
that's going to move again anyway. Revisit this boundary once the graph is stable enough that the
math changes. See [BKHL_017](../../tickets/BKHL_017_epoch-maintenance-node-backport.md) for
tracking this convention into Backhaul's own default docs.

**A container's `status` tracks whether it's currently blocking anything, not whether every ticket
ever logged on it is closed.** A container can gather more than one ticket over its life, and they
won't all carry equal weight — one might be the real blocker a dependent needs, another incidental
low-priority follow-up with no downstream relevance. Flip `resolved` once nothing currently gating
a dependent remains open; non-blocking content can stay logged and open underneath that without
holding the flip back. (First real case: [RM_FRO_020](../../roadmap/RM_FRO_020_susan-01.md) gathered
its actual blocker alongside unrelated polish within hours of each other — see its own log.)

**Required shape when that happens: a `Deferred, non-blocking:` callout at the top of the
container's body, not just a mention buried partway into its log.** A short bullet list of what's
still open and why it isn't holding the status back — a reader shouldn't have to read the full log
to find out a `resolved`/`clear` container still has loose ends. This is a body-content convention,
not a schema field; nothing enforces it mechanically today. A first-class `kind: maintenance` with
its own `collecting <-> clear` status pair (mirroring convergence's reversible `WIP <-> reached`,
since a container can pick up a new blocker after looking clear) would make this structural instead
of a documentation habit — tracked as part of [BKHL_017](../../tickets/BKHL_017_epoch-maintenance-node-backport.md)'s
ask, deliberately not built yet. This callout convention is the interim version.

**Ticket-side signal: a `[<Container>]` prefix on the ticket's own `context`.** The container's
own callout (above) is the record that a ticket is attached, but it only reaches a reader who
opens the roadmap node — `BOARD.md` is the view that actually gets scanned day to day, and
without a matching signal there a container-attached ticket reads exactly like a loose one still
waiting on someone. Prepend `[<Epoch>_<NN>]` — the container's own persona name, capitalized, plus
its two-digit sequence, matching its slug (`susan-02` → `[Susan_02]`) — to the front of the
ticket's `context` field, the same day it's logged on the container. This is a BHT `context`-field
convention, not a wiki-page one, so it doesn't fall under BHW's no-status-in-prose rule — a
container attachment is exactly the kind of live-status fact `context` exists to carry, unlike an
architecture page. It does share `bht.md`'s length standard: `context` is still capped at ~100
characters including the tag, so trim the surrounding wording to make room rather than skip the
tag or blow the budget.

```
bhrm new --client <name> --title "..." --owner <name> [--kind work|convergence] [--slug code] [--depends-on ID,ID]
bhrm validate --uid RM_XXX
bhrm frontier --uid RM_XXX
bhrm dependents <ID>   |   bhrm downstream <ID>   |   bhrm blocking <ID>
bhrm render --uid RM_XXX [--output PATH] [--title "..."]
bhrm export-json --uid RM_XXX [--out PATH]
bhrm index [--output PATH] [--title "..."]    # every UID's graph, its own section
bhrm projects
```

`--project <name>` / `--config <path>` selects the project. Every subcommand except `projects`
refuses to run if `"roadmap"` isn't in that project's `enabled_modules`.

## Related pages

- [BHT — Ticket Conventions](../meta/bht.md)
- [BHW — Wiki Conventions](../meta/bhw.md)
- [BHRole — Agent Role Conventions](../meta/bhrole.md)
- [Git Branching by Epoch](../meta/git-branching.md) — evaluated and deferred, 2026-08-30: parking
  a container's leftover content on its own branch, on top of leaving it un-wired on the graph
