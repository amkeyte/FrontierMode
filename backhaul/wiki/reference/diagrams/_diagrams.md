---
id: reference/diagrams/_diagrams
category: reference/diagrams
slug: _diagrams
title: _Diagrams
summary: Landing page for every Mermaid diagram in this wiki -- one consolidated category
  (Reference), no per-mod split. Each diagram page has a matching large-font standalone
  HTML render linked from here, in html/.
keywords: null
status: verified
updated: '2026-09-09'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · reference / diagrams
<!-- bh-header:end -->

# _Diagrams

Landing page for every Mermaid diagram across this whole project -- FrontierMode, Satchel, and
anything else that comes along later. Diagrams used to split by mod (`frontiermode/diagrams`,
`satchel/diagrams`); that split is dropped as of this page. Everything lives in one place, next to
[Top Baby Names of 1945](../baby-names-1945.md) and the rest of Reference.

## Convention, going forward

- One wiki page per diagram, flat under this category (`reference/diagrams/<slug>.md`) --
  regardless of which mod or subsystem it documents. The page's own content says what it's about;
  the category no longer does.
- Every page's Mermaid source stays a plain fenced code block, same as any other wiki page --
  readable as text, diffable, greppable, no rendering step required.
- Every page also gets a matching self-contained HTML render under `html/<slug>.html` -- same
  Mermaid source, wrapped in a minimal dark-theme page, explicit large font, `useMaxWidth:false`
  so the layout renders at true size instead of being scaled down to fit a container. Loaded from
  a CDN, so it opens directly in a browser with no build step. Verified to actually render (headless
  Chromium, screenshot-checked) before ever being linked from here.
- Why both formats: the `.md` source is what a text-reading agent, or a human editing this page,
  actually wants -- no rendering needed to understand the shape of the thing. The `.html` render is
  for anyone who wants to *look* at the picture -- open it in a real browser, or point a
  screenshot-capable agent at it -- without needing to trust a mental render of the Mermaid syntax,
  or run a Mermaid-aware tool of their own. Kept in a `html/` sub-folder so this category's file
  listing stays readable as wiki pages first.
- Scratch, in-progress diagrams that haven't earned a place here yet live in `Scrapyard/Mermaid/`
  at the repo root, same `.md` + `.html` pairing, informal and intentionally not indexed on this
  page -- promoted here only once they're worth keeping.

## Diagrams

| Diagram | Source | HTML render | Summary |
|---|---|---|---|
| [Border Module Shape](border-module-shape.md) | [border-module-shape.md](border-module-shape.md) | [border-module-shape.html](html/border-module-shape.html) | High-level module map of Border: BordersBundle's sibling fixtures, BordersFixture's four facets, the BorderAPI facade, and the three consumer surfaces (commands, client rendering, server rules/triggers). |
| [Satchel: Forge Event to ScopeEvent](satchel-forge-to-scopeevent.md) | [satchel-forge-to-scopeevent.md](satchel-forge-to-scopeevent.md) | [satchel-forge-to-scopeevent.html](html/satchel-forge-to-scopeevent.html) | High-level block diagram of Satchel's own pipeline: how a raw Forge event (LevelEvent, PlayerEvent, TickEvent) becomes a ScopeEvent.Loaded/Tick/Unloaded that module EventHandlers subscribe to. |
| [Bundle Lifecycle](bundle-lifecycle.md) | [bundle-lifecycle.md](bundle-lifecycle.md) | [bundle-lifecycle.html](html/bundle-lifecycle.html) | How a SatchelBundle moves CONSTRUCTED -> CREATED -> HYDRATED -> LOADED -> ACTIVE (and on to DESTROYING/DESTROYED), and which of Jig/Coupler/Engine/Bundle drives each step. |
