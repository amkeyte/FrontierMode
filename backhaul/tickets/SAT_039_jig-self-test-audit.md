---
id: SAT_039
uid: SAT
number: 39
client: Satchel
status: open
title: Audit and build automatic self-test coverage for every Jig kind
context: 'Found while scoping RM_SAT_022 (Roger): nothing in Satchel exercises MobJig''s
  CLIENT/BOTH path at all, so a real regression (client-scoped MobScopes torn down
  every cycle) sat completely undetected. Project owner wants this generalized: every
  jig kind gets its own automatic, in-repo self-test, not a manual verification pass
  repeated by hand each time.'
priority: high
opened: '2026-08-23'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

Every jig kind (`LevelJig`, `PlayerJig`, `MobJig`) needs its own automatic, in-repo self-test —
something that proves its actual runtime behavior (introduce/tick/teardown, on whichever sides it's
applicable to) without a human running a manual verification pass. Right now that only happens by
hand, and it doesn't always happen at all.

## Why this is being opened now

Scoping [RM_SAT_022](../roadmap/RM_SAT_022_roger.md) ("Roger") surfaced a concrete failure of the
current approach: nothing anywhere in either repo registers a `CLIENT`- or `BOTH`-applicability
`MobJig` consumer — `MobTrackingModule` (Satchel's own verification tool) is pinned `SERVER`
explicitly, and `BossModule` (FrontierMode's, not yet built) is designed `SERVER` too. So the
client-side teardown bug Roger's design fixes (a `CLIENT`/`BOTH`-scoped `MobScope` torn down every
reconcile cycle regardless of the mob's real state) was never actually exercisable by anything —
not a near miss, a real gap nobody could have caught by running the existing tools, however
carefully. Roger's own done bar needs a `BOTH`/`CLIENT` test path built before it can be verified at
all — this ticket is that requirement generalized to every jig kind, tracked on its own rather than
folded into Roger's build.

## What "self-test" means here — automatic, silent on pass, loud on fail

Not `/satchel mobtrack`-style manual commands (that's what verified Frank, by hand, on a real
server, once). What's wanted:

- **Runs automatically on every build** — no person has to remember to invoke it, connect a client,
  or read log output line-by-line to notice something's wrong.
- **Silent on pass.** A healthy run shouldn't add noise anyone has to scroll past.
- **Loud on fail.** A failing self-test logs clearly *and* fails hard — crashes the build, or throws
  in a way nothing swallows — not a quiet warning buried in normal output. This is a different
  posture from [RM_SAT_013](../roadmap/RM_SAT_013_gary.md)'s existing bundle-lifecycle
  `healthCheckPulse()` (see `SatchelBundle.healthCheckPulse()`), which is deliberately diagnostic-only
  and never throws — that mechanism catches silent inertness at the bundle level and should stay as
  it is; this ticket is about per-jig-kind functional correctness, a different concern, not a
  replacement for it.
- **A regression caught this way is automatically high priority** — if a self-test ever goes from
  green to red, that's treated as the same class of urgency a broken build gets, not routine backlog.
- **Lives in external test-class routines, not inline assertions scattered through production
  code.** Coverage belongs in dedicated test classes — `src/test/...` for whatever JUnit can reach,
  an equivalent isolated harness for whatever needs the in-game/integration half — never
  `assert`/ad-hoc sanity checks peppered into `MobJig`/`LevelJig`/`PlayerJig`/`ForgeEgress` or any
  other production class to satisfy this ticket. Tests stay a distinguishable concern: reading
  production code shouldn't mean reading test logic, and auditing coverage shouldn't mean hunting
  through production classes to find out what's actually verified.

## Audit — starting point, confirm and extend

Checked against source 2026-08-23:

- **`MobJig`** has exactly one automated test, `MobReconcileLogicTest`
  (`src/test/java/test/arryn/satchel/jig/mob/MobReconcileLogicTest.java`, plain JUnit 5) — covers
  `MobReconcileLogic.computeTeardowns()`'s pure logic only. It does not exercise `reconcile()`'s
  actual entity-resolution path on either side, and nothing else about `MobJig` is tested
  automatically at all.
- **`LevelJig`** — no test file exists anywhere in `src/test/`.
- **`PlayerJig`** — no test file exists anywhere in `src/test/`.
- **Gradle already runs `test { useJUnitPlatform() }`**, so anything written as a JUnit test already
  gets the "runs on every build, fails loud" property for free — the open question per jig kind is
  whether its actual behavior *can* be captured in a plain JUnit test (pure logic, the way
  `computeTeardowns` was) or needs something closer to an in-game/integration check (real `Level`,
  real Forge lifecycle) that JUnit alone can't reach — Minecraft/Forge's own GameTest framework is
  worth evaluating for that half, not assumed to be the answer.

## What this ticket is asking for

1. **Audit each jig kind** against the standard above: does an automatic, build-integrated test
   exist that would actually catch a regression like Roger's, on every side that kind claims to
   support? Confirm or correct the starting point above.
2. **Where the answer is no, design, build, and document one.** Documentation goes on the wiki
   (likely a new page, or a section on [Jig & Scope
   Runtime](../wiki/satchel/architecture/runtime.md) — Architect's call once the shape is known) —
   not just code comments, so the next person adding a jig kind or a consumer knows the coverage
   bar and how to meet it.
3. **Whatever shape the fix takes, it has to be automatic, silent-on-pass, loud-on-fail, and live in
   external test classes** — per "What 'self-test' means here" above. A manual command someone has
   to remember to run doesn't satisfy this ticket, however useful it also is to keep around, and
   neither does an inline check bolted onto production code — that's a second failure mode this
   ticket exists to rule out, not just the manual-command one.
4. **`MobJig`'s `CLIENT`/`BOTH` gap is this ticket's most urgent single item** — it's what's
   currently blocking a credible verification of Roger's done bar. Worth landing first if the audit
   confirms it's the worst gap, but the ticket's scope is all three jig kinds, not just Mob.

## Not in scope

Roger's own build (`RM_SAT_022`) — this ticket is deliberately separate from it, per project
owner's own call, even though the two are closely related and Roger's done bar will likely lean on
whatever `MobJig` self-test comes out of this ticket.

## Log

- 2026-08-23: Ticket opened.

- 2026-08-23: **Requirement added (project owner): tests must be external test-class routines, not
  inline assertions scattered through production code.** Folded into "What 'self-test' means here"
  and item 3 above rather than as a separate section — this is a constraint on the same deliverable,
  not a new one. Motivation stated directly: tests have to remain a distinguishable concern, not
  something peppered throughout the codebase.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
