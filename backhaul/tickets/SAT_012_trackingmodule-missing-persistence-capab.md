---
id: SAT_012
uid: SAT
number: 12
client: Satchel
status: done
title: TrackingModule missing persistence capability
context: LevelJigConfig defaults requiresPersistence=false; TrackingModule never overrides
  it. Routed to PM -- pause/reroute when picked up.
priority: normal
opened: '2026-08-13'
closed: '2026-08-13'
---

<!-- board:start -->
<!-- board:end -->

## Summary

TrackingModule missing persistence capability

### Detail

`ScopeEngine_Server.hydrateBundle()` and `flushIfDirty()` both call
`resolveServerLevel(info, CapableOf.PERSISTENCE)`, which returns `null` (capability not required
→ skip) whenever `policies().capabilities().requiresPersistence()` is `false`. Both call sites
then treat a `null` level as an error and throw `SatchelException.AccessFailed` unconditionally
-- there's no code path where "persistence not required" is actually a legitimate no-op for
`create()`/`flushIfDirty()`. So in practice, `requiresPersistence` isn't optional for any
`LevelJig`-based config whose bundles get created server-side at all -- it's `true` or the very
first `getOrCreate()` call throws.

`LevelJigConfig.createPresets()` sets `p.policies.capabilities = JigPolicies.Capabilities.defaults()`,
and `Capabilities.defaults()` is `(false, false, false)` -- `requiresPersistence` defaults false.
`TrackingModule.init()` never calls `config.policies().capabilities(...)` to override it. So the
first time `TrackingModule.onScopeLoaded()` calls `jig.getOrCreate(info.scope(), TrackingModule.BUNDLE)`
on the server, it should hit this and throw.

Found while porting Border to the same `LevelJigConfig` pattern (FRO_012) -- Border's config
needed the same override (`config.policies().capabilities(new JigPolicies.Capabilities(true, false, false))`)
since border state is persisted, and TrackingModule (the "live template" the port was told to
copy) turned out to have the same gap already. Not fixed here since it's Satchel's own tracker,
not FrontierMode/Border scope, and whether `TrackingModule`'s bundle is actually meant to persist
(vs. being intentionally ephemeral, in which case the real fix is elsewhere, e.g. in
`ScopeEngine_Server`'s null-handling) is a design call, not a mechanical one.

### Suggested fix

Either set `config.policies().capabilities(new JigPolicies.Capabilities(true, false, false))` in
`TrackingModule.init()` (if the tracker bundle should genuinely persist), or reconsider whether
`ScopeEngine_Server`'s persistence-required check should have a real "not required" path instead
of always throwing on a `null` level -- worth an Architect look either way, since the same
question will recur for the next `LevelJigConfig` consumer.

### Log

- 2026-08-13: Ticket opened.
- 2026-08-13 (Architect): Read `TrackingModule.java`/`TrackerFixture.java` directly rather than
  assuming from the name. `TrackerFixture` holds four `long` counters (`loads`, `unloads`,
  `externalTicks`, `internalTicks`) and never calls `registerCustom(...)` or any other
  field-persistence helper — contrast with `BordersFixture`, which explicitly registers its
  persisted state. This is a pure in-memory diagnostic counter with no save/load path at all. Read
  as designed-ephemeral, not "forgot to persist" — setting `requiresPersistence = true` here would
  be matching Border's fix mechanically without matching Border's actual situation.

  So the second option in the Suggested fix is the right one, not the first. Traced both call
  sites in `ScopeEngine_Server`: `resolveServerLevel(info, requirement)` returns `null` for two
  completely different reasons — "capability not required, this is a legitimate skip" and
  "capability required but no Level available, this is a real error" — and both `hydrateBundle()`
  and `flushIfDirty()` collapse that distinction, unconditionally throwing `AccessFailed` the
  moment `level == null`, with no branch for the first case. That means `requiresPersistence =
  false` is not actually a usable value anywhere in the `LevelJigConfig` path today — the very
  first `create()` call (which `hydrateBundle()` runs inside of, on every scope load) throws for
  *any* config that leaves it at the `Capabilities.defaults()` value, persisted or not. This isn't
  TrackingModule-specific; it'll hit the next `LevelJigConfig` consumer too, the same way it would
  have hit Border if FRO_012 hadn't set the flag.

  Call: fix `ScopeEngine_Server`, not `TrackingModule`. Give `hydrateBundle()`/`flushIfDirty()` a
  real no-op path when `resolveServerLevel(...)` returns `null` because the capability genuinely
  isn't required (as opposed to required-but-missing) — the ambiguity is that `resolveServerLevel`
  overloads one sentinel (`null`) for two meanings its callers can't tell apart. Leave
  `TrackingModule.init()` alone; its tracker bundle should stay ephemeral. Split into
  [SAT_013](SAT_013_scopeengine-server-null-as-skip-vs-null.md), scoped correctly against
  `ScopeEngine_Server`. Closing this one — the title's premise ("TrackingModule is missing a
  capability it needs") doesn't hold up; nothing further to do under this framing.
<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/Satchel)
<!-- bh-header:end -->
