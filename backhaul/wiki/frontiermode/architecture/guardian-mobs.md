---
id: frontiermode/architecture/guardian-mobs
category: frontiermode/architecture
slug: guardian-mobs
title: Guardian Mobs
summary: 'Technical shape for BossGuardiansFixture (RM_FRO_029, Gloria): a stateless
  BossBundle sibling hooking Forge''s mob-spawn-finalization event, gated on Border
  Curve''s placement/difficulty records -- grounded directly against BossTellFixture,
  the sibling already built for Environmental Tells.'
keywords: null
status: verified
updated: '2026-09-07'
---

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../../../BACKHAUL.md) · [Wiki Index](../../../WIKI_INDEX.md) · frontiermode / architecture
<!-- bh-header:end -->

# Guardian Mobs

The architecture counterpart to [Guardian Mobs](../design/guardian-mobs.md)'s design intent, and a
dedicated page graduated from [Boss Discovery Systems](discovery-systems.md#guardian-mobs)'s own
Guardian Mobs section, the same move [Border Curve](border-curve.md), [Border
Pregeneration](border-pregeneration.md), and [Special Compass](special-compass.md) each made once
their own proposals firmed up enough to warrant a page of their own. Feeds
[RM_FRO_023](../../../roadmap/RM_FRO_023_kathleen.md) ("Kathleen") via
[RM_FRO_029](../../../roadmap/RM_FRO_029_gloria.md) ("Gloria").

**Closer to build-ready than Compass or Beacons: nothing here waits on an undecided game rule.**
RM_FRO_029 is gated only on [RM_FRO_035](../../../roadmap/RM_FRO_035_donna-03.md) ("Donna_03")
clearing -- the same shared-infrastructure edge wired onto every Tier 2 discovery-gradient
sibling -- and its own node names one further open item: which Borders actually turn Guardian
Mobs on, and at what curve settings, a Game Designer config question, not a blocker on building
the mechanism itself. Everything below is proposal, not ruling, same as every other page in this
cluster -- but it leans harder on real, already-shipped code than Compass's page could, for a
concrete reason: [Environmental Tells](discovery-systems.md#environmental-tells) already shipped
a sibling fixture of almost exactly this shape (`BossTellFixture`), and this page works out
Guardian Mobs as that fixture's nearest relative rather than a design worked out from scratch.

## The nearest real precedent: `BossTellFixture`

**Not hypothetical, the same way Special Compass's formalization plan wasn't:** `BossTellFixture`
is real, shipped code -- a sibling fixture to `BossFixture` inside `BossBundle`, reading a
purpose-keyed `BorderCurve` via `BorderCurveFixture`, computing intensity through
`BorderMath.intensityAt(BorderCurve, double)`, and rolling independent probabilities off
`BossRules`-tunable coefficients (`tellParticleCoefficient()`, `tellSoundCoefficient()`). Guardian
Mobs was always described, on the hub page, as reading the same kind of curve the same way; having
a real, working sibling built now means this page can propose Guardian Mobs' own shape as a close
variant of Tell's, not an independent design.

**The one real structural difference: trigger.** `BossTellFixture` is cadence-gated off its own
`onJigTick()` (`BossRules.tellTickInterval()`), evaluated per online player every interval.
Guardian Mobs is event-gated -- it has to answer its question exactly once, at the moment a hostile
mob is about to materialize, not on a recurring timer. Everything downstream of "what's the
intensity here" reuses Tell's pattern directly; only the trigger and the position being evaluated
change.

## `BossGuardiansFixture`: `BossBundle`'s next sibling

`BossBundle` today (`boss/common/bundle/BossBundle.java`) hosts exactly two fixtures --
`BossFixture` (`FrontierKeys.BOSS`) and `BossTellFixture` (`FrontierKeys.BOSS_TELL`) -- registered
in `BossModule.registerBossJig()` as a two-entry `FixtureDecl` list on the bundle's `Schema`.
**Proposed: a third entry, `FrontierKeys.BOSS_GUARDIANS`, following the exact same registration
shape** -- a new `FixtureDecl<BossGuardiansFixture>` added to that same list, and a matching
`guardians()` accessor added to `BossBundle` alongside its existing `boss()`/`tell()` methods. No
change to `BOSS_JIG`'s own binding/persistence/lifecycle configuration is needed for this addition
by itself -- see "Event-driven, not tick-driven" below.

**Zero persisted state, per the hub page -- worth being a real `SatchelFixture` anyway.** A
guardian isn't tracked once it spawns; there's no record to save or load, so `BossGuardiansFixture`
would need no `registerCustom(...)` call the way `BossTellFixture`'s constructor makes one for its
`KEY_TELLS` list. The reason to make it a fixture at all, rather than a stateless static utility in
the shape of `BorderPathCompass`, is access parity: `BossTellFixture.onJigTick()` reaches
`BossFixture` through `getBundle().boss()`, not through a second, parallel static lookup -- putting
Guardian Mobs' own logic on a fixture inside the same bundle keeps it on that one path too, and
keeps it discoverable next to its siblings in `BossBundle`'s own query surface rather than living
as an unrelated utility class elsewhere. Costs nothing today; leaves the door open if a future
requirement (a density cap, say -- explicitly not needed now, see "Open questions" below) ever
does need real state.

**Event-driven, not tick-driven -- doesn't need `BOSS_JIG`'s tick/pulse wiring for its own logic.**
Unlike `BossTellFixture`, `BossGuardiansFixture` has no `onJigTick()` body to speak of: its one job
runs off a Forge spawn event, not `BOSS_JIG`'s own cadence. It still rides inside `BossBundle` (and
therefore inside `BOSS_JIG`'s existing `withTick`/`withExecutionPulse` wiring, already paid for by
`BossFixture`/`BossTellFixture`), but purely for bundle membership and sibling access -- not
because this fixture itself needs a tick.

## Hooking the spawn event

**A raw Forge touch point, not routed through `MobDied`/`ScopeEvent`, restated from the hub page:**
spawn-finalization isn't a Mob-kind lifecycle concern [Mob Lifecycle
Signals](../../satchel/architecture/mob-lifecycle-signals.md) covers (those fire for a *tracked*
boss mob gaining/losing interest, not for an arbitrary hostile mob about to spawn anywhere), and
this is a decision that has to happen *before* the mob fully materializes, which those signals
don't reach.

**Proposed concrete hook, flagged as unconfirmed: `MobSpawnEvent.FinalizeSpawn`**
(`net.minecraftforge.event.entity.living.MobSpawnEvent`), Forge 47.4.10 / MC 1.20.1's
spawn-finalization event -- the modern replacement for the older `LivingSpawnEvent.SpecialSpawn`
this codebase's own Forge version no longer carries. **Not verified against this project's actual
Forge sources** (no local access to the Forge jar from this pass) -- Lead Dev's job to confirm the
exact class/method shape at build time, same hedge the hub page already carries for "exact hook is
Lead Dev's call."

**Registration shape, proposed against the one real precedent this codebase has for a raw-Forge
listener:** `FrontierMode.java` is currently the sole class registered on
`MinecraftForge.EVENT_BUS` (`MinecraftForge.EVENT_BUS.register(this)`, constructor), and its one
`@SubscribeEvent` method (`onRegisterCommands`) immediately delegates to module static methods by
name (`BorderModule.onRegisterCommands(event)`, `BossModule.onRegisterCommands(event)`) rather than
holding any logic itself. Proposed: a new `FrontierMode.onMobSpawnFinalize(...)` `@SubscribeEvent`
method following that identical shape, delegating immediately to a new
`BossModule.onMobSpawnFinalize(event)` static method -- keeps `FrontierMode.java` the single real
Forge-subscription point (unchanged discipline) and keeps Boss-domain decision logic inside
`BossModule`, where `registerBossJig()`/`registerBossMobJig()`/`registerNavigatorResolver()`
already live.

## Finding the relevant boss and its curves

Mirrors `BossTellFixture.runTellsForPlayers`'s own resolution almost exactly, substituting the
spawn position for a player position:

1. **Resolve the level's `BossFixture`** via `BossAPI.bosses(level)` -- the existing "standby,
   don't crash" static entry point `BossModule.registerNavigatorResolver()` and `BossAPI`'s own
   `CRUD`/`RULES`/`INFO` facet resolvers already use. Empty (Satchel not ready, scope not known
   yet) -> let the vanilla spawn proceed unmodified.
2. **Find the nearest alive, positioned boss with a `borderId`** to the spawn position -- the same
   nearest-candidate loop shape `runTellsForPlayers` already establishes (`candidates.stream()...`
   filtered to `alive() && positioned() && borderId().isPresent()`, then a manual nearest-distance
   scan). No new distance-search idiom needed; [Boss Discovery
   Systems](discovery-systems.md#navigation-lives-in-border)'s own "deliberately no shared
   `nearest()` method" reasoning applies here exactly as it does to every other consumer -- the
   filter (`alive`/`positioned`/`borderId`) is call-site-specific, so a one-line `min` folded into
   it costs nothing extra.
3. **No candidate in range** (empty `BossFixture`, or the nearest boss has no resolvable
   `borderId`/curves) -> same early return `runTellsForPlayers` takes on an empty candidate list:
   let the vanilla spawn proceed unmodified, no guardian roll attempted.
4. **Resolve `"placement"`/`"difficulty"` `BorderCurve` records** for that boss's `borderId` via
   `BorderAPI.CURVE(level)` -- the identical accessor `BossTellFixture` already calls for its own
   `"tell"`-purpose curve.

## The two curves and the spawn decision

**`createGuardianCurvesIfAbsent(level, borderId)` proposal, mirroring
`BossTellFixture.createTellCurveIfAbsent` exactly:** an idempotent helper creating a
`"placement"`-purpose `LINEAR` curve and a `"difficulty"`-purpose `LOG` curve for `borderId` if
either is missing -- the two curves [Border Curve § Worked example: Guardian
Mobs](border-curve.md#worked-example-guardian-mobs-two-curves) already specifies. **Same paired
call sites `createTellCurveIfAbsent` already runs from**, not a new set to discover:
`BossJigHandlers.onBordersScopeLoaded`, `BossJigHandlers.onMobDied`, and `BossAPI.forceDefeat`
(`/boss transform defeat`'s cascade) -- every place a boss/border pair is freshly created already
calls `BossTellFixture.createTellCurveIfAbsent(level, result.border().id())` today; Guardian Mobs'
own curve-seeding call would sit right beside it at each of those three sites, not introduce a
fourth call site to keep in sync.

**Spawn decision, per the hub page, made concrete against real method names:**

1. `normalizedDistance = BorderMath.distanceTo(spawnPos, nearestBoss.position()) /
   border.radius()` -- distance to the boss's own resolved position, **not** the border's
   geometric center. `BossTellFixture`'s own 2026-09-06 bugfix (see that fixture's `intensity`
   comment) already settled this exact question the hard way: `FRO_072` deliberately biases boss
   placement toward its border's outer edge, so measuring from center instead of from the boss
   itself silently under-reported intensity near a boss that wasn't centered. Guardian Mobs
   inherits that same corrected pattern from the start rather than re-discovering it.
2. `intensityAt(placementCurve, normalizedDistance)` -> roll against a proposed new
   `BossRules.guardianPlacementCoefficient()`, the same "intensity × coefficient against a `[0,1)`
   uniform draw" shape `tellParticleCoefficient()`/`tellSoundCoefficient()` already establish on
   that interface. A miss -> vanilla spawn proceeds unmodified; this is a *modifier* on an
   already-happening spawn, not a summon, so "no roll" costs nothing extra.
3. **On a hit:** tier-bucketing (which guardian variant) and stat scaling both stay
   `BorderRules`/`BossRules`-pluggable-strategy territory, matching [Boss § Spawn
   algorithm](boss.md#spawn-algorithm-a-pluggable-strategy-mirroring-borderrules)'s own precedent
   -- proposed as two further `BossRules` methods (a variant/tier lookup, and a stat-scaling
   application point keyed off `intensityAt(difficultyCurve, normalizedDistance)`), "safe
   baseline, replace later" like every other tunable in this cluster. Exact signatures are Lead
   Dev's call, not fixed here -- the hub page never settled them either.
4. **Visible marker -- ruled 2026-09-07, project owner's direct call, revised same day:** a
   name tag *and* a team-colored glow, mirroring `DefaultBossRules.tagVisibly()`
   (`boss/server/rules/DefaultBossRules.java`) in full now, not just the name-tag half. Format:
   **`GM-<tier>[<intensity>%]`**, e.g. `GM-2[46%]`, mirroring `"Boss (Layer " + layer + ")"`'s own
   shape. `<tier>` is the same difficulty-curve bucket driving stat scaling below -- not
   decorative, a direct readout of what the mob actually got, deliberately useful for eyeballing
   during playtesting. `<intensity>` is `intensityAt(difficultyCurve, normalizedDistance)`
   rendered as a rounded percentage. No record of *which* mobs are guardians is kept anywhere; the
   guardian-ness lives entirely in the spawned entity's own stats, team, and name tag, the same
   non-tracking precedent [Boss](boss.md) itself already uses.

   **Glow, revised in from "deferred" to built, same day:** the name-tag-only pass above turned
   out not to answer a real need -- density has to be evaluatable underground, where name tags
   don't render past normal sight/distance the way a through-wall glow outline does, the same
   reason Boss's own glow already works this way. Boss's glow is unteamed (renders vanilla
   default white), so an unteamed glowing guardian would be visually identical to a boss at a
   glance -- exactly the ambiguity a single shared **`guardian`** scoreboard team, colored
   `ChatFormatting.DARK_PURPLE`, exists to remove. One team for every guardian regardless of tier
   (not tier-graded -- the name tag already carries that signal); dark, to read as visually
   distinct from a boss's white outline against typical cave/underground surroundings, same "safe
   baseline, replace later" status as every other constant in this cluster. Full mechanism (team
   creation, assignment, and the accepted team-membership-leak tradeoff) is on
   [Effects § Team assignment](effects.md#team-assignment-persistent-visual-state) -- this is the
   first real consumer of that capability, not a Guardian-Mobs-specific mechanism.

## Marker and stat scaling: mirroring `tagVisibly()`/`applyStatScaling()` directly

**Ruled 2026-09-07, alongside the marker decision above -- both curves ship together in the
initial build, not just placement/frequency.** `DefaultBossRules` already establishes the exact
two-method shape this needs (`tagVisibly(Mob, int layer)` / `applyStatScaling(Mob, int layer)`),
and Guardian Mobs' own version proposes following it directly rather than inventing a new one:

```java
private void tagGuardian(Mob mob, int tier, double difficultyIntensity, ServerLevel level) {
    mob.setCustomName(Component.literal(
        "GM-" + tier + "[" + Math.round(difficultyIntensity * 100) + "%]"));
    mob.setCustomNameVisible(true);
    PlayerTeam team = EffectsAPI.ensureTeam(level.getScoreboard(), "guardian", ChatFormatting.DARK_PURPLE);
    EffectsAPI.assignToTeam(mob, team);
    mob.setGlowingTag(true);
}

private void applyGuardianStatScaling(Mob mob, double difficultyIntensity) {
    double factor = 1.0 + (GUARDIAN_HEALTH_SCALE * difficultyIntensity);
    var healthAttr = mob.getAttribute(Attributes.MAX_HEALTH);
    if (healthAttr != null) {
        healthAttr.setBaseValue(healthAttr.getBaseValue() * factor);
        mob.setHealth(mob.getMaxHealth());
    }
    var damageAttr = mob.getAttribute(Attributes.ATTACK_DAMAGE);
    if (damageAttr != null) {
        damageAttr.setBaseValue(damageAttr.getBaseValue() * factor);
    }
}
```

Illustrative, same status as every other code sketch on this page -- exact method placement
(`BossRules` vs. `BossGuardiansFixture` itself), the `tier` bucketing formula from
`difficultyIntensity`, and `GUARDIAN_HEALTH_SCALE`'s actual value are all still Lead Dev's call,
"safe baseline, replace later" like `HEALTH_SCALE_PER_LAYER`. `EffectsAPI.ensureTeam`/
`assignToTeam` are proposed additions to the Effects module, not existing methods -- see
[Effects § Team assignment](effects.md#team-assignment-persistent-visual-state) for their own
shape. What's no longer open is *whether* stat scaling ships in v1 -- it does, tied to the same
tier the name tag shows, not deferred behind spawn-frequency alone.

**Guardians are unmodified vanilla mob types.** Ruled the same pass: no new entity, no reskin, no
resource pack. The spawning mob `MobSpawnEvent.FinalizeSpawn` hands this fixture is whatever the
biome would have spawned anyway -- the guardian treatment (name tag + stat scaling) is applied to
that entity in place, not swapped for a curated table the way `DefaultBossRules.LAYER_MOBS`
chooses a boss's own body. Keeps this a modifier on an already-happening spawn, consistent with
"a miss -> vanilla spawn proceeds unmodified" above, rather than a second mob-selection system to
maintain alongside Boss's.

## Open questions

Built against [FRO_096](../../../tickets/FRO_096_gloria-build.md); the judgment calls this page
originally left open are settled as shipped:

- ~~`BossGuardiansFixture` as a real fixture vs. a stateless static utility~~ -- built as a real
  fixture, per this page's own recommendation.
- ~~The exact Forge spawn-finalization hook~~ -- `MobSpawnEvent.FinalizeSpawn`
  (`net.minecraftforge.event.entity.living`, `MinecraftForge.EVENT_BUS`, fires before the mob's
  own vanilla `finalizeSpawn()`) confirmed directly against real Forge 1.20.x sources.
- ~~The registration entry point~~ -- built as proposed: `FrontierMode.onMobSpawnFinalize`
  delegating to `BossModule.onMobSpawnFinalize`.
- ~~`guardianPlacementCoefficient()` and the tier-bucketing/stat-scaling methods' exact shape~~ --
  settled from live playtest, not just a build-time guess: `guardianPlacementRadiusFraction()` is
  0.5 (see [Border Curve's worked example](border-curve.md#worked-example-guardian-mobs-two-curves)
  for what that changes), `GUARDIAN_PLACEMENT_COEFFICIENT` is 0.6 (max roll chance standing on the
  boss, still decaying with distance and still a roll rather than a guarantee). Difficulty/stat-
  scaling (`DIFFICULTY_SHAPE = Shape.LOG` against the full border radius,
  `GUARDIAN_TIER_COUNT`/`GUARDIAN_HEALTH_SCALE`) shipped at its original sketched baseline --
  unchanged since build, no playtest feedback against it.
- ~~`BossGuardiansFixture` visible marker~~ -- name tag only (`GM-<tier>[<intensity>%]`) plus a
  `guardian`-team glow (`ChatFormatting.DARK_PURPLE`, unteamed-vs-boss's-white distinction), no
  reskin. See [Marker and stat scaling](#marker-and-stat-scaling-mirroring-tagvisibly-applystatscaling-directly)
  above.
- **Which Borders turn Guardian Mobs on, and at what curve settings** -- named on
  [RM_FRO_029](../../../roadmap/RM_FRO_029_gloria.md) itself as the one open item, Game Designer's
  call, not a blocker on the mechanism above. See also [Guardian Mobs
  (design)](../design/guardian-mobs.md#when-theyre-introduced)'s own framing of the same question
  ("Design obligation this creates") and its separate ["guardians in old
  territory"](../design/guardian-mobs.md#open-question-guardians-in-old-territory) question --
  both explicitly Game Designer's, not re-litigated here.
- **Whether Guardian Mobs ever needs real tracking** (a density cap beyond vanilla's own
  spawn-attempt/mob-cap system, despawn-on-boss-defeat, reward attribution) -- explicitly deferred
  per the hub page; revisit only if one becomes a real requirement, per that page's own framing.

## Related pages

- [Boss Discovery Systems § Guardian Mobs](discovery-systems.md#guardian-mobs) -- the hub page's
  own summary and the module-wiring precedent this page builds directly against
- [Boss Discovery Systems § Environmental Tells](discovery-systems.md#environmental-tells) -- the
  real, shipped sibling fixture (`BossTellFixture`) this page's proposal mirrors almost exactly
- [Guardian Mobs (design)](../design/guardian-mobs.md) -- the design intent, and the two
  Game-Designer open questions this page explicitly defers to it
- [Border Curve § Worked example: Guardian Mobs' two curves](border-curve.md#worked-example-guardian-mobs-two-curves)
  -- the `"placement"`/`"difficulty"` curve shape this page's spawn decision reads from
- [Boss § Spawn algorithm](boss.md#spawn-algorithm-a-pluggable-strategy-mirroring-borderrules) --
  the `BossRules`-pluggable-strategy precedent this page's tier-bucketing/stat-scaling proposal
  follows
- [Boss](boss.md) -- `DefaultBossRules.tagVisibly()`/`applyStatScaling()`, the real shipped
  methods the marker/scaling ruling above mirrors directly
- [Effects § Team assignment](effects.md#team-assignment-persistent-visual-state) -- the
  `guardian` team/glow-color mechanism this page is the first real consumer of
- [RM_FRO_029](../../../roadmap/RM_FRO_029_gloria.md) ("Gloria") -- this page's own roadmap node
- [RM_FRO_035](../../../roadmap/RM_FRO_035_donna-03.md) ("Donna_03") -- the actual blocker
- [RM_FRO_030](../../../roadmap/RM_FRO_030_janice.md) ("Janice") -- Environmental Tells, the
  already-built sibling this page leans on throughout
