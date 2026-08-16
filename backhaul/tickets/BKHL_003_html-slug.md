---
id: BKHL_003
uid: BKHL
number: 3
client: Backhaul
status: done
title: render-html doesn't show a node's filename slug
context: PM currently hand-patches node.title with a throwaway script every session
  to prepend the slug; doesn't persist, not part of the tool. Should be automatic.
  See body.
priority: normal
opened: '2026-08-16'
closed: '2026-08-16'
---

<!-- board:start -->
<!-- board:end -->

## Summary

render-html doesn't show a node's filename slug

## Full report

[Found while using the Backhaul CLI from inside mcRepos's PM role sandbox — same pattern as
BKHL_001/BKHL_002.]

**Context.** This project started giving roadmap nodes memorable filename slugs (baby names —
`RM_SAT_012_donald.md` instead of a descriptive slug) specifically so they'd be easier to say
and remember than the `RM_SAT_012`-style ID, mirroring how `bhrm.md` already recommends short
mnemonic slugs over descriptive ones. But `bhrm render-html` only ever shows the ID and the
node's `title` — the slug never appears anywhere in the generated chart, which defeats half the
point of having it. Every time I regenerate the charts for this project, I currently run a
throwaway Python script that loads the graph via the installed package's own
`load_graph()`/`render_html()`, derives each node's slug from its filename, and monkeypatches
`node.frontmatter.title` (via `dataclasses.replace`, since `Node`/`RoadmapNodeFrontmatter` are
frozen) to prepend `f"{slug.capitalize()} — {title}"` before calling `render_html()`. That works,
but it's dead weight outside this one ticket — a fresh sandbox has to be told to run it, it isn't
documented anywhere, and it doesn't help any other Backhaul project that wants the same thing.

**The fix should be automatic, not a new CLI flag or workflow step.** A PM's actual workflow for
changing a node's slug is already just: rename the file (or use `--slug` at `bhrm new` time), then
run `bhrm refresh`/`index` like after any other edit — no dedicated "rename" command exists today,
and none is needed, since it's a plain filesystem operation. The slug showing up in the generated
HTML should work the same way: rename the file, regenerate, done. No new command, no config flag.

**Where the slug should come from.** `Node` (in `modules/roadmap/graph.py`) already carries
`path: Path` as a field — every node already knows its own real filename, so the slug doesn't
need a new frontmatter field or schema change (matches this project's own "computed, not stored"
discipline — see `bhw.md`'s spec-page conventions and BKHL_002's advisory-not-stored precedent).
Concretely, the slug is `path.stem` with the `f"{id}_"` prefix stripped — the same string
`bhrm new --slug` writes and `bhrm.md`'s own filename convention documents
(`<ID>_<slug>.md`).

**Suggested shape (not a committed design):**
- Add a `slug` property to `Node`, computed from `self.path.stem.removeprefix(f"{self.id}_")` —
  read-only, derived, no schema/frontmatter change, same pattern `id`/`kind`/`title` already use
  as properties over `frontmatter`.
- In `render_html()`'s node-sub text (`GrowthTriggerRenderer`-style — currently just
  `_xml_escape(node.title)`), prepend the slug when it's non-empty and differs from a
  slugified-title fallback: `f"{node.slug.capitalize()} — {node.title}"` in the `<text
  class="node-sub">` line and the `<title>` tooltip. If a node's filename slug happens to equal
  the auto-slugified title (the BHT/BHW default when `--slug` wasn't passed), skip the prefix —
  no point repeating the title as its own label prefix for projects that don't use mnemonic
  slugs.
- Frontier-banner text (currently `f"{nid} ({nodes[nid].title})"`) could pick up the same
  treatment for consistency, though that's a smaller win since it's already a title, not just an
  ID.

**Scope:** `modules/roadmap/graph.py` (`Node.slug` property, `render_html()`'s node-sub/tooltip
text), tests (a node whose slug differs from its slugified title shows the prefix; a node whose
slug matches doesn't; layout/color output otherwise unchanged — extends the existing
`render_html()` test coverage from BH_005 rather than a new test file).

Priority: normal — not blocking, purely a chart-readability gap, but the workaround costs a
manual step every session in this project specifically.

## Log

- 2026-08-16: **Landed.** `Node.slug` property added exactly as scoped (`path.stem` minus the
  `f"{id}_"` prefix, no schema change). Implementation diverged from this ticket's suggested
  placement in one respect — instead of prepending to `node-sub`, the slug renders as a bold gold
  `<tspan class="node-slug">` right after the ID on the label line, plus folds into the tooltip
  (`RM_SAT_012 · donald — work · resolved. ...`) — same information, arguably cleaner since it
  keeps the ID and slug together as one identity line rather than merging the slug into the title
  line. No `--slug`-vs-slugified-title skip logic needed for that placement (the slug sits next
  to the ID, not competing with the title for the reader's eye the way a prepend would). Verified
  against both mcRepos charts after reinstall — every node's baby-name slug shows correctly
  (`donald`, `paul`, `jerry`, etc.). Retired the session-local monkeypatch script now that this
  is a real `bhrm render-html` feature. Closing.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/backhaul)
<!-- bh-header:end -->
