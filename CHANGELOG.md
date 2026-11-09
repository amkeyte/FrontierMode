# Changelog

Release notes for FrontierMode and Satchel, newest first. The FrontierMode notes are kept deliberately general: the mode is meant
to be learned by playing it, so they say what changed without spelling out what to look for.

Satchel keeps version parity with FrontierMode. When Satchel has nothing else to report, its entry is just the version
update. From 0.2.0 Satchel's entries say specifically what changed, since there is nothing in them to spoil. Earlier versions are not recorded here.

Worlds are not guaranteed to carry over between versions. If an update misbehaves in an old world, start a new one.

---

## FrontierMode

### 0.2.0

- Feral mobs now scale with your progress rather than with how far you travel.
- Spawn balance adjustments.
- Keeper fixes, and the defeat announcement now names the Keeper's level.
- Fixed the Frontier edge reading as inside for a thin strip just outside a Border.
- Admin tool fixes.
- Worlds from earlier versions will crash when loaded; start a new world.

### 0.1.5

- Reward tuning.
- Balance data cleanup.

### 0.1.4

- Discovery structure adjustments.
- Further reward adjustments.
- Allies and wildlife tuning.
- Worlds from earlier versions may not work; start a new one.

### 0.1.3

- Fixed two single-player crashes.
- Feral mobs now also appear underground.
- The Diorite Striker's sparks got a visual update.
- Command and tooltip readability improvements.
- Reward adjustments.
- Small audio touches.

### 0.1.2

- Crash fixes.
- Nether mobs now scale with your progress.
- New item: Edge Stone. It pops when it crosses the Frontier line.
- Keepers no longer convert, for example a Zombie turning into a Drowned in water.
- Sentinels no longer stand idle at spawn.
- Defeating a Keeper now announces it, and a new Keeper announces itself when it's placed.
- Smaller fixes and balance tweaks.

---

## Satchel

### 0.2.0

- Module events: per-side ingress, post and subscribe, scoped by context key with priority, receive-cancelled and a depth guard; proven end to end with a health probe.
- Trinkets: the `DynamicTrinket` host, `ScopeInfo.getOrCreate` at attach, host jigs with a contribution API (side and order, lazy), and a global Trinket tick.
- `ScopeInfo` is now a public interface with a `jigKey()` delegate, and `jigInfo()` is removed (breaking). Scope lookup by UUID.
- Jigs: registered through the booter with side-keyed factories, a deterministic walk, `reset()` on every jig, and deferred readiness.
- Mobs: interest as a scope with a cap and sweep, instant teardown with `LeaveReason` and a leak check, a scope only for a joined mob, and `MobData.has`.
- Sides: `Satchel.isClient()` beside `isServer()`; the `LogicalSide` members are deprecated; `@PlatformBound` with a JUnit scan, and a second scan rule (platform imports must be listed, marked or on a Satchel type) with a shrink-only baseline; pre-level Forge events.
- Failure policy: programming errors crash; rescue is deferred.
- Health and sync: pilot probes, cleared statics, a client WARN for a stuck bundle, a client sweep for an unclaimed parcel, the `MobRemoved` WARN lowered, `ParcelInbox` cleared at logout, and the stop-time save limited to level scopes.
- Balance data re-synced. Version updated to match FrontierMode.

### 0.1.5

- Balance data cleanup. Version updated to match FrontierMode.

### 0.1.4

- Minor internal cleanup. Version updated to match FrontierMode.
