package com.arryn.frontiermode.feral;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.effects.common.EffectsAPI;
import com.arryn.frontiermode.feral.common.FeralLogic;
import com.arryn.frontiermode.feral.server.commands.FeralCommands;
import com.arryn.frontiermode.feral.server.rules.FeralRules;
import com.arryn.frontiermode.spawn.MobSpawns;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.config.JigBundles;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.level.LevelJigConfig;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.jig.player.PlayerJigConfig;
import com.arryn.satchel.common.jig.player.PlayerScope;
import com.arryn.satchel.common.lifecycle.EntityJoinLevelSignal;
import com.arryn.satchel.common.lifecycle.EventHandlers;
import com.arryn.satchel.common.lifecycle.LivingDropsSignal;
import com.arryn.satchel.common.lifecycle.LivingExperienceDropSignal;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import com.arryn.satchel.common.lifecycle.SpawnPlacementCheckSignal;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
 * <p>No persisted state, no fixture/bundle. {@code onRegisterCommands} is Feral's one remaining
 * raw-Forge delegate target (command registration, a permanently-accepted exception per
 * wiki/satchel/spec/forge-integration.md) -- every other concern rides a Satchel signal or
 * {@code ScopeEvent.Tick}, none of them a raw Forge touch point of Feral's own.
 *
 * <p><b>Tick-driven work</b> uses its own two {@code JigConfig}s (FRO_146) --
 * {@link FrontierKeys#FERAL_PLAYER_JIG}/{@link FrontierKeys#FERAL_LEVEL_JIG}, registered in
 * {@link #init()} with an empty bundle schema (no fixture/bundle needed, same as the module as a
 * whole -- these two keys exist purely to get a per-tick {@code ScopeEvent.Tick} callback).
 * Feral's own keys, not Border's {@code BORDER_PLAYER_JIG}/{@code BORDERS_JIG} -- Feral depends on
 * Border, never the reverse (same discipline Boss/Border already keep), so it doesn't ride a tick
 * source another module owns. {@link #onPlayerScopeTick}/{@link #onLevelScopeTick} are the
 * {@code ScopeEvent.Tick} subscribers; {@link #onPlayerTick}/{@link #onLevelTick} hold the actual
 * per-player/per-level work and are called from those subscribers once a tick's worth of
 * {@code ServerPlayer}/{@code ServerLevel} has been resolved -- {@link #onPlayerTick} covers the
 * cosmetic tell on already-spawned Feral mobs and {@link #maintainPackNearPlayer}'s force-spawn
 * maintenance; {@link #onLevelTick} covers FRO_111's re-entry sweep (kills a Feral mob that
 * wandered back inside the Frontier).
 *
 * <p><b>Everything else rides a bare Satchel signal</b> (SAT_072's {@link LivingDropsSignal}/
 * {@link LivingExperienceDropSignal} for the no-free-loot rule, FRO_147; SAT_076's
 * {@link SpawnPlacementCheckSignal}/{@link EntityJoinLevelSignal} for the light-bypass wall and
 * the actual tagging authority, FRO_107 -- see {@link #onLivingDrops}/{@link #onLivingExperienceDrop}/
 * {@link #onSpawnPlacementCheck}/{@link #onEntityJoinLevel}), all subscribed via the same
 * {@code FERAL_LEVEL_JIG} config's {@code eventHandlers()}. Every one of these is a bare, unscoped
 * signal (no jig-key filtering needed, unlike {@code ScopeEvent.Tick}); declaring their handlers
 * on {@code FERAL_LEVEL_JIG} is only where installation happens to occur, not a claim this is
 * jig-scoped -- same precedent {@code BossTellModule}/{@code StructureModule} already established
 * for {@code ItemPickupSignal}/{@code BlockBreakSignal}. {@link #onMobSpawnFinalize} is the one
 * exception -- it stays a plain {@code (Mob, ServerLevel)} delegate target, called directly from
 * {@code SpawnCoordinator}, the cross-module fan-out, rather than
 * subscribing to {@code MobSpawnFinalizeSignal} itself, since Boss/Border/Feral all need a turn on
 * the same spawn in a specific order (Slime deny first) that independent bare-signal subscribers
 * can't guarantee.
 */
public final class FeralModule {

    private FeralModule() {
    }

    // Shared, not-persisted RNG -- same static-field shape BossTellFixture.RNG/
    // BossGuardiansFixture.RNG/BorderModule.RNG already use.
    private static final RandomSource RNG = RandomSource.create();

    // Candidate spots MobSpawns.scatter tries for one density companion.
    private static final int DENSITY_SCATTER_TRIES = 4;

    // FRO_111: per-level cadence gate for the re-entry sweep, same TickThrottler(intervalTicks,
    // level::getGameTime)-keyed-by-object-identity shape BordersRevisionMonitor already
    // establishes for "throttle this recurring check independently per level."
    private static final Map<Level, TickThrottler> REENTRY_SWEEP_THROTTLERS = new HashMap<>();

    // FRO_128: per-mob lingering despawn timer, keyed by entity UUID -- started the first sweep a
    // Feral mob is found farther than FeralRules.despawnRadius() from every player, cleared if it
    // comes back within range before the timer fires. Not persisted, same "in-memory only, a
    // world reload just re-seeds it" precedent as REENTRY_SWEEP_THROTTLERS above -- losing a
    // partially-elapsed timer on reload only delays a despawn, never blocks one. Entries are
    // removed on every path a feral-teamed mob's life can end (see sweepFeralCleanup's re-entry
    // branch, this method's own timer-fire branch, and onLivingDrops for an ordinary combat
    // kill), so this only grows with mobs currently alive and still lingering near the despawn
    // threshold, not with Feral's full lifetime population.
    private static final Map<UUID, TickDelay> DESPAWN_TIMERS = new HashMap<>();

    // Playtest feedback (2026-09-21, fifth pass): distinguishes a spawnPackMember-created mob
    // from an ordinary natural Exterior spawn that onEntityJoinLevel tags feral the same way --
    // see maintainPackNearPlayer/countAlivePackMembers for why that distinction matters now.
    // Stored on the entity's own persistent data (Forge's per-entity NBT compound), not a static
    // map -- survives exactly as long as the entity itself does, no separate cleanup needed.
    private static final String PACK_MEMBER_TAG = "frontiermode_feral_pack";

    // FRO_135: debug toggle for Feral's glowing tag, same "plain static flag, not persisted"
    // shape BorderModule.sicknessEnabled already establishes for this kind of playtest on/off
    // switch. Defaults to true -- matches the always-on behavior tagFeral had before this ticket.
    private static boolean glowEnabled = true;

    public static void setGlowEnabled(boolean value) {
        glowEnabled = value;
    }

    // Playtest feedback (2026-09-21): both the piggyback density mechanic (rollDensityCompanion,
    // riding vanilla's own sparse natural spawn attempts) and the radius+linger despawn timer
    // left the Exterior feeling thin and easy to just weave through -- "I need more mobs near me
    // when I run the gauntlet." Turned off in favor of maintainPackNearPlayer's direct
    // force-spawn-near-the-player approach below, same "plain static flag" shape as glowEnabled
    // above, not a code deletion -- easy to flip back and compare if the new approach doesn't
    // land either.
    private static final boolean densityCompanionEnabled = false;
    private static final boolean legacyDistanceDespawnEnabled = false;

    public static void init() {
        OUT.info("FrontierMode - Feral Module Initiating.");
        registerPlayerJig();
        registerLevelJig();
    }

    // FRO_146: no fixture/bundle -- these two configs exist purely to get a ScopeEvent.Tick
    // source, replacing the raw TickEvent.PlayerTickEvent/LevelTickEvent subscriptions that used
    // to live in FrontierMode.java. See this class's own doc for why Feral gets its own keys
    // rather than riding Border's.

    private static void registerPlayerJig() {
        EventHandlers eventHandlers =
                EventHandlers.builder()
                        .on(ScopeEvent.Tick.class, FeralModule::onPlayerScopeTick)
                        .build();

        PlayerJigConfig config = new PlayerJigConfig(FrontierKeys.FERAL_PLAYER_JIG);
        config.bundles().schema(new JigBundles.Schema<>(List.of()));
        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults().withTick(true))
                .eventHandlers(eventHandlers);

        Satchel.registerJigConfig(config);
    }

    private static void registerLevelJig() {
        EventHandlers eventHandlers =
                EventHandlers.builder()
                        .on(ScopeEvent.Tick.class, FeralModule::onLevelScopeTick)
                        // FRO_147: bare, unscoped signals like ItemPickupSignal (see
                        // BossTellModule.init()/StructureModule.init() for the identical
                        // precedent) -- declaring them here is just where installation happens to
                        // occur, not a claim this is FERAL_LEVEL_JIG-scoped.
                        .on(LivingDropsSignal.class, FeralModule::onLivingDrops)
                        .on(LivingExperienceDropSignal.class, FeralModule::onLivingExperienceDrop)
                        // FRO_107: same bare-signal precedent, SAT_076's spawn-cluster signals.
                        .on(SpawnPlacementCheckSignal.class, FeralModule::onSpawnPlacementCheck)
                        .on(EntityJoinLevelSignal.class, FeralModule::onEntityJoinLevel)
                        .build();

        LevelJigConfig config = new LevelJigConfig(FrontierKeys.FERAL_LEVEL_JIG);
        config.bundles().schema(new JigBundles.Schema<>(List.of()));
        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults().withTick(true))
                .eventHandlers(eventHandlers);

        Satchel.registerJigConfig(config);
    }

    /**
     * Shared-bus caveat, same as every other {@code ScopeEvent} handler in this codebase --
     * {@code ScopeEvent.Tick} fires for every jig scoped to whatever just ticked, so the
     * {@code FERAL_PLAYER_JIG} key check is what keeps this from running against the wrong jig's
     * scopes (Border's own {@code BORDER_PLAYER_JIG} ticks included).
     */
    private static void onPlayerScopeTick(ScopeEvent.Tick event) {
        ScopeInfo info = event.info();
        Objects.requireNonNull(info, "info");

        if (!FrontierKeys.FERAL_PLAYER_JIG.equals(info.jigInfo().key)) {
            return;
        }

        PlayerScope scope = (PlayerScope) info.scope();
        ServerPlayer player = (ServerPlayer) scope.player();
        onPlayerTick(player);
    }

    /**
     * Same shared-bus caveat as {@link #onPlayerScopeTick}, filtered to {@code FERAL_LEVEL_JIG}.
     * Direct cast to {@code ServerLevel}, no instanceof guard -- {@code LevelJigConfig}'s own
     * default is already {@code SideApplicability.SERVER} (unoverridden here), so this handler is
     * never invoked client-side to begin with (same reasoning FRO_104 already applied to
     * {@code BossJigHandlers.onTick}).
     */
    private static void onLevelScopeTick(ScopeEvent.Tick event) {
        ScopeInfo info = event.info();
        Objects.requireNonNull(info, "info");

        if (!FrontierKeys.FERAL_LEVEL_JIG.equals(info.jigInfo().key)) {
            return;
        }

        LevelScope scope = (LevelScope) info.scope();
        ServerLevel level = (ServerLevel) scope.level();
        onLevelTick(level);
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
     * field -- Feral has no per-player object to hold one on (no fixture, no bundle). Called from
     * {@link #onPlayerScopeTick}, which already runs once per player per real game tick --
     * {@code ScopeEvent.Tick} has no START/END-phase double-fire the way raw
     * {@code TickEvent.PlayerTickEvent} did, so there's nothing left here to gate on.
     */
    private static void onPlayerTick(ServerPlayer player) {
        // Own cadence, independent of the tell-interval gate below -- maintainPackNearPlayer's
        // own doc covers why this rides onPlayerTick rather than a new Forge subscription.
        maintainPackNearPlayer(player);

        if (player.tickCount % FeralRules.tellIntervalTicks() != 0) {
            return;
        }

        ServerLevel level = player.serverLevel();
        AABB searchArea = new AABB(player.blockPosition()).inflate(FeralRules.tellRadius());
        List<Mob> nearby = level.getEntitiesOfClass(Mob.class, searchArea,
                m -> m.getType().getCategory() == MobCategory.MONSTER);

        for (Mob mob : nearby) {
            Team team = mob.getTeam();
            if (team == null || !"feral".equals(team.getName())) {
                continue;
            }
            rollFeralTell(player, level, mob);
        }
    }

    private static void rollFeralTell(ServerPlayer player, ServerLevel level, Mob monster) {
        Vec3 pos = monster.position();

        // Two independent rolls, same shape ExteriorTellFixture/BossTellFixture already use --
        // particle is targeted (only this player sees their own local tell), sound is an area
        // broadcast (anyone nearby hears it, not just this one player).
        if (RNG.nextDouble() < FeralRules.tellParticleChance()) {
            // Placeholder particle -- same WITCH placeholder ExteriorTellFixture uses, same
            // "swap for a real poison-green particle once confirmed against real sources" hedge.
            EffectsAPI.sendParticleToPlayer(
                    player, ParticleTypes.WITCH,
                    pos.x, pos.y + monster.getBbHeight() * 0.5, pos.z,
                    6, 0.2, 0.2, 0.2, 0.01);
        }
        if (RNG.nextDouble() < FeralRules.tellSoundChance()) {
            // Placeholder sound -- same GENERIC_HURT placeholder ExteriorTellFixture uses.
            EffectsAPI.broadcastSound(
                    level, pos.x, pos.y, pos.z,
                    SoundEvents.GENERIC_HURT, SoundSource.HOSTILE,
                    0.4f, 1.4f + RNG.nextFloat() * 0.3f);
        }
    }

    /**
     * Playtest feedback (2026-09-21): piggybacking on vanilla's own sparse natural spawn attempts
     * (the disabled {@link #densityCompanionEnabled} path) left the Exterior feeling thin --
     * "I just weave around them for about 200m before the sickness kills me." This replaces that
     * indirection with a direct guarantee: while a player is in the Exterior, top up the Feral
     * mobs actually near *them* specifically, rather than hoping natural spawns happen to land
     * nearby. Rides {@link #onPlayerTick} rather than its own Forge subscription -- same
     * "no new touch point needed" shape that method's cosmetic-tell call already established,
     * just gated on {@link FeralRules#packMaintainIntervalTicks()} independently of the tell's
     * own cadence.
     *
     * <p>Counts currently-alive Feral mobs within {@link FeralRules#packMaintainRadius()} of
     * {@code player}; if under {@link FeralRules#packTargetCount()}, spawns the shortfall via
     * {@link #spawnPackMember}, clamped to whatever room remains under
     * {@link FeralRules#maxAliveFeral()} the same way {@link #rollDensityCompanion} already
     * respects that cap. A no-op outside the Exterior (mirrors every other Feral check's own
     * {@code frontierDistance <= 0} guard) and while the cap has no room left.
     *
     * <p>Playtest feedback (2026-09-21, fourth pass): "got one pack... but I still passed them" --
     * a plain radius around the player counted a pack the player had already run past just as
     * "present" as one still ahead of them, so once a pack fell behind, its own stragglers kept
     * satisfying {@code packTargetCount()} and blocked a fresh spawn ahead until they eventually
     * drifted past {@link FeralRules#packMaintainRadius()} entirely -- a real gap, not just a
     * distance to retune. {@link #isAheadOfPlayer} now filters the count to mobs actually ahead
     * of the player's own look direction, so passed mobs stop counting toward the quota the
     * moment they're behind, not whenever they finally fall far enough back.
     *
     * <p>Playtest feedback (fifth pass): "we are getting a lot of mobs from regular spawn algo as
     * well, that's most of the cap" -- {@code onEntityJoinLevel} still unconditionally tags every
     * natural Exterior spawn Feral (the standing 2026-09-18 design ruling, untouched by any of
     * this), and that population was never capped at all before now -- only whichever mechanic
     * was actively checking {@code MAX_ALIVE_FERAL} got throttled by it. Originally addressed by
     * giving pack members their own dedicated {@link FeralRules#maxAlivePackMembers()} budget
     * via {@link #countAlivePackMembers} -- but this method also kept checking the shared
     * {@code MAX_ALIVE_FERAL} total as a second, combined-population gate.
     *
     * <p>Playtest diagnostics (ninth pass, {@code logPackDiagnostics}): that second gate was
     * still the actual problem. A sprinting player loads fresh chunks continuously, and every
     * one gets instantly hostile-populated ({@code onSpawnPlacementCheck} force-allows regardless
     * of light) -- ambient count blew past 40 and stayed there for most of a run, so {@code
     * MAX_ALIVE_FERAL - countAliveFeral(level)} sat at or below 0 almost continuously, vetoing
     * every pack spawn attempt despite {@code maxAlivePackMembers()} having room to spare. Logs
     * showed the exact signature: pack count bled out from 24 to 3 over one run as
     * {@code frontierDistance} climbed, tracking ambient count exceeding 40, not anything about
     * the pack itself. Dropped the shared check entirely -- room is now
     * {@code maxAlivePackMembers()} alone, so ambient spawns can never crowd the pack out again.
     */
    private static void maintainPackNearPlayer(ServerPlayer player) {
        if (player.tickCount % FeralRules.packMaintainIntervalTicks() != 0) {
            return;
        }

        // FRO_141: warn (never clamp) if the pack Tunables' documented relationships are broken.
        FeralRules.warnIfPackTunablesInconsistent();

        ServerLevel level = player.serverLevel();
        if (BorderAPI.frontierDistanceAt(level, player.blockPosition()) <= 0) {
            return;
        }

        Vec3 playerPos = player.position();
        float playerYaw = player.getYRot();
        AABB maintainArea = new AABB(player.blockPosition()).inflate(FeralRules.packMaintainRadius());
        List<Mob> nearbyFeral = level.getEntitiesOfClass(Mob.class, maintainArea,
                m -> isFeralMonster(m) && isAheadOfPlayer(m, playerPos, playerYaw));
        if (nearbyFeral.size() >= FeralRules.packTargetCount()) {
            return;
        }

        int room = FeralRules.maxAlivePackMembers() - countAlivePackMembers(level);
        if (room <= 0) {
            return;
        }

        int toSpawn = Math.min(FeralRules.packTargetCount() - nearbyFeral.size(), room);
        for (int i = 0; i < toSpawn; i++) {
            spawnPackMember(player, level);
        }
    }

    /**
     * Playtest feedback (2026-09-21, eighth pass): "once I get past the first few spawns they
     * seem to quit going in front of me" -- the original dot-product test only excluded a mob
     * more than 90 degrees off the player's own facing, meaning anything within an entire
     * forward hemisphere (including mobs almost directly to the player's left or right) still
     * counted as "ahead" and kept satisfying {@link FeralRules#packTargetCount()}, even once the
     * player's actual path forward was empty. Narrowed to the real spawn cone --
     * {@link FeralRules#packSpawnConeHalfAngleDegrees()} either side of the player's own yaw --
     * so a mob has to still be in roughly the same direction pack members actually spawn in to
     * count as "part of the pack," not just technically in front.
     *
     * <p>Yaw, not {@code getLookAngle()} -- a pure horizontal heading, immune to the pitch-driven
     * shrinking a 3D look vector's {@code x}/{@code z} components would otherwise have when the
     * player's looking down (e.g. picking a path over terrain while running).
     */
    private static boolean isAheadOfPlayer(Mob mob, Vec3 playerPos, float playerYaw) {
        Vec3 toMob = mob.position().subtract(playerPos);
        if (toMob.x == 0.0 && toMob.z == 0.0) {
            return true;
        }

        double toMobAngle = Math.atan2(-toMob.x, toMob.z);
        double yawRad = Math.toRadians(playerYaw);
        double diff = normalizeAngleRad(toMobAngle - yawRad);
        return Math.abs(diff) <= Math.toRadians(FeralRules.packSpawnConeHalfAngleDegrees());
    }

    /** Wraps a radian angle into {@code (-PI, PI]}, so comparing angles across the +/-180 seam works. */
    private static double normalizeAngleRad(double angle) {
        double twoPi = Math.PI * 2.0;
        angle = angle % twoPi;
        if (angle > Math.PI) {
            angle -= twoPi;
        } else if (angle < -Math.PI) {
            angle += twoPi;
        }
        return angle;
    }

    private static boolean isFeralMonster(Mob mob) {
        if (mob.getType().getCategory() != MobCategory.MONSTER) {
            return false;
        }
        return isFeral(mob);
    }

    /**
     * Force-spawns one hostile ahead of {@code player} with {@code DISPATCHED} setup, the same real
     * Forge finalize-spawn dispatch {@code FeralCommandHandler
     * .spawnTestHostiles} uses (unlike {@code /summon}, this still fires
     * {@code MobSpawnEvent.FinalizeSpawn}, so it stays fully integrated with everything else on
     * that touch point -- the Exterior-only Slime/MagmaCube deny in {@code BossJigHandlers
     * .onMobSpawnFinalize} (FRO_154) and Guardian conversion both still apply normally, and
     * {@link #onEntityJoinLevel} still catches and tags it Feral through the ordinary
     * {@link EntityJoinLevelSignal} path, same as any other spawn). Zombie, matching that same
     * debug command's own choice -- a deliberately simple placeholder, not tuned for variety yet.
     *
     * <p>Playtest feedback (2026-09-21, third pass): a plain radius around the player's own
     * position meant the pack spawned in whatever direction they'd already left behind, since a
     * sprinting player outpaces the maintenance cadence -- "I'm just moving faster than the
     * spawning can keep up." Two changes address that together, not either alone:
     *
     * <ul>
     *   <li>Position is sampled from a real cone in front of the player (eighth pass -- "are we
     *       doing a wedge or more like a cone to the front?"), not a single fixed point: a random
     *       bearing within {@link FeralRules#packSpawnConeHalfAngleDegrees()} either side of
     *       the player's own yaw, at a random distance between
     *       {@link FeralRules#packSpawnMinDistance()} and {@link FeralRules#packSpawnMaxDistance()}
     *       -- mobs land somewhere in the player's actual path, not clustered at one exact spot.
     *       Yaw-based, not {@code getLookAngle()} -- see {@link #isAheadOfPlayer}'s own doc for
     *       why a pure heading is more robust than a pitch-sensitive 3D look vector here.
     *   <li>{@link #applyPackSpeedBoost} gives the spawned mob a temporary
     *       {@code MobEffects.MOVEMENT_SPEED} boost -- a plain zombie's walk speed can't keep pace
     *       with a sprinting player at all, so even a perfectly-placed spawn falls behind within a
     *       few seconds without this.
     * </ul>
     *
     * <p>Position is still checked against {@code BorderAPI.frontierDistanceAt} and skipped if it
     * resolves inside a border, same guard {@link #spawnDensityCompanion} added for its own
     * widened spread.
     */
    private static void spawnPackMember(ServerPlayer player, ServerLevel level) {
        double halfAngleRad = Math.toRadians(FeralRules.packSpawnConeHalfAngleDegrees());
        double angle = Math.toRadians(player.getYRot()) + (RNG.nextDouble() * 2.0 - 1.0) * halfAngleRad;
        double distance = FeralRules.packSpawnMinDistance()
                + RNG.nextDouble() * (FeralRules.packSpawnMaxDistance() - FeralRules.packSpawnMinDistance());

        double targetX = player.getX() + (-Math.sin(angle)) * distance;
        double targetZ = player.getZ() + Math.cos(angle) * distance;
        BlockPos offset = BlockPos.containing(targetX, player.getY(), targetZ);
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, offset);

        if (BorderAPI.frontierDistanceAt(level, ground) <= 0) {
            return;
        }

        // Marked before it ever joins the level -- see PACK_MEMBER_TAG's own doc for why this
        // distinction matters (countAlivePackMembers, maxAlivePackMembers()).
        Optional<Mob> spawned = MobSpawns.spawn(level, EntityType.ZOMBIE, ground, MobSpawns.Setup.DISPATCHED,
                mob -> mob.getPersistentData().putBoolean(PACK_MEMBER_TAG, true));
        if (spawned.isEmpty()) {
            return;
        }
        Mob mob = spawned.get();

        applyPackSpeedBoost(mob);
        applyPackFollowRange(mob);
    }

    /**
     * See {@link #spawnPackMember}'s own doc for why this exists -- a plain zombie can't sustain
     * a chase against a sprinting player. {@code ambient=false, visible=true}: the vanilla
     * swirling particles are a deliberate tell that this specific mob is unnaturally fast, not
     * hidden polish. Duration is generous ({@link FeralRules#packSpeedDurationTicks()}) rather
     * than tied to actual engagement time, since there's no cheap signal for "has this mob
     * stopped being relevant yet" -- {@link #despawnIfBeyondPackRadius} is what actually ends a
     * mob's relevance, not this effect running out first.
     */
    private static void applyPackSpeedBoost(Mob mob) {
        mob.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SPEED, FeralRules.packSpeedDurationTicks(),
                FeralRules.packSpeedAmplifier(), false, true));
    }

    /**
     * Playtest ask (2026-09-22): wider aggro range. Vanilla's own {@code Attributes.FOLLOW_RANGE}
     * default is meaningfully shorter than {@link FeralRules#packSpawnMaxDistance()} -- a pack
     * member spawned at the far edge of the cone could land already outside its own vanilla
     * target-acquisition range, sitting there doing nothing until the player closed most of that
     * gap on their own. Raised to {@link FeralRules#packFollowRange()} so a pack member reliably
     * notices and starts pursuing the moment it spawns, anywhere in the cone.
     */
    private static void applyPackFollowRange(Mob mob) {
        var followRange = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null) {
            followRange.setBaseValue(FeralRules.packFollowRange());
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
     * {@code FeralRules.tellRadius()} -- the guarantee has to hold everywhere a Feral mob might
     * wander back to, not just wherever a player happens to be standing. Cadence-gated per level
     * via {@link #REENTRY_SWEEP_THROTTLERS} rather than every tick -- this doesn't need to be
     * instant, only reliable. Called from {@link #onLevelScopeTick}, which already runs once per
     * level per real game tick -- same "no START/END double-fire to gate on anymore" reasoning as
     * {@link #onPlayerTick}.
     */
    private static void onLevelTick(ServerLevel level) {
        logPackDiagnostics(level);

        TickThrottler throttler = REENTRY_SWEEP_THROTTLERS.computeIfAbsent(
                level, l -> new TickThrottler(FeralRules.reentrySweepIntervalTicks(), level::getGameTime));
        if (!throttler.allow()) {
            return;
        }

        sweepFeralCleanup(level);
    }

    // Playtest ask (2026-09-22): "let's try running a logger that throttles to 1 second when a
    // feral mob exists" -- eight rounds of blind tuning is enough; this replaces guessing with
    // actually seeing what's happening. Own throttler, independent of REENTRY_SWEEP_THROTTLERS'
    // 2s cadence (1s, per the ask).
    private static final Map<Level, TickThrottler> DIAGNOSTIC_LOG_THROTTLERS = new HashMap<>();

    /**
     * Fires at most once a second per level, and only when at least one Feral mob exists (a
     * quiet Frontier logs nothing). Beyond the three counts asked for (pack members, all Feral,
     * all mobs), also logs, per player currently in the Exterior: whether they're sprinting,
     * their {@code frontierDistance}, and the distance to their own nearest pack member --
     * that last one is the one number that should directly answer "are pack members actually
     * closing the gap or quietly losing ground despite the speed boost," which raw counts alone
     * can't show. Also counts how many pack members currently have a live target
     * ({@code Mob#getTarget()}) set to a player at all -- distinguishes "can't keep up" from
     * "isn't even trying to chase," since FOLLOW_RANGE/speed only matter once a mob has actually
     * decided to pursue.
     */
    private static void logPackDiagnostics(ServerLevel level) {
        // Playtest ask (2026-09-22): "we can mothball that logging" -- FRO_144 is confirmed working,
        // so logPackDiagnostics's own per-second [Feral][Diag] line has served its purpose. Same
        // "disabled, not deleted" shape as the two flags above -- a one-line flip to bring the
        // diagnostic back if a future regression needs the same visibility again.
        boolean diagnosticLoggingEnabled = false;
        if (!diagnosticLoggingEnabled) {
            return;
        }

        TickThrottler throttler = DIAGNOSTIC_LOG_THROTTLERS.computeIfAbsent(
                level, l -> new TickThrottler(20, level::getGameTime));
        if (!throttler.allow()) {
            return;
        }

        int totalEntities = 0;
        int totalMobs = 0;
        int allFeral = 0;
        int packMembersTargetingPlayer = 0;
        List<Mob> packMembersFound = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            totalEntities++;
            if (!(entity instanceof Mob mob)) {
                continue;
            }
            totalMobs++;
            if (!isFeral(mob)) {
                continue;
            }
            allFeral++;
            if (isPackMember(mob)) {
                packMembersFound.add(mob);
                if (mob.getTarget() instanceof Player) {
                    packMembersTargetingPlayer++;
                }
            }
        }

        if (allFeral == 0) {
            return;
        }

        StringBuilder perPlayer = new StringBuilder();
        for (ServerPlayer player : level.players()) {
            double nearestPackDist = Double.NaN;
            for (Mob mob : packMembersFound) {
                double d = mob.distanceTo(player);
                if (Double.isNaN(nearestPackDist) || d < nearestPackDist) {
                    nearestPackDist = d;
                }
            }
            perPlayer.append(player.getGameProfile().getName())
                    .append("[frontierDist=").append(BorderAPI.frontierDistanceAt(level, player.blockPosition()))
                    .append(",sprinting=").append(player.isSprinting())
                    .append(",nearestPack=")
                    .append(Double.isNaN(nearestPackDist) ? "none" : String.format("%.1f", nearestPackDist))
                    .append("] ");
        }

        OUT.info("[Feral][Diag] totalEntities=" + totalEntities + " totalMobs=" + totalMobs
                + " allFeral=" + allFeral + " packMembers=" + packMembersFound.size()
                + " packTargetingPlayer=" + packMembersTargetingPlayer + " | " + perPlayer);
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
     *   <li><b>Still in the Exterior, but too far from every player:</b> dispatches to
     *       {@link #despawnWithLegacyLinger} when {@link #legacyDistanceDespawnEnabled}, otherwise
     *       to {@link #despawnIfBeyondPackRadius} for a {@link #isPackMember}-marked mob or
     *       {@link #despawnIfBeyondAmbientRadius} for an ordinary natural spawn -- see those
     *       methods' own docs for the actual behavior each one implements; this method doesn't
     *       care which is active.
     * </ol>
     */
    private static void sweepFeralCleanup(ServerLevel level) {
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof Mob monster) || monster.getType().getCategory() != MobCategory.MONSTER) {
                continue;
            }
            Team team = monster.getTeam();
            if (team == null || !"feral".equals(team.getName())) {
                continue;
            }

            if (BorderAPI.frontierDistanceAt(level, monster.blockPosition()) <= 0) {
                killFeralAndCleanupSplit(level, monster);
                DESPAWN_TIMERS.remove(monster.getUUID());
                continue;
            }

            if (legacyDistanceDespawnEnabled) {
                despawnWithLegacyLinger(level, monster);
            } else if (isPackMember(monster)) {
                despawnIfBeyondPackRadius(level, monster);
            } else {
                despawnIfBeyondAmbientRadius(level, monster);
            }
        }
    }

    /**
     * FRO_128's original approach -- a lingering {@link TickDelay} per mob, tolerating a brief
     * crossing of {@link FeralRules#despawnRadius()} without flickering. Disabled by default (see
     * {@link #legacyDistanceDespawnEnabled}); kept intact rather than deleted so it's a one-flag
     * flip to compare against {@link #despawnIfBeyondPackRadius} again later.
     *
     * <p>[Arryn] examine why the Result value is never used.
     */
    @SuppressWarnings("UnusedReturnValue")
    private static boolean despawnWithLegacyLinger(ServerLevel level, Mob monster) {
        Player nearest = level.getNearestPlayer(monster, -1.0);
        boolean farFromEveryPlayer = nearest == null || monster.distanceTo(nearest) > FeralRules.despawnRadius();
        if (!farFromEveryPlayer) {
            DESPAWN_TIMERS.remove(monster.getUUID());
            return false;
        }

        TickDelay timer = DESPAWN_TIMERS.computeIfAbsent(monster.getUUID(),
                id -> new TickDelay(FeralRules.despawnLingerTicks(), level::getGameTime));
        if (!timer.allow()) {
            return false;
        }
        monster.discard();
        DESPAWN_TIMERS.remove(monster.getUUID());
        return true;
    }

    /**
     * Playtest feedback (2026-09-21): "force spawn the pack on my position, then delete them when
     * I get too far from them" -- the despawn half of that ask, paired with
     * {@link #maintainPackNearPlayer} on the spawn side. No linger, unlike
     * {@link #despawnWithLegacyLinger}.
     *
     * <p>Playtest feedback (fifth pass): "hitting mob caps again" -- {@link #applyPackSpeedBoost}
     * gives pack members enough speed to keep pace with a sprinting player even from behind, so a
     * mob the player has already passed can trail them indefinitely without ever actually
     * covering {@link FeralRules#packDespawnRadius()} worth of raw distance -- it just never
     * despawns, quietly eating {@link FeralRules#maxAliveFeral()}'s budget until nothing new can
     * spawn ahead. Direction-aware now, using the same {@link #isAheadOfPlayer} test
     * {@link #maintainPackNearPlayer} already applies to counting: a mob still ahead gets the
     * generous {@code packDespawnRadius()} leash (a distant but real ambush shouldn't be destroyed
     * just for not being reached yet), but a mob that's fallen behind only gets
     * {@link FeralRules#packBehindDespawnRadius()} -- raw distance no longer matters once the
     * player has moved past it; that's the actual signal it's done being relevant, not how far
     * away it managed to stay.
     *
     * <p>[Arryn] examine why the Result value is never used.
     */
    @SuppressWarnings("UnusedReturnValue")
    private static boolean despawnIfBeyondPackRadius(ServerLevel level, Mob monster) {
        Player nearest = level.getNearestPlayer(monster, -1.0);
        if (nearest == null) {
            monster.discard();
            return true;
        }

        boolean ahead = isAheadOfPlayer(monster, nearest.position(), nearest.getYRot());
        double radius = ahead ? FeralRules.packDespawnRadius() : FeralRules.packBehindDespawnRadius();
        if (monster.distanceTo(nearest) <= radius) {
            return false;
        }
        monster.discard();
        return true;
    }

    /**
     * Playtest feedback (2026-09-21, sixth pass): "still hitting cap... we should remove all
     * feral mobs spawned outside a radius now, they are inconsequential" -- an ordinary natural
     * Exterior spawn (not a {@link #isPackMember}-marked one) has no deliberate encounter behind
     * it at all, so it doesn't earn {@link #despawnIfBeyondPackRadius}'s generous ahead-leash or
     * its ahead/behind distinction -- either way, once it's farther than
     * {@link FeralRules#ambientDespawnRadius()} from every player, it was never going to be
     * encountered and is just sitting on {@link FeralRules#maxAliveFeral()}'s shared budget for
     * nothing. Flat distance check, no direction, no linger.
     *
     * <p>[Arryn] examine why the Result value is never used.
     */
    @SuppressWarnings("UnusedReturnValue")
    private static boolean despawnIfBeyondAmbientRadius(ServerLevel level, Mob monster) {
        Player nearest = level.getNearestPlayer(monster, -1.0);
        boolean farFromEveryPlayer = nearest == null || monster.distanceTo(nearest) > FeralRules.ambientDespawnRadius();
        if (!farFromEveryPlayer) {
            return false;
        }
        monster.discard();
        return true;
    }

    /** See {@link #PACK_MEMBER_TAG}'s own doc. */
    private static boolean isPackMember(Mob mob) {
        return mob.getPersistentData().getBoolean(PACK_MEMBER_TAG);
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
     * Originally a permanent, unconditional design change (project owner direction): hostiles in
     * the Exterior spawn regardless of light level, so the Exterior reads as dangerous everywhere
     * out there, not just in the dark. Fires from {@code SpawnPlacements#checkSpawnRules}, per
     * that event's own doc, "before a mob attempts to spawn" -- {@code MobSpawnEvent.FinalizeSpawn}
     * fires too late for this; light gating happens upstream of it, so a hostile blocked here never
     * reaches {@link #onMobSpawnFinalize} at all.
     *
     * <p>Playtest ask (2026-09-22): "once they start outside the fence we don't need those mobs
     * anymore" -- now conditional on the nearest player's own state, not just the spawn
     * position's. Resolved per attempt: whoever's nearest to {@code signal.pos()}.
     *
     * <ul>
     *   <li><b>That player hasn't crossed yet</b> ({@code frontierDistance <= 0} for <em>them</em>,
     *       even though the spawn position itself is in the Exterior): unchanged from the
     *       original behavior -- force {@link SpawnPlacementCheckSignal#allow()}. This is the
     *       visual-tell wall, visible from inside the Frontier, that lures/warns a player who
     *       hasn't committed to crossing yet.
     *   <li><b>That player has already crossed</b>: force {@link SpawnPlacementCheckSignal#deny()}
     *       outright --
     *       not a reversion to vanilla's own light-gated rules, a full stop. Once a player is
     *       genuinely in the wild, {@link #maintainPackNearPlayer}'s deliberate pack is the real
     *       threat; ambient chaff spawning around them no longer serves the visual-tell purpose
     *       (they're already out there) and was the actual driver behind ambient population
     *       ballooning toward vanilla's own hostile cap during a sprint.
     * </ul>
     *
     * <p>Still scoped to {@link MobCategory#MONSTER} entity types with {@code frontierDistance >
     * 0} for the spawn position -- everywhere else (inside the Frontier, or any non-hostile
     * category: animals, water creatures, ambient), this event is left at its default result, so
     * vanilla's own spawn placement checks are completely unchanged there. "Standby, don't
     * crash": {@code getLevel()} not actually a real {@link ServerLevel} (e.g. a
     * {@code WorldGenRegion} during chunk generation), Border state not resolvable yet, or no
     * player found near the spawn position all fall through to the original ALLOW behavior rather
     * than denying on an unresolvable state.
     *
     * <p>FRO_107: rides {@link SpawnPlacementCheckSignal} (SAT_076) instead of a raw Forge
     * subscription -- {@code allow()}/{@code deny()}, not {@code cancel()}: this event is
     * {@code @HasResult}, not {@code @Cancelable} (see that signal's own doc).
     */
    private static void onSpawnPlacementCheck(SpawnPlacementCheckSignal signal) {
        if (signal.entityType().getCategory() != MobCategory.MONSTER) {
            return;
        }
        //noinspection resource
        if (!(signal.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = signal.pos();
        if (BorderAPI.frontierDistanceAt(level, pos) <= 0) {
            return;
        }

        Player nearest = level.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), -1.0, false);
        if (nearest != null && BorderAPI.frontierDistanceAt(level, nearest.blockPosition()) > 0) {
            signal.deny();
            return;
        }

        // allow() bypasses vanilla's whole monster spawn predicate, not just its darkness test, so
        // the spawn surface is re-checked; if it fails the result stays unset and vanilla refuses.
        @SuppressWarnings("unchecked")
        EntityType<? extends Mob> mobType = (EntityType<? extends Mob>) signal.entityType();
        if (MobSpawns.surfaceAllows(level, mobType, signal.spawnType(), pos)) {
            signal.allow();
        }
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
     *
     * <p>FRO_147: rides {@link LivingDropsSignal} (SAT_072) instead of a raw Forge subscription --
     * {@link LivingDropsSignal#cancel()} passes straight through to the wrapped event's own
     * {@code setCanceled(true)}.
     */
    private static void onLivingDrops(LivingDropsSignal signal) {
        if (isFeral(signal.entity())) {
            signal.cancel();
            // FRO_128: a legitimate combat kill is the one death path sweepFeralCleanup itself
            // never sees (it's already removed from the level by the time the next sweep runs) --
            // clear any lingering DESPAWN_TIMERS entry here so it doesn't outlive the mob it was
            // tracking.
            DESPAWN_TIMERS.remove(signal.entity().getUUID());
        }
    }

    /**
     * XP counts as "free loot" too, per the same ruling {@link #onLivingDrops} follows. FRO_147:
     * rides {@link LivingExperienceDropSignal} (SAT_072) instead of a raw Forge subscription.
     */
    private static void onLivingExperienceDrop(LivingExperienceDropSignal signal) {
        if (isFeral(signal.entity())) {
            signal.cancel();
        }
    }

    private static boolean isFeral(LivingEntity entity) {
        Team team = entity.getTeam();
        return team != null && "feral".equals(team.getName());
    }

    /**
     * Exterior policy: Slime and MagmaCube (a {@code Slime}) are removed from the Exterior only --
     * where {@code frontierDistanceAt > 0} -- so Feral rules govern out there, and they spawn at the
     * ordinary vanilla rate inside the border. The caller denies the spawn with
     * {@code MobSpawnFinalizeSignal#denySpawn()}, not {@code cancel()}, which only skips
     * {@code Mob#finalizeSpawn}'s initialization and still lets the entity spawn.
     */
    public static boolean shouldDenySpawn(Mob mob, ServerLevel level) {
        return mob instanceof Slime && BorderAPI.frontierDistanceAt(level, mob.blockPosition()) > 0;
    }

    /**
     * Density boost only, now -- see {@link #onEntityJoinLevel} for the actual tagging authority.
     * "Standby, don't crash": the spawning mob isn't a {@code MobCategory.MONSTER} hostile, or
     * {@code frontierDistance <= 0} (not in the Exterior), is a silent no-op. Never cancels the
     * spawn either way. Currently a no-op regardless, per {@link #densityCompanionEnabled} -- see
     * that field's own doc.
     */
    public static void onMobSpawnFinalize(Mob mob, ServerLevel level) {
        if (!densityCompanionEnabled) {
            return;
        }
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
     * {@link EntityJoinLevelSignal} is the general fix: it fires whenever anything joins a level
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
     *
     * <p>FRO_107: rides {@link EntityJoinLevelSignal} (SAT_076) instead of a raw Forge
     * subscription.
     */
    private static void onEntityJoinLevel(EntityJoinLevelSignal signal) {
        if (signal.loadedFromDisk()) {
            return;
        }
        if (!(signal.entity() instanceof Mob mob) || mob.getType().getCategory() != MobCategory.MONSTER) {
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
        //noinspection resource
        if (!(signal.level() instanceof ServerLevel level)) {
            return;
        }

        int frontierDistance = BorderAPI.frontierDistanceAt(level, mob.blockPosition());
        if (frontierDistance <= 0) {
            return;
        }

        double intensity = FeralLogic.intensity(frontierDistance, FeralRules.intensityScale());
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
     * events. Clamped to {@link FeralRules#densityMaxPerEvent()} -- a safety valve against an
     * extreme distance turning one spawn into a lag spike, not a design cap (see that method's
     * own doc).
     */
    private static void rollDensityCompanion(Mob mob, ServerLevel level, BlockPos spawnPos, int frontierDistance) {
        double expected = FeralLogic.expectedDensityCount(frontierDistance, FeralRules.densityCountScale());
        if (expected <= 0.0) {
            return;
        }

        int count = MobSpawns.rollCount(expected, FeralRules.densityMaxPerEvent(), RNG);

        // FRO_128 follow-up (project owner, 2026-09-21): clamp to remaining room under
        // FeralRules.maxAliveFeral() -- the density mechanic, not natural spawns/tagging, is what
        // actually drives the "border wall eats the whole mob cap" problem, so this is the one
        // place the cap needs enforcing. countAliveFeral() is a live scan, same "resolve live off
        // the scoreboard, never a maintained count" precedent every other Feral check follows.
        int room = FeralRules.maxAliveFeral() - countAliveFeral(level);
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
     * Same live-scan shape as {@link #countAliveFeral}, but filtered to mobs
     * {@link #spawnPackMember} itself created (see {@link #PACK_MEMBER_TAG}) -- a natural
     * Exterior spawn is still {@code feral}-teamed the moment it joins, but was never a pack
     * member, and {@link #maintainPackNearPlayer}'s own doc covers why that distinction has to be
     * counted separately now.
     */
    private static int countAlivePackMembers(ServerLevel level) {
        int count = 0;
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof Mob monster) || monster.getType().getCategory() != MobCategory.MONSTER) {
                continue;
            }
            Team team = monster.getTeam();
            if (team == null || !"feral".equals(team.getName())) {
                continue;
            }
            if (isPackMember(monster)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Spawns one extra copy of {@code mob}'s own type near {@code nearPos}, with {@code VANILLA}
     * setup: vanilla's {@code Mob#finalizeSpawn} runs but the Forge event doesn't, so this never
     * re-enters {@link #onMobSpawnFinalize} for the companion it creates.
     * {@link #onEntityJoinLevel} still catches it on join and tags it, so no explicit tag call is
     * needed here.
     */
    private static void spawnDensityCompanion(Mob mob, ServerLevel level, BlockPos nearPos) {
        // Spread across ground the player has to maneuver through, not one dodge-able clump on the
        // original spawn -- see FeralRules.DENSITY_COMPANION_SPREAD_RADIUS.
        BlockPos spot = MobSpawns.scatter(
                level, mob, nearPos, FeralRules.DENSITY_COMPANION_SPREAD_RADIUS, 1.0F, DENSITY_SCATTER_TRIES);

        // The spread can cross back over a border edge, especially against a border that hasn't grown
        // large yet. Skip rather than place an extra, permanently-untagged hostile inside the
        // player's own safe zone (see this class's re-entry-kill doc).
        if (BorderAPI.frontierDistanceAt(level, spot) <= 0) {
            return;
        }

        MobSpawns.spawn(level, mob.getType(), spot, MobSpawns.Setup.VANILLA);
    }

    /**
     * Name tag + a shared {@code feral} scoreboard team. The tag shows the raw frontier-distance
     * {@code mob} spawned at, not a bucketed tier -- per the design page's explicit "no bucketed
     * tiers picked from a table, just steadily more twisted." Team color is {@code DARK_GREEN} for
     * the design page's "sickly green," distinct from Guardian's {@code DARK_PURPLE} and Boss's
     * unteamed white glow.
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
    }

    /**
     * Continuous, uncapped stat scaling by {@code intensity} -- mirrors
     * {@code DefaultBossRules.applyGuardianStatScaling}'s health/attack-damage formula shape
     * exactly, keyed off {@link FeralLogic#intensity} instead of a {@code BorderCurve} intensity.
     */
    private static void applyStatScaling(Mob mob, double intensity) {
        double factor = 1.0 + (FeralRules.healthScale() * intensity);

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
