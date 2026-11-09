package com.arryn.frontiermode.keeper;

import com.arryn.frontiermode.FrontierMode;
import com.arryn.frontiermode.difficulty.DifficultyAPI;
import com.arryn.frontiermode.difficulty.common.LadderMath;
import com.arryn.frontiermode.keeper.common.fixture.KeeperRecord;
import com.arryn.satchel.common.tunable.CellType;
import com.arryn.satchel.common.tunable.TuningTable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;

import java.util.List;

/**
 * What a Keeper is made of (wiki/frontiermode/architecture/keeper.md § Keeper stats): the five tuning tables, the
 * authored bodies and their ladder, the pool and egg roll past path position 20, the size, and the seeds that fill
 * the tables. The tables are declared when this class loads, so they appear on the first use of a Keeper's stats
 * ({@code /satchel tables list}), not at level boot.
 */
public final class KeeperBodies {

    // Path positions 1-20 are hand-authored per stat; past 20 the body is rolled and the stats follow
    // a formula.
    static final int AUTHORED_THROUGH = 20;

    /** The rows a fresh {@code keeper-bodies} table starts with, and what the seed formulas read. */
    private record BodyDefault(String name, EntityType<?> type, float referenceHit, float sizePast20,
                               String sizeGrowth, String poolWeight, String eggChance) {
    }

    // referenceHit: one ordinary hit on Normal (0: an ordinary Rabbit deals none). The Killer Bunny is a
    // Rabbit variant, hence a body named apart from its entity type. The pool past 20 is a placeholder:
    // the authored bodies at equal weight (the Game Designer's to design). The Killer Bunny is an easter
    // egg: no pool weight, an eggChance of its own.
    private static final List<BodyDefault> BODY_DEFAULTS = List.of(
            new BodyDefault("rabbit", EntityType.RABBIT, 0f, 1f, "", "1.0", ""),
            new BodyDefault("killer_bunny", EntityType.RABBIT, 8f, 1f, "", "", "0.05"),
            new BodyDefault("spider", EntityType.SPIDER, 2f, 1.5f, "", "1.0", ""),
            // The one body that keeps growing past 20 ("bigger and bigger"): 0.1 per path position.
            new BodyDefault("zombie", EntityType.ZOMBIE, 3f, 2f, "0.1", "1.0", ""),
            new BodyDefault("skeleton", EntityType.SKELETON, 4f, 2.3f, "", "1.0", ""),
            new BodyDefault("zombified_piglin", EntityType.ZOMBIFIED_PIGLIN, 8f, 2f, "", "1.0", ""),
            new BodyDefault("pillager", EntityType.PILLAGER, 4f, 2f, "", "1.0", ""),
            new BodyDefault("vindicator", EntityType.VINDICATOR, 13f, 1.7f, "", "1.0", ""),
            new BodyDefault("evoker", EntityType.EVOKER, 6f, 1f, "", "1.0", ""));

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

    // The owner's authored sizes. Rabbit (1) and Evoker (20) are the named
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

    private static final RandomSource RNG = RandomSource.create();

    private KeeperBodies() {
    }

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
                .optionalColumn("poolWeight", CellType.FLOAT)
                .optionalColumn("eggChance", CellType.FLOAT);
        for (BodyDefault d : BODY_DEFAULTS) {
            builder.defaultRow(d.name(), EntityType.getKey(d.type()).toString(), Float.toString(d.referenceHit()),
                    Float.toString(d.sizePast20()), d.sizeGrowth(), d.poolWeight(), d.eggChance());
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

    /** The {@code keeper-bodies} row for {@code bodyName}. */
    static TuningTable.Row body(String bodyName) {
        return KEEPER_BODIES.row(bodyName);
    }

    /** The {@code keeper-health} table's value for an authored path position (1 through {@link #AUTHORED_THROUGH}). */
    static float authoredHealth(int pathPosition) {
        return KEEPER_HEALTH.row(pathPosition).getFloat("value");
    }

    /** The {@code keeper-damage} table's value for an authored path position (1 through {@link #AUTHORED_THROUGH}). */
    static float authoredDamage(int pathPosition) {
        return KEEPER_DAMAGE.row(pathPosition).getFloat("value");
    }

    /**
     * The name of the body a boss gets at path position {@code pathPosition}, from the {@code keeper-bodies} table:
     * the authored body through path position 20, a body rolled by pool weight past it. Called once, at record
     * creation. Past 20, in two steps (boss.md § Keeper bodies): each body with an {@code eggChance} gets one roll,
     * in table order, and the first success is the body (the Killer Bunny, 5%); otherwise a body is picked by
     * {@code poolWeight} among the rows with no {@code eggChance}. A row with an {@code eggChance} is never in the
     * weighted pool, so a leftover pool weight in a live config can't give it a second chance.
     */
    static String chooseBody(int pathPosition) {
        int p = Math.max(pathPosition, 1);
        if (p <= AUTHORED_THROUGH) {
            return KEEPER_BODY.row(p).getString("value");
        }
        for (TuningTable.Row row : KEEPER_BODIES.rows()) {
            if (row.has("eggChance") && RNG.nextDouble() < row.getFloat("eggChance")) {
                return row.key();
            }
        }
        double total = 0.0;
        for (TuningTable.Row row : KEEPER_BODIES.rows()) {
            if (!row.has("eggChance") && row.has("poolWeight")) {
                total += Math.max(0f, row.getFloat("poolWeight"));
            }
        }
        if (total <= 0.0) {
            throw new IllegalStateException("keeper-bodies has no row with a poolWeight above 0: nothing to roll past path position "
                    + AUTHORED_THROUGH);
        }
        double roll = RNG.nextDouble() * total;
        for (TuningTable.Row row : KEEPER_BODIES.rows()) {
            if (row.has("eggChance") || !row.has("poolWeight")) {
                continue;
            }
            roll -= Math.max(0f, row.getFloat("poolWeight"));
            if (roll < 0.0) {
                return row.key();
            }
        }
        // Float rounding only: the last weighted row.
        return KEEPER_BODIES.rows().stream().filter(r -> !r.has("eggChance") && r.has("poolWeight"))
                .reduce((a, b) -> b).orElseThrow().key();
    }

    /**
     * The Keeper's size multiplier -- the {@code keeper-size} table through path position 20, its body's size past it
     * (boss.md § Keeper stats). One number driving both the entity's own hitbox/render scale and the spawn
     * platform's footprint and clearance. Computed from the record's own path position and body, never from a live
     * Border. Past 20 it is the body's size, growing by its optional {@code sizeGrowth} per path position past 20
     * (the Zombie's, uncapped).
     */
    public static float keeperScale(KeeperRecord keeper) {
        int p = Math.max(KeeperAPI.pathPositionOf(keeper), 1);
        if (p <= AUTHORED_THROUGH) {
            return KEEPER_SIZE.row(p).getFloat("value");
        }
        if (keeper.body().isEmpty()) {
            return 1.0F;
        }
        TuningTable.Row body = KEEPER_BODIES.row(keeper.body());
        float size = body.getFloat("sizePast20");
        if (body.has("sizeGrowth")) {
            size += body.getFloat("sizeGrowth") * (p - AUTHORED_THROUGH);
        }
        return size;
    }
}
