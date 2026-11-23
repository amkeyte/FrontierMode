package com.arryn.frontiermode.bosstell.common.items;

import com.arryn.frontiermode.bosstell.common.entities.AmethystEye;
import com.arryn.frontiermode.keeper.KeeperAPI;
import com.arryn.satchel.common.platform.PlatformBound;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Optional;

/**
 * The Amethyst Eye (amethyst-eye.md § Using it): vanilla's Eye of Ender with a different target. {@code use} is
 * {@code EnderEyeItem.use} with the stronghold lookup replaced by {@link KeeperAPI#pathTipPad}: it throws an
 * {@link AmethystEye} toward the pad of the living Keeper at the path tip, spent unless the player is in creative.
 * With no pad to point at it does nothing at all, as vanilla's Eye does when it finds no stronghold. It does not
 * override {@code useOn}, so it does not fill an End portal frame.
 *
 * <p>The whole class is the use contract of a vanilla item, which is why it carries the marker.
 */
@PlatformBound(reason = "item")
public final class AmethystEyeItem extends Item {

    public AmethystEyeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // As vanilla's Eye does: aimed at an End portal frame, the click is not a throw.
        BlockHitResult looking = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (looking.getType() == HitResult.Type.BLOCK
                && level.getBlockState(looking.getBlockPos()).is(Blocks.END_PORTAL_FRAME)) {
            return InteractionResultHolder.pass(stack);
        }

        // The server decides; the entity reaches the client by tracking.
        if (level.isClientSide) {
            return InteractionResultHolder.consume(stack);
        }
        Optional<BlockPos> pad = KeeperAPI.pathTipPad(level);
        if (pad.isEmpty()) {
            return InteractionResultHolder.consume(stack);
        }

        AmethystEye eye = new AmethystEye(level, player.getX(), player.getY(0.5D), player.getZ());
        eye.setItem(stack);
        eye.signalTo(pad.get());
        level.gameEvent(GameEvent.PROJECTILE_SHOOT, eye.position(), GameEvent.Context.of(player));
        level.addFreshEntity(eye);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_EYE_LAUNCH,
                SoundSource.NEUTRAL, 0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        level.levelEvent(null, 1003, player.blockPosition(), 0);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        player.swing(hand, true);
        return InteractionResultHolder.success(stack);
    }
}
