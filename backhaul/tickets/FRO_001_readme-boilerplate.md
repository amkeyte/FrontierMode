---
id: FRO_001
uid: FRO
number: 1
client: FrontierMode
status: open
title: README.txt is stock MDK boilerplate
context: Route to Lead Dev — real description lives in mods.toml, not README.txt.
priority: normal
opened: '2026-08-11'
closed: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->

## README.txt is stock MDK boilerplate

- 2026-08-11: Ticket opened by PM.

### Context

`FrontierMode/README.txt` is the unmodified Forge MDK setup guide (Eclipse/IntelliJ import
steps, mapping license notes). It says nothing about what FrontierMode actually is. The real
identity currently only lives in `src/main/resources/META-INF/mods.toml`:

> Frontier Mode: Gameplay and world-tuning modifications for Minecraft.

I've captured that (plus the Satchel dependency) on the wiki as an interim source of truth:
[FrontierMode](../wiki/mods/frontiermode.md).

### Suggested fix

Lead Dev replaces or prepends `README.txt` with a short "About this mod" section (can crib
directly from the wiki page above) ahead of the Forge setup instructions, so a new contributor
doesn't have to go spelunking in `mods.toml` to find out what the mod does.

### Required By

*(none)*
