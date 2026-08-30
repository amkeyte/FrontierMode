---
id: BKHL_016
uid: BKHL
number: 16
client: Backhaul
status: done
title: Unquoted title colon crashes bht board/open
context: 'FRO_051''s title ''Border load count: client vs server'' was written unquoted;
  the embedded colon-space breaks YAML parsing in frontmatter.parse(), and since bht
  open/board scan every ticket to rebuild the board, this crashed bht for the whole
  project until fixed. Found live, fixed live (quoted the string) -- see log.'
priority: high
opened: '2026-08-28'
closed: '2026-08-28'
---

<!-- board:start -->
<!-- board:end -->

## Summary

[Found while using the Backhaul CLI from inside mcRepos's PM role sandbox — same pattern as
BKHL_001/BKHL_002, filed here rather than against Backhaul's own tracker.]

`bht --config backhaul/config.local.json board` (and, since `bht open` rebuilds the board as a
side effect, `bht open` too) crashed with a `yaml.scanner.ScannerError: mapping values are not
allowed here` while trying to open an unrelated ticket in this session. Root cause:
`FRO_051_border-load-count-mismatch.md`'s frontmatter had

```yaml
title: Border load count: client vs server
```

— an unquoted scalar containing `: ` (colon-space), which YAML reads as the start of a second
mapping key inside the value position. `frontmatter.parse()` (`foundation/frontmatter.py`) has no
defense against this: it hands the raw YAML straight to `yaml.safe_load`, so one malformed ticket
anywhere in the tickets folder takes down `board`/`open` project-wide, not just reading that one
file.

## Fix applied live

Quoted the title (`title: 'Border load count: client vs server'`) directly in
`FRO_051_border-load-count-mismatch.md` — no other content changed, `board` confirmed working
again immediately after. Whoever authored FRO_051 (Architect or Lead Dev, from its own log) most
likely typed the title with a colon without thinking about YAML scalar rules, same mistake anyone
could make.

## Suggested shape (not a committed design)

Two independent things worth doing upstream, not just this one file:

1. **`bht`/`bhrm` writers (`open`, `new`, etc.) should always emit quoted string scalars for
   free-text fields** (`title`, `context`, and any other user-supplied string) rather than relying
   on the value happening to be YAML-safe unquoted — this specific crash could not have happened if
   the tool itself had quoted the title when FRO_051 was created.
2. **`frontmatter.parse()` should catch `yaml.YAMLError` and raise a `FrontmatterParseError`
   naming the offending file path**, rather than letting a raw YAML traceback (with no file context
   at all in the stack trace) surface to the CLI user. A rollup over dozens of files with one bad
   apple currently gives no clue which file is the problem — this session found it by binary
   reasoning (freshest files first), not from the error itself.

Priority: high, not normal — unlike most BKHL findings this isn't just a hygiene gap, it's an
active crash that blocks `board`/`open` (two of the most frequently run commands) for every role
on the project until someone finds and fixes the specific bad file by hand.

## Log

- 2026-08-28: **Closed.** Live fix (quoting `FRO_051`'s title) already applied and confirmed
  working — see body. Both upstream hardening items tracked as a single ticket, BH_020, high
  priority given this is a confirmed project-wide crash, not just a hygiene gap.

- 2026-08-28: Ticket opened and fixed live in the same pass — `bht board` was crashing project-wide
  before this fix, needed to be working again before this session could open its own next ticket.
  See body for root cause, the one-line fix applied to FRO_051, and the two suggested upstream
  hardening items (writer-side quoting, parse-error file context).
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/backhaul)
<!-- bh-header:end -->
