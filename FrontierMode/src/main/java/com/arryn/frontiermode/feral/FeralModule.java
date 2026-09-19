package com.arryn.frontiermode.feral;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.effects.common.EffectsAPI;
import com.arryn.frontiermode.feral.common.FeralLogic;
import com.arryn.frontiermode.feral.server.commands.FeralCommands;
import com.arryn.frontiermode.feral.server.rules.FeralRules;
import com.arryn.satchel.common.util.out.OUT;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * <p>No persisted state, no {@code JigConfig}, no fixture/bundle -- purely event-driven off four
 * Forge events, each registered from its own {@code FrontierMode} delegate alongside the
 * Boss/Border ones already on that shared touch point: {@code MobSpawnEvent.FinalizeSpawn} (tag a
 * hostile that already vanilla-spawned), {@code MobSpawnEvent.SpawnPlacementCheck} (force-allow a
 * hostile spawn attempt the Exterior's own light level would otherwise have blocked -- see
 * {@link #onSpawnPlacementCheck}), {@code TickEvent.PlayerTickEvent} (the cosmetic tell on
 * already-spawned Feral mobs -- see {@link #onPlayerTick}), and {@code TickEvent.LevelTickEvent}
 * (FRO_111: kills a Feral mob that wandered back inside the Frontier -- see
 * {@link #onLevelTick}). Both tick hooks are Feral's own, not Border's
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

        sweepReentry(level);
    }

    /**
     * Resolves {@code feral}-team membership live off every {@link Mob} currently in
     * {@code level} whose type is a real {@link MobCategory#MONSTER} hostile -- not an
     * {@code instanceof Monster} check, same reasoning as {@link #onPlayerTick}'s own search --
     * per this fixture-free module's own "no persisted tracking" precedent, not a list Feral
     * itself maintains. Kills (not a silent {@code discard()}) any match whose own current
     * position has re-entered the Frontier, per the project owner's own wording ("must die on
     * re-entry") -- {@link net.minecraft.world.entity.LivingEntity#kill()} runs the same death
     * handling (drops, XP, death message) an ordinary combat death would, so this reads as "the
     * Frontier killed it," not "it vanished."
     */
    private static void sweepReentry(ServerLevel level) {
        int feralCount = 0;
        int killedCount = 0;
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof Mob monster) || monster.getType().getCategory() != MobCategory.MONSTER) {
                continue;
            }
            Team team = monster.getTeam();
            if (team == null || !"feral".equals(team.getName())) {
                continue;
            }
            feralCount++;

            if (BorderAPI.frontierDistanceAt(level, monster.blockPosition()) > 0) {
                // Still in the Exterior -- not re-entered, leave it alone.
                continue;
            }

            monster.kill();
            killedCount++;
        }

        // TEMP DIAGNOSTIC (2026-09-18, FRO_111 playtest report): tells "this sweep never finds a
        // feral-teamed mob at all" apart from "it finds them but the frontierDistance/kill call
        // itself is broken." Safe to delete once confirmed.
        if (feralCount > 0) {
            OUT.info("[Feral] sweepReentry level=" + level.dimension().location()
                    + " feralTeamed=" + feralCount + " killed=" + killedCount);
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
     * Design ruling (project owner, 2026-09-18): outside the Frontier, ordinary (non-Feral)
     * hostiles shouldn't exist at all -- every real hostile spawning in the Exterior is Feral,
     * unconditionally. Supersedes the earlier bounded, distance-saturating roll
     * ({@code FeralLogic.spawnChance}, removed) -- what scales with distance is meant to be raw
     * mob count/density, not a per-mob conversion chance; density scaling itself is tracked
     * separately (not built yet, see FRO_110's own log) and stays out of this method's job.
     *
     * <p>Gated on {@link MobCategory#MONSTER} (not {@code instanceof Monster}) -- playtest found
     * {@code Slime}/{@code MagmaCube} never converting, a real vanilla quirk: both are genuine
     * {@code MobCategory.MONSTER} hostiles that don't extend the {@code Monster} class. Matches
     * {@link #onSpawnPlacementCheck}'s own test, which already used the category correctly.
     *
     * <p>"Standby, don't crash": Border state not resolvable yet, the spawning mob isn't a
     * {@code MobCategory.MONSTER} hostile, or {@code frontierDistance <= 0} (not in the Exterior),
     * is a silent no-op. Never cancels the spawn either way.
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

        // Density boost -- an extra companion spawn, independent of (and in addition to) the
        // unconditional tagging below. See spawnDensityCompanion's own doc.
        rollDensityCompanion(mob, level, spawnPos, frontierDistance);

        double intensity = FeralLogic.intensity(frontierDistance, FeralRules.INTENSITY_SCALE);
        tagFeral(mob, frontierDistance, level);
        applyStatScaling(mob, intensity);
    }

    /**
     * Permanent design change, per project owner direction: hostiles should show up more often in
     * the Exterior. {@code MobSpawnEvent.FinalizeSpawn} fires per already-decided spawn attempt
     * rather than controlling how many attempts vanilla makes, so this is an approximation of a
     * true spawn-rate multiplier -- same acknowledged limitation, same mechanism, as Sick
     * Wildlife's own density boost (see {@code BorderModule.spawnDensityCompanion}), just gated on
     * a hostile in the Exterior instead of a passive animal.
     */
    private static void rollDensityCompanion(Mob mob, ServerLevel level, BlockPos spawnPos, int frontierDistance) {
        double multiplier = FeralLogic.densityMultiplier(
                frontierDistance, FeralRules.DENSITY_FALLOFF_DISTANCE, FeralRules.DENSITY_MAX_MULTIPLIER);
        double extraSpawnChance = multiplier - 1.0;
        if (extraSpawnChance <= 0.0 || RNG.nextDouble() >= extraSpawnChance) {
            return;
        }

        spawnDensityCompanion(mob, level, spawnPos, frontierDistance);
    }

    /**
     * Spawns one extra copy of {@code mob}'s own type near {@code spawnPos} -- mirrors
     * {@code BorderModule.spawnDensityCompanion} exactly. Calls {@link Mob#finalizeSpawn} directly
     * (not through Forge's event bus again) so this never re-enters {@link #onMobSpawnFinalize}
     * for the companion it creates -- which means it also never picks up Feral's own tagging
     * on its own, so this method tags it explicitly, same {@code frontierDistance} as the
     * original (they spawn at essentially the same position). Playtest found roughly half of all
     * ordinary hostiles going untagged -- exactly this companion, created outside the tagging
     * path by design, now closed per the "every hostile outside is Feral" ruling above.
     */
    private static void spawnDensityCompanion(Mob mob, ServerLevel level, BlockPos nearPos, int frontierDistance) {
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
            return;
        }

        double intensity = FeralLogic.intensity(frontierDistance, FeralRules.INTENSITY_SCALE);
        tagFeral(companion, frontierDistance, level);
        applyStatScaling(companion, intensity);
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

        mob.setGlowingTag(true);

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
