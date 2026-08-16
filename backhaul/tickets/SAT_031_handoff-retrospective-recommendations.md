---
id: SAT_031
uid: SAT
number: 31
client: Satchel
status: done
title: 'Handoff retrospective: recommended next steps before next phase'
context: Not a bug. Synthesis ticket written at the close of the runtime-verification
  pass (SAT_022-030, FRO_015-019) -- ten real "does it run" bugs found and fixed after
  the mods already built clean. Soft recommendations for what to look at next, before
  handing this back to Douglas, based on patterns visible across the whole pass.
priority: normal
opened: '2026-08-14'
closed: '2026-08-14'
---

<!-- board:start -->
<!-- board:end -->

## Summary

Not a bug report — a handoff note. Ten real, previously-invisible runtime bugs
([SAT_022](SAT_022_tracker-bundlefactory-missing.md),
[SAT_023](SAT_023_scopeengine-shared-config.md),
[SAT_024](SAT_024_client-engine-wrong-exception.md),
[SAT_025](SAT_025_client-tick-missing-active-guard.md),
[SAT_026](SAT_026_network-register-never-called.md),
[SAT_027](SAT_027_server-hydrate-bypasses-lifecycle.md),
[SAT_029](SAT_029_send-sidedness-classcast.md),
[SAT_030](SAT_030_client-refresh-single-shot-hydrate.md),
[FRO_015](FRO_015_empty-path-tip-crash.md),
[FRO_017](FRO_017_growpath-never-registered.md)) got found and fixed in this one
"does it build vs does it run" pass, all downstream of code that compiled clean and
passed validation. That density is itself the finding worth carrying forward, not just
the individual fixes. Below are soft recommendations, not commitments — flagging
what's worth a conscious decision before the next phase starts, not asking for
immediate action.

## Recommendations

**1. Confirm FRO_016 before calling this pass fully closed.**
[FRO_016](FRO_016_null-level-on-exit-crash.md) (null-level crash on world disconnect)
is fixed but not yet exercised by an actual exit this session — everything else in the
chain got a real re-run to confirm; this one didn't. Worth one clean "join, play, quit"
cycle before treating the whole pass as verified end to end.

**2. Prioritize SAT_028 before more modules get built on the current pattern.**
[SAT_028](SAT_028_consolidate-bundlefactories-into-schema.md) (BundleFactories vs
JigBundles.Schema duality) is filed as deferred, correctly — but it's already caused
the *same* bug twice independently (SAT_022 in TrackingModule, and the identical shape
in Border, which only avoided it because of a defensive comment). If more modules are
coming, every one of them inherits this footgun fresh. Not urgent, but the cost of
deferring it goes up with each new module built on the current registry, not down.

**3. The silent-inertness pattern showed up repeatedly — worth a structural fix, not
just point fixes.** SAT_026 (network never registered), FRO_018 (executionPulse never
enabled), and SAT_027 (hydration bypassing the lifecycle) all shared the same shape: a
jig or bundle silently sat inert or stuck in the wrong state, with no error, no log
line, nothing to catch it short of a human noticing gameplay wasn't working. Consider a
lightweight health-check — e.g. log a warning if a bundle hasn't reached ACTIVE within
some N ticks of being created, or if a jig's executionPulse flag is off but it has
fixtures that expect syncing. Would have surfaced several of this pass's bugs in
seconds instead of requiring a full manual trace through the log.

**4. `LevelJig` scopes never actually unload — worth a deliberate decision either way.**
Flagged in passing during FRO_016's trace and already noted as a "known gap" in
runtime.md: `LevelEvent.Unload` re-announces the source instead of removing it, so a
`LevelJig` scope's unload path is currently dead code in practice. That's fine if it's
an intentional simplification for now, but it's currently just an ambient gap rather
than a documented decision — worth either writing down "not implementing unload,
because X" or scoping it as real work.

**5. Consider a short "adding a new Satchel module" checklist.** Given the scale
ambition discussed earlier in this pass (fixtures as a shared base across ~20 planned
modules) and that two of this pass's bugs were the *same* omission made independently
in two different modules, a short wiki checklist (register schema AND factory, set
executionPulse if you need sync, wire your Forge listeners, etc.) would be cheap
insurance against every future module rediscovering the same handful of footguns one
at a time.

**6. Minor, cosmetic:** the gold-block growth trigger radius is intentionally tight (4
blocks around the exact path tip) — confirmed correct behavior, not a bug, but worth a
quick gameplay-feel gut check with Douglas since it wasn't an explicit design spec,
just the value already in the code.

None of the above blocks anything currently working. Flagging for a conscious call,
not asking for action.

## Log

- 2026-08-14: Every recommendation given a conscious disposition (this ticket's actual job,
  per its own framing — "flagging for a conscious call, not asking for action"). Closing.
  - #1 (confirm FRO_016) — stays tracked on [FRO_016](FRO_016_null-level-on-exit-crash.md)
    itself, still in-progress pending a real join/play/quit cycle. Not resolved by this ticket;
    correctly stays its own thing.
  - #2 (prioritize SAT_028) — promoted onto the roadmap as
    [RM_SAT_012](../roadmap/RM_SAT_012_donald.md), actionable now.
  - #3 (silent-inertness structural fix) — promoted as
    [RM_SAT_013](../roadmap/RM_SAT_013_gary.md), actionable now.
  - #4 (LevelJig unload decision) — promoted as
    [RM_SAT_014](../roadmap/RM_SAT_014_joseph.md), actionable now.
  - #5 (new-module checklist) — promoted as
    [RM_SAT_016](../roadmap/RM_SAT_016_kenneth.md), sequenced after RM_SAT_012.
  - #6 (gold-block radius gut-check) — explicitly out of Architect/roadmap scope, a Game
    Designer gameplay-feel question. Not promoted; not lost either, noted in
    [RM_FRO_010](../roadmap/RM_FRO_010_susan.md)'s body so it isn't rediscovered from
    scratch.
- 2026-08-14: Ticket opened at the close of the runtime-verification pass.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
