package com.arryn.frontiermode.diorite.common.items;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.common.fixture.BordersPathFacet;
import com.arryn.frontiermode.border.server.rules.BorderRules;
import com.arryn.frontiermode.diorite.common.StrikerMath;
import com.arryn.frontiermode.diorite.server.rules.DioriteRules;
import com.arryn.frontiermode.effects.common.EffectsAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * The Diorite Striker (diorite-tools.md § The Diorite Striker): shaped like flint and steel, it lights
 * nothing. Struck against natural stone it reads the ground and shows the result to everyone nearby
 * as sparks -- color is the Relevant Border's Difficulty against the path tip's, the count is the wave
 * step of its path position. In the Wilds a grey sputter; off the Overworld, a small explosion.
 *
 * <p>Used on anything but natural stone it does nothing and costs nothing. The reading is the
 * server's: {@code useOn} returns success on both sides so the player's arm swings.
 */
public final class DioriteStrikerItem extends Item {

    public DioriteStrikerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos();
        if (player == null || !level.getBlockState(pos).is(BlockTags.BASE_STONE_OVERWORLD)) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.sidedSuccess(true);
        }

        ItemStack stack = context.getItemInHand();
        Direction face = context.getClickedFace();
        DioriteRules rules = DioriteRules.ACTIVE;
        // The struck face's center, nudged just off the surface so sparks start outside the block.
        double x = pos.getX() + 0.5 + face.getStepX() * 0.51;
        double y = pos.getY() + 0.5 + face.getStepY() * 0.51;
        double z = pos.getZ() + 0.5 + face.getStepZ() * 0.51;

        if (level.dimension() != Level.OVERWORLD) {
            // No source entity: vanilla's explosion skips its own source when gathering what to hurt,
            // so naming the player as the source would spare them.
            serverLevel.explode(null, x, y, z, rules.strikerBlastPower(), Level.ExplosionInteraction.NONE);
            stack.hurtAndBreak(rules.strikerBlastDurability(), player, p -> p.broadcastBreakEvent(context.getHand()));
            return InteractionResult.sidedSuccess(false);
        }

        Optional<List<Border>> containing = BorderAPI.RULES(serverLevel).map(r -> r.containing(pos));
        Border relevant = containing.isEmpty() || containing.get().isEmpty()
                ? null
                : BorderRules.ACTIVE.getRelevant(containing.get(), pos);
        if (relevant == null) {
            sputter(serverLevel, x, y, z);
        } else {
            spark(serverLevel, rules, relevant, face, x, y, z);
        }
        stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(context.getHand()));
        return InteractionResult.sidedSuccess(false);
    }

    /** The Wilds: a few grey smoke particles and a fizzle, no color and no count. */
    private static void sputter(ServerLevel level, double x, double y, double z) {
        EffectsAPI.broadcastParticle(level, ParticleTypes.SMOKE, x, y, z, 6, 0.1, 0.1, 0.1, 0.02);
        EffectsAPI.broadcastSound(level, x, y, z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 1.0F);
    }

    /**
     * {@code count} separate colored sparks, each one countable: one single-particle broadcast apiece,
     * fanned across the struck face. An off-path Relevant Border gives color only, a few sparks so the
     * color still reads.
     */
    private static void spark(ServerLevel level, DioriteRules rules, Border relevant, Direction face,
                              double x, double y, double z) {
        BorderRules borderRules = BorderRules.ACTIVE;
        Optional<BordersPathFacet> path = BorderAPI.PATH(level);

        int difficulty = borderRules.layerToDifficulty(relevant.layer());
        int tipDifficulty = path.flatMap(BordersPathFacet::tip)
                .map(tip -> borderRules.layerToDifficulty(tip.layer()))
                .orElse(1);
        float[] rgb = StrikerMath.color(StrikerMath.heat(difficulty, tipDifficulty));

        int pathIndex = path.map(p -> p.indexOf(relevant.id())).orElse(-1);
        int count = pathIndex >= 0 ? borderRules.waveStep(pathIndex + 1) : rules.strikerColorOnlySparks();

        DustParticleOptions dust = new DustParticleOptions(new Vector3f(rgb[0], rgb[1], rgb[2]), rules.strikerSparkSize());
        // A line across the face, perpendicular to its normal: horizontal on a wall, east-west on a floor or ceiling.
        double tx = face.getAxis() == Direction.Axis.Y ? 1.0 : -face.getStepZ();
        double tz = face.getAxis() == Direction.Axis.Y ? 0.0 : face.getStepX();
        for (int i = 0; i < count; i++) {
            double s = (i - (count - 1) / 2.0) * 0.4;
            // Count 0 makes the offsets a velocity: out along the normal, spreading along the line.
            EffectsAPI.broadcastParticle(level, dust,
                    x + tx * s * 0.25, y, z + tz * s * 0.25,
                    0, face.getStepX() * 0.25 + tx * s * 0.15, 0.05 + face.getStepY() * 0.25,
                    face.getStepZ() * 0.25 + tz * s * 0.15, 1.0);
        }
        EffectsAPI.broadcastSound(level, x, y, z, SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 1.0F,
                level.getRandom().nextFloat() * 0.4F + 0.8F);
    }
}
