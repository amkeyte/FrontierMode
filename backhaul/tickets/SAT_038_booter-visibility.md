---
id: SAT_038
uid: SAT
number: 38
client: Satchel
status: done
title: Narrow LogicalFoundation.booter() to private
context: Found while designing SAT_037/RM_SAT_022 (Roger). No real external caller
  -- grep confirms only installConfigs()'s own booter().engine() call and one dead
  commented-out line elsewhere.
priority: low
opened: '2026-08-23'
closed: '2026-08-23'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Narrow LogicalFoundation.booter() to private

## Full report

`LogicalFoundation.booter()` (`common/jig/guts/LogicalFoundation.java`) is `public`, but grepping
`.booter(` across both repos turns up exactly one live call site: `installConfigs()`'s own
`booter().engine()`, invoked on `this` from inside the very class that declares the getter — it
doesn't need to be public for that. The only other hit anywhere is a dead, commented-out line in
`JigConfigCompiler.java` (`//var engine = Satchel.require().booter().engine();`). Nothing in either
repo reaches a `LogicalFoundation`'s booter from outside `LogicalFoundation` itself.

Found while designing [SAT_037](SAT_037_mobjig-sidedness.md)/[RM_SAT_022](../roadmap/RM_SAT_022_roger.md)
("Roger") — the new `ForgeEgress` installed there deliberately never routes through `booter()` at
all (installed from inside each concrete booter's own `installFoundation()`, reached afterward via
`LogicalFoundation.egress()` directly), on the reasoning that a public `booter()` getter invites
exactly the "call two layers deep through booter to reach a capability" shape that ticket's design
discussion explicitly moved away from. Doesn't block Roger either way — this is a clean, independent
finding, not something Roger's build needs first.

## What this ticket is asking for

Change `public ASatchelFoundationBooter booter()` to `private`, and update `installConfigs()`'s one
call site accordingly (still works fine calling a private method on `this`). If a real external
need for `booter()` shows up later, restoring the public accessor at that point is a one-line,
reversible change — not a reason to keep unused public surface around now.

## Log

- 2026-08-23: **Done — project owner's own change: "Changed booter() to private. close ticked."**

  Verified against source before closing. `LogicalFoundation.booter()` is now
  `private ASatchelFoundationBooter booter()`. Its one live call site is unchanged and still
  correct — `installConfigs()`'s unqualified `booter().engine()`, which is a call on `this` from
  inside the declaring class and legal against a private method. A repo-wide `grep -rn "\.booter("`
  across `Satchel/src` and `FrontierMode/src` returns exactly one other hit, the same dead
  commented-out line in `JigConfigCompiler.java` this ticket already named. No external caller
  existed, so nothing lost access.

  **One cosmetic leftover, deliberately not filed as its own ticket:** that commented-out line
  (`//var engine = Satchel.require().booter().engine();`) now references a private method, so it
  would no longer compile if anyone ever uncommented it. It was already dead before this change and
  is dead in exactly the same way after — noting it here so a future reader who does uncomment it
  understands why it fails, rather than treating it as a regression.

  As this ticket's own text says, restoring the public accessor is a one-line reversible change if
  a real external need appears.
- 2026-08-23: Ticket opened.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
