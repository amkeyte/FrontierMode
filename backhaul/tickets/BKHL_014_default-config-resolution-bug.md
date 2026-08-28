---
id: BKHL_014
uid: BKHL
number: 14
client: Backhaul
status: open
title: Default config resolution ignores checkout, contradicts bht.md
context: bht.md says omit --config for the checkout's own default; bare bht commands
  fail with ConfigError instead.
priority: normal
opened: '2026-08-28'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

`bht.md`'s CLI cheatsheet says: "`--project <name>` (or `--config <path>`) selects which
project's tickets/board this touches; omit both for **this checkout's own default config**."
That's not what happens. Every bare `bht open`/`bht board`/`bht close` run tonight from inside the
actual mcRepos checkout (`BACKHAUL_LOCAL_ROOT` correctly exported, cwd inside the repo) failed
until `--config backhaul/config.local.json` was added explicitly.

## Reproduction

```
$ cd ~/mnt/mcRepos   # the real checkout, config.local.json present at backhaul/config.local.json
$ bht board
Traceback (most recent call last):
  ...
  File ".../backhaul/foundation/config.py", line 96, in load_config
    raise ConfigError(
backhaul.foundation.config.ConfigError: /sessions/.../.local/lib/config/config.local.json:
no config.local.json here. Copy config/config.local.example.json to config/config.local.json
and point content_roots at this machine's content.
```

The path in the error (`.../.local/lib/config/config.local.json`) is relative to where the
`backhaul` Python package itself is installed, not to the current working directory and not to
anything under the actual checkout. Passing `--config backhaul/config.local.json` explicitly
works every time; the failure is specifically in whatever `_resolve_config_path` falls back to
when neither `--project` nor `--config` is given.

## Impact

Low severity -- `--config` is a reliable, documented workaround, and every command tonight
succeeded once it was added. But it directly contradicts what `bht.md` tells a new user to
expect ("omit both for this checkout's own default config"), and cost real trial-and-error before
landing on the workaround the first time this session hit it.

## Suggested direction, not a committed design

Two independent ways to close this, either sufficient alone: (1) make the no-flag default
actually search upward from cwd for a `config.local.json` under a `backhaul/` (or similarly named)
directory, the way `git` finds `.git`, so "this checkout's own default config" is literally true
when run from inside a checkout; or (2) if the current behavior (config lives next to wherever the
package is installed, full stop) is actually intentional -- e.g. deliberately supporting a
machine with several unrelated checkouts and no single "current" one -- fix `bht.md`'s wording
instead, since the doc is what's actually wrong in that case. Not asserting which is correct;
flagging the mismatch either way.

## Log

- 2026-08-28: Ticket opened, off FRO_047's build cycle -- same session and same reporting
  perspective as [BKHL_011](BKHL_011_log-append-command.md).
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/backhaul)
<!-- bh-header:end -->
