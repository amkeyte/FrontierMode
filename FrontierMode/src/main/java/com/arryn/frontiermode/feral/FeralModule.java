package com.arryn.frontiermode.feral;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.effects.common.EffectsAPI;
import com.arryn.frontiermode.feral.common.FeralLogic;
import com.arryn.frontiermode.feral.server.commands.FeralCommands;
import com.arryn.frontiermode.feral.server.rules.FeralRules;
import com.arryn.satchel.common.util.out.OUT;
import com.arryn.satchel.common.util.schedule.TickDelay;
import com.arryn.satchel.common.util.schedule.TickThrottler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Entry point for Feral (RM_FRO_038 "Martha") -- corrupted, distance-scaling hostile mobs native
 * to the Exterior. See wiki/frontiermode/design/exterior.md#feral for design intent and
 * wiki/frontiermode/architecture/exterior.md#consumers-frontier-sickness-and-feral for the
 * technical shape.
 *
 * <p><b>Its own module, deliberately, not folded into {@code BorderModule}.</b> Feral reads
 * {@link BorderAPI#frontierDistanceAt(net.minecraft.world.level.Level, BlockPos)} (Border) and
 * {@link EffectsAPI#ensureTeam}/{@link EffectsAPI#assignToTeam} (Effects) the same way Guardian
 * Mobs reads Border/Effects from inside Boss's own module -- through their public APIs, not by
 * living inside them. That's the real precedent here, not {@code BossGuardiansFixture}'s own
 * bundle placement: Guardian is a real {@code SatchelFixture} specifically for "access parity"
 * reaching a sibling fixture (its own doc comment says so); Feral has no sibling fixture to reach
 * (no persisted state at all), so it needed neither a fixture nor a home inside another module's
 * files. Kept separate on purpose -- this feature is expected to grow a lot, and containment now
 * is cheaper than untangling it from Border's own files later.
 *
 * <p>No persisted state, no {@code JigConfig}, no fixture/bundle -- purely event-driven off six
 * Forge events, each registered from its own {@code FrontierMode} delegate alongside the
 * Boss/Border ones already on that shared touch point: {@code EntityJoinLevelEvent} (the actual
 * tagging authority -- see {@link #onEntityJoinLevel} for why this replaced tagging from
 * {@code MobSpawnEvent.FinalizeSpawn}), {@code MobSpawnEvent.FinalizeSpawn} (density-boost roll
 * only now -- see {@link #onMobSpawnFinalize}), {@code MobSpawnEvent.SpawnPlacementCheck}
 * (force-allow a hostile spawn attempt the Exterior's own light level would otherwise have
 * blocked -- see {@link #onSpawnPlacementCheck}), {@code TickEvent.PlayerTickEvent} (the cosmetic
 * tell on already-spawned Feral mobs -- see {@link #onPlayerTick}), {@code TickEvent.LevelTickEvent}
 * (FRO_111: kills a Feral mob that wandered back inside the Frontier -- see {@link #onLevelTick}),
 * and {@code LivingDropsEvent}/{@code LivingExperienceDropEvent} (no free loot -- see
 * {@link #onLivingDrops}). Both tick hooks are Feral's own, not Border's
 * {@code BorderModule.onPlayerScopeTick} -- Feral depends on Border, never the reverse (same
 * discipline Boss/Border already keep), so it can't ride a hook Border would have to call into it
 * from. {@link #init()} exists only for the same "Module Initiating" bootstrap-logging consistency
 * every other module follows -- there's nothing else to initialize.
 */
public final class FeralModule {

    private FeralModule() {
    }

    // Shared, not-persisted RNG -- same static-field shape BossTellFixture.RNG/
    // BossGuardiansFixture.RNG/BorderModule.RNG already use.
    private static final RandomSource RNG = RandomSource.create();

    // FRO_111: per-level cadence gate for the re-entry sweep, same TickThrottler(intervalTicks,
    // level::getGameTime)-keyed-by-object-identity shape BordersRevisionMonitor already
    // establishes for "throttle this recurring check independently per level."
    private static final Map<Level, TickThrottler> REENTRY_SWEEP_THROTTLERS = new HashMap<>();

    // FRO_128: per-mob lingering despawn timer, keyed by entity UUID -- started the first sweep a
    // Feral mob is found farther than FeralRules.DESPAWN_RADIUS from every player, cleared if it
    // comes back within range before the timer fires. Not persisted, same "in-memory only, a
    // world reload just re-seeds it" precedent as REENTRY_SWEEP_THROTTLERS above -- losing a
    // partially-elapsed timer on reload only delays a despawn, never blocks one. Entries are
    // removed on every path a feral-teamed mob's life can end (see sweepFeralCleanup's re-entry
    // branch, this method's own timer-fire branch, and onLivingDrops for an ordinary combat
    // kill), so this only grows with mobs currently alive and still lingering near the despawn
    // threshold, not with Feral's full lifetime population.
    private static final Map<UUID, TickDelay> DESPAWN_TIMERS = new HashMap<>();

    // FRO_135: debug toggle for Feral's glowing tag, same "plain static flag, not persisted"
    // shape BorderModule.sicknessEnabled already establishes for this kind of playtest on/off
    // switch. Defaults to true -- matches the always-on behavior tagFeral had before this ticket.
    private static boolean glowEnabled = true;

    public static void setGlowEnabled(boolean value) {
        glowEnabled = value;
    }

    public static void init() {
        OUT.info("FrontierMode - Feral Module Initiating.");
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        FeralCommands.register(event.getDispatcher());
    }

    /**
     * Cosmetic-only tell on already-spawned Feral mobs -- poison particles / hurt sounds, mirroring
     * {@code ExteriorTellFixture}'s own Sick Wildlife tell shape exactly (same two-independent-
     * rolls dispatch, same particle/sound placeholders), just searching for {@code feral}-teamed
     * hostiles instead of {@link net.minecraft.world.entity.animal.Animal}s and gating per mob's
     * actual team membership rather than recomputing frontier-distance (a Feral mob is already,
     * permanently, tagged the moment it spawns -- see {@link #tagFeral} -- so there's nothing to
     * recompute here). Never touches health/combat, same "harmless, no real stakes" precedent Sick
     * Wildlife established -- this only marks a Feral mob as visibly afflicted, the same way sick
     * wildlife already is.
     *
     * <p>Searches {@link Mob} (not {@link Monster}) filtered to {@link MobCategory#MONSTER} --
     * same category test {@link #onSpawnPlacementCheck}/{@link #onMobSpawnFinalize} use, not a
     * class-hierarchy check. {@code Slime}/{@code MagmaCube} are real {@code MobCategory.MONSTER}
     * hostiles that don't extend {@code Monster} (a genuine vanilla quirk) -- an
     * {@code instanceof Monster} search would silently skip them here too, the same gap that kept
     * them from ever being tagged in the first place.
     *
     * <p>Cadence-gated on {@code player.tickCount}, same modulo shape
     * {@code BorderModule.applySicknessEffects} already uses, rather than a per-player counter
     * field -- Feral has no per-player object to hold one on (no fixture, no bundle). Gated to
     * {@code Phase.END} so this runs once per player per tick, not twice.
     */
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % FeralRules.TELL_INTERVAL_TICKS != 0) {
            return;
        }

        ServerLevel level = player.serverLevel();
        AABB searchArea = new AABB(player.blockPosition()).inflate(FeralRules.TELL_RADIUS);
        List<Mob> nearby = level.getEntitiesOfClass(Mob.class, searchArea,
                m -> m.getType().getCategory() == MobCategory.MONSTER);

        int feralCount = 0;
        for (Mob mob : nearby) {
            Team team = mob.getTeam();
            if (team == null || !"feral".equals(team.getName())) {
                continue;
            }
            feralCount++;
            rollFeralTell(player, level, mob);
        }

        // TEMP DIAGNOSTIC (2026-09-18, FRO_111 playtest report): tells "this hook never sees a
        // feral-teamed mob nearby" apart from "it sees one but the roll/dispatch is broken." Safe
        // to delete once confirmed.
        if (!nearby.isEmpty()) {
            OUT.info("[Feral] onPlayerTick player=" + player.getGameProfile().getName()
                    + " nearbyMonsters=" + nearby.size() + " feralTeamed=" + feralCount);
        }
    }

    private static void rollFeralTell(ServerPlayer player, ServerLevel level, Mob monster) {
        Vec3 pos = monster.position();

        // Two independent rolls, same shape ExteriorTellFixture/BossTellFixture already use --
        // particle is targeted (only this player sees their own local tell), sound is an area
        // broadcast (anyone nearby hears it, not just this one player).
        if (RNG.nextDouble() < FeralRules.TELL_PARTICLE_CHANCE) {
            // Placeholder particle -- same WITCH placeholder ExteriorTellFixture uses, same
            // "swap for a real poison-green particle once confirmed against real sources" hedge.
            EffectsAPI.sendParticleToPlayer(
                    player, ParticleTypes.WITCH,
                    pos.x, pos.y + monster.getBbHeight() * 0.5, pos.z,
                    6, 0.2, 0.2, 0.2, 0.01);
        }
        if (RNG.nextDouble() < FeralRules.TELL_SOUND_CHANCE) {
            // Placeholder sound -- same GENERIC_HURT placeholder ExteriorTellFixture uses.
            EffectsAPI.broadcastSound(
                    level, pos.x, pos.y, pos.z,
                    SoundEvents.GENERIC_HURT, SoundSource.HOSTILE,
                    0.4f, 1.4f + RNG.nextFloat() * 0.3f);
        }
    }

    /**
     * FRO_111 (project owner ruling, routed via Architect): a Feral mob that wanders back inside
     * the Frontier must not survive -- ordinary hostiles, Boss, and Guardian Mobs are unaffected
     * and stay exactly as designed. Sick Wildlife's own "cured by walking back in" precedent (it
     * just stops registering a positive {@code frontierDistance} and is left alone, no code
     * needed) doesn't transfer: Sick Wildlife is harmless, but a Feral mob is stat-scaled and
     * permanently tagged, so surviving re-entry would let accumulated intensity follow it into the
     * player's own claimed land.
     *
     * <p>As of this same sweep, also clears any Feral mob too far from every player to matter --
     * see {@link #sweepFeralCleanup}'s own doc for why (project owner report, 2026-09-20: the
     * density-driven swarm hits vanilla's own local mob cap after its first burst and then never
     * recovers).
     *
     * <p>Deliberately level-wide, not bounded to {@link #onPlayerTick}'s
     * {@code FeralRules.TELL_RADIUS} -- the guarantee has to hold everywhere a Feral mob might
     * wander back to, not just wherever a player happens to be standing. Cadence-gated per level
     * via {@link #REENTRY_SWEEP_THROTTLERS} rather than every tick -- this doesn't need to be
     * instant, only reliable.
     */
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.level instanceof ServerLevel level)) {
            return;
        }

        TickThrottler throttler = REENTRY_SWEEP_THROTTLERS.computeIfAbsent(
                level, l -> new TickThrottler(FeralRules.REENTRY_SWEEP_INTERVAL_TICKS, level::getGameTime));
        if (!throttler.allow()) {
            return;
        }

        sweepFeralCleanup(level);
    }

    /**
     * Resolves {@code feral}-team membership live off every {@link Mob} currently in
     * {@code level} whose type is a real {@link MobCategory#MONSTER} hostile -- not an
     * {@code instanceof Monster} check, same reasoning as {@link #onPlayerTick}'s own search --
     * per this fixture-free module's own "no persisted tracking" precedent, not a list Feral
     * itself maintains. One pass, two independent cleanup checks per mob found:
     *
     * <ol>
     *   <li><b>Re-entered the Frontier:</b> kills it (not a silent {@code discard()}), per the
     *       project owner's own wording ("must die on re-entry") -- {@code LivingEntity#kill()}
     *       runs the same death handling (drops, XP, death message) an ordinary combat death
     *       would, so this reads as "the Frontier killed it," not "it vanished."
     *   <li><b>Still in the Exterior, but too far from every player</b> ({@code
     *       FeralRules.DESPAWN_RADIUS}): starts (or continues) a lingering per-mob despawn timer
     *       -- see {@link #DESPAWN_TIMERS} -- rather than an instant {@code discard()}. FRO_128
     *       (project owner refinement): a tight radius keeps only an immediate, close-in band of
     *       mobs alive as a visual cue at the border; the timer just tolerates a player's distance
     *       briefly crossing that threshold without mobs flickering out from under them. Once the
     *       timer actually fires, this is a silent {@code discard()} -- no death event, no
     *       message, matching vanilla's own silent distant-mob despawn precedent -- freeing the
     *       mob-cap slot the density mechanic ({@code rollDensityCompanion}) needs in order to
     *       keep piggybacking on real spawn attempts near the player, per the project owner's own
     *       report that the initial border-crossing burst otherwise saturates the cap and nothing
     *       new ever spawns again nearby. A mob that comes back within range before its timer
     *       fires has its entry cleared, same as never having left.
     * </ol>
     */
    private static void sweepFeralCleanup(ServerLevel level) {
        int feralCount = 0;
        int killedCount = 0;
        int despawnedCount = 0;
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof Mob monster) || monster.getType().getCategory() != MobCategory.MONSTER) {
                continue;
            }
            Team team = monster.getTeam();
            if (team == null || !"feral".equals(team.getName())) {
                continue;
            }
            feralCount++;

            if (BorderAPI.frontierDistanceAt(level, monster.blockPosition()) <= 0) {
                killFeralAndCleanupSplit(level, monster);
                DESPAWN_TIMERS.remove(monster.getUUID());
                killedCount++;
                continue;
            }

            Player nearest = level.getNearestPlayer(monster, -1.0);
            boolean farFromEveryPlayer = nearest == null || monster.distanceTo(nearest) > FeralRules.DESPAWN_RADIUS;
            if (!farFromEveryPlayer) {
                DESPAWN_TIMERS.remove(monster.getUUID());
                continue;
            }

            TickDelay timer = DESPAWN_TIMERS.computeIfAbsent(monster.getUUID(),
                    id -> new TickDelay(FeralRules.DESPAWN_LINGER_TICKS, level::getGameTime));
            if (timer.allow()) {
                monster.discard();
                DESPAWN_TIMERS.remove(monster.getUUID());
                despawnedCount++;
            }
        }

        // TEMP DIAGNOSTIC (2026-09-18, FRO_111 playtest report): tells "this sweep never finds a
        // feral-teamed mob at all" apart from "it finds them but the cleanup logic itself is
        // broken." Safe to delete once confirmed.
        if (feralCount > 0) {
            OUT.info("[Feral] sweepFeralCleanup level=" + level.dimension().location()
                    + " feralTeamed=" + feralCount + " killed=" + killedCount
                    + " despawned=" + despawnedCount);
        }
    }

    /**
     * Playtest found a Feral Slime/MagmaCube surviving re-entry in every practical sense that
     * matters, despite {@code kill()} succeeding: checked against real 1.20.1-47.4.10 sources --
     * {@code Slime.remove(RemovalReason)} spawns 2-4 same-typed, smaller children whenever
     * {@code getSize() > 1} and {@code isDeadOrDying()} is true, entirely independent of the
     * {@code LivingDropsEvent}/{@code LivingExperienceDropEvent} cancellation {@link
     * #onLivingDrops}/{@link #onLivingExperienceDrop} already apply -- that cancels loot, not
     * the split itself. {@code MagmaCube extends Slime} in vanilla, so this check and cleanup
     * cover both. The children spawn inside {@code remove()}'s own call, synchronously, before
     * {@code kill()} returns here -- so by the time this method's own {@code kill()} call
     * returns, any split children already exist in the level and can be found and removed in
     * the same tick, closing the same "must not survive re-entry" gap for them too.
     *
     * <p>{@code discard()}, not another {@code kill()} -- {@code kill()} was the project owner's
     * own deliberate choice for the <em>original</em> Feral mob (a real death, so it reads as
     * "the Frontier killed it," see {@link #sweepFeralCleanup}'s own doc) -- but the split children
     * were never really alive as their own encounter; discarding them silently (no death event,
     * no re-triggering this same split check on a already-{@code isDeadOrDying()}-false entity)
     * is the correct "this never really happened" cleanup for an artifact of the parent's own
     * death mechanic, not a second kill needing its own narrative.
     */
    private static void killFeralAndCleanupSplit(ServerLevel level, Mob monster) {
        BlockPos pos = monster.blockPosition();
        boolean splits = monster instanceof Slime;

        monster.kill();

        if (!splits) {
            return;
        }
        for (Slime child : level.getEntitiesOfClass(Slime.class, new AABB(pos).inflate(2.0))) {
            if (child.getType() == monster.getType()) {
                child.discard();
            }
        }
    }

    /**
     * Permanent design change, per project owner direction: hostiles in the Exterior should be
     * able to spawn regardless of light level -- the Exterior is meant to be dangerous everywhere
     * out there, not just in the dark. Fires from {@code SpawnPlacements#checkSpawnRules}, per
     * that event's own doc, "before a mob attempts to spawn" -- {@code MobSpawnEvent.FinalizeSpawn}
     * fires too late for this; light gating happens upstream of it, so a hostile blocked here never
     * reaches {@link #onMobSpawnFinalize} at all. Only forces {@link Event.Result#ALLOW} for
     * {@link MobCategory#MONSTER} entity types while {@code frontierDistance > 0} for the spawn
     * position -- everywhere else (inside the Frontier, or any non-hostile category: animals,
     * water creatures, ambient), this event is left at its default result, so vanilla's own spawn
     * placement checks (light included) are completely unchanged. "Standby, don't crash":
     * {@code getLevel()} not actually a real {@link ServerLevel} (e.g. a {@code WorldGenRegion}
     * during chunk generation) or Border state not resolvable yet both fall through untouched.
     */
    public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        if (event.getEntityType().getCategory() != MobCategory.MONSTER) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (BorderAPI.frontierDistanceAt(level, event.getPos()) <= 0) {
            return;
        }

        event.setResult(Event.Result.ALLOW);
    }

    /**
     * Permanent design change, per project owner direction: no exploiting a Feral mob for free
     * loot. Both drop paths are closed, not just the obvious one -- the {@link #onLevelTick}
     * re-entry kill is the actual exploit vector ("lure it across the border, let the sweep
     * kill it, no risk taken"), but a legitimate combat kill drops through the exact same vanilla
     * death path ({@code LivingEntity#die}), so gating on cause-of-death would need to
     * distinguish the two -- simpler and unambiguous to make a Feral mob drop nothing at all,
     * however it dies. Gated on {@code feral}-team membership (not a fixture/list -- same "no
     * persisted tracking" precedent everywhere else in this module), so this only ever touches
     * mobs Feral itself tagged; ordinary hostiles, Boss, and Guardian Mobs are unaffected.
     */
    public static void onLivingDrops(LivingDropsEvent event) {
        if (isFeral(event.getEntity())) {
            event.setCanceled(true);
            // FRO_128: a legitimate combat kill is the one death path sweepFeralCleanup itself
            // never sees (it's already removed from the level by the time the next sweep runs) --
            // clear any lingering DESPAWN_TIMERS entry here so it doesn't outlive the mob it was
            // tracking.
            DESPAWN_TIMERS.remove(event.getEntity().getUUID());
        }
    }

    /** XP counts as "free loot" too, per the same ruling {@link #onLivingDrops} follows. */
    public static void onLivingExperienceDrop(LivingExperienceDropEvent event) {
        if (isFeral(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private static boolean isFeral(LivingEntity entity) {
        Team team = entity.getTeam();
        return team != null && "feral".equals(team.getName());
    }

    /**
     * Density boost only, now -- see {@link #onEntityJoinLevel} for the actual tagging authority.
     * "Standby, don't crash": the spawning mob isn't a {@code MobCategory.MONSTER} hostile, or
     * {@code frontierDistance <= 0} (not in the Exterior), is a silent no-op. Never cancels the
     * spawn either way.
     */
    public static void onMobSpawnFinalize(Mob mob, ServerLevel level) {
        if (mob.getType().getCategory() != MobCategory.MONSTER) {
            return;
        }

        BlockPos spawnPos = mob.blockPosition();
        int frontierDistance = BorderAPI.frontierDistanceAt(level, spawnPos);
        if (frontierDistance <= 0) {
            return;
        }

        rollDensityCompanion(mob, level, spawnPos, frontierDistance);
    }

    /**
     * Design ruling (project owner, 2026-09-18): outside the Frontier, ordinary (non-Feral)
     * hostiles shouldn't exist at all -- every real hostile in the Exterior is Feral,
     * unconditionally. Supersedes the earlier bounded, distance-saturating roll
     * ({@code FeralLogic.spawnChance}, removed) -- what scales with distance is meant to be raw
     * mob count/density, not a per-mob conversion chance; density scaling itself is tracked
     * separately (not built yet, see FRO_110's own log) and stays out of this method's job.
     *
     * <p><b>Deliberately not done from {@link #onMobSpawnFinalize} anymore.</b> Playtest found
     * split-off Slime/MagmaCube children still dropping loot even after the category fix --
     * vanilla only drops slimeballs from the smallest slime size; larger ones just split into
     * smaller slimes on death instead. That split-spawn is a special vanilla mechanic, not a
     * natural spawn, and almost certainly never reaches {@code MobSpawnEvent.FinalizeSpawn} at
     * all (per that event's own doc, it's injected by a transformer at specific vanilla call
     * sites, not universally wherever {@code Mob#finalizeSpawn} happens to be called) -- the same
     * reason this module's own {@code spawnDensityCompanion} deliberately avoids the event bus.
     * {@link EntityJoinLevelEvent} is the general fix: it fires whenever anything joins a level
     * <em>however it was created</em> ({@code Level#addFreshEntity}, per its own doc), so it
     * catches split children, this module's own density companions, and any future mechanic that
     * bypasses the normal spawn-event pipeline the same way, with one rule instead of chasing
     * each bypass individually.
     *
     * <p>Gated on {@link MobCategory#MONSTER} (not {@code instanceof Monster}) -- the same
     * category test {@link #onSpawnPlacementCheck}/{@link #onMobSpawnFinalize} use, needed
     * separately here since {@code Slime}/{@code MagmaCube} don't extend {@code Monster}.
     * Idempotent via {@link #isFeral}: skips anything already tagged (e.g. by a prior call to
     * this same method for the identical entity, since {@code EntityJoinLevelEvent} can fire more
     * than once for edge cases like a dimension change) rather than re-tagging and re-logging.
     * {@code loadedFromDisk()} excludes chunk-load rehydration -- this only tags genuinely new
     * joins, not every already-tagged-or-not entity a chunk reload brings back.
     */
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk()) {
            return;
        }
        if (!(event.getEntity() instanceof Mob mob) || mob.getType().getCategory() != MobCategory.MONSTER) {
            return;
        }
        if (mob instanceof Slime) {
            // FRO_125 (project owner ruling, 2026-09-20): Slime/MagmaCube dropped from Feral
            // eligibility entirely, rather than continuing to chase the vanilla split-on-death/
            // mob-cap interaction their own size-reduction mechanic caused -- MagmaCube extends
            // Slime in vanilla, so this one check covers both. The only place a mob "becomes"
            // Feral is the tag call below, so excluding here is sufficient on its own; nothing
            // downstream (onSpawnPlacementCheck's light bypass, onMobSpawnFinalize's density
            // roll) needs its own matching exclusion -- both stay generic to every Exterior
            // hostile, Slime/MagmaCube included, same as any other never-tagged mob passing
            // through them.
            return;
        }
        if (isFeral(mob)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        int frontierDistance = BorderAPI.frontierDistanceAt(level, mob.blockPosition());
        if (frontierDistance <= 0) {
            return;
        }

        double intensity = FeralLogic.intensity(frontierDistance, FeralRules.INTENSITY_SCALE);
        tagFeral(mob, frontierDistance, level);
        applyStatScaling(mob, intensity);
    }

    /**
     * Design ruling (project owner, 2026-09-18): density scales with raw distance, uncapped --
     * "steadily more," not a flat multiplier that flattens out. {@code MobSpawnEvent.FinalizeSpawn}
     * fires per already-decided spawn attempt rather than controlling how many attempts vanilla
     * makes, so this is still an approximation of a true spawn-rate multiplier -- same acknowledged
     * limitation as Sick Wildlife's own density boost -- just no longer bounded to "at most one
     * extra."
     *
     * <p>{@link FeralLogic#expectedDensityCount} returns a real-valued expectation, not a count --
     * turned into an actual integer the standard unbiased way: the whole part spawns
     * unconditionally, the fractional remainder is one more independent roll. E.g. an expectation
     * of 2.7 always spawns 2 companions and has a 70% chance of a 3rd, averaging 2.7 over many
     * events. Clamped to {@link FeralRules#DENSITY_MAX_PER_EVENT} -- a safety valve against an
     * extreme distance turning one spawn into a lag spike, not a design cap (see that constant's
     * own doc).
     */
    private static void rollDensityCompanion(Mob mob, ServerLevel level, BlockPos spawnPos, int frontierDistance) {
        double expected = FeralLogic.expectedDensityCount(frontierDistance, FeralRules.DENSITY_COUNT_SCALE);
        if (expected <= 0.0) {
            return;
        }

        int guaranteed = (int) Math.floor(expected);
        double fractional = expected - guaranteed;
        int count = Math.min(guaranteed, FeralRules.DENSITY_MAX_PER_EVENT);
        if (count < FeralRules.DENSITY_MAX_PER_EVENT && RNG.nextDouble() < fractional) {
            count++;
        }

        // FRO_128 follow-up (project owner, 2026-09-21): clamp to remaining room under
        // FeralRules.MAX_ALIVE_FERAL -- the density mechanic, not natural spawns/tagging, is what
        // actually drives the "border wall eats the whole mob cap" problem, so this is the one
        // place the cap needs enforcing. countAliveFeral() is a live scan, same "resolve live off
        // the scoreboard, never a maintained count" precedent every other Feral check follows.
        int room = FeralRules.MAX_ALIVE_FERAL - countAliveFeral(level);
        if (room <= 0) {
            return;
        }
        count = Math.min(count, room);

        for (int i = 0; i < count; i++) {
            spawnDensityCompanion(mob, level, spawnPos);
        }
    }

    /**
     * Live count of every currently-loaded {@code feral}-teamed mob in {@code level} -- same
     * resolve-live-off-the-scoreboard shape {@link #sweepFeralCleanup}/{@link #onPlayerTick}/
     * {@code FeralCommandHandler.setGlow} already use, factored out here since
     * {@link #rollDensityCompanion} needs just the count, not a cleanup or tag pass over the
     * results.
     */
    private static int countAliveFeral(ServerLevel level) {
        int count = 0;
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof Mob monster) || monster.getType().getCategory() != MobCategory.MONSTER) {
                continue;
            }
            Team team = monster.getTeam();
            if (team != null && "feral".equals(team.getName())) {
                count++;
            }
        }
        return count;
    }

    /**
     * Spawns one extra copy of {@code mob}'s own type near {@code spawnPos} -- mirrors
     * {@code BorderModule.spawnDensityCompanion} exactly. Calls {@link Mob#finalizeSpawn} directly
     * (not through Forge's event bus again) so this never re-enters {@link #onMobSpawnFinalize}
     * for the companion it creates -- but {@link #onEntityJoinLevel} still catches it via
     * {@code level.addFreshEntity} below and tags it there, so no explicit tag call is needed
     * here anymore.
     */
    private static void spawnDensityCompanion(Mob mob, ServerLevel level, BlockPos nearPos) {
        Entity created = mob.getType().create(level);
        if (!(created instanceof Mob companion)) {
            return;
        }

        int dx = RNG.nextInt(7) - 3;
        int dz = RNG.nextInt(7) - 3;
        BlockPos offset = nearPos.offset(dx, 0, dz);
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, offset);

        companion.moveTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, RNG.nextFloat() * 360.0F, 0.0F);
        companion.finalizeSpawn(level, level.getCurrentDifficultyAt(ground), MobSpawnType.EVENT, null, null);

        if (!level.addFreshEntity(companion)) {
            OUT.warn("[Feral] spawnDensityCompanion(): addFreshEntity failed for " + mob.getType() + " near " + nearPos);
        }
    }

    /**
     * Name tag + a shared {@code feral} scoreboard team, mirroring
     * {@code DefaultBossRules.tagGuardian}'s shape directly. The tag shows the raw
     * frontier-distance {@code mob} spawned at, not a bucketed tier -- per the design page's
     * explicit "no bucketed tiers picked from a table, just steadily more twisted," a continuous
     * number is the honest readout here, the same way Guardian's own tag is "a direct readout of
     * what the mob actually got." Team color proposed as {@code DARK_GREEN} for the design page's
     * "sickly green," distinct from Guardian's {@code DARK_PURPLE} and Boss's unteamed white glow.
     */
    private static void tagFeral(Mob mob, int frontierDistance, ServerLevel level) {
        mob.setCustomName(Component.literal("Feral [" + frontierDistance + "]"));
        mob.setCustomNameVisible(true);

        Scoreboard scoreboard = level.getScoreboard();
        PlayerTeam team = EffectsAPI.ensureTeam(scoreboard, "feral", ChatFormatting.DARK_GREEN);
        EffectsAPI.assignToTeam(mob, scoreboard, team);

        // FRO_135: reads the debug toggle instead of an unconditional true, so /feral debug glow
        // affects newly-tagged mobs immediately; FeralCommandHandler.setGlow handles retroactively
        // updating already-tagged ones.
        mob.setGlowingTag(glowEnabled);

        // TEMP DIAGNOSTIC (2026-09-18, FRO_111 playtest report): confirms the team assignment
        // actually stuck -- reads getTeam() back immediately rather than trusting assignToTeam
        // didn't silently no-op/throw. Safe to delete once confirmed.
        OUT.info("[Feral] tagFeral(): tagged " + mob.getType() + " uuid=" + mob.getStringUUID()
                + " readBackTeam=" + mob.getTeam());
    }

    /**
     * Continuous, uncapped stat scaling by {@code intensity} -- mirrors
     * {@code DefaultBossRules.applyGuardianStatScaling}'s health/attack-damage formula shape
     * exactly, keyed off {@link FeralLogic#intensity} instead of a {@code BorderCurve} intensity.
     */
    private static void applyStatScaling(Mob mob, double intensity) {
        double factor = 1.0 + (FeralRules.HEALTH_SCALE * intensity);

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
}
