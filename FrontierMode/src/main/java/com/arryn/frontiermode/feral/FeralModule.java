package com.arryn.frontiermode.feral;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.effects.common.EffectsAPI;
import com.arryn.frontiermode.feral.common.FeralLogic;
import com.arryn.frontiermode.feral.server.commands.FeralCommands;
import com.arryn.frontiermode.feral.server.rules.FeralRules;
import com.arryn.satchel.common.util.out.OUT;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;

import java.util.List;

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
 * <p>No persisted state, no {@code JigConfig}, no fixture/bundle -- purely event-driven off three
 * Forge events, each registered from its own {@code FrontierMode} delegate alongside the
 * Boss/Border ones already on that shared touch point: {@code MobSpawnEvent.FinalizeSpawn} (tag a
 * hostile that already vanilla-spawned), {@code MobSpawnEvent.SpawnPlacementCheck} (force-allow a
 * hostile spawn attempt the Exterior's own light level would otherwise have blocked -- see
 * {@link #onSpawnPlacementCheck}), and {@code TickEvent.PlayerTickEvent} (the cosmetic tell on
 * already-spawned Feral mobs -- see {@link #onPlayerTick}). The tick hook is Feral's own, not
 * Border's {@code BorderModule.onPlayerScopeTick} -- Feral depends on Border, never the reverse
 * (same discipline Boss/Border already keep), so it can't ride a hook Border would have to call
 * into it from. {@link #init()} exists only for the same "Module Initiating" bootstrap-logging
 * consistency every other module follows -- there's nothing else to initialize.
 */
public final class FeralModule {

    private FeralModule() {
    }

    // Shared, not-persisted RNG -- same static-field shape BossTellFixture.RNG/
    // BossGuardiansFixture.RNG/BorderModule.RNG already use.
    private static final RandomSource RNG = RandomSource.create();

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
     * {@link Monster}s instead of {@link net.minecraft.world.entity.animal.Animal}s and gating per
     * mob's actual team membership rather than recomputing frontier-distance (a Feral mob is
     * already, permanently, tagged the moment it spawns -- see {@link #tagFeral} -- so there's
     * nothing to recompute here). Never touches health/combat, same "harmless, no real stakes"
     * precedent Sick Wildlife established -- this only marks a Feral mob as visibly afflicted, the
     * same way sick wildlife already is.
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
        List<Monster> nearby = level.getEntitiesOfClass(Monster.class, searchArea);

        for (Monster monster : nearby) {
            Team team = monster.getTeam();
            if (team == null || !"feral".equals(team.getName())) {
                continue;
            }
            rollFeralTell(player, level, monster);
        }
    }

    private static void rollFeralTell(ServerPlayer player, ServerLevel level, Monster monster) {
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
     * Rolls a chance, bounded by {@link FeralLogic#spawnChance}, that {@code mob} (an ordinary
     * hostile already about to spawn) becomes Feral; on a hit, tags it in place per
     * {@link #tagFeral(Mob, int, ServerLevel)}/{@link #applyStatScaling(Mob, double)} rather than
     * replacing or summoning anything -- same "a miss leaves vanilla unmodified" discipline as
     * Guardian Mobs and Sick Wildlife's own hooks. "Standby, don't crash": Border state not
     * resolvable yet, the spawning mob isn't a hostile {@link Monster}, or
     * {@code frontierDistance <= 0} (not in the Exterior), is a silent no-op. Never cancels the
     * spawn either way.
     */
    public static void onMobSpawnFinalize(Mob mob, ServerLevel level) {
        if (!(mob instanceof Monster)) {
            return;
        }

        BlockPos spawnPos = mob.blockPosition();
        int frontierDistance = BorderAPI.frontierDistanceAt(level, spawnPos);
        if (frontierDistance <= 0) {
            return;
        }

        // Density boost -- independent of the Feral-conversion roll below; fires for any hostile
        // in the Exterior, tagged or not. See spawnDensityCompanion's own doc.
        rollDensityCompanion(mob, level, spawnPos, frontierDistance);

        double chance = FeralLogic.spawnChance(
                frontierDistance, FeralRules.SPAWN_FALLOFF_DISTANCE, FeralRules.SPAWN_MAX_CHANCE);
        if (RNG.nextDouble() >= chance) {
            // Miss -- vanilla spawn proceeds unmodified, no retry.
            return;
        }

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

        spawnDensityCompanion(mob, level, spawnPos);
    }

    /**
     * Spawns one extra copy of {@code mob}'s own type near {@code spawnPos} -- mirrors
     * {@code BorderModule.spawnDensityCompanion} exactly. Calls {@link Mob#finalizeSpawn} directly
     * (not through Forge's event bus again) so this never re-enters {@link #onMobSpawnFinalize}
     * for the companion it creates.
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

        mob.setGlowingTag(true);
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
