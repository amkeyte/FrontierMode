---
id: SAT_036
uid: SAT
number: 36
client: Satchel
status: done
title: Retro-track two shared-framework log fixes
context: SAT_035 changed SatchelException and Tracer outside its scope. Curtis flagged
  both for splitting out.
priority: low
opened: '2026-08-22'
closed: '2026-08-23'
---

<!-- board:start -->
<!-- board:end -->

## Summary

[SAT_035](SAT_035_mobjig-build.md) made two changes to **shared framework code that every jig kind
depends on**, neither in its scope. Lead Dev flagged both explicitly at the time — "flagging the
scope boundary in case this should split to its own ticket later" — rather than letting them ride
silently, which is the right call and the reason this ticket exists. Both are already implemented
and are live in the same build that verified Frank; this is retroactive tracking, not new work.

Filed because a framework change recorded only inside another ticket's log is the exact shape this
project has been bitten by before — see [FRO_040](FRO_040_bordersbundle-warn.md), where a finding
buried in a closed ticket got rediscovered and re-flagged four days later by someone who couldn't
find it.

## Change 1 — `SatchelException.BundleNotFound` no longer logs at ERROR

`common/jig/guts/SatchelException.java`. The base constructor logged at ERROR unconditionally, so
every routine "bundle doesn't exist yet, create it" event emitted an ERROR
("A requested bundle was not found: ...") immediately followed by `AScopeCoupler`'s WARN
("BundleNotFound ignored; falling through to create") — on every scope load, on every jig kind.
`BundleNotFound` is the only `SatchelException` subtype ever caught anywhere (always in
`AScopeCoupler.getOrCreate`, always to fall through to `create`), confirmed by grep.

Fix: a silent-constructor variant, with both `BundleNotFound` constructors routed through it.
Every other subtype logs exactly as before. An uncaught `BundleNotFound` still surfaces via the
JVM's own stack trace, so nothing about a genuine failure goes quiet.

**Verified against the documented behavior it touches.** [Jig & Scope
Runtime](../wiki/satchel/architecture/runtime.md#jiginfo-scopeinfo-and-satcheljig) documents the
client's first-creation log pair for exactly this path (added by FRO_040). Checked against source
2026-08-22: both lines that page names still emit — `AScopeCoupler`'s WARN and
`ScopeEngine_Client`'s "[engine] CLIENT bundle became dirty (read-only violation)". Only the
third, redundant ERROR is gone. **That page is still accurate and needs no correction.**

## Change 2 — `Tracer` logs at TRACE instead of DEBUG

`common/util/out/Tracer.java`. Both emission points called `LOGGER.debug(...)` despite the class
being named `Tracer` and one overload prefixing its output with `[TRACE]`. Since
`Satchel/build.gradle` sets `forge.logging.console.level = 'debug'` for dev runs, every `Tracer`
call reached the console by default — including unthrottled ones (`ParcelInbox`'s two calls,
`ScopeEngine_Server`'s "Sending Parcel") and a per-render-frame readiness check in FrontierMode's
border rendering firing every 1-3 seconds.

Fix: both calls changed to `LOGGER.trace(...)`. No call site changed; the throttling and
enable/disable machinery is untouched. Anyone who wants the detail can set their own level to
TRACE. `INFO`-level output (`[MobTracking]`/`[PlayerTracking]` LOADED/TICK/UNLOADED, via
`OUT.info(...)`) goes through a different path entirely and is unaffected.

## What this ticket is actually asking for

Nothing to build. Two things to decide:

1. **Whether change 2's blast radius was checked.** It suppresses console output for every
   `OUT.TRACE()` call across both repos, not just the noisy ones. If any diagnostic anyone
   currently relies on was riding `Tracer` at DEBUG, it silently stopped appearing in dev runs. A
   grep of `OUT.TRACE()` call sites would settle it in a few minutes.
2. **Whether either change deserves a line on a wiki page.** Change 1's "routine misses don't log
   ERROR" is arguably a fact about how Satchel reports missing bundles, which
   [Jig & Scope Runtime](../wiki/satchel/architecture/runtime.md) already half-documents; change 2
   is closer to a build/dev-environment convention than an architecture fact. Architect's call.

Low priority: both changes are live and working, and neither blocks anything.

## Log



- 2026-08-22: Ticket opened.

- 2026-08-23: **Closed — project owner's call.** Checked item 1 directly rather than leaving it
  open: grepped `OUT.TRACE()` across both repos. Every call site is Satchel-internal diagnostic
  logging — bundle creation/dirty-parcel handling in both `ScopeEngine_Server`/`ScopeEngine_Client`,
  jig readiness/scope-acceptance in `ASatchelJig`/`SatchelFixture`, parcel enqueue/drain in
  `ParcelInbox`, saved-data creation in `BundleSavedData`/`WorldIdentitySavedData`, plus
  `OUT_Tester`'s own demo calls. Nothing in FrontierMode calls `OUT.TRACE()` at all — no hit outside
  `Satchel/`. Nothing here reads as a diagnostic anyone's actually relying on at DEBUG; the change
  is exactly what it was described as doing. Item 2 (wiki mention) was already effectively answered
  in this ticket's own body — change 1 needs no wiki correction (the page it touches is still
  accurate), change 2 is a dev-environment log-level convention, not an architecture fact, not worth
  a line. Project owner's explicit framing: this started as "make the log spam go away," Curtis did
  exactly that, and nothing about it is broken — closing rather than continuing to track it as open
  work. Re-open if a real symptom (a diagnostic someone actually needed at DEBUG) ever surfaces.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
