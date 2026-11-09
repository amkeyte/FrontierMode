package com.arryn.frontiermode.feral;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.feral.server.rules.FeralRules;
import com.arryn.frontiermode.core.MobSpawns;
import com.arryn.satchel.common.util.MobData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;

import java.util.Collection;
import java.util.Optional;

/**
 * The pack (feral.md § The pack): counting the Feral mobs ahead of a player, choosing a spot in the cone
 * pack members spawn in, sampling a column, spawning a member, and the speed and follow-range boosts that
 * let it catch a sprinter. A helper keeps no field; it takes the level, the player, presence and a random
 * source as arguments. {@link FeralPlayerTick} decides when.
 */
final class FeralPackSpawner {

    // A player this many blocks below the surface heightmap counts as underground for the pack, and
    // how many cone columns are sampled for an open floor there before giving up for this pass.
    private static final int UNDERGROUND_DEPTH = 3;
    private static final int PACK_UNDERGROUND_TRIES = 24;

    // The MobData key that owns a pack member's raised follow range: one modifier per key.
    private static final ResourceLocation PACK_FOLLOW_RANGE_KEY =
            new ResourceLocation("frontiermode", "feral_pack_follow_range");

    private FeralPackSpawner() {
    }

    /**
     * How many of {@code feral} are inside {@code area} and ahead of the player. Counting ahead only is
     * the point: a pack the player has run past would otherwise keep satisfying the quota and block a
     * fresh one in front.
     */
    static int countAhead(Collection<Mob> feral, AABB area, Vec3 playerPos, float playerYaw) {
        int count = 0;
        for (Mob mob : feral) {
            if (area.intersects(mob.getBoundingBox()) && isAheadOfPlayer(mob, playerPos, playerYaw)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Whether {@code mob} is inside the cone {@link FeralRules#packSpawnConeHalfAngleDegrees()} either
     * side of the player's own yaw -- the same cone pack members spawn in, so a mob has to be in roughly
     * the direction a pack would be placed to count as part of it, not just technically in front.
     *
     * <p>Yaw, not {@code getLookAngle()}: a pure horizontal heading, immune to the pitch-driven shrinking
     * a 3D look vector's {@code x}/{@code z} components would have when the player looks down.
     */
    static boolean isAheadOfPlayer(Mob mob, Vec3 playerPos, float playerYaw) {
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

    /**
     * Force-spawns one hostile ahead of {@code player} with {@code DISPATCHED} setup, the same real Forge
     * finalize-spawn dispatch {@code FeralCommandHandler.spawnTestHostiles} uses (unlike {@code /summon}), so
     * every other module's turn on a spawn still applies, and {@link FeralTagging#onHostileJoining} still
     * queues it in presence. A Zombie, a deliberately simple placeholder. Its position is a column in the
     * cone ahead of the player ({@link #choosePackSpot}), skipped if it resolves inside a border;
     * {@link #applyPackSpeedBoost} gives it the speed to keep pace with a sprinting player.
     */
    static void spawnMember(Player player, ServerLevel level, RandomSource random) {
        BlockPos ground = choosePackSpot(player, level, random);
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
     * Where the next pack member goes: a spot in the cone ahead of the player. On the surface that is
     * the surface heightmap of a random column in the cone. Underground (the player is more than
     * {@value #UNDERGROUND_DEPTH} blocks below the surface) the heightmap would put the pack on the
     * ground above them, out of reach, so the spot is a standable floor near the player's own height
     * instead, found by sampling up to {@value #PACK_UNDERGROUND_TRIES} columns in the cone; {@code null}
     * if none is open -- nothing spawns in rock, and the next maintenance pass tries again. In a tight
     * tunnel expect fewer members than in an open cave.
     */
    private static BlockPos choosePackSpot(Player player, Level level, RandomSource random) {
        BlockPos playerPos = player.blockPosition();
        int surfaceY = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, playerPos).getY();
        if (playerPos.getY() >= surfaceY - UNDERGROUND_DEPTH) {
            double[] xz = samplePackColumn(player, random);
            return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    BlockPos.containing(xz[0], player.getY(), xz[1]));
        }

        Mob probe = EntityType.ZOMBIE.create(level);
        if (probe == null) {
            return null;
        }
        for (int i = 0; i < PACK_UNDERGROUND_TRIES; i++) {
            double[] xz = samplePackColumn(player, random);
            Optional<BlockPos> spot = MobSpawns.standingSpot(
                    level, probe, 1.0F, (int) Math.floor(xz[0]), playerPos.getY(), (int) Math.floor(xz[1]));
            if (spot.isPresent()) {
                return spot.get();
            }
        }
        return null;
    }

    /** A random column in the cone ahead of {@code player}: a bearing within the cone, at a random distance in range. */
    private static double[] samplePackColumn(Player player, RandomSource random) {
        double halfAngleRad = Math.toRadians(FeralRules.packSpawnConeHalfAngleDegrees());
        double angle = Math.toRadians(player.getYRot()) + (random.nextDouble() * 2.0 - 1.0) * halfAngleRad;
        double distance = FeralRules.packSpawnMinDistance()
                + random.nextDouble() * (FeralRules.packSpawnMaxDistance() - FeralRules.packSpawnMinDistance());
        return new double[]{player.getX() + (-Math.sin(angle)) * distance, player.getZ() + Math.cos(angle) * distance};
    }

    /**
     * See {@link #spawnMember}'s own doc for why this exists -- a plain zombie can't sustain
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
     * Widens a pack member's aggro range. Vanilla's {@code Attributes.FOLLOW_RANGE} default is
     * shorter than {@link FeralRules#packSpawnMaxDistance()}, so a member spawned at the far edge of
     * the cone could land outside its own target-acquisition range and sit there until the player
     * closed most of the gap. Raised to {@link FeralRules#packFollowRange()} so a member notices and
     * pursues the player the moment it spawns, anywhere in the cone.
     */
    private static void applyPackFollowRange(Mob mob) {
        // A modifier owned by this key, set to a target reach: MobData keeps it across a conversion and
        // never writes the attribute's base.
        MobData.setFollowRange(mob, PACK_FOLLOW_RANGE_KEY, FeralRules.packFollowRange());
    }
}
