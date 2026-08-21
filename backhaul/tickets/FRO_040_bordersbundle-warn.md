---
id: FRO_040
uid: FRO
number: 40
client: FrontierMode
status: done
title: BordersBundle WARN has two verdicts
context: FRO_025 ruled the read-only WARN benign by design. FRO_026/RM_FRO_006 re-flagged
  it unknown.
priority: low
opened: '2026-08-21'
closed: '2026-08-21'
---

<!-- board:start -->
<!-- board:end -->

## Summary

The same WARN has two documented verdicts four days apart, and both tickets that carry them are
closed.

**[FRO_025](FRO_025_client-crash-borders-jig-not-installed-o.md), 2026-08-16** — examined
`[engine] CLIENT bundle became dirty (read-only violation)` on `BordersBundle` and ruled it benign:
"the documented SAT_024 first-creation fallback path firing exactly as designed," alongside
`BundleNotFound ignored; falling through to create`, immediately followed by `clearDirty()` and a
successful `Borders loaded: 5`. Fired once per new scope/dimension. Recorded, in that ticket's own
words, "only so it isn't mistaken for a new bug on the next log read."

**[FRO_026](FRO_026_sandra-implementation.md) and
[RM_FRO_006](../roadmap/RM_FRO_006_sandra.md), 2026-08-20** — it was mistaken for a new bug on the
next log read. Both flag the identical WARN, on the identical bundle, at the identical cadence
(once per dimension load), as an unexplained observation "worth a look wherever `BordersBundle`'s
client-side read-only handling is next touched." Neither references FRO_025.

Both tickets are now closed and the observation is tracked nowhere.

## What to actually do

Cheap path first: confirm the two descriptions are the same phenomenon. They match on bundle, side,
message, and cadence, and FRO_025's explanation predicts exactly what Sandra observed — so most
likely FRO_025 was right and Sandra's flag is a rediscovery.

If so, the fix is documentation, not code: note the resolution on
[RM_FRO_006](../roadmap/RM_FRO_006_sandra.md)'s observation so the next log reader lands on
FRO_025's answer instead of re-flagging it a third time. FRO_025's own "so it isn't mistaken on the
next log read" note failed at its one job because it was buried in a closed ticket about a
different crash.

If not — if something changed between 08-16 and 08-20 that makes the newer sighting a different
event — that's a real Satchel-side finding and needs a SAT ticket rather than living here.

## Log

- 2026-08-21: **Done — written into the wiki, project owner's call: this is a factual statement
  about how the client behaves, not a status.** That reframing is the right one and changes the fix:
  a ticket annotation would have answered the question for whoever read that ticket, whereas the
  behavior belongs where someone reading a client log will actually look it up.

  **Home: [Jig & Scope Runtime](../wiki/satchel/architecture/runtime.md), in the
  `AScopeCoupler.getOrCreate()` section** — the page and passage that already explains the
  first-creation fallback these two lines come out of. It now states them verbatim, explains why
  each appears (the first is the catch clause announcing itself; the second is a read-only client
  bundle marking itself dirty during construction and tripping the guard's warning, cleared
  immediately after), and gives the expected cadence: **once per new scope**, so once per dimension
  on every world entry and portal transition.

  Also written down, because a "this is fine" note is only useful if it says what *wouldn't* be
  fine: a repeating per-dimension cadence is the normal shape, while the pair appearing without a
  successful hydrate behind it, or twice for the same scope, would be a real signal.

  [RM_FRO_006](../roadmap/RM_FRO_006_sandra.md)'s own observation now carries a pointer to that
  section, so the flag and its answer sit together. The original log entry is left as written.

  **Resolves the disagreement this ticket was filed against.** No code changed and none was needed —
  [FRO_025](FRO_025_client-crash-borders-jig-not-installed-o.md)'s reading was correct all along.
  The failure was that its conclusion lived in a closed ticket about a different crash, so
  [FRO_026](FRO_026_sandra-implementation.md) and RM_FRO_006 re-flagged it four days later without
  finding it. Putting it on the architecture page is what stops a third pass.
- 2026-08-21: Ticket opened by PM off a doc audit of Susan's subtree. Filed low: no evidence of
  actual misbehaviour on either reading, and FRO_025's analysis is specific and checkable.

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
