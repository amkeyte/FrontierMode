---
id: RM_SAT_013
uid: RM_SAT
number: 13
kind: work
status: resolved
title: Add silent-inertness health-check
owner: Arryn
depends_on:
- RM_SAT_011
created: '2026-08-14'
superseded_by: null
ticket: null
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Roadmap Index](../ROADMAP_INDEX.md) · RM_SAT
<!-- bh-header:end -->

## Add silent-inertness health-check

- 2026-08-15: **Resolved by Lead Dev (Curtis), scoped to diagnostic + confirm-only** (project
  owner's call — the proactive-push question below is reported, not fixed, in this pass).
  - **Boot-time check** (`LogicalFoundation.installConfigs`, new
    `checkExecutionPulseHealth(key, cfg)`): for each compiled `JigConfig`, warns if
    `policies().capabilities()` requires persistence and/or networking but
    `execution().lifecycle().participatesInExecutionPulse()` is false — the exact FRO_018 shape
    (tick-enabled, capability-requiring, execution-pulse silently off). Deliberately checks
    capability requirements, not just "has fixtures," since that's what actually gates
    `flushIfDirty`/`scheduleSync`/`applyIncomingParcels` per
    [Jig & Scope Runtime](../wiki/satchel/architecture/runtime.md).
  - **Runtime check** (`SatchelBundle.healthCheckPulse()`, called once per execution pulse from
    both `ScopeEngine_Server.onExecutionPulse`/`ScopeEngine_Client.onExecutionPulse`, alongside
    `pulseSync`): warns once if a bundle has sat below `ACTIVE` for more than
    `INERTNESS_WARNING_TICKS` (10s — deliberately double `DEFAULT_SYNC_INTERVAL_TICKS`'s 5s so a
    bundle just waiting on its first regular sync interval doesn't trip it under normal
    conditions), then goes quiet — resets on reaching `ACTIVE`, warns at most once per bundle.
  - **Finding on the proactive-push question, not fixed this pass:** traced
    `SatchelBundle.pulseSync(ScopeInfo)` directly. **Confirmed: there is no proactive push.** The
    sync delegate (`ScopeEngine_Server::scheduleSync`) only fires from the same fixed-interval
    counter `pulseSync` already uses for everything — `syncCounter` increments every execution
    pulse and only calls `syncDelegate.sync(...)` once it reaches `syncIntervalTicks`
    (`DEFAULT_SYNC_INTERVAL_TICKS` = 100 ticks / 5s at 20 TPS). `onCreated()` calls `markDirty()`
    but that has no effect on when the *first* sync fires — `pulseSync` doesn't consult
    `isDirty()` at all, so a freshly created bundle's first parcel is sent by the same periodic
    counter as every later one, meaning up to 5s of latency after creation before a client bundle
    receives anything, not immediately. This is *not* "sits inert forever" in the normal case —
    execution-pulse being on (now guarded by the boot-time check above) guarantees the counter
    eventually fires — but it is confirmed to be interval-only with no fast path, which the wiki
    page correctly flagged as unverified. Left as a finding, not a fix, per this pass's scope;
    whoever picks up a fast-path push (e.g. an immediate `scheduleSync` call from
    `ScopeEngine_Server.create()`/`hydrateBundle` right after a bundle first reaches `ACTIVE`)
    should update [Jig & Scope Runtime](../wiki/satchel/architecture/runtime.md#known-gaps)'s
    "Known gaps" entry alongside it.
  - Not build-tested in this sandboxed session (no Forge/Mojang maven access) — reviewed by
    tracing the call chain, same caveat as RM_SAT_014.
- 2026-08-14: Node opened, per
  [SAT_031](../tickets/SAT_031_handoff-retrospective-recommendations.md) recommendation #3.

Three separate bugs from the runtime-verification pass shared one shape: a jig or bundle silently
sat inert or stuck in the wrong state, with no error and no log line, discoverable only by a human
noticing gameplay wasn't working —
[SAT_026](../tickets/SAT_026_network-register-never-called.md) (network channel never
registered), [FRO_018](../tickets/FRO_018_border-executionpulse-disabled.md) (execution-pulse
never enabled), and [SAT_027](../tickets/SAT_027_server-hydrate-bypasses-lifecycle.md) (hydration
bypassing the lifecycle contract). A lightweight health-check — e.g. log a warning if a bundle
hasn't reached `ACTIVE` within N ticks of creation, or if a jig's `executionPulse` flag is off but
it has fixtures expecting sync — would have surfaced several of these in seconds instead of a full
manual log trace.

Also folds in a sharper, related question from
[Jig & Scope Runtime](../wiki/satchel/architecture/runtime.md#known-gaps)'s last known gap: whether
the server proactively syncs a bundle's first parcel on scope load, or only syncs already-dirty
bundles on its regular interval, is currently unverified — a client bundle that never receives a
first parcel just sits inert forever rather than crashing (SAT_025 stopped it from crashing, it
didn't confirm the sync path itself). Confirming and, if needed, fixing that proactive-push
behavior belongs under this same health-check work, not as a separate node — it's the same "silent
inertness, no signal" shape as the three bugs above.

## Required By

<!-- required-by:start -->
- [**RM_SAT_017**](RM_SAT_017_paul.md) — Prototype hardening
<!-- required-by:end -->
