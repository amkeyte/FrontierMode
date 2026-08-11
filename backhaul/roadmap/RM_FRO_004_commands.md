---
id: RM_FRO_004
uid: RM_FRO
number: 4
kind: work
status: resolved
title: Command and selector interface
owner: Arryn
depends_on:
- RM_FRO_002
created: '2026-08-11'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_FRO
<!-- bh-header:end -->

## Command and selector interface

- 2026-08-11: Node opened, backfilled as resolved history.

Evidenced by `border/server/commands/*` (`BorderCommandHandler`, `BorderCommands`,
`BorderSelector`, `BorderSelectorArgumentType`, `BorderSelectorArgumentTypeInfo`) and by
`FrontierMode.java`, which registers a custom `ArgumentTypeInfo` (`border_selector`) with Forge's
command-argument registry and wires it through `commonSetup`. A real custom Brigadier argument
type, not a stub.

## Required By

*(computed — nothing depends on this yet)*
