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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
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
     * {@code count} separate colored sparks in a circle, each one countable (diorite-tools.md § The
     * sparks): one single-particle broadcast apiece, on a circle of {@code strikerCircleRadius} in the
     * plane of the struck face, evenly spaced -- one, two opposite, five a pentagon -- firing clockwise
     * as seen from outside, the whole ring turned to a random angle each strike so it doesn't land in
     * the same places every time. An off-path Relevant Border gives color only, a few sparks on the
     * same circle.
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
        // The face's own axes: the normal, "top" (world up on a wall, north on a floor or ceiling) and the
        // viewer's right as seen from outside the face (top x normal).
        double nx = face.getStepX();
        double ny = face.getStepY();
        double nz = face.getStepZ();
        double ux = 0.0;
        double uy = face.getAxis() == Direction.Axis.Y ? 0.0 : 1.0;
        double uz = face.getAxis() == Direction.Axis.Y ? -1.0 : 0.0;
        double rx = uy * nz - uz * ny;
        double ry = uz * nx - ux * nz;
        double rz = ux * ny - uy * nx;
        double radius = rules.strikerCircleRadius();
        int delay = rules.strikerSparkDelayTicks();
        MinecraftServer server = level.getServer();
        // The sparks stay evenly spaced, so the count still reads, but the whole ring starts at a random
        // angle each strike, so they don't land in the same places every time. They fire clockwise.
        double phase = level.getRandom().nextDouble() * 2.0 * Math.PI;
        for (int i = 0; i < count; i++) {
            double angle = phase + 2.0 * Math.PI * i / count;
            double sin = Math.sin(angle);
            double cos = Math.cos(angle);
            double ox = rx * sin + ux * cos;
            double oy = ry * sin + uy * cos;
            double oz = rz * sin + uz * cos;
            double px = x + ox * radius;
            double py = y + oy * radius;
            double pz = z + oz * radius;
            // Out along the normal, with a small push away from the centre so the circle opens as it rises.
            double vx = nx * 0.25 + ox * 0.06;
            double vy = ny * 0.25 + oy * 0.06;
            double vz = nz * 0.25 + oz * 0.06;
            Runnable fire = () -> {
                // Count 0 makes the offsets a velocity.
                EffectsAPI.broadcastParticle(level, dust, px, py, pz, 0, vx, vy, vz, 1.0);
                // A click per spark, so the count can be heard as well as seen.
                EffectsAPI.broadcastSound(level, px, py, pz, SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS,
                        0.6F, level.getRandom().nextFloat() * 0.4F + 0.8F);
            };
            if (i == 0 || delay <= 0) {
                fire.run();
            } else {
                // Staggered in time: one spark, then the next, rather than a single burst.
                server.tell(new TickTask(server.getTickCount() + i * delay, fire));
            }
        }
    }
}
