package com.arryn.frontiermode.boss.server.rules;

import com.arryn.frontiermode.FrontierMode;
import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.boss.BossAPI;
import com.arryn.frontiermode.boss.BossModule;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.frontiermode.difficulty.DifficultyAPI;
import com.arryn.frontiermode.difficulty.common.LadderMath;
import com.arryn.frontiermode.spawn.MobSpawns;
import com.arryn.satchel.common.tunable.CellType;
import com.arryn.satchel.common.tunable.Tunable;
import com.arryn.satchel.common.tunable.TuningTable;
import com.arryn.satchel.common.util.StackData;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
//import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Optional;

/**
 * Default, opinionated implementation of {@link BossRules} -- a safe baseline meant to be
 * replaced by real balance tuning later (Game Designer/playtest territory).
 */
public final class DefaultBossRules implements BossRules {

    // Border Pregeneration: how many candidate chunk-center positions choosePosition() scores
    // before picking a winner -- a tuning number ("safe baseline, replace later"), not settled by
    // border-pregeneration.md itself. Bumped from 5 -> 20 after a real playtest hit "all 5
    // candidates scored hazardous" on a radius-32 (3209-chunk) border and fell back to placing a
    // boss at the world floor -- 5 independent random draws just isn't enough coverage once a
    // disk gets that large, and a heightmap + block-state lookup is cheap enough (this only runs
    // once per boss, not per tick) that quadrupling it is effectively free insurance.
    // The boss mob's player-facing name, and the text LogFilters matches in vanilla's death log line.
    // See FRO_182.
    public static final String BOSS_NAME = "Amethyst Keeper";

    private static final int POSITION_CANDIDATES = 20;

    // FRO_163: Y used when every candidate scores hazardous -- see choosePosition's fallback branch.
    private static final int FALLBACK_Y = 64;

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

    // FRO_197: HurtByTargetGoal's own alert conditions -- vanilla's combat rules, no sight test.
    private static final TargetingConditions SENTINEL_ALERT_TARGETING =
            TargetingConditions.forCombat().ignoreLineOfSight().ignoreInvisibilityTesting();

    private final RandomSource rng;

    public DefaultBossRules() {
        this(RandomSource.create());
    }

    public DefaultBossRules(RandomSource rng) {
        this.rng = rng;
    }

    @Override
    /*
     * [Arryn] Review this. It may need reworked for NPE and algorithm robustness.
     */
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
            // FRO_156: snapping to a chunk center can move a candidate up to ~11 blocks, which on a
            // tiny border (an admin-made one can be a chunk or less across) throws it outside the
            // border entirely. Keep the unsnapped sample -- already inside the annulus -- when the
            // snap would leave the border.
            if (BorderAPI.MATH.distanceOutside(chunkCenter, border.center(), border.radius()) > 0) {
                chunkCenter = sample;
            }
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
        // FRO_163: the best-scoring hazardous column is often the void sentinel (y = world floor),
        // which reads as perfectly flat -- placing there dropped a boss into the void. Keep the
        // chosen X/Z but pin Y to a fixed height instead: it may end up in the ground or floating
        // over water, but it is never the void, and it is loud in the log so a real occurrence
        // gets looked at rather than breaking the game loop.
        BlockPos fallback = new BlockPos(bestAny.getX(), FALLBACK_Y, bestAny.getZ());
        OUT.warn("[Boss] choosePosition(): all " + POSITION_CANDIDATES + " candidates for border "
                + Ids.shortId(border.id()) + " scored hazardous -- placing at " + fallback.getX() + ", "
                + fallback.getY() + ", " + fallback.getZ() + " anyway (fixed-height fallback; best"
                + " candidate read y=" + bestAny.getY() + ").");
        return fallback;
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

    // FRO_120 (Cairn): safe-baseline flatness floor for footprintSuitable() below -- a tuning
    // number, not part of the shared BossRules contract (flatnessScore's own continuous score is
    // the contract; where to draw a boolean pass/fail line on it is this implementation's call,
    // same as every other threshold in this class).
    private static final double FOOTPRINT_MIN_FLATNESS = 0.5;

    @Override
    public boolean footprintSuitable(Level level, BlockPos center, int size, int clearance) {
        int half = size / 2;
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                BlockPos column = center.offset(dx, 0, dz);
                BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);

                if (hazardScore(level, ground) >= 1.0) {
                    return false;
                }
                if (flatnessScore(level, ground) < FOOTPRINT_MIN_FLATNESS) {
                    return false;
                }
                for (int dy = 1; dy <= clearance; dy++) {
                    if (!level.getBlockState(ground.above(dy)).isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // ── Keeper stats (boss.md § Keeper stats) ───────────────────────────────

    // Path positions 1-20 are hand-authored per stat; past 20 the body is rolled and the stats follow
    // a formula.
    private static final int AUTHORED_THROUGH = 20;

    /** The rows a fresh {@code keeper-bodies} table starts with, and what the seed formulas read. */
    private record BodyDefault(String name, EntityType<?> type, float referenceHit, float sizePast20,
                               String sizeGrowth, float poolWeight) {
    }

    // referenceHit: one ordinary hit on Normal (0: an ordinary Rabbit deals none). The Killer Bunny is a
    // Rabbit variant, hence a body named apart from its entity type. The pool past 20 is a placeholder:
    // the authored bodies plus the Killer Bunny, at equal weight (the Game Designer's to design).
    private static final List<BodyDefault> BODY_DEFAULTS = List.of(
            new BodyDefault("rabbit", EntityType.RABBIT, 0f, 1f, "", 1f),
            new BodyDefault("killer_bunny", EntityType.RABBIT, 8f, 1f, "", 1f),
            new BodyDefault("spider", EntityType.SPIDER, 2f, 1.5f, "", 1f),
            // The one body that keeps growing past 20 ("bigger and bigger"): 0.1 per path position.
            new BodyDefault("zombie", EntityType.ZOMBIE, 3f, 2f, "0.1", 1f),
            new BodyDefault("skeleton", EntityType.SKELETON, 4f, 2.3f, "", 1f),
            new BodyDefault("zombified_piglin", EntityType.ZOMBIFIED_PIGLIN, 8f, 2f, "", 1f),
            new BodyDefault("pillager", EntityType.PILLAGER, 4f, 2f, "", 1f),
            new BodyDefault("vindicator", EntityType.VINDICATOR, 13f, 1.7f, "", 1f),
            new BodyDefault("evoker", EntityType.EVOKER, 6f, 1f, "", 1f));

    // The authored ladder, by path position 1-20: Rabbit, Spider x3, Zombie x3, Skeleton x3,
    // Zombified Piglin x3, Pillager x3, Vindicator x3, Evoker.
    private static final List<String> AUTHORED_LADDER = List.of(
            "rabbit",
            "spider", "spider", "spider",
            "zombie", "zombie", "zombie",
            "skeleton", "skeleton", "skeleton",
            "zombified_piglin", "zombified_piglin", "zombified_piglin",
            "pillager", "pillager", "pillager",
            "vindicator", "vindicator", "vindicator",
            "evoker");

    // The owner's authored sizes (FRO_208, 2026-10-01). Rabbit (1) and Evoker (20) are the named
    // exceptions to "always larger than its kind". The seed only: the value column is for tuning.
    private static final float[] AUTHORED_SIZE = {
            1f, 1.2f, 1.3f, 1.5f, 2f, 1.5f, 2f, 1.2f, 1.5f, 2.3f,
            1.2f, 1.5f, 2f, 1.5f, 2f, 2f, 1.5f, 1.6f, 1.7f, 1f};

    private static final TuningTable KEEPER_BODIES = keeperBodiesTable();

    private static final TuningTable KEEPER_BODY = TuningTable.builder(tableId("keeper-body"))
            .intKeys("pathPosition", 1, AUTHORED_THROUGH)
            .seededColumn("value", "seed", CellType.STRING, p -> AUTHORED_LADDER.get(p - 1))
            .build();

    private static final TuningTable KEEPER_HEALTH = TuningTable.builder(tableId("keeper-health"))
            .intKeys("pathPosition", 1, AUTHORED_THROUGH)
            .seededColumn("value", "seed", CellType.FLOAT, p -> authoredHealthSeed(p))
            .build();

    private static final TuningTable KEEPER_DAMAGE = TuningTable.builder(tableId("keeper-damage"))
            .intKeys("pathPosition", 1, AUTHORED_THROUGH)
            .seededColumn("value", "seed", CellType.FLOAT, p -> authoredDamageSeed(p))
            .build();

    private static final TuningTable KEEPER_SIZE = TuningTable.builder(tableId("keeper-size"))
            .intKeys("pathPosition", 1, AUTHORED_THROUGH)
            .seededColumn("value", "seed", CellType.FLOAT, p -> AUTHORED_SIZE[p - 1])
            .build();

    private static ResourceLocation tableId(String name) {
        return new ResourceLocation(FrontierMode.MODID, name);
    }

    private static TuningTable keeperBodiesTable() {
        TuningTable.Builder builder = TuningTable.builder(tableId("keeper-bodies"))
                .freeKeys("body", CellType.STRING)
                .column("entityType", CellType.ENTITY_TYPE)
                .column("referenceHit", CellType.FLOAT)
                .column("sizePast20", CellType.FLOAT)
                .optionalColumn("sizeGrowth", CellType.FLOAT)
                .optionalColumn("poolWeight", CellType.FLOAT);
        for (BodyDefault d : BODY_DEFAULTS) {
            builder.defaultRow(d.name(), EntityType.getKey(d.type()).toString(), Float.toString(d.referenceHit()),
                    Float.toString(d.sizePast20()), d.sizeGrowth(), Float.toString(d.poolWeight()));
        }
        return builder.build();
    }

    private static BodyDefault authoredBody(int pathPosition) {
        String name = AUTHORED_LADDER.get(pathPosition - 1);
        return BODY_DEFAULTS.stream().filter(d -> d.name().equals(name)).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static double vanillaMaxHealth(EntityType<?> type) {
        return DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type)
                .getBaseValue(Attributes.MAX_HEALTH);
    }

    // The seeds are the formula (boss.md § Keeper stats): the body's vanilla value x (1 + growth x (p - 1)).
    private static double authoredHealthSeed(int pathPosition) {
        return vanillaMaxHealth(authoredBody(pathPosition).type())
                * LadderMath.multiplier(DifficultyAPI.keeperHealthGrowthTotal(), pathPosition);
    }

    private static double authoredDamageSeed(int pathPosition) {
        return authoredBody(pathPosition).referenceHit()
                * LadderMath.multiplier(DifficultyAPI.keeperDamageGrowthTotal(), pathPosition);
    }

    /** Authored body through path position 20; past it, a body rolled by pool weight. */
    @Override
    public String chooseBody(int pathPosition) {
        int p = Math.max(pathPosition, 1);
        if (p <= AUTHORED_THROUGH) {
            return KEEPER_BODY.row(p).getString("value");
        }
        double total = 0.0;
        for (TuningTable.Row row : KEEPER_BODIES.rows()) {
            if (row.has("poolWeight")) {
                total += Math.max(0f, row.getFloat("poolWeight"));
            }
        }
        if (total <= 0.0) {
            throw new IllegalStateException("keeper-bodies has no row with a poolWeight above 0: nothing to roll past path position "
                    + AUTHORED_THROUGH);
        }
        double roll = rng.nextDouble() * total;
        for (TuningTable.Row row : KEEPER_BODIES.rows()) {
            if (!row.has("poolWeight")) {
                continue;
            }
            roll -= Math.max(0f, row.getFloat("poolWeight"));
            if (roll < 0.0) {
                return row.key();
            }
        }
        // Float rounding only: the last weighted row.
        return KEEPER_BODIES.rows().stream().filter(r -> r.has("poolWeight")).reduce((a, b) -> b).orElseThrow().key();
    }

    /**
     * The size table through path position 20; past it the body's size, growing by its optional
     * {@code sizeGrowth} per path position past 20 (the Zombie's, uncapped).
     */
    @Override
    public float keeperScale(BossRecord record) {
        int p = Math.max(BossAPI.pathPositionOf(record), 1);
        if (p <= AUTHORED_THROUGH) {
            return KEEPER_SIZE.row(p).getFloat("value");
        }
        if (record.body().isEmpty()) {
            return 1.0F;
        }
        TuningTable.Row body = KEEPER_BODIES.row(record.body());
        float size = body.getFloat("sizePast20");
        if (body.has("sizeGrowth")) {
            size += body.getFloat("sizeGrowth") * (p - AUTHORED_THROUGH);
        }
        return size;
    }

    @Override
    public Optional<Mob> materialize(Level level, BlockPos position, BossRecord record) {
        // Border Pregeneration: position was already validated (flatness/hazard-scored) against
        // real, pregenerated terrain when choosePosition() chose it -- no in-place Y-resolution
        // or liquid-column check left to do here. See border-pregeneration.md's "Worked example:
        // Boss placement, revised" and "What this changes in Boss" sections.
        int pathPosition = Math.max(BossAPI.pathPositionOf(record), 1);
        // A record saved before the body field existed has none: choose it now (not saved back).
        String bodyName = record.body().isEmpty() ? chooseBody(pathPosition) : record.body();
        TuningTable.Row body = KEEPER_BODIES.row(bodyName);
        EntityType<?> type = body.getEntityType("entityType");
        if (!(level instanceof ServerLevel serverLevel)) {
            throw new IllegalStateException("Non server level");
        }

        // VANILLA setup: it is what arms a boss (a Skeleton's bow, a Pillager's crossbow). The boss's
        // own steps run after it, because setup can change max health (a leader zombie multiplies it
        // without healing), so scaling first would leave that boss at a fraction of its health.
        Optional<Mob> spawned = MobSpawns.spawn(serverLevel, type, position, MobSpawns.Setup.VANILLA,
                mob -> {
                }, mob -> {
                    // A plain vanilla Mob is still subject to natural despawn unless persistence is
                    // required, and Border Pregeneration can run minutes before a player arrives.
                    mob.setPersistenceRequired();
                    // Vanilla setup rolls a Rabbit's variant, so the Killer Bunny is set after it.
                    if (bodyName.equals("killer_bunny") && mob instanceof Rabbit rabbit) {
                        rabbit.setVariant(Rabbit.Variant.EVIL);
                    }
                    applyKeeperRung(mob, pathPosition, body);
                    makeFireImmune(mob);
                    // Fire immunity stops the damage; the sun ward stops the ignition itself, so a
                    // Zombie or Skeleton boss doesn't visibly burn in daylight.
                    equipSunWard(mob);
                    name(mob);
                });
        if (spawned.isEmpty()) {
            OUT.warn("[Boss] materialize(): spawn failed for " + type + " at " + position);
        }
        return spawned;
    }

    /**
     * Puts the Keeper on the Keeper rung of the stat ladder, before it joins the level. Health: the
     * table's value divided by the body's vanilla maximum, so the Keeper has exactly the table's
     * health. Damage: the table's value divided by the body's reference hit (an ordinary Rabbit has
     * none, so no multiplier). Past path position 20 both are the formula, 1 + growth x (p - 1). Every
     * kind of damage scales when it lands, so one number covers swords, arrows, bolts and fangs.
     */
    private void applyKeeperRung(Mob mob, int pathPosition, TuningTable.Row body) {
        float referenceHit = body.getFloat("referenceHit");
        double healthMultiplier;
        double damageMultiplier = 1.0;
        if (pathPosition <= AUTHORED_THROUGH) {
            healthMultiplier = KEEPER_HEALTH.row(pathPosition).getFloat("value")
                    / mob.getAttributeBaseValue(Attributes.MAX_HEALTH);
            if (referenceHit > 0f) {
                damageMultiplier = KEEPER_DAMAGE.row(pathPosition).getFloat("value") / referenceHit;
            }
        } else {
            healthMultiplier = LadderMath.multiplier(DifficultyAPI.keeperHealthGrowthTotal(), pathPosition);
            if (referenceHit > 0f) {
                damageMultiplier = LadderMath.multiplier(DifficultyAPI.keeperDamageGrowthTotal(), pathPosition);
            }
        }
        DifficultyAPI.applyKeeperRung(mob, healthMultiplier, damageMultiplier);
    }

    // Boss invariant (boss.md Scope): no fire damage for any boss. A permanent Fire Resistance
    // effect covers everything in #minecraft:is_fire (burning, in-fire, lava, magma) with no
    // damage-event subscription. Infinite duration, no particles/icon; effects persist in the
    // entity's NBT, so this survives unload/reload without re-application. The burning visual may
    // still show in daylight -- Fire Resistance blocks the damage, not the flame render.
    private void makeFireImmune(Mob mob) {
        mob.addEffect(new MobEffectInstance(
                MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
    }

    // Players see the name in vanilla death messages ("slain by Amethyst Keeper"), never as a tag.
    private void name(Mob mob) {
        mob.setCustomName(Component.literal(BOSS_NAME));
        // The tag's visibility is the client-side debug view's to turn on (AdminViewClient); the
        // name itself stays set.
        mob.setCustomNameVisible(false);
    }

    // FRO_160: non-player-death respawn tuning -- see BossRules for the meaning of each.
    @Override
    public int respawnDelayTicks() {
        return new Tunable("respawnDelayTicks", 1, 6000, 100).intValue();
    }

    @Override
    public int maxConsecutiveNonPlayerDeaths() {
        return new Tunable("maxConsecutiveNonPlayerDeaths", 1, 100, 5).intValue();
    }

    // ------------------------------------------------------------------
    // FRO_087 (Janice): BossTellFixture tunable baseline values
    // ------------------------------------------------------------------

    @Override
    public int tellTickInterval() {
        // ~1 second at 20 TPS. Safe baseline; playtest territory. FRO_142: Tunable-backed (SAT_074).
        return new Tunable("tellTickInterval", 1, 200, 20).intValue();
    }

    @Override
    public double tellParticleCoefficient() {
        // Was temporarily maxed to 1.0 as a location-debug aid alongside PARTICLE_COUNT's bump
        // in BossTellFixture (player call, 2026-09-06: "make it a ridiculous amount to see where
        // they're actually showing up") -- guaranteed a particle fire on every tick-interval
        // where intensity > 0, so a walk toward/around a boss revealed the tell radius without
        // waiting on RNG. Scatter shape confirmed good ("that's about where I want it"), so
        // dialed back to the original 70% tuned baseline -- roll is intensity x this coefficient,
        // peaking at 1.0 right at the boss and fading to 0.0 at the border edge. Playtest
        // territory, tune further if it now reads too sparse without the debug multiplier.
        // FRO_142: Tunable-backed (SAT_074) -- [0,1] bound, this coefficient is a probability roll.
        return new Tunable("tellParticleCoefficient", 0f, 1f, 0.7f).floatValue();
    }

    @Override
    public double tellSoundCoefficient() {
        // 5% roll at maximum intensity -- sounds rarer than particles by design.
        // Safe baseline; playtest territory. FRO_142: Tunable-backed (SAT_074).
        return new Tunable("tellSoundCoefficient", 0f, 1f, 0.05f).floatValue();
    }

    @Override
    public int tellSoundCooldownTicks() {
        // 30 seconds at 20 TPS -- see BossRules#tellSoundCooldownTicks's own doc for why this
        // exists (round-robin, 4x ~30s clips, project owner request 2026-09-08). Safe baseline;
        // playtest territory, and worth revisiting per-clip if the 4 supplied sounds aren't all
        // the same length. FRO_142: Tunable-backed (SAT_074).
        return new Tunable("tellSoundCooldownTicks", 0, 6000, 600).intValue();
    }

    // ------------------------------------------------------------------
    // RM_FRO_029 (Gloria): BossSentinelsFixture tunable baseline values
    // ------------------------------------------------------------------

    // The sentinel numbers are Tunable-backed, constructed inside their own accessor methods rather
    // than cached as fields.

    @Override
    public double sentinelPlacementCoefficient() {
        // 2026-09-07 playtest, fourth and (per Arryn) final bump for this round: 0.15 -> 0.4 -> 0.6.
        // Max 60% chance per spawn attempt at intensity 1.0 (right on top of the boss), decaying with
        // placementIntensity same as always. Still a roll, not a guarantee, so ordinary vanilla spawns
        // still happen near the boss too. Called "good for now" after this value -- further tuning is
        // a fresh playtest decision, not an assumed follow-up. [0,1] bound -- a probability roll.
        return new Tunable("sentinelPlacementCoefficient", 0f, 1f, 0.6f).floatValue();
    }

    @Override
    public double sentinelPlacementRadiusFraction() {
        // 2026-09-07 playtest, revised twice same session: started at the full border radius (read as
        // "clustered right on the boss, nothing beyond ~30 blocks" on a large border -- the LINEAR
        // ramp was mathematically correct but imperceptibly thin across the whole disc); briefly tried
        // FLAT/uniform across the whole border (reverted -- "doesn't act as a tell at all," no gradient
        // to notice at all). Settled: same LINEAR formula, same direction (climbs toward the boss), but
        // normalized against half the border's own radius instead of the whole thing -- a real,
        // noticeable ramp concentrated in a smaller ring around the boss. Safe baseline; playtest
        // territory, same as sentinelPlacementCoefficient above. [0,1] bound -- a fraction of radius.
        return new Tunable("sentinelPlacementRadiusFraction", 0f, 1f, 0.5f).floatValue();
    }

    @Override
    public void healSentinel(Mob mob) {
        mob.setHealth(mob.getMaxHealth());
    }

    @Override
    public int sentinelDefeatKillsPerTick() {
        return new Tunable("sentinelDefeatKillsPerTick", 1, 100, 5).intValue();
    }

    @Override
    public double keeperPlayerShare() {
        return new Tunable("keeperPlayerShare", 0f, 1f, 0.5f).floatValue();
    }

    @Override
    public int bossBarLingerTicks() {
        return new Tunable("bossBarLingerTicks", 1, 72000, 600).intValue();
    }

    @Override
    public void killSentinel(Mob mob, ServerPlayer credited) {
        if (credited != null) {
            mob.setLastHurtByPlayer(credited);
        }
        mob.kill();
    }

    // FRO_197: sentinels defend the boss (sentinels.md § Defending the boss).
    @Override
    public int sentinelAlertRadius() {
        return new Tunable("sentinelAlertRadius", 4, 64, 24).intValue();
    }

    @Override
    public int sentinelAlertMemoryTicks() {
        return new Tunable("sentinelAlertMemoryTicks", 20, 1200, 200).intValue();
    }

    @Override
    public boolean alertSentinel(Mob sentinel, ServerPlayer player) {
        // Allies come first: a Sentinel already fighting an ally is left to it, so no alert window is
        // recorded; once the ally is gone or out of sight the next hit on the boss alerts it as usual.
        if (sentinel.getTarget() != null && DifficultyAPI.isAlly(sentinel.getTarget())) {
            return false;
        }
        if (!SENTINEL_ALERT_TARGETING.test(sentinel, player)) {
            return false;
        }
        sentinel.setTarget(player);
        return true;
    }

    @Override
    public void releaseSentinel(Mob sentinel, ServerPlayer player) {
        if (sentinel.getTarget() != player) {
            return;
        }
        var followRange = sentinel.getAttribute(Attributes.FOLLOW_RANGE);
        double range = followRange == null ? 0.0 : followRange.getValue();
        // Same level first: after a portal, distance and sight would be measured against the
        // player's coordinates in the other dimension and can come out true.
        boolean canSee = player.level() == sentinel.level()
                && sentinel.distanceToSqr(player) <= range * range
                && sentinel.getSensing().hasLineOfSight(player);
        if (!canSee) {
            sentinel.setTarget(null);
        }
    }

    @Override
    public void dismiss(Mob mob) {
        // Entity event 60: vanilla's death puff.
        mob.level().broadcastEntityEvent(mob, (byte) 60);
        mob.discard();
    }

    @Override
    public boolean canBeSentinel(EntityType<?> type) {
        return type.getCategory() == MobCategory.MONSTER
                && type != EntityType.PHANTOM
                && type != EntityType.SLIME
                && type != EntityType.MAGMA_CUBE;
    }

    // Sentinel size and adds: starting values for playtest (sentinels.md, "Size and adds").
    @Override
    public double sentinelTargetScale(int keeperPathPosition) {
        // The Sentinels' own size curve (the per-step growth bosses used before their size became a
        // table), kept so Sentinel sizes don't follow a table built for Keepers.
        double step = new Tunable("sentinelScaleStep", 0f, 1f, 0.1f).floatValue();
        double ratio = new Tunable("sentinelScaleRatio", 0f, 1f, 0.5f).floatValue();
        return 1.0 + Math.max(keeperPathPosition, 0) * step * ratio;
    }

    @Override
    public double sentinelMaxScale() {
        return new Tunable("sentinelMaxScale", 1f, 5f, 1.5f).floatValue();
    }

    @Override
    public double sentinelFitStep() {
        return new Tunable("sentinelFitStep", 0.01f, 1f, 0.1f).floatValue();
    }

    @Override
    public double sentinelAddCost() {
        return new Tunable("sentinelAddCost", 0.05f, 10f, 0.5f).floatValue();
    }

    @Override
    public int sentinelMaxAddsPerSpawn() {
        return new Tunable("sentinelMaxAddsPerSpawn", 0, 20, 3).intValue();
    }

    @Override
    public int sentinelAddScatterRadius() {
        return new Tunable("sentinelAddScatterRadius", 1, 32, 4).intValue();
    }

    // The trail's three numbers. Tunables are server-only, so BOSS_JIG's tick copies them into
    // SentinelPublicFixture, the one channel from server to client for sentinels.
    @Override
    public int trailIntervalTicks() {
        return new Tunable("trailIntervalTicks", 1, 200, 4).intValue();
    }

    @Override
    public int trailLifetimeTicks() {
        return new Tunable("trailLifetimeTicks", 1, 600, 30).intValue();
    }

    @Override
    public int trailRadius() {
        return new Tunable("trailRadius", 1, 128, 24).intValue();
    }

    @Override
    public void equipSunWard(Mob mob) {
        ItemStack ward = new ItemStack(BossModule.SUN_WARD.get());
        // Mob#canReplaceCurrentItem refuses to replace an item with Curse of Binding, so a
        // sentinel that picks up a real helmet keeps the ward.
        StackData.enchant(ward, Enchantments.BINDING_CURSE, 1);
        mob.setItemSlot(EquipmentSlot.HEAD, ward);
        mob.setDropChance(EquipmentSlot.HEAD, 0.0F);
    }
}
