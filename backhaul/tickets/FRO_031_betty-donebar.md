---
id: FRO_031
uid: FRO
number: 31
client: FrontierMode
status: open
title: Betty resolved with 2 unconfirmed items
context: RM_FRO_011's own log leaves an @none tab-complete check and an add-rejection
  retest unconfirmed.
priority: low
opened: '2026-08-21'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

[RM_FRO_011](../roadmap/RM_FRO_011_betty.md) ("Betty," border mutation validation hardening) is
`resolved`, and is one of the six children [RM_FRO_010](../roadmap/RM_FRO_010_susan.md) ("Susan")
flipped to `reached` on 2026-08-20. Two items from Betty's own done bar were never confirmed
anywhere in her log.

Betty's own closing checklist named four outstanding items:

| Item | Confirmed? |
|---|---|
| Clean individual retests of the three `/border transform` forms | Yes — "all three confirmed individually" |
| `/border path fixlayers` behaviour after the honest-message fix | Yes — retested, behaves as left |
| `/border add ~ ~ ~ 999999999 0` retest (single log line + real reason reaches the player) | **No** |
| `@none` tab-complete visual check | **No** |

The `@none` item is explicitly marked "not verifiable from text logs (client-side UI-only
interaction, nothing gets logged) — needs a direct visual confirmation from the project owner." No
such confirmation follows anywhere in the file. The `999999999` retest was owed after a second fix
(routing `failureReason()`'s real text through `applyProposal`'s thrown exception) and is listed as
still open in the last entry that mentions it.

Susan's superseded 2026-08-16 entry nonetheless reads "RM_FRO_011 (all three validation-hardening
items individually confirmed against a real build)," and her 2026-08-20 reached entry rolls Betty
up as `resolved` without revisiting. This is the one place in Susan's subtree where the
convergence's evidence trail claims more than the record supports.

## Scope

Both remaining items are small and in-game:

1. **`/border add ~ ~ ~ 999999999 0`** — confirm the rejection is logged exactly once (not doubled)
   and that the player sees the real reason (`radius 999999999 outside allowed range [1, 512]`),
   not the generic "Border proposal rejected by validation."
2. **`@none` tab-complete** — type `/border info ` and confirm `@none` appears in the suggestion
   list alongside `@all`/`@containing`/`@coord`/`@relevant`.

Note that item 1 sits downstream of two separate fixes made after the last time it was exercised,
so this is a genuine first run against the current code, not a re-confirmation.

## Done bar

Both checks run against a real build, results logged on
[RM_FRO_011](../roadmap/RM_FRO_011_betty.md). If either fails, Betty needs reopening and Susan's
`reached` flip needs revisiting — `bhrm` convergence status is reversible by design (see
[BHRM — Roadmap Conventions](../wiki/meta/bhrm.md)), which is exactly the case this is for.

## Log
- 20260821 - per Arryn, node stays closed; leave this ticket. Commands are low priority.

- 2026-08-21: Ticket opened by PM off a doc audit of Susan's subtree. Filed as verification work,
  not as a claim that either fix is wrong — the code changes were made and read-through verified;
  what's missing is the confirmation the node's own bar asked for.

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
