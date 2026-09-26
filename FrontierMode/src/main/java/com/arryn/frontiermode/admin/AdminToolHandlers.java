package com.arryn.frontiermode.admin;

import com.arryn.frontiermode.FrontierMode;
import com.arryn.satchel.common.lifecycle.LivingDropsSignal;
import com.arryn.satchel.common.lifecycle.PlayerAttackSignal;
import com.arryn.satchel.common.lifecycle.PlayerRespawnSignal;
import com.arryn.satchel.common.util.StackData;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The admin tools' handler bodies: the soulbound paths (death-drop stash, respawn hand-back, toss
 * cancel), Death Touch's damage override, and Inspect's cancel-and-report. {@code AdminToolsModule}
 * only installs the signal handlers; the two raw Forge subscribers here ({@code LivingHurtEvent},
 * {@code ItemTossEvent}) have no Satchel signal yet. See wiki admin-tools.md.
 */
@Mod.EventBusSubscriber(modid = FrontierMode.MODID)
public final class AdminToolHandlers {

    /**
     * Stacks removed from a player's death drops aren't preserved anywhere by that removal alone --
     * stashed here, keyed by the dying player's UUID, and handed back in {@link #onPlayerRespawn}. A
     * plain static map, not persisted: a respawn always follows a death within the same server session
     * in practice, the same precedent as the other in-memory tracking maps in this codebase.
     */
    private static final Map<UUID, List<ItemStack>> SOULBOUND_STASH = new HashMap<>();

    private AdminToolHandlers() {
    }

    /**
     * Soulbound, "survives death": pulls any admin tool out of the death drop list before it becomes a
     * lootable {@code ItemEntity} and stashes it for {@link #onPlayerRespawn}. Vanilla's own death
     * handling has already cleared the player's real inventory by this point, so this is the only place
     * left to intercept it.
     */
    static void onLivingDrops(LivingDropsSignal signal) {
        if (!(signal.entity() instanceof ServerPlayer player)) {
            return;
        }

        List<ItemStack> stashed = new ArrayList<>();
        signal.drops().removeIf(itemEntity -> {
            ItemStack stack = itemEntity.getItem();
            if (!AdminTools.isAdminTool(stack)) {
                return false;
            }
            stashed.add(stack);
            return true;
        });

        if (!stashed.isEmpty()) {
            SOULBOUND_STASH.put(player.getUUID(), stashed);
        }
    }

    /** Hands back whatever {@link #onLivingDrops} stashed for this player, if anything. */
    static void onPlayerRespawn(PlayerRespawnSignal signal) {
        if (!(signal.player() instanceof ServerPlayer player)) {
            return;
        }
        List<ItemStack> stashed = SOULBOUND_STASH.remove(player.getUUID());
        if (stashed == null) {
            return;
        }

        for (ItemStack stack : stashed) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    /**
     * Inspect: a hit with an Inspect stick is cancelled before anything of it happens (no damage,
     * knockback, aggro, or kill-credit window), and the holder gets a report on the target instead --
     * chat to that player only, and the same text in the server log. The signal is posted server-side
     * only; the client's own predicted swing still plays, which is cosmetic.
     */
    static void onPlayerAttack(PlayerAttackSignal signal) {
        if (!(signal.player() instanceof ServerPlayer player)
                || StackData.enchantmentLevel(player.getMainHandItem(), AdminToolsModule.INSPECT.get()) <= 0) {
            return;
        }

        signal.cancel();
        if (signal.target() instanceof LivingEntity target && player.level() instanceof ServerLevel level) {
            List<String> lines = InspectReport.lines(target, level);
            for (String line : lines) {
                player.sendSystemMessage(Component.literal(line));
            }
            OUT.info("[Admin] inspect(): " + String.join(" / ", lines));
        }
    }

    /**
     * Any hit landed with a weapon carrying Death Touch overwhelms the target's health outright --
     * {@code setAmount(Float.MAX_VALUE)} rather than calling {@code target.kill()} from inside this
     * handler, so the kill still runs through the normal, already-in-progress damage pipeline
     * (armor/absorption/death handling). {@code DamageSource.getEntity()} is the true causer, checked
     * against their main hand item specifically, since that's what actually landed the hit.
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) {
            return;
        }
        if (StackData.enchantmentLevel(attacker.getMainHandItem(), AdminToolsModule.DEATH_TOUCH.get()) <= 0) {
            return;
        }
        event.setAmount(Float.MAX_VALUE);
    }

    /**
     * Soulbound, "can't be dropped": cancels the toss and hands the stack straight back to the player's
     * own inventory rather than just cancelling -- {@code Player#drop} already removed it from the
     * inventory slot by the time this event fires, so cancelling alone would delete it.
     */
    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getEntity().getItem();
        if (!AdminTools.isAdminTool(stack)) {
            return;
        }

        event.setCanceled(true);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
