package com.arryn.frontiermode.spawn;

import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.ForgeEventFactory;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * The one place mobs are made to appear: every module's "create a mob, place it, set it up, add it
 * to the level" code calls this, so the vanilla and Forge calls involved live here instead of a copy
 * per caller. Stateless and free of domain knowledge -- callers decide what, where, and how many;
 * this only does it. See sentinels.md's "MobSpawns".
 */
public final class MobSpawns {

    /** Which spawn setup {@link #spawn} runs on the new mob. */
    public enum Setup {
        /**
         * Forge's finalize-spawn event, then vanilla's setup. Every module's spawn fan-out treats
         * the mob as a spawn.
         */
        DISPATCHED,
        /** Vanilla's {@code Mob#finalizeSpawn} only: no Forge event, so no module sees the spawn. */
        VANILLA
    }

    // How far above and below a scatter candidate's center height to look for a standable floor.
    private static final int FLOOR_SEARCH_RANGE = 3;

    private MobSpawns() {
    }

    /**
     * Creates a {@code type} mob at {@code pos}, runs {@code preSetup} (a caller staging state before
     * anything else sees the mob), runs {@code setup}, runs {@code postSetup} (a caller adjusting what
     * setup produced -- setup can change max health, so scaling belongs here), and adds it to the
     * level. Empty if the type isn't a {@link Mob}, can't be created, or the level refuses it.
     */
    public static Optional<Mob> spawn(ServerLevel level, EntityType<?> type, BlockPos pos, Setup setup,
                                      Consumer<Mob> preSetup, Consumer<Mob> postSetup) {
        Entity created = type.create(level);
        if (!(created instanceof Mob mob)) {
            return Optional.empty();
        }

        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        preSetup.accept(mob);
        runSetup(mob, level, pos, setup);
        postSetup.accept(mob);

        if (!level.addFreshEntity(mob)) {
            OUT.warn("[MobSpawns] spawn(): addFreshEntity failed for " + type + " at " + pos);
            return Optional.empty();
        }
        return Optional.of(mob);
    }

    /** {@link #spawn(ServerLevel, EntityType, BlockPos, Setup, Consumer, Consumer)} with nothing to run after setup. */
    public static Optional<Mob> spawn(ServerLevel level, EntityType<?> type, BlockPos pos, Setup setup,
                                      Consumer<Mob> preSetup) {
        return spawn(level, type, pos, setup, preSetup, mob -> {
        });
    }

    /** {@link #spawn(ServerLevel, EntityType, BlockPos, Setup, Consumer, Consumer)} with nothing to stage or adjust. */
    public static Optional<Mob> spawn(ServerLevel level, EntityType<?> type, BlockPos pos, Setup setup) {
        return spawn(level, type, pos, setup, mob -> {
        });
    }

    @SuppressWarnings("deprecation")
    private static void runSetup(Mob mob, ServerLevel level, BlockPos pos, Setup setup) {
        switch (setup) {
            case DISPATCHED -> ForgeEventFactory.onFinalizeSpawn(
                    mob, level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
            case VANILLA -> mob.finalizeSpawn(
                    level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
        }
    }

    /**
     * The whole-plus-fractional roll: the whole part of {@code expected}, plus one more on a roll
     * against the remainder, clamped to {@code cap}. An expectation of 2.7 always gives 2 and has a
     * 70% chance of a 3rd, averaging 2.7 over many rolls.
     */
    public static int rollCount(double expected, int cap, RandomSource random) {
        if (expected <= 0.0 || cap <= 0) {
            return 0;
        }
        int whole = (int) Math.floor(expected);
        int count = whole + (random.nextDouble() < expected - whole ? 1 : 0);
        return Math.min(count, cap);
    }

    /**
     * A spot within {@code radius} of {@code center} where {@code mob} fits at {@code scale}, from up
     * to {@code tries} random candidates; {@code center} itself if none fits. Each candidate's floor
     * is found near {@code center}'s own height -- never the surface heightmap, which would put a
     * cave spawn's companion on the surface above it.
     */
    public static BlockPos scatter(ServerLevel level, Mob mob, BlockPos center, int radius, float scale, int tries) {
        RandomSource random = level.getRandom();
        for (int i = 0; i < tries; i++) {
            int x = center.getX() + random.nextInt(radius * 2 + 1) - radius;
            int z = center.getZ() + random.nextInt(radius * 2 + 1) - radius;
            Optional<BlockPos> floor = standingSpot(level, mob, scale, x, center.getY(), z);
            if (floor.isPresent()) {
                return floor.get();
            }
        }
        return center;
    }

    /**
     * The nearest standable spot in the column {@code (x, z)} to {@code centerY}, looking outward from
     * it (0, +1, -1, +2, -2, ...): a spot that stands on a solid face, isn't in a fluid, and where
     * {@code mob} fits at {@code scale}. Empty if the column has none within reach -- a column that is
     * solid rock at that height, say. Never the surface heightmap.
     */
    public static Optional<BlockPos> standingSpot(ServerLevel level, Mob mob, float scale, int x, int centerY, int z) {
        for (int step = 0; step <= FLOOR_SEARCH_RANGE * 2; step++) {
            int dy = (step + 1) / 2 * (step % 2 == 1 ? 1 : -1);
            BlockPos pos = new BlockPos(x, centerY + dy, z);
            if (!level.isInWorldBounds(pos)) {
                continue;
            }
            BlockPos below = pos.below();
            if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                    && level.getFluidState(pos).isEmpty()
                    && fits(level, mob, scale, pos)) {
                return Optional.of(pos);
            }
        }
        return Optional.empty();
    }

    /**
     * Vanilla's block-collision test on {@code mob}'s type dimensions scaled by {@code scale}, at
     * {@code pos}. The full-size type dimensions, not the mob's current ones: a caller asks about a
     * mob that hasn't been set up yet.
     */
    public static boolean fits(ServerLevel level, Mob mob, float scale, BlockPos pos) {
        EntityDimensions dimensions = mob.getType().getDimensions().scale(scale);
        return level.noCollision(mob, dimensions.makeBoundingBox(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
    }

    /**
     * {@code Mob#checkSpawnObstruction}: no liquid or entity in the way of {@code mob}'s box. For any
     * handler that {@code allow()}s a position check, which bypasses this test along with the light one.
     */
    public static boolean obstructionClear(ServerLevel level, Mob mob) {
        return mob.checkSpawnObstruction(level);
    }

    /**
     * {@code Mob.checkMobSpawnRules}: the block below {@code pos} is a valid spawn surface. For any
     * handler that {@code allow()}s a spawn and so bypasses vanilla's own predicate.
     */
    public static boolean surfaceAllows(ServerLevel level, EntityType<? extends Mob> type,
                                        MobSpawnType spawnType, BlockPos pos) {
        return Mob.checkMobSpawnRules(type, level, spawnType, pos, level.getRandom());
    }
}
