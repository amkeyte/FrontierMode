package com.arryn.frontiermode.boss.server.rules;

import com.arryn.frontiermode.border.common.BorderMath;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.server.level.ServerLevel;

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

    private final RandomSource rng;

    public DefaultBossRules() {
        this(RandomSource.create());
    }

    public DefaultBossRules(RandomSource rng) {
        this.rng = rng;
    }

    @Override
    public BlockPos choosePosition(Level level, Border border) {
        return BorderMath.randomPointInDisk(rng, border.center(), border.radius());
    }

    @Override
    public Optional<Mob> materialize(ServerLevel level, BlockPos xz, int layer) {
        // Surface height, not inside a solid block or a liquid -- same category of check vanilla
        // natural mob spawning already does. getHeightmapPos already returns the first open block
        // above the top solid/liquid surface at this column.
        BlockPos groundPos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, xz);

        if (!level.getBlockState(groundPos).getFluidState().isEmpty()) {
            // Entirely liquid column (e.g. open ocean) -- not a valid boss spawn. No-op, retried
            // next tick same as an unloaded chunk would be -- this is a normal outcome, not an
            // error, since the XZ is never re-picked.
            return Optional.empty();
        }

        EntityType<? extends Mob> type = mobTypeForLayer(layer);
        Mob mob = type.create(level);
        if (mob == null) {
            OUT.warn("[Boss] materialize(): EntityType.create returned null for " + type + " at " + groundPos);
            return Optional.empty();
        }

        mob.moveTo(groundPos.getX() + 0.5, groundPos.getY(), groundPos.getZ() + 0.5, rng.nextFloat() * 360.0F, 0.0F);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(groundPos), MobSpawnType.EVENT, null, null);

        applyStatScaling(mob, layer);
        tagVisibly(mob, layer);

        if (!level.addFreshEntity(mob)) {
            OUT.warn("[Boss] materialize(): addFreshEntity failed for " + type + " at " + groundPos);
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
}
