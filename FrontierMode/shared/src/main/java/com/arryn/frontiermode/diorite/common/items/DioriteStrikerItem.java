package com.arryn.frontiermode.diorite.common.items;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.core.FrontierLine;
import com.arryn.frontiermode.diorite.common.DioriteRules;
import com.arryn.frontiermode.diorite.server.StrikerSparks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;


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
        if (level.isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }

        ItemStack stack = context.getItemInHand();
        Direction face = context.getClickedFace();
        // The struck face's center, nudged just off the surface so sparks start outside the block.
        double x = pos.getX() + 0.5 + face.getStepX() * 0.51;
        double y = pos.getY() + 0.5 + face.getStepY() * 0.51;
        double z = pos.getZ() + 0.5 + face.getStepZ() * 0.51;

        if (level.dimension() != Level.OVERWORLD) {
            // No source entity: vanilla's explosion skips its own source when gathering what to hurt,
            // so naming the player as the source would spare them.
            level.explode(null, x, y, z, DioriteRules.strikerBlastPower(), Level.ExplosionInteraction.NONE);
            FrontierLine.damageItem(stack, DioriteRules.strikerBlastDurability(), player, context.getHand());
            return InteractionResult.sidedSuccess(false);
        }

        Border relevant = BorderAPI.getRelevant(level, pos).orElse(null);
        if (relevant == null) {
            StrikerSparks.sputter(level, x, y, z);
        } else {
            StrikerSparks.spark(level, relevant, face, x, y, z);
        }
        FrontierLine.damageItem(stack, 1, player, context.getHand());
        return InteractionResult.sidedSuccess(false);
    }
}
