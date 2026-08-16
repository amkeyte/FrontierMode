---
id: SAT_009
uid: SAT
number: 9
client: Satchel
status: done
title: Fix validateTypes(jig, scope) call order
context: Two call sites pass validateTypes(jig, scope) backwards (expects key, jig).
  Routed to PM -- pause/reroute to Architect when picked up.
priority: normal
opened: '2026-08-13'
closed: '2026-08-13'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Fix validateTypes(jig, scope) call order

### Detail

`JigKey.validateTypes(Object key, Object jig)` requires its first argument to actually be a
`JigKey` (its first check is `key instanceof JigKey<?> jk`, throwing `IllegalStateException`
otherwise). Two call sites pass the arguments backwards:

- `Satchel.get()` (`Satchel.java`): `JigKey.validateTypes(jig, scope)` — first arg is a
  `SatchelJig`, second is a `SatchelScope`. Neither is a `JigKey`.
- `LogicalFoundation.requireScopeInfo()`: `JigKey.validateTypes(ji.jig, scope)` — same pattern.

Contrast with `JigInfo`'s constructor, which calls it correctly: `JigKey.validateTypes(key, jig)`.

Compiles fine (both params are typed `Object`), so this is a runtime bug, not a compile error —
out of scope for [SAT_008](SAT_008_fix-4-satchel-compile-blocking-errors.md), where it was found.
Likely never actually hit yet, since nothing currently populates the jig registry end-to-end (see
[Jig & Strap Registration](../wiki/satchel/architecture/jig-registration-break.md)).

Open question for whoever picks this up: swap the two call sites' argument order (mechanical), or
reconsider `validateTypes`'s signature/name if the (key, jig) contract itself deserves scrutiny —
Architect's call.

### Log

- 2026-08-13: Ticket opened.
- 2026-08-13 (Architect): Checked `JigKey.validateTypes(Object key, Object jig)` directly — the
  contract is unambiguous (`key instanceof JigKey<?>` is the first check, params are literally
  named `key`/`jig`), and `JigInfo`'s constructor already calls it correctly in that order. No
  signature/name reconsideration needed — this is a plain argument-order bug at the two call
  sites. Swap the arguments; nothing more to decide here.
- 2026-08-13: Swapped both call sites to `validateTypes(key, jig)`. Grepped all call sites
  project-wide (4 total: `Satchel.get()`, `LogicalFoundation.requireScopeInfo()`, `JigInfo`'s
  constructor, `JigConfigCompiler.instantiateJig()`) — the latter two were already correct, all
  four now agree. Confirmed by a real `gradlew build`: `BUILD SUCCESSFUL`. Closing.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
