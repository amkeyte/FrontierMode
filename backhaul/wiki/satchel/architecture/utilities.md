---
id: satchel/architecture/utilities
category: satchel/architecture
slug: utilities
title: Utilities
summary: Cross-cutting helpers under common/util and server/util -- structured logging
  (OUT/Tracer), tick-interval gating (TickThrottler), side-marking (SideToken), and
  client/server simulation parity (SimParity).
keywords: null
status: verified
updated: '2026-09-06'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · satchel / architecture
<!-- bh-header:end -->

# Utilities

Small, stateless-or-nearly-stateless helpers under `common/util`/`server/util` (`com.arryn.satchel.*.util`)
that any module reaches for directly -- no fixture, no bundle, no `JigConfig`. Same category of
thing as [Border's `BorderMath`](../../frontiermode/architecture/border-curve.md): pure supporting
code, not part of the jig/scope runtime itself.

## `OUT` / `Tracer` -- structured logging

`OUT` (`common/util/out/OUT.java`) is the project's logging facade: static `info`/`warn`/`debug`/
`error` calls wrapping SLF4J's `LogUtils.getLogger()`, plus a `dumpStack()` helper. This is the
established discipline behind "reject -> log via `OUT.warn`" already documented as Border's and
Boss's own validation convention (see [Border § Mutation
surface](../../frontiermode/architecture/border.md#mutation-surface) and [Boss § Mutation
validation boundary](../../frontiermode/architecture/boss.md#mutation-validation-boundary)) -- both
point back at this class, not a raw logger call.

`Tracer` (`common/util/out/Tracer.java`) is a named logging channel with its own enable/disable
state, held by `OUT` as a default instance (`OUT.TRACE()`) alongside a registry of additional
named tracers (`OUT.TRACE(tracerId)`, `OUT.registerTracer(...)`). Its distinguishing feature is a
`Caller` registry: a specific call site (an object + optional tag) can register itself against a
`ThrottleClockSource` and an interval, so a tracer logs that call site's messages at most once per
interval instead of once per tick/frame -- built specifically so render- and tick-thread logging
doesn't flood the console.

## `TickThrottler` / `ThrottleClockSource` -- interval gating

`ThrottleClockSource` (`common/util/throttle/`) is a pluggable "elapsed" counter -- an interface
around whatever the implementation counts (real ticks, poll count, anything monotonic). Two
concrete sources exist: `ServerWorldTimeClockSource` (`server/util/throttle/`, wraps
`MinecraftServer.overworld().getGameTime()`) and `TickThrottler.AutoClock` (increments once per
poll, no external clock needed).

`TickThrottler` itself: given an interval and a clock source, `allow()` returns `true` no more
than once per `interval` elapsed units, advancing in whole steps to avoid drift rather than
resetting to zero. Real call sites today: `BorderPregenFixture`, `BordersRevisionMonitor`,
`SatchelFixture`, `ASatchelJig`, `MobJig` -- gating expensive or log-worthy work on tick/render
threads without a separate scheduler.

**Worth being precise about what guarantee this does and doesn't provide.** `TickThrottler`'s
window starts at whenever `allow()` is first polled on that particular instance -- it isn't pinned
to a fixed point on the shared game-time counter. Two independent instances (one on the server,
one on the client, say) aren't guaranteed to agree on which ticks return `true` unless something
else pins them to the same phase. That's exactly right for its actual job -- don't repeat this
expensive/loggable thing too often on this one side -- but it's a different guarantee from two
independent sides computing an identical answer without talking to each other. See "Honorable
mention" below.

## `SideToken`

A bare marker interface (`common/util/SideToken.java`) with two empty implementations, `Server`
and `Client`. No current call sites reference it.

## `SimParity` -- simulation parity

`SimParity` (`common/util/SimParity.java`) is a third axis alongside sidedness and readiness --
not "which side am I on" or "is state here yet," but "do both sides agree, without a packet." A
pure static utility -- no fixture, no bundle, no `JigConfig` -- built from two independent pieces
callers combine at the call site:

* `isCheckpoint(long gameTime, long interval)` -- a timing gate on `gameTime mod interval`, never
  a raw tick-delta comparison. If both sides briefly disagree on the exact tick (a caught-up
  client, network jitter), they re-agree automatically at the next multiple of `interval`, since
  both read the same shared counter against the same fixed divisor rather than tracking
  independent per-side state.
* `parityValue(UUID id, long gameTime, long salt)` / `parityRoll(UUID id, long gameTime, long salt)`
  -- deterministically mixes a stable id, `GameTime` (never wall-clock time, which isn't reliably
  synced between server and client), and a per-call-site salt into a value both sides compute
  identically. `parityRoll` maps the same mix to a double in `[0, 1)` for callers comparing
  against a probability directly.

The mix itself is the SplitMix64 finalizer applied to XOR-folded inputs -- built entirely from
primitive long arithmetic rather than `Object.hashCode()`/`UUID.hashCode()`, so bit-identical
output across JVMs holds by construction rather than by relying on a language-spec guarantee.
`SimParityTest` locks the exact mix output with independently-computed golden vectors, alongside
determinism/sensitivity/range checks, so a future refactor can't silently change it.

This is a deliberately different guarantee from `TickThrottler` above -- that one gates one side's
own repeated calls; `SimParity` is meant to make two independent sides agree with each other by
construction.

## Related pages

- [Fixture](fixture.md), [Bundle](bundle.md), [Jig & Scope Runtime](runtime.md) -- the machinery
  this package supports rather than participates in
- [Border](../../frontiermode/architecture/border.md), [Boss](../../frontiermode/architecture/boss.md) --
  both cite `OUT`'s reject-then-warn discipline as their own validation convention
