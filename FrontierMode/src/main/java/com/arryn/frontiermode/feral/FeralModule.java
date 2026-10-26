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
import com.arryn.satchel.common.jig.player.PlayerJigConfig;
import com.arryn.satchel.common.jig.player.PlayerScope;
import com.arryn.satchel.common.lifecycle.EntityJoinLevelSignal;
import com.arryn.satchel.common.lifecycle.EventHandlers;
import com.arryn.satchel.common.lifecycle.LivingDropsSignal;
import com.arryn.satchel.common.lifecycle.LivingExperienceDropSignal;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import com.arryn.satchel.common.lifecycle.SpawnPlacementCheckSignal;
import com.arryn.satchel.common.lifecycle.SpawnPositionCheckSignal;
import com.arryn.satchel.common.trinket.Order;
import com.arryn.satchel.common.util.MobData;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Entry point for Feral (RM_FRO_038 "Martha") -- corrupted, distance-scaling hostile mobs native to the
 * Wilds. See wiki/frontiermode/architecture/feral.md for the technical shape.
 *
 * <p><b>Its own module, deliberately, not folded into {@code BorderModule}.</b> Feral reads
 * {@link BorderAPI#frontierDistanceAt(net.minecraft.world.level.Level, BlockPos)} (Border) through its public
 * API, and depends on Border, never the reverse. Kept separate on purpose: this feature is expected to grow,
 * and containment now is cheaper than untangling it from Border's files later.
 *
 * <p>Feral persists nothing of its own. Which mobs are Feral, and which of those are pack members, is the
 * record on the mob ({@link FeralData}); the Feral mobs in the level now are {@link FeralPresence}, a Trinket
 * on the level host; everything else is resolved live. {@link #onRegisterCommands} is Feral's one direct
 * Forge touch (command registration, a permanently-accepted exception per
 * wiki/satchel/spec/forge-integration.md).
 *
 * <p><b>The player tick</b> -- the pack top-up and the cosmetic tell -- is a {@code ScopeEvent.Tick} on
 * {@link FrontierKeys#FERAL_PLAYER_JIG}, Feral's own key rather than Border's. The bare Satchel signals
 * (drops, XP, the two spawn gates and the join) are declared on that config too, only because it is the
 * topically closest one. {@link #onMobSpawnFinalize} is the exception: a plain {@code (Mob, ServerLevel)}
 * delegate target called directly from {@code SpawnCoordinator}, since Boss, Border and Feral all need a turn
 * on the same spawn in a fixed order (the Slime deny first).
 */
public final class FeralModule {

    private FeralModule() {
    }

    // Shared, not-persisted RNG -- same static-field shape BossTellFixture.RNG/
    // BossSentinelsFixture.RNG/BorderModule.RNG already use.
    private static final RandomSource RNG = RandomSource.create();

    // Candidate spots MobSpawns.scatter tries for one density companion.
    private static final int DENSITY_SCATTER_TRIES = 4;

    // A player this many blocks below the surface heightmap counts as underground for the pack, and
    // how many cone columns are sampled for an open floor there before giving up for this pass.
    private static final int UNDERGROUND_DEPTH = 3;
    private static final int PACK_UNDERGROUND_TRIES = 24;

    // Playtest feedback (2026-09-21): both the piggyback density mechanic (rollDensityCompanion,
    // riding vanilla's own sparse natural spawn attempts) and the radius+linger despawn timer
    // left the Wilds feeling thin and easy to just weave through -- "I need more mobs near me
    // when I run the gauntlet." Turned off in favor of maintainPackNearPlayer's direct
    // force-spawn-near-the-player approach below, same "plain static flag" shape as the other debug switches
    // above, not a code deletion -- easy to flip back and compare if the new approach doesn't
    // land either.
    private static final boolean densityCompanionEnabled = false;

    // The MobData key that owns a pack member's raised follow range (FRO_219): one modifier per key.
    private static final net.minecraft.resources.ResourceLocation PACK_FOLLOW_RANGE_KEY =
            new net.minecraft.resources.ResourceLocation("frontiermode", "feral_pack_follow_range");

    public static void init() {
        OUT.info("FrontierMode - Feral Module Initiating.");
        registerPlayerJig();
        Satchel.contributeLevelTrinket("frontiermode:feral", JigPolicies.SideApplicability.SERVER, Order.EARLY,
                FeralPresence.class, () -> new FeralPresence());
    }

    // FRO_146: Feral's player tick, a ScopeEvent.Tick source. The level-side work is FeralPresence, a
    // Trinket on the level host. See this class's own doc for why Feral gets its own key rather than
    // riding Border's.

    private static void registerPlayerJig() {
        EventHandlers eventHandlers =
                EventHandlers.builder()
                        .on(ScopeEvent.Tick.class, FeralModule::onPlayerScopeTick)
                        // Bare, unscoped signals -- declared here only because this is the topically
                        // closest config, not a claim they are FERAL_PLAYER_JIG-scoped.
                        .on(LivingDropsSignal.class, FeralModule::onLivingDrops)
                        .on(LivingExperienceDropSignal.class, FeralModule::onLivingExperienceDrop)
                        .on(SpawnPlacementCheckSignal.class, FeralSpawnGateHandlers::onSpawnPlacementCheck)
                        .on(SpawnPositionCheckSignal.class, FeralSpawnGateHandlers::onSpawnPositionCheck)
                        .on(EntityJoinLevelSignal.class, FeralModule::onEntityJoinLevel)
                        .build();

        PlayerJigConfig config = new PlayerJigConfig(FrontierKeys.FERAL_PLAYER_JIG);
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

        if (!FrontierKeys.FERAL_PLAYER_JIG.equals(info.jigKey())) {
            return;
        }

        PlayerScope scope = (PlayerScope) info.scope();
        ServerPlayer player = (ServerPlayer) scope.player();
        onPlayerTick(player);
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        FeralCommands.register(event.getDispatcher());
    }

    /**
     * The pack top-up and the cosmetic tell on already-spawned Feral mobs -- poison particles and hurt
     * sounds, mirroring {@code WildsTellFixture}'s Sick Wildlife tell (two independent rolls, particle
     * targeted and sound broadcast). The Feral mobs are the ones in presence, within the tell radius of
     * the player. Never touches health or combat.
     *
     * <p>Cadence-gated on {@code player.tickCount}, the modulo shape {@code
     * BorderModule.applySicknessEffects} uses, rather than a per-player counter field. Called from
     * {@link #onPlayerScopeTick}, which runs once per player per game tick.
     */
    private static void onPlayerTick(ServerPlayer player) {
        // Own cadence, independent of the tell-interval gate below -- maintainPackNearPlayer's
        // own doc covers why this rides onPlayerTick rather than a new Forge subscription.
        maintainPackNearPlayer(player);

        if (player.tickCount % FeralRules.tellIntervalTicks() != 0) {
            return;
        }

        ServerLevel level = player.serverLevel();
        Optional<FeralPresence> presence = FeralPresence.of(level);
        if (presence.isEmpty()) {
            return;
        }
        AABB searchArea = new AABB(player.blockPosition()).inflate(FeralRules.tellRadius());
        for (Mob mob : presence.get().mobs()) {
            if (searchArea.intersects(mob.getBoundingBox())) {
                rollFeralTell(player, level, mob);
            }
        }
    }

    private static void rollFeralTell(ServerPlayer player, ServerLevel level, Mob monster) {
        Vec3 pos = monster.position();

        // Two independent rolls, same shape WildsTellFixture/BossTellFixture already use --
        // particle is targeted (only this player sees their own local tell), sound is an area
        // broadcast (anyone nearby hears it, not just this one player).
        if (RNG.nextDouble() < FeralRules.tellParticleChance()) {
            // Placeholder particle -- same WITCH placeholder WildsTellFixture uses, same
            // "swap for a real poison-green particle once confirmed against real sources" hedge.
            EffectsAPI.sendParticleToPlayer(
                    player, ParticleTypes.WITCH,
                    pos.x, pos.y + monster.getBbHeight() * 0.5, pos.z,
                    6, 0.2, 0.2, 0.2, 0.01);
        }
        if (RNG.nextDouble() < FeralRules.tellSoundChance()) {
            // Placeholder sound -- same GENERIC_HURT placeholder WildsTellFixture uses.
            EffectsAPI.broadcastSound(
                    level, pos.x, pos.y, pos.z,
                    SoundEvents.GENERIC_HURT, SoundSource.HOSTILE,
                    0.4f, 1.4f + RNG.nextFloat() * 0.3f);
        }
    }

    /**
     * While a player is in the Wilds, tops up the pack near them: counts the Feral mobs in presence within
     * {@link FeralRules#packMaintainRadius()} that are ahead of the player ({@link FeralPackSpawner}), and
     * if there are fewer than {@link FeralRules#packTargetCount()}, force-spawns the shortfall with
     * {@link #spawnPackMember}, clamped to the room under {@link FeralRules#maxAlivePackMembers()}. The
     * budget counts pack members only, so ambient spawns can never crowd the pack out. A no-op outside
     * the Wilds. Rides {@link #onPlayerTick} on its own cadence ({@link FeralRules#packMaintainIntervalTicks()}).
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

        Optional<FeralPresence> presence = FeralPresence.of(level);
        if (presence.isEmpty()) {
            return;
        }

        AABB maintainArea = new AABB(player.blockPosition()).inflate(FeralRules.packMaintainRadius());
        int ahead = FeralPackSpawner.countAhead(
                presence.get().mobs(), maintainArea, player.position(), player.getYRot());
        if (ahead >= FeralRules.packTargetCount()) {
            return;
        }

        int room = FeralRules.maxAlivePackMembers() - presence.get().packCount();
        if (room <= 0) {
            return;
        }

        int toSpawn = Math.min(FeralRules.packTargetCount() - ahead, room);
        for (int i = 0; i < toSpawn; i++) {
            spawnPackMember(player, level);
        }
    }

    /**
     * Where the next pack member goes: a spot in the cone ahead of the player. On the surface that is
     * the surface heightmap of a random column in the cone. Underground (the player is more than
     * {@value #UNDERGROUND_DEPTH} blocks below the surface) the heightmap would put the pack on the
     * ground above them, out of reach, so the spot is a standable floor near the player's own height
     * instead, found by sampling up to {@value #PACK_UNDERGROUND_TRIES} columns in the cone; {@code null}
     * if none is open -- nothing spawns in rock, and the next maintenance pass tries again. In a tight
     * tunnel expect fewer members than in an open cave.
     */
    private static BlockPos choosePackSpot(ServerPlayer player, ServerLevel level) {
        BlockPos playerPos = player.blockPosition();
        int surfaceY = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, playerPos).getY();
        if (playerPos.getY() >= surfaceY - UNDERGROUND_DEPTH) {
            double[] xz = samplePackColumn(player);
            return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    BlockPos.containing(xz[0], player.getY(), xz[1]));
        }

        Mob probe = EntityType.ZOMBIE.create(level);
        if (probe == null) {
            return null;
        }
        for (int i = 0; i < PACK_UNDERGROUND_TRIES; i++) {
            double[] xz = samplePackColumn(player);
            Optional<BlockPos> spot = MobSpawns.standingSpot(
                    level, probe, 1.0F, (int) Math.floor(xz[0]), playerPos.getY(), (int) Math.floor(xz[1]));
            if (spot.isPresent()) {
                return spot.get();
            }
        }
        return null;
    }

    /** A random column in the cone ahead of {@code player}: a bearing within the cone, at a random distance in range. */
    private static double[] samplePackColumn(ServerPlayer player) {
        double halfAngleRad = Math.toRadians(FeralRules.packSpawnConeHalfAngleDegrees());
        double angle = Math.toRadians(player.getYRot()) + (RNG.nextDouble() * 2.0 - 1.0) * halfAngleRad;
        double distance = FeralRules.packSpawnMinDistance()
                + RNG.nextDouble() * (FeralRules.packSpawnMaxDistance() - FeralRules.packSpawnMinDistance());
        return new double[]{player.getX() + (-Math.sin(angle)) * distance, player.getZ() + Math.cos(angle) * distance};
    }

    /**
     * Force-spawns one hostile ahead of {@code player} with {@code DISPATCHED} setup, the same real Forge
     * finalize-spawn dispatch {@code FeralCommandHandler.spawnTestHostiles} uses (unlike {@code /summon}), so
     * every other module's turn on a spawn still applies, and {@link #onEntityJoinLevel} still queues it in
     * presence. A Zombie, a deliberately simple placeholder. Its position is a column in the cone ahead of
     * the player ({@link #choosePackSpot}), skipped if it resolves inside a border; {@link #applyPackSpeedBoost}
     * gives it the speed to keep pace with a sprinting player.
     */
    private static void spawnPackMember(ServerPlayer player, ServerLevel level) {
        BlockPos ground = choosePackSpot(player, level);
        if (ground == null) {
            return;
        }
        int frontierDistance = BorderAPI.frontierDistanceAt(level, ground);
        if (frontierDistance <= 0) {
            return;
        }

        // The Feral record, with pack: true, is written before the mob joins the level (preSetup), so
        // the join queues it in presence as a pack member from its first tick. A spawn that fails
        // takes the record with the mob.
        Optional<Mob> spawned = MobSpawns.spawn(level, EntityType.ZOMBIE, ground, MobSpawns.Setup.DISPATCHED,
                mob -> FeralData.writeIfAbsent(mob, frontierDistance, true));
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
     * stopped being relevant yet" -- the pack despawn rule ({@code FeralCleanup}) is what actually ends a
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
        // A modifier owned by this key, set to a target reach: MobData keeps it across a conversion and
        // never writes the attribute's base (FRO_219).
        MobData.setFollowRange(mob, PACK_FOLLOW_RANGE_KEY, FeralRules.packFollowRange());
    }

    /**
     * A Feral mob drops nothing, however it dies. Both drop paths are closed: the re-entry kill is the
     * obvious exploit ("lure it across the border, let the sweep kill it, no risk taken"), and a combat kill
     * drops through the same vanilla death path, so gating on cause of death would need extra state to tell
     * them apart. Gated on the Feral record on the mob, so ordinary hostiles, Boss and Sentinels are
     * unaffected. FRO_147: rides {@link LivingDropsSignal}; {@code cancel()} passes straight through to the
     * wrapped event's own {@code setCanceled(true)}.
     */
    private static void onLivingDrops(LivingDropsSignal signal) {
        if (FeralData.has(signal.entity())) {
            signal.cancel();
        }
    }

    /**
     * XP counts as "free loot" too, per the same ruling {@link #onLivingDrops} follows. FRO_147:
     * rides {@link LivingExperienceDropSignal} (SAT_072) instead of a raw Forge subscription.
     */
    private static void onLivingExperienceDrop(LivingExperienceDropSignal signal) {
        if (FeralData.has(signal.entity())) {
            signal.cancel();
        }
    }

    /**
     * Wilds policy: Slime and MagmaCube (a {@code Slime}) are removed from the Wilds only --
     * where {@code frontierDistanceAt > 0} -- so Feral rules govern out there, and they spawn at the
     * ordinary vanilla rate inside the border. The caller denies the spawn with
     * {@code MobSpawnFinalizing#denySpawn()}, not a cancel, which only skips
     * {@code Mob#finalizeSpawn}'s initialization and still lets the entity spawn.
     */
    public static boolean shouldDenySpawn(Mob mob, ServerLevel level) {
        return mob instanceof Slime && BorderAPI.frontierDistanceAt(level, mob.blockPosition()) > 0;
    }

    /**
     * Density boost only, now -- see {@link #onEntityJoinLevel} for the actual tagging authority.
     * "Standby, don't crash": the spawning mob isn't a {@code MobCategory.MONSTER} hostile, or
     * {@code frontierDistance <= 0} (not in the Wilds), is a silent no-op. Never cancels the
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
     * Every real hostile outside the Frontier is Feral, unconditionally: a fresh join in the Wilds gets the
     * Feral record and the {@code feral} rung ({@link FeralTagging#tagFreshJoin}), and a join of any mob
     * that carries the record, fresh or loaded from disk, is queued in {@link FeralPresence}. Tagging is
     * here and not at spawn-finalize because {@link EntityJoinLevelSignal} fires however the mob was
     * created ({@code Level#addFreshEntity}): natural spawns, slime split children, density companions and
     * pack members, with one rule. Gated on {@link MobCategory#MONSTER} (not {@code instanceof Monster}):
     * {@code Slime}/{@code MagmaCube} are {@code MONSTER}-category hostiles that don't extend
     * {@code Monster}, though a Slime is never tagged. {@code loadedFromDisk()} excludes chunk-load
     * rehydration from tagging: a reloaded Feral mob only re-queues.
     */
    private static void onEntityJoinLevel(EntityJoinLevelSignal signal) {
        if (!(signal.entity() instanceof Mob mob) || mob.getType().getCategory() != MobCategory.MONSTER) {
            return;
        }
        //noinspection resource
        if (!(signal.level() instanceof ServerLevel level)) {
            return;
        }

        if (signal.loadedFromDisk()) {
            if (FeralData.has(mob)) {
                FeralPresence.of(level).ifPresent(presence -> presence.join(mob, false));
            }
            return;
        }
        if (mob instanceof Slime) {
            return;
        }
        if (FeralTagging.tagFreshJoin(mob, level)) {
            FeralPresence.of(level).ifPresent(presence -> presence.join(mob, true));
        }
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
        // place the cap needs enforcing. countAliveFeral() reads presence.
        int room = FeralRules.maxAliveFeral() - countAliveFeral(level);
        if (room <= 0) {
            return;
        }
        count = Math.min(count, room);

        for (int i = 0; i < count; i++) {
            spawnDensityCompanion(mob, level, spawnPos);
        }
    }

    /** How many Feral mobs the level holds now, from presence; 0 while presence isn't ready. */
    private static int countAliveFeral(ServerLevel level) {
        return FeralPresence.of(level).map(FeralPresence::count).orElse(0);
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
}
