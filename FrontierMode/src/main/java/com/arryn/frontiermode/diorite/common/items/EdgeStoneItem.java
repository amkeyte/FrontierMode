package com.arryn.frontiermode.diorite.common.items;

import com.arryn.frontiermode.diorite.common.EdgeStone;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The Edge Stone (diorite-tools.md § The Edge Stone): the vanilla snowball's shape -- {@code use}
 * throws an {@link EdgeStone}, spent unless the player is in creative. No cooldown.
 */
public final class EdgeStoneItem extends Item {

    // Vanilla's snowball throw: velocity and inaccuracy.
    private static final float THROW_VELOCITY = 1.5F;
    private static final float THROW_INACCURACY = 1.0F;

    public EdgeStoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EGG_THROW,
                SoundSource.PLAYERS, 0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        if (!level.isClientSide) {
            EdgeStone stone = new EdgeStone(level, player);
            stone.setItem(stack);
            stone.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, THROW_VELOCITY, THROW_INACCURACY);
            level.addFreshEntity(stone);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
