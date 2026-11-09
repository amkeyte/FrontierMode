package com.arryn.frontiermode.admin;

import com.arryn.frontiermode.core.InfoPrinter;
import com.arryn.frontiermode.core.InfoRequest;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The admin tools' consumers: Inspect's cancel-and-report, Death Touch's damage override, and the
 * soulbound toss cancel. Stateless; the ingress registers them ({@link AdminIngress}) and the stash that
 * carries a tool through a death is a Trinket ({@code SoulboundStash}). See wiki admin-tools.md.
 */
final class AdminToolHandlers {

    private AdminToolHandlers() {
    }

    /**
     * Inspect: a swing with an Inspect stick is stopped before anything of it happens (no damage,
     * knockback, aggro, or kill-credit window), and the holder gets a report on the target instead --
     * chat to that player only, and the same text in the server log. The event is delivered server-side
     * only; the client's own predicted swing still plays, which is cosmetic.
     *
     * <p>Admin knows the entity and asks the modules about it: it adds the entity's type, short UUID,
     * health, position and category itself, ahead of what the modules add, and hands the request to the
     * shared printer.
     */
    static void onInspectSwing(InspectSwing swing) {
        OUT.debug("[Admin] InspectSwing reached its consumer");

        swing.stopAttack();
        Player player = swing.player();
        if (!(swing.target() instanceof LivingEntity target)) {
            return;
        }

        InfoRequest request = new InfoRequest(player.level(), List.of(target));
        request.add(target, "admin", () -> baseLines(target));
        InfoPrinter.report(request, line -> player.sendSystemMessage(Component.literal(line)));
    }

    private static String baseLines(LivingEntity target) {
        BlockPos pos = target.blockPosition();
        ResourceLocation typeId = EntityType.getKey(target.getType());
        return String.format("[Inspect] %s #%s  HP %.1f/%.1f  (%d, %d, %d)\n  category %s",
                typeId, Ids.shortId(target.getUUID()), target.getHealth(), target.getMaxHealth(),
                pos.getX(), pos.getY(), pos.getZ(), target.getType().getCategory());
    }

    /**
     * Any hit landed with a weapon carrying Death Touch overwhelms the target's health outright, through
     * the damage amount rather than a kill call from inside the handler (see {@link DeathTouchHit}).
     */
    static void onDeathTouchHit(DeathTouchHit hit) {
        OUT.debug("[Admin] DeathTouchHit reached its consumer");

        hit.overwhelm();
    }

    /**
     * Soulbound, "can't be dropped": cancels the toss and hands the stack straight back to the player's
     * own inventory rather than just cancelling -- {@code Player#drop} already removed it from the
     * inventory slot by the time the event fires, so cancelling alone would delete it.
     */
    static void onSoulboundToss(SoulboundToss toss) {
        OUT.debug("[Admin] SoulboundToss reached its consumer");

        toss.cancel();
        Player player = toss.player();
        ItemStack stack = toss.stack();
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
