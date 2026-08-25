---
id: BKHL_008
uid: BKHL
number: 8
client: Backhaul
status: done
title: bhrm render --output ignores file extension
context: Pointing the markdown renderer at a .html path silently overwrites a generated
  graph with markdown.
priority: low
opened: '2026-08-22'
closed: '2026-08-24'
---

<!-- board:start -->
<!-- board:end -->

## Summary

`bhrm render --uid X --output PATH` writes **markdown**. `bhrm index` writes the **HTML graph**
(`ROADMAP_GRAPH_<UID>.html`) as a side effect. Neither is documented as such in
[BHRM — Roadmap Conventions](../wiki/meta/bhrm.md)'s CLI cheatsheet, and `render` does not look at
the output path's extension.

So the obvious command for "regenerate the roadmap graph HTML" —

```
bhrm render --uid RM_SAT --output backhaul/ROADMAP_GRAPH_RM_SAT.html
```

— reports `OK: wrote render to ...` and silently replaces a 14 KB styled SVG graph with a flat
markdown document that happens to have a `.html` extension. No warning, no error, exit 0.

## Hit live, 2026-08-22

PM ran exactly that while regenerating the RM_SAT graph after opening
[RM_SAT_022](../roadmap/RM_SAT_022_roger.md). Both graph files were clobbered. Caught only by
diffing the output against a copy taken beforehand — the command's own success message gives no
signal, and a markdown file opened in a browser still renders as *something*, so a casual check
would pass too. Recovered by re-running `bhrm index`.

The near-miss is the point: the failure is silent, the recovery is non-obvious unless you already
know `index` is what writes the HTML, and the damage lands on a generated file that carries a
"do not hand-edit" notice — so nobody would think to check it into review.

## Suggested direction, not a committed design

1. **Refuse the mismatch.** If `--output` ends in `.html`, either error with a pointer to the right
   command, or emit HTML. Erroring is probably better than guessing.
2. **Document the split.** `bhrm.md`'s cheatsheet lists `render` and `index` without saying which
   produces what. One clause on each line fixes the discoverability half:
   `render` → markdown to stdout or `--output`; `index` → rebuilds `ROADMAP_INDEX.md` **and** every
   `ROADMAP_GRAPH_<UID>.html`.
3. Worth checking whether `bhw`/`bht`/`bhrole` have the same shape — this ticket only looked at
   `bhrm`.

Related but distinct: [BKHL_003](BKHL_003_html-slug.md) covered `render_html()`'s node labels, not
the CLI's output-format handling.

## Log

- 2026-08-24: **Closed.** Cheatsheet clarified directly in `backhaul/wiki/meta/bhrm.md` (which
  command writes what, and the exact failure mode to avoid). The actual guard — `render` refusing
  or erroring on a `.html` `--output` path — needs Backhaul code and is tracked upstream as BH_012.



- 2026-08-22: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/backhaul)
<!-- bh-header:end -->
