package com.arryn.frontiermode.boss.server.rules;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
//import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Optional;

/**
 * Default, opinionated implementation of {@link BossRules} -- a safe baseline meant to be
 * replaced by real balance tuning later (Game Designer/playtest territory), same "safe baseline"
 * framing as {@code DefaultBorderRules.GROWTH_FACTOR}.
 */
public final class DefaultBossRules implements BossRules {

    // Placeholder layer -> mob type table. Layer 1 is Progression & Frontier Mechanics' own named
    // example (a rabbit); higher layers step up through tougher vanilla mobs. Not a locked curve.
    // Index 0 covers layer 0 (a level's very first border, per Border's own "0 for the initial
    // border" rule) the same as layer 1, since Tier 1 draws no meaningful distinction between them.
    private static final List<EntityType<? extends Mob>> LAYER_MOBS = List.of(
            EntityType.RABBIT,
            EntityType.ZOMBIE,
            EntityType.SPIDER,
            EntityType.SKELETON,
            EntityType.ZOMBIFIED_PIGLIN,
            EntityType.PILLAGER,
            EntityType.VINDICATOR,
            EntityType.RAVAGER
    );

    // Safe-baseline linear scale, same shape as DefaultBorderRules.GROWTH_FACTOR: +50% max health
    // per layer above the table's own baseline. Not a locked curve.
    private static final double HEALTH_SCALE_PER_LAYER = 0.5;

    // Border Pregeneration: how many candidate chunk-center positions choosePosition() scores
    // before picking a winner -- a tuning number ("safe baseline, replace later"), not settled by
    // border-pregeneration.md itself. Bumped from 5 -> 20 after a real playtest hit "all 5
    // candidates scored hazardous" on a radius-32 (3209-chunk) border and fell back to placing a
    // boss at the world floor -- 5 independent random draws just isn't enough coverage once a
    // disk gets that large, and a heightmap + block-state lookup is cheap enough (this only runs
    // once per boss, not per tick) that quadrupling it is effectively free insurance.
    private static final int POSITION_CANDIDATES = 20;

    // flatnessScore()'s own safe baseline: candidate height compared against four points this far
    // out in each cardinal direction. A tuning number, not an architecture decision.
    private static final int FLATNESS_SAMPLE_DISTANCE = 4;

    // flatnessScore(): a height delta at or above this many blocks scores 0 (steepest); below it
    // scales linearly toward 1 (flattest). A tuning number.
    private static final double FLATNESS_MAX_DELTA = 8.0;

    // FRO_072: each new border centers on wherever its own boss died (BossModule
    // .onLivingDeath -- deathLocation is the grow() center directly). A boss placed near its own
    // border's center barely moves the *next* border's center either, so successive growths were
    // nesting almost concentrically instead of the path actually "crawling" outward -- exactly
    // the reported symptom (heavy grouping, borders eventually overlapping enough to block
    // movement). Fix: bias placement toward this same border's own outer edge -- candidates only
    // sample the outer (1 - EDGE_BIAS_INNER_FRACTION) share of the radius, so wherever this boss
    // ends up (and the *next* border centers on) is reliably well away from this border's own
    // center. A tuning number, not a locked algorithm -- Game Designer/playtest territory once
    // there's a real path shape to tune against.
    private static final double EDGE_BIAS_INNER_FRACTION = 0.6;

    private final RandomSource rng;

    public DefaultBossRules() {
        this(RandomSource.create());
    }

    public DefaultBossRules(RandomSource rng) {
        this.rng = rng;
    }

    @Override
    public BlockPos choosePosition(Level level, Border border) {
        BlockPos bestSafe = null;
        double bestSafeScore = Double.NEGATIVE_INFINITY;
        BlockPos bestAny = null;
        double bestAnyScore = Double.NEGATIVE_INFINITY;

        // FRO_072: bias every candidate toward border's own outer edge -- see
        // EDGE_BIAS_INNER_FRACTION's own doc for why. innerRadius clamps to border.radius() so a
        // small border (radius 0 or 1, shouldn't happen given BorderConstants.MIN_RADIUS but
        // cheap to guard) never produces an invalid inner > outer annulus.
        int innerRadius = Math.min(
                border.radius(), (int) Math.round(border.radius() * EDGE_BIAS_INNER_FRACTION));

        for (int i = 0; i < POSITION_CANDIDATES; i++) {
            BlockPos sample = BorderAPI.MATH.randomPointInAnnulus(rng, border.center(), innerRadius, border.radius());
            BlockPos chunkCenter = chunkCenterOf(sample);
            // The whole border disk is already pregenerated by the time this runs (this method
            // is only ever called once BorderAPI.isPregenReady() has passed) -- reading real
            // height/block data here is safe, not a forced-generation side effect.
            BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, chunkCenter);

            double hazard = hazardScore(level, ground);
            double score = flatnessScore(level, ground) - hazard;

            if (score > bestAnyScore) {
                bestAnyScore = score;
                bestAny = ground;
            }
            // A disqualifying hazard (fluid, world-floor void, or no solid footing -- see
            // hazardScore's own doc) never wins a real spot, no matter how it scores -- an empty
            // column reads back as artificially flat (every sample lands on the same "nothing
            // found" sentinel height, so neighbor deltas are all zero), which is exactly what let
            // a hazardous candidate win outright before this fix caught a real playtest boss
            // landing at y=0 in open space.
            if (hazard < 1.0 && score > bestSafeScore) {
                bestSafeScore = score;
                bestSafe = ground;
            }
        }

        if (bestSafe != null) {
            return bestSafe;
        }

        // Every one of POSITION_CANDIDATES came back disqualified -- genuinely bad luck, or this
        // border's disk is thinner on safe ground than usual. POSITION_CANDIDATES is a
        // compile-time constant > 0 so bestAny is never null here; returning it (rather than
        // throwing) keeps this method's existing "always returns a position" contract, but it's
        // loud on purpose -- a boss placed from this branch is placed somewhere hazardous, and
        // that belongs in the log the moment it happens, not discovered later by someone
        // teleporting out to an empty location.
        OUT.warn("[Boss] choosePosition(): all " + POSITION_CANDIDATES + " candidates for border "
                + border.id() + " scored hazardous -- placing at " + bestAny.getX() + ", "
                + bestAny.getY() + ", " + bestAny.getZ() + " anyway (best of a bad batch).");
        return bestAny;
    }

    private static BlockPos chunkCenterOf(BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        return new BlockPos(chunkX * 16 + 8, pos.getY(), chunkZ * 16 + 8);
    }

    @Override
    public double flatnessScore(Level level, BlockPos candidate) {
        // Safe baseline: compares candidate's own resolved height against its four cardinal
        // neighbors, FLATNESS_SAMPLE_DISTANCE blocks over -- a flat plateau scores near 1.0, a
        // sharp cliff edge scores near 0.0. Not a locked algorithm -- Game Designer/playtest
        // territory once there's something to tune.
        int centerY = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, candidate).getY();

        int maxDelta = 0;
        int[][] offsets = {
                {FLATNESS_SAMPLE_DISTANCE, 0}, {-FLATNESS_SAMPLE_DISTANCE, 0},
                {0, FLATNESS_SAMPLE_DISTANCE}, {0, -FLATNESS_SAMPLE_DISTANCE}
        };
        for (int[] offset : offsets) {
            BlockPos neighbor = new BlockPos(
                    candidate.getX() + offset[0], candidate.getY(), candidate.getZ() + offset[1]);
            int neighborY = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, neighbor).getY();
            maxDelta = Math.max(maxDelta, Math.abs(neighborY - centerY));
        }

        return Math.max(0.0, 1.0 - (maxDelta / FLATNESS_MAX_DELTA));
    }

    @Override
    public double hazardScore(Level level, BlockPos candidate) {
        // Safe baseline: 1.0 (worst, disqualifying in choosePosition) for any of three real
        // hazards --
        //   - the resolved position itself sits in a fluid (open water/lava -- same
        //     heightmap-lands-in-the-fluid signal the pre-Pregeneration materialize() used to
        //     check);
        //   - the column bottoms out at/near the world floor (a genuine "nothing found" void
        //     column reads back at the heightmap's own floor sentinel);
        //   - the block directly underfoot isn't actually solid. A ravine or cave mouth
        //     breaching the surface can pull MOTION_BLOCKING_NO_LEAVES's own result down into
        //     open space well above the world floor -- the two checks above can't see that at
        //     all, which is exactly how a real playtest boss ended up placed at y=0 in a
        //     -64..320 world, nowhere near either floor threshold.
        // 0.0 otherwise. Not a locked algorithm.
        if (!level.getBlockState(candidate).getFluidState().isEmpty()) {
            return 1.0;
        }
        if (candidate.getY() <= level.getMinBuildHeight() + 1) {
            return 1.0;
        }
        BlockPos below = candidate.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
            return 1.0;
        }
        return 0.0;
    }

    @Override
    public Optional<Mob> materialize(Level level, BlockPos position, int layer) {
        // Border Pregeneration: position was already validated (flatness/hazard-scored) against
        // real, pregenerated terrain when choosePosition() chose it -- no in-place Y-resolution
        // or liquid-column check left to do here. See border-pregeneration.md's "Worked example:
        // Boss placement, revised" and "What this changes in Boss" sections.
        EntityType<? extends Mob> type = mobTypeForLayer(layer);
        Mob mob = type.create(level);
        if (mob == null) {
            OUT.warn("[Boss] materialize(): EntityType.create returned null for " + type + " at " + position);
            return Optional.empty();
        }



        mob.moveTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5, rng.nextFloat() * 360.0F, 0.0F);
        if (!(level instanceof ServerLevel serverLevel)) {
            throw new IllegalStateException("Non server level");
        }

        mob.finalizeSpawn(serverLevel, level.getCurrentDifficultyAt(position), MobSpawnType.EVENT, null, null);

        // Never set anywhere for boss mobs until now -- a plain vanilla Mob spawned via
        // finalizeSpawn is still subject to natural despawn (instant if far from every player,
        // otherwise a random roll every so often) unless persistence is explicitly required.
        // Border Pregeneration alone can now run multiple real minutes before this method is
        // even reachable, and the player then still has to travel to find the boss -- more than
        // enough idle time for that despawn roll to claim it before anyone arrives. Same fix
        // Satchel's own health-check canary already uses (SatchelHealth's
        // setPersistenceRequired() call) for the identical reason: "deliberately never killable
        // or wandering off."
        mob.setPersistenceRequired();

        applyStatScaling(mob, layer);
        tagVisibly(mob, layer);

        if (!level.addFreshEntity(mob)) {
            OUT.warn("[Boss] materialize(): addFreshEntity failed for " + type + " at " + position);
            return Optional.empty();
        }

        return Optional.of(mob);
    }

    private EntityType<? extends Mob> mobTypeForLayer(int layer) {
        int index = Math.min(Math.max(layer, 0), LAYER_MOBS.size() - 1);
        return LAYER_MOBS.get(index);
    }

    private void applyStatScaling(Mob mob, int layer) {
        double factor = 1.0 + (HEALTH_SCALE_PER_LAYER * Math.max(layer, 0));

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

    private void tagVisibly(Mob mob, int layer) {
        // "No discovery aids" (Tier 1) means findable by looking, not invisible until you already
        // know -- a lightweight visible marker, not a real discovery mechanic (that's Tier 2).
        mob.setCustomName(Component.literal("Boss (Layer " + layer + ")"));
        mob.setCustomNameVisible(true);
        mob.setGlowingTag(true);
    }

    // ------------------------------------------------------------------
    // FRO_087 (Janice): BossTellFixture tunable baseline values
    // ------------------------------------------------------------------

    @Override
    public int tellTickInterval() {
        // ~1 second at 20 TPS. Safe baseline; playtest territory.
        return 20;
    }

    @Override
    public double tellParticleCoefficient() {
        // 30% roll at maximum intensity. Safe baseline; playtest territory.
        return 0.3;
    }

    @Override
    public double tellSoundCoefficient() {
        // 5% roll at maximum intensity -- sounds rarer than particles by design.
        // Safe baseline; playtest territory.
        return 0.05;
    }
}
