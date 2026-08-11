---
id: SAT_001
uid: SAT
number: 1
client: Satchel
status: open
title: README.txt is stock MDK boilerplate
context: Route to Lead Dev — real description lives in gradle.properties, not README.txt.
priority: normal
opened: '2026-08-11'
closed: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->

## README.txt is stock MDK boilerplate

- 2026-08-11: Ticket opened by PM.

### Context

`Satchel/README.txt` is the unmodified Forge MDK setup guide, identical boilerplate to
FrontierMode's. It says nothing about what Satchel actually is. The real identity lives in
`gradle.properties` (which templates into `mods.toml`):

> Satchel: Core data & utility mod for FrontierMode.

I've captured that — plus the DocletProject API-dump tooling link and the existing `design/`
diagrams — on the wiki as an interim source of truth: [Satchel](../wiki/mods/satchel.md).

### Suggested fix

Same as [FRO_001](FRO_001_readme-boilerplate.md): Lead Dev adds a short "About this mod" section
to `README.txt` ahead of the Forge setup instructions, so the mod's purpose and its relationship
to FrontierMode/DocletProject is visible without digging through `gradle.properties`.

### Required By

*(none)*
