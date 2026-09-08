---
id: FRO_099
uid: FRO
number: 99
client: FrontierMode
status: open
title: Frontier Sickness core build
context: Dev(FrontierMode) build for RM_FRO_037 -- also fixes a live distanceToSurface
  bug. See body.
priority: normal
opened: '2026-09-08'
closed: null
---

<!-- board:start -->
<!-- board:end -->

## Summary

Dev (FrontierMode) build ticket for [RM_FRO_037](../roadmap/RM_FRO_037_brenda.md) ("Brenda,"
Frontier Sickness epoch 1) -- see that node and [Exterior -- architecture](../wiki/frontiermode/architecture/exterior.md)
for the full design/technical spec. This ticket exists to give the build a place to log against;
it does not restate the spec.

## Scope

1. **`BorderMath.distanceOutside(BlockPos p, BlockPos center, int radius)`** -- `max(0,
   distanceTo(p, center) - radius)`. Mirror into `BorderAPI.MATH` per the FRO_078 facade
   convention (see any existing `MATH.*` wrapper for the pattern).
2. **Frontier-distance at a point** -- `min` over every `Border` in `BordersFixture`'s list of
   `distanceOutside(point, border.center(), border.radius())`. Slot this into
   `BorderModule.onPlayerScopeTick()` as a second reduction over the `borders` list already
   fetched there -- not a second walk, not a new tick.
3. **New field on `BorderPlayerStatusFixture`** carrying that value (0/absent = not in the
   Exterior). Same "not persisted, live-recomputed" contract the fixture already documents.
4. **Frontier Sickness's climbing debuff** -- target-and-catch-up severity per
   [Exterior § Frontier Sickness](../wiki/frontiermode/design/exterior.md#frontier-sickness).
   Where the severity value itself persists (a second fixture field vs. its own small fixture) is
   this ticket's call, not decided upstream -- pick one and note which in the log.
5. **Entry Cue** (bass-drop tone on crossing into the Exterior) and **Sick Wildlife** (poison-particle/
   hurt-sound passive mobs, spawning more densely with distance per [FRO_098](FRO_098_design-note-for-sick-wildlife-passive-mo.md)) --
   both material-free, per [Exterior § Sensory design](../wiki/frontiermode/design/exterior.md#sensory-design).
6. **Bugfix, folded in per project owner direction:** `BorderMath.distanceToSurface(Border,
   BlockPos)` currently returns `(int) (distanceSqToCenter(border, pos) - border.radius())` --
   subtracting a linear radius from a *squared* distance. Live today in
   `BorderPlayerLogic`'s outside-every-border fallback branch (feeds
   `BorderPlayerStatusFixture`'s distance field), zero test coverage. Fix to true
   `distanceTo(center, pos) - radius` (or route it through the new `distanceOutside()` primitive
   directly, if the two turn out to be the same computation once this is built) while in this
   area -- flagged during Brenda's own scoping, not a pre-existing ticket.

## Explicitly out of scope for this ticket

Feral ([RM_FRO_038](../roadmap/RM_FRO_038_martha.md), "Martha") -- separate node, separate
ticket when picked up. Edge Stone / Diorite Wick detection items -- parked per the architecture
page's own build sequencing, not scoped into either Exterior node yet. The hallucination layer --
part of Exterior's sensory design but gated to "deep distance," not called out as part of this
node's first-pass scope.

## Log

- 2026-09-08: Ticket opened.
- 2026-09-08: Core build complete (Dev/FrontierMode, "Curtis"). Manual review only --
  no sandbox compile or playtest available this session (no network path to the Gradle wrapper
  distribution, no JDK on the device-side shell -- same limitation FRO_078/FRO_088 already
  logged). A real build/playtest is still owed before this can be called done.
  - `BorderMath.distanceOutside(BlockPos, BlockPos, int)` added and mirrored into
    `BorderAPI.MATH.distanceOutside(...)` per the FRO_078 facade convention.
  - Bugfix (item 6): `distanceToSurface` was subtracting a linear radius from a squared
    distance; fixed via a new pure `BorderMathLogic.distanceToEdge(distanceToCenter, radius)`
    helper, with a regression test pinned to the old (wrong) behavior's actual numbers.
  - Frontier-distance: a second `min`-over-`borders` reduction, inlined separately at its two
    call sites (`BorderPlayerLogic.evaluate()` and `BorderModule.onMobSpawnFinalize()`) rather
    than factored into a shared helper -- matches this codebase's own stated no-shared-reduction
    convention (see discovery-systems.md).
  - Storage-shape call (item 4, delegated to Dev): `sicknessSeverity` is a second scalar field
    riding the existing `BorderPlayerEval`/`BorderPlayerStatus`/`BorderPlayerStatusFixture` chain,
    not a new fixture -- the target-and-catch-up climb needs only one number of continuity, and
    the fixture already threads its own previous snapshot in. New pure `FrontierSicknessLogic`
    class holds `severityTarget`/`climbSeverity`/`wildlifeDensityMultiplier`, unit-tested.
  - Tiered debuff (`PlayerRules.SICKNESS_TIER_1..4`) refreshes vanilla `MobEffectInstance`s on a
    short cadence while a tier's threshold holds and lets vanilla's own duration countdown clear
    each effect independently once it stops being refreshed -- no explicit decay timers. All
    thresholds/rates/durations are placeholders (`PlayerRules`), explicitly not playtest-tuned,
    per this ticket's own "pick one and note which" and the design page's open-questions section.
  - Entry Cue: targeted to the crossing player only (project owner direction), sent via a new
    `EffectsAPI.sendSoundToPlayer(...)` (`ClientboundSoundPacket` direct to that player's
    connection -- `Level.playSound(Player, ...)`'s Player param is an *exclude*, not a target,
    confirmed via web search) using `SoundEvents.AMBIENT_CAVE` as a placeholder tone (materials
    out of scope).
  - Sick Wildlife has two independent halves: a cosmetic tell (`ExteriorTellFixture`, a sibling
    fixture in `BorderPlayerBundle` alongside `BorderPlayerStatusFixture` -- placeholder particle/
    sound on nearby passive mobs) and a density boost (`BorderModule.onMobSpawnFinalize`, hooked
    into the mod's existing single `MobSpawnEvent.FinalizeSpawn` subscriber alongside Boss's own
    handler). Density curve is pronounced right at the Frontier edge and flattens to a flat
    multiplier further out per project owner correction (not a slow climb like severity) --
    `FrontierSicknessLogic.wildlifeDensityMultiplier`, baseline placeholders
    `WILDLIFE_DENSITY_FALLOFF_DISTANCE=15.0`, `WILDLIFE_DENSITY_MAX_MULTIPLIER=2.0`. Mechanism is
    an extra-companion roll on `FinalizeSpawn` (can't retroactively multiply spawn attempts),
    calling `Mob#finalizeSpawn` directly rather than re-posting the Forge event, so it can't
    recurse into its own handler.
  - API shapes I couldn't otherwise confirm without a compiler were verified via web search
    (authorized by project owner): `MobSpawnEvent.FinalizeSpawn`/`ForgeEventFactory.onFinalizeSpawn`,
    `Mob#finalizeSpawn(ServerLevelAccessor, DifficultyInstance, MobSpawnType, SpawnGroupData,
    CompoundTag)`'s 5-arg shape, `MobSpawnType.EVENT`, `ClientboundSoundPacket`'s constructor, and
    official-mapping `MobEffects` field names.
  - Not committed -- git managed by project owner this session, per this project's established
    convention.
- 2026-09-08: First real build/playtest report back from project owner. Frontier-distance
  tracking, severity stacking, and effect wear-off on return all confirmed working correctly.
  One compile error found and already fixed by project owner directly:
  `SoundEvents.GENERIC_HURT.value()` in `ExteriorTellFixture` didn't compile -- `GENERIC_HURT` is
  a plain `SoundEvent` in this codebase's real mappings, not a `Holder<SoundEvent>` like
  `AMBIENT_CAVE` (which does compile with `.value()`, confirmed by the same successful build --
  `BorderModule`'s Entry Cue call was left as-is). Correction for future work in this area: not
  every `SoundEvents.*` constant is the same wrapped-vs-unwrapped shape -- check each one
  individually rather than assuming the pattern generalizes from a single confirmed example.
  Open follow-ups from this report: (1) the density-boost companion spawn wasn't visibly
  observed ("no additional animals") -- most likely explained by the hook's own documented
  limitation (it can only piggyback on spawn attempts vanilla already makes, not create new ones),
  not a code defect; worth a longer/denser playtest before concluding otherwise. (2) Entry Cue's
  placeholder `AMBIENT_CAVE` tone is being swapped for a real project-supplied sound asset.
- 2026-09-08: Entry Cue's real sound wired in (project owner supplied
  `Scrapyard/assets/368641__megablasterrecordings__couch-drop.wav`). No existing sound-
  registration facility in this codebase to reuse -- this is the mod's first real `SoundEvent`.
  - Converted to OGG Vorbis (`ffmpeg -c:a libvorbis -q:a 5`) -> `assets/frontiermode/sounds/
    entry_cue.ogg`, plus a matching `assets/frontiermode/sounds.json` entry and a new
    `assets/frontiermode/lang/en_us.json` subtitle key (this project's first lang file).
  - Registered as a real `SoundEvent` in `FrontierMode.java`: a `DeferredRegister<SoundEvent>`
    (`ForgeRegistries.SOUND_EVENTS`) + `RegistryObject<SoundEvent> ENTRY_CUE`, same shape as the
    existing `ARGUMENT_TYPES` registration in that same class, via
    `SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "entry_cue"))`. Verified
    both the registry constant and the factory method against Forge's own docs/javadoc (web
    search) since neither existed anywhere in this codebase before to copy from.
  - `BorderModule.onFrontierSicknessTick`'s Entry Cue call now passes `FrontierMode.ENTRY_CUE
    .get()` instead of the old `SoundEvents.AMBIENT_CAVE.value()` placeholder -- volume/pitch
    (0.6f / 0.5f) untouched, still playtest territory, worth an ear-check now that it's a real
    asset instead of a placeholder tone.
- 2026-09-08: Second playtest report -- Entry Cue sound confirmed good. Sick Wildlife still
  shows no visible effect on passive mobs at all (not just the density companion -- the cosmetic
  tell's particle/sound on already-present animals is also silent), which is a different symptom
  than "no additional animals" alone and worth actually instrumenting rather than guessing again.
  Added two TEMP DIAGNOSTIC `OUT.info(...)` lines (clearly marked, safe to delete once the cause
  is confirmed): one in `ExteriorTellFixture.onJigTick()` logging `frontierDistance` and the
  nearby-`Animal` count found each tell pass, one in `BorderModule.onMobSpawnFinalize` logging
  `frontierDistance`/multiplier/roll/outcome for every Animal spawn attempt in the Exterior. Next
  playtest should tell us definitively whether this is "no animals ever in range/spawning out
  there at all" (most likely, matches both symptoms with one cause) vs. a real dispatch bug in
  either fixture.
- 2026-09-08: Added a small test aid at project owner's request rather than waiting on natural
  spawns: `data/frontiermode/functions/test/spawn_rabbits.mcfunction` (this mod's first shipped
  datapack function) -- `/function frontiermode:test/spawn_rabbits` scatters ~24 rabbits around
  the caller via `execute positioned ... run summon`, not stacked in one spot. Flagged for the
  next playtest: `/summon` does not route through `Mob#finalizeSpawn`/`MobSpawnEvent.FinalizeSpawn`
  the way natural/spawner spawns do, so these rabbits will exercise `ExteriorTellFixture`'s
  cosmetic tell but will NOT trigger the density companion hook -- that half still needs a real
  natural (or spawner-based) spawn to test.
- 2026-09-08: Project owner asked how Boss spawning does it, since /summon turned out not to
  fire FinalizeSpawn. Answer: it doesn't, either -- `DefaultBossRules.materialize()` calls
  `Mob#finalizeSpawn` directly (bypassing the Forge event bus on purpose, same reasoning
  `spawnDensityCompanion` copies), so boss materialization was never a route to a real
  `MobSpawnEvent.FinalizeSpawn` either. Added a proper one instead: `/border debug spawn-animal
  [count]` (`BorderCommandHandler.spawnTestAnimals`, wired in `BorderCommands.debug()`) spawns
  rabbits scattered around the caller via `ForgeEventFactory.onFinalizeSpawn(...)` -- the actual
  method natural/spawner spawns call, which constructs and posts the real event before calling
  `finalizeSpawn`. Unlike both `/summon` and the Boss materialize path, this one genuinely
  exercises `BorderModule.onMobSpawnFinalize` (and `BossModule`'s) on demand, so the density hook
  no longer has to wait on natural spawn RNG to test. Default count 10, capped at 200 per call.
- 2026-09-08: Third playtest report, with `/border debug spawn-animal` and both diagnostics live.
  Good news: the density hook is confirmed CORRECT. `[Border] onMobSpawnFinalize` logged 6 real
  rolls against 6 rabbits with frontierDistance>0 (up to 14), computed sane multipliers/chances,
  and correctly decided `willSpawnCompanion=true` twice against `false` four times, matching the
  roll-vs-chance math exactly. Still no visible sound/particle effects on any rabbit, and
  `[ExteriorTell]` never printed even once in the same test run -- meaning `ExteriorTellFixture`
  never got past its own gate. Root cause candidate: that gate checks `status.frontierDistance()`
  for the PLAYER, not for each nearby animal -- if the player's own reading was <=0 (near/at the
  boundary, even while some scattered rabbits registered well into the Exterior), the whole tell
  pass silently skips regardless of how many valid targets are nearby. The temp diagnostic line
  was sitting AFTER that exact gate, which is why it never fired even once despite Border's own
  log showing real Exterior activity the same tick. Moved the diagnostic ahead of every early
  return (now prints unconditionally every 20-tick pass: whether the status fixture/snapshot
  exist at all, and the player's own frontierDistance reading) so the next test tells us directly
  whether it's "player wasn't far enough outside" (most likely, given the data so far) vs. a real
  gate/dispatch bug. Next test: watch the player's own severity/sickness effects (direct in-game
  feedback that frontierDistance()>0) at the same time as running spawn-animal, to confirm the
  player is genuinely registering as outside when the rabbits are.
- 2026-09-08: ROOT CAUSE FOUND (fourth playtest report: still zero effect, even with the
  gate-agnostic diagnostic and rabbits confirmed at frontierDistance up to 46 by Border's own
  log). `ExteriorTellFixture.onJigTick()` was never printing anything -- not even the
  unconditional line -- because `SatchelFixture.onJigTick()` is never actually invoked by
  anything in this codebase. Confirmed via Satchel's own `TrackerFixture`: it increments
  `internalTicks` only from `onJigTick()` and `externalTicks` from a separate call; a session
  that logged `externalTicks=2708` on unload still showed `internalTicks=0`. The whole dispatch
  chain (`SatchelBundle.onJigTick()` looping fixtures, `ScopeEngine_Server.onJigTick()` sweeping
  bundles, `FoundationLifecycleDispatcher.pulse()` driving every jig/scope) reads correctly on
  paper but never reaches a single fixture in practice. This is NOT limited to the new code --
  `BorderPregenFixture` (LevelJig-scoped, pre-existing) relies on the identical path and has
  apparently never ticked once either: zero `"[BorderPregen]"` log lines exist anywhere across
  this world's play history despite 7 borders on record needing pregeneration.
  - Filed **SAT_049** against Satchel for the underlying dead dispatch path -- out of Dev
    (FrontierMode)'s authority to fix (Satchel/src/ is Dev (Satchel)'s territory), and it's a
    bigger finding than this ticket (BorderPregenFixture's whole pipeline may be silently
    non-functional in real play, unrelated to RM_FRO_037).
  - Local fix for this ticket, entirely in FrontierMode: `BorderModule.onPlayerScopeTick` now
    fetches `ExteriorTellFixture` via `bundle.getOrCreateFixture(...)` and calls its
    `onJigTick()` directly, every player tick -- the exact same manual-drive pattern
    `BorderPlayerStatusFixture.accept()` already used one line below it (which is *why*
    `BorderPlayerStatusFixture` was never affected by this bug in the first place: it was never
    relying on the broken automatic path to begin with). No dependency on SAT_049 landing before
    this can work.
  Next playtest should be the real test: rebuild, and both the density hook and the cosmetic
  tell should now be exercisable together via `/border debug spawn-animal`.
- 2026-09-08: Compile error from project owner's rebuild -- `ForgeEventFactory.onFinalizeSpawn`
  actually takes 6 args (`Mob, ServerLevelAccessor, DifficultyInstance, MobSpawnType,
  SpawnGroupData, CompoundTag`), matching `Mob#finalizeSpawn`'s own 5-arg shape plus the mob
  itself -- my web-sourced signature was missing the trailing `CompoundTag` param. Fixed in
  `BorderCommandHandler.spawnTestAnimals` (added the second trailing `null`). Manual review only,
  same standing limitation -- worth a rebuild check before trusting any further un-compiled
  web-verified signature in this ticket at face value.
- 2026-09-08: Design bug fixed on top of the SAT_049 dispatch workaround (Dev/FrontierMode,
  "Curtis"), per project owner playtest report: "I reset the server, and now I see that it's my
  position that triggers the effect like you said. It needs to be the mob's position." Once the
  dispatch fix made `ExteriorTellFixture.onJigTick()` actually run, it became visible that the
  fixture was gating its whole pass on the PLAYER's own `BorderPlayerStatusFixture.frontierDistance()`
  reading rather than each individual animal's position -- a player standing just inside the
  boundary could stand next to animals well out in the Exterior and see nothing, and a player deep
  outside would roll effects on animals still technically inside.
  - Reworked `ExteriorTellFixture.onJigTick()` to drop its `BorderPlayerBundle`/
    `BorderPlayerStatusFixture` dependency entirely. It now pulls `borders` directly via
    `BorderAPI.CRUD(level).map(BordersCrudFacet::all).orElseGet(List::of)` and, per nearby
    `Animal`, computes that animal's own frontier distance with the same
    `borders.stream().mapToInt(b -> BorderAPI.MATH.distanceOutside(animalPos, b.center(),
    b.radius())).min().orElse(0)` reduction the density hook
    (`BorderModule.onMobSpawnFinalize`) already uses -- skipping (not rolling effects on) any
    animal whose own distance is <= 0.
  - Diagnostic updated to report the per-animal split: `nearbyAnimals` (total found in the search
    radius) vs `sickEligible` (how many actually cleared the per-animal Exterior check and got
    rolled against). Still a TEMP DIAGNOSTIC, safe to delete once confirmed good in play.
  - Verified: file re-read in full via device_bash after the patch, parens/braces balanced
    (56/56, 23/23), and the new `BorderAPI.CRUD` / `BordersCrudFacet::all` / `BorderAPI.MATH.
    distanceOutside(BlockPos, BlockPos, int)` call shapes cross-checked against their real
    declarations and against `BorderModule.onMobSpawnFinalize`'s own proven-working use of the
    exact same calls.
  - Awaiting next rebuild/playtest for confirmation.
- 2026-09-08: Entry Cue sound re-tuned by project owner (Audacity project
  `Scrapyard/assets/entry_que.aup3`, exported to `Scrapyard/assets/entry_que.wav`) and rebuilt
  into `assets/frontiermode/sounds/entry_cue.ogg` -- same encode this ticket's log already
  documents for the original asset (`ffmpeg -c:a libvorbis -q:a 5`), just pointed at the new
  source WAV so the settings stay consistent. New clip is 2.89s stereo 44.1kHz (down from the
  original 6s) -- reasonable for a tightened/tuned cue, not investigated further since I can't
  listen to confirm intent, only that the conversion itself is faithful to the source. Did not
  touch the `.aup3` project file itself (found it live -- `.aup3-wal`/`.aup3-shm` journals present
  -- so read a local copy only, out of caution, and used the project owner's own WAV export
  instead of attempting to reconstruct audio from the project database by hand). No code or
  registration changes needed -- `sounds.json`/`FrontierMode.ENTRY_CUE` already point at this
  same filename.
- 2026-09-08: Entry Cue pitch fixed at project owner's request ("let's play it back at full
  speed"). `BorderModule.onFrontierSicknessTick`'s `EffectsAPI.sendSoundToPlayer(...)` call was
  still passing `pitch=0.5f` -- a value tuned against the old placeholder asset back when Entry
  Cue used `SoundEvents.AMBIENT_CAVE`, never revisited once the real (and now re-tuned)
  `entry_cue.ogg` was wired in. Changed to `1.0f` (full speed / authored pitch); volume (`0.6f`)
  left as-is, not raised as part of this request. Balance-verified (238/238 parens, 38/38 braces).
  Awaiting next rebuild/playtest for confirmation.
- 2026-09-08: Added a debug toggle for the rendered border rings, per project owner request
  ("a ./border debug command to true/false the visible rendered rings ... Default true").
  `WorldBordersRenderer` gets a static `visible` flag (default `true`, matching its behavior
  before the toggle existed) with `setVisible(boolean)`/`isVisible()`, checked as an early return
  at the top of `render()`.
  - Command lives entirely CLIENT-side: `Rendering.onRegisterClientCommands` registers
    `/border debug rings <true|false>` on Forge's `RegisterClientCommandsEvent` dispatcher (a
    genuine `CommandDispatcher<CommandSourceStack>`, confirmed via web search against the Forge
    PR that introduced client commands -- backed by a real `ClientCommandSourceStack extends
    CommandSourceStack`, so the existing `ctx.getSource().sendSuccess(...)` idiom this codebase
    already uses everywhere else works unchanged). Deliberately NOT added to `BorderCommands`'
    server-side dispatcher -- visibility of a purely client-side render pass has no server-side
    meaning, this codebase has no networking channel to build for it, and none is needed: a
    client command that fully parses is handled locally and never reaches the server at all, so
    it coexists with the server's own separate `/border debug ...` tree (spawn-animal, etc.)
    without collision -- two different dispatchers, not one shared tree.
  - Balance-verified both files (`WorldBordersRenderer.java` 74/74 parens, 12/12 braces;
    `Rendering.java` 81/81 parens, 21/21 braces). Manual review only, standing limitation --
    RegisterClientCommandsEvent is new to this codebase (nothing to copy an existing usage from,
    unlike most other API touch points here), so this is worth an explicit rebuild check before
    trusting it.
- 2026-09-08: Boss audio tell round-robin scaffolding, per project owner request ("I'd like to
  supply 4 30 second sounds and we can round robin them for the Boss tells"). Confirmed with
  project owner up front: rotation is PER-BOSS, not one shared global rotation (two different
  bosses can independently be on different tracks).
  - Flagged and fixed a real overlap risk before writing the rotation itself:
    `BossTellFixture`'s sound roll (`BossRules#tellTickInterval()` ~1s cadence x
    `tellSoundCoefficient()` 5% max-intensity chance, rolled independently per nearby player) was
    tuned for a sub-second `AMBIENT_CAVE` blip -- at that cadence a ~30s clip would very likely
    get re-triggered (by the same player re-rolling, or a different player near the same boss
    rolling independently) before the previous one finished, stacking overlapping playback
    instead of a clean rotation.
  - Added `BossRules#tellSoundCooldownTicks()` (baseline 600 = 30s @ 20 TPS, `DefaultBossRules`)
    as a hard per-boss floor gating the existing probability roll -- only once cooldown has
    elapsed for a boss does that boss's roll get a chance to fire (and only then does its
    round-robin advance). Only implementer of `BossRules` is `DefaultBossRules`; updated, no other
    call sites broken by the new interface method.
  - `BossTellFixture` gets two new non-persisted per-boss maps (`tellSoundRotation`,
    `lastTellSoundTick`, keyed by bossId) -- same "off/on signal" ephemeral-state discipline as
    this fixture's own `tickCounter` and `ExteriorTellFixture`'s (resets on restart, harmless;
    unbounded but trivially small, one entry per boss that has ever rolled a tell sound).
  - `TELL_SOUNDS` is a 4-element `List<SoundEvent>`, all 4 slots currently the SAME placeholder
    (`SoundEvents.AMBIENT_CAVE.value()`) rather than 4 newly-registered `SoundEvent`s pointing at
    files that don't exist yet -- deliberately following Entry Cue's own precedent (placeholder
    resolves to a REAL valid sound, not a broken resource reference) since a missing-sound warning
    is exactly the kind of thing I can't verify or clean up without a real playtest. Once the 4
    real clips are supplied (expecting `Scrapyard/assets/`, same workflow as Entry Cue), the next
    step is registering 4 real `RegistryObject<SoundEvent>`s (mirroring `FrontierMode.ENTRY_CUE`)
    with `"stream": true` sounds.json entries given the ~30s length, and swapping them into
    `TELL_SOUNDS` in order.
  - Balance-verified all three touched files (`BossRules.java` interface addition,
    `DefaultBossRules.java` impl, `BossTellFixture.java` fields + roll site).
  - Sound content is unchanged from before this edit (still `AMBIENT_CAVE` x4) until real assets
    land -- the round-robin/cooldown MECHANISM is what's real and ready to verify via logs/
    playtest now (should show up as the existing tell sound simply firing far less often per
    boss, capped at once per 30s per boss, rather than any audible variety yet).
- 2026-09-08: First real boss-tell clip wired in end-to-end, per project owner request ("let's
  see if one of these is usable now" -- 5 total clips incoming, converting the rest to mono
  themselves, testing the pipeline with slot 1 first). Project owner also confirmed: leave the
  30s cooldown flat for now (their 5 clips are "designed so that some overlap won't hurt" -- the
  per-clip-length concern I raised isn't a real problem for this asset set), and they'll do the
  mono conversion on their own end (spatial positioning on the triggering player, per their own
  call) -- I converted what's currently at `Scrapyard/assets/boss_tell/boss_tell_1.ogg` as-is
  (48kHz stereo, 36.88s) rather than waiting.
  - `ffmpeg -c:a libvorbis -q:a 5` (same convention as Entry Cue/the other 4 slots' eventual
    conversion) -> `assets/frontiermode/sounds/boss_tell_1.ogg`. Duration matched source exactly
    (36.88s). NOTE: ffmpeg logged several "Non-monotonous DTS" / "Queue input is backward in
    time" warnings during this specific transcode (ogg-vorbis source re-encoded to ogg-vorbis) --
    output duration and stream info both look correct via ffprobe, but I have no way to listen and
    confirm the audio itself is clean. Worth an actual listen on the project owner's end before
    trusting this one, more so than prior conversions that didn't warn.
  - `sounds.json` entry uses the object form (not the bare-string form Entry Cue used) so it could
    carry `"stream": true` -- appropriate here given the ~37s length (this ticket's own earlier
    log already flagged `stream` as the right setting for anything beyond a few seconds).
  - New lang key `subtitles.frontiermode.boss_tell_1` ("Distant presence").
  - `FrontierMode.BOSS_TELL_1` registered as a `RegistryObject<SoundEvent>`, exact same
    `DeferredRegister`/`createVariableRangeEvent` shape as `ENTRY_CUE`.
  - `BossTellFixture.TELL_SOUNDS` converted from an eager `static final List<SoundEvent>` field to
    a lazily-initialized `tellSounds()` accessor, and expanded from 4 to 5 slots (slot 1 real,
    slots 2-5 still the `AMBIENT_CAVE` placeholder). This wasn't just a mechanical resize: mixing
    a mod-registered `RegistryObject` into a field that's evaluated eagerly at class-load time
    risks the exact same class of bug `Rendering.java`'s own `bordersRenderer()` doc already
    documents in this codebase (an eager static initializer touching something not guaranteed
    ready yet -- there it was dist-loading order crashing a dedicated server's first tick; here it
    would be `FrontierMode.BOSS_TELL_1.get()` potentially running before Forge's registry event
    populates it). A vanilla `SoundEvents.*` constant is always safe eagerly; a custom
    `RegistryObject` is not, once `BossTellFixture` might be classloaded early. Both call sites
    (the sound pick and the rotation-advance modulo) updated to call `tellSounds()` instead of
    referencing the old field directly.
  - Balance-verified `BossTellFixture.java` (269/269 parens, 71/71 braces) and `FrontierMode.java`
    (61/61 parens, 7/7 braces).
  - Manual review only, as ever -- this is a real rebuild-and-listen test, not just a
    compiles-and-logs-correctly one, since I can't verify sound content myself.
- 2026-09-08: Remaining 4 boss-tell clips wired in -- project owner supplied slots 2-5 directly
  as ready-to-use mono OGG Vorbis ("I'm good if the bitrates, etc aren't standardized. If you
  can't use these files directly for the Minecraft sounds, let me know why."). Short answer: no
  reason not to -- probed all 4 with ffprobe first (all valid `vorbis` streams, 48kHz mono,
  59-67kb/s, 29-40s each) and copied them byte-for-byte into `assets/frontiermode/sounds/` with NO
  ffmpeg re-encoding at all (`cmp` confirmed identical to source). Minecraft's sound engine has no
  standardization requirement across sound files -- format must be OGG Vorbis (it is), but sample
  rate, bitrate, and channel count can differ freely file-to-file, same as vanilla's own asset set.
  - Registered `FrontierMode.BOSS_TELL_2..5`, same `DeferredRegister`/`createVariableRangeEvent`
    shape as `BOSS_TELL_1`/`ENTRY_CUE`.
  - `sounds.json` entries for all 4, `"stream": true` given their length (consistent with slot 1
    and this ticket's own earlier stream-flag reasoning).
  - Lang subtitle keys for all 4 (reused slot 1's "Distant presence" text -- cosmetic, trivial to
    retitle later if project owner wants each clip individually labeled).
  - `BossTellFixture.tellSounds()` now returns all 5 real `RegistryObject`s -- no more
    `SoundEvents.AMBIENT_CAVE` placeholder anywhere in this fixture. Removed the now-unused
    `SoundEvents` import and rewrote the field's doc comment (previously written across two
    partial edits describing an already-superseded 4-placeholder-then-1-real state) into one
    accurate final version.
  - Note: slot 1 (`boss_tell_1.ogg`) is still the STEREO file I converted via ffmpeg last entry --
    project owner has not re-supplied a mono version of that one specifically. Everything else
    (cooldown, rotation, registration) treats all 5 slots identically regardless of channel count,
    so this isn't a functional blocker -- flagging only so a future pass doesn't assume all 5 are
    already mono.
  - Balance-verified `BossTellFixture.java` (270/270 parens, 71/71 braces) and `FrontierMode.java`
    (78/78 parens, 7/7 braces); `sounds.json`/`en_us.json` both parse as valid JSON.
  - This is now a genuinely complete real-asset round-robin, ready for an actual playtest --
    manual review only, as ever, but there's no placeholder content left anywhere in this pass.
- 2026-09-08: Compile error from project owner's rebuild -- `cannot find symbol
  tellSoundCooldownTicks() ... location: variable RULES of type BossRulesFacet`. Real cause: when
  `BossRules#tellSoundCooldownTicks()` was added earlier in this ticket, I checked for other
  `BossRules` implementers (only `DefaultBossRules`, updated) but missed that `BossTellFixture`
  never calls the raw `BossRules` instance directly -- `bossFixture.RULES` is a `BossRulesFacet`
  (FRO_081's pluggable-strategy wrapper), which hand-declares its own passthrough method per
  `BossRules` method it exposes (`tellTickInterval()`, `tellSoundCoefficient()`, etc. each have one
  already). Adding a method to the interface + impl doesn't automatically add its passthrough on
  this facet -- that's a separate, manual step, and I only did two of the three. Fixed: added
  `BossRulesFacet.tellSoundCooldownTicks()`, same delegate-to-RULES shape as its siblings.
  Cross-checked every other `tellTickInterval`/`tellSoundCoefficient` reference in the codebase to
  confirm no other facet/wrapper has the same gap for this new method. Balance-verified
  (47/47 parens, 44/44 braces). Lesson for future `BossRules`/`BorderRules`-shaped interface
  additions in this codebase: check for a Facet wrapper class in addition to checking for other
  raw interface implementers -- this is the second time in this ticket a manual-review-only change
  has needed a real compile to catch (see the earlier `ForgeEventFactory.onFinalizeSpawn` arg-count
  entry above).
- 2026-09-08: Added a debug toggle for Boss/Guardian glow, per project owner request ("possible
  to true/false the guardian and boss glow?"). Adjacent to this ticket's own RM_FRO_037 scope
  (touches Boss/Guardian Mobs, RM_FRO_029/FRO_057 territory) but logged here same as this
  session's other debug-tooling additions, for one continuous log.
  - `mob.setGlowingTag(true)` has exactly two call sites in this codebase: `DefaultBossRules
    .tagVisibly` (Boss materialize) and `.tagGuardian` (Guardian Mobs tagging). Both now read
    `mob.setGlowingTag(glowEnabled)`, a new instance field (default `true`, matching pre-toggle
    behavior) with `BossRules.glowEnabled()`/`setGlowEnabled(boolean)` getter/setter -- the only
    mutable, real-time pair on an interface that's otherwise fixed strategy/baseline methods.
    Deliberately kept on this same interface/facet path (not a standalone static field like
    `WorldBordersRenderer.visible`) because this needs to be reachable from a SERVER command --
    glow is real synced entity state, not a purely client render decision.
  - Learned last entry's lesson: added the `BossRulesFacet` passthrough (`glowEnabled()`/
    `setGlowEnabled(boolean)`) in the SAME edit as the interface/impl methods this time, not as a
    separate follow-up compile-error fix. Confirmed `DefaultBossRules` is still the only
    `BossRules` implementer.
  - New command `/boss debug glow <true|false>` (`BossCommandHandler.setGlow`) -- global, not
    per-boss-selector, so it doesn't route through `applySelector` the way `debugGoto`/
    `debugDistance` do. Sets the toggle AND retroactively walks every currently-materialized,
    alive `BossRecord` on the executing level via `level.getEntity(bossEntityId)` (confirmed via
    web search: `ServerLevel#getEntity(UUID)` returns nullable `Entity`, standard 1.20.1 API,
    unverified in this codebase before now) and re-applies the new value directly -- necessary
    because glow is a synced flag set once at tag time, never re-evaluated on its own, so without
    this the toggle would only visibly affect bosses materialized AFTER the command ran.
  - Guardian Mobs deliberately NOT retroactively updated -- `BossGuardiansFixture` tags them once
    at spawn (a raw `MobSpawnEvent.FinalizeSpawn` listener, `tryGuardianize`) and keeps no record
    of which mobs it tagged afterward, so there's nothing to look back up for an already-spawned
    guardian. Command response says this plainly rather than silently only half-working. Adding
    real guardian tracking to close this gap would be a separate, bigger piece of work -- not
    attempted here since it wasn't asked for and this ticket's existing per-boss precedent
    (`BossRecord.bossEntityId()`) doesn't exist for guardians at all.
  - Balance-verified all five touched files (`BossRules.java`, `DefaultBossRules.java`,
    `BossRulesFacet.java`, `BossCommands.java`, `BossCommandHandler.java`); `BossRules.java`'s own
    paren count carries the same pre-existing 2-off "[0,1)" interval-notation prose imbalance this
    ticket already noted on an earlier entry, not a new issue.
  - Manual review only, as ever -- `Entity#setGlowingTag`/`ServerLevel#getEntity(UUID)` are both
    well-established vanilla API, but worth an actual rebuild+playtest before trusting the
    retroactive-apply behavior specifically (glow persistence across a relog/chunk-unload isn't
    something I can verify without one).

<!-- bh-header:start -->
**mcRepos** — [Dashboard](../../BACKHAUL.md) · [Board](../BOARD.md) · [Folder](openfolder:///C:/_local/mcRepos/FrontierMode)
<!-- bh-header:end -->
