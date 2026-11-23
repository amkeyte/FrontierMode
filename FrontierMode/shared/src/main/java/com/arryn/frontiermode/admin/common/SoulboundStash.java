package com.arryn.frontiermode.admin.common;

import com.arryn.frontiermode.admin.AdminTools;
import com.arryn.frontiermode.admin.SoulboundDeathDrops;
import com.arryn.frontiermode.admin.SoulboundRespawn;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.trinket.Trinket;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Carries a player's admin tools through a death. Soulbound, "survives death": on a death it takes the
 * admin tools out of the drops before they become lootable item entities and keeps them in a field; on the
 * respawn it hands them back, into the inventory or, when that is full, dropped at the player.
 *
 * <p>A Trinket on the player host: the player's scope outlives the death (it unloads at logout only) and
 * looks its player up fresh on every call, so the respawned player instance reaches the same Trinket. A
 * player who quits at the death screen loses the stash; the tools are regenerable with {@code /admin}.
 * Neither saved nor synced, so the stash is a field and ends with the scope. See wiki admin-tools.md,
 * "Soulbound".
 */
public final class SoulboundStash implements Trinket {

    private final List<ItemStack> stash = new ArrayList<>();

    @Override
    public void onAttach(ScopeInfo info) {
        info.listenFor(SoulboundDeathDrops.class, this::onDeathDrops);
        info.listenFor(SoulboundRespawn.class, this::onRespawn);
    }

    private void onDeathDrops(SoulboundDeathDrops event) {
        OUT.debug("[Admin] SoulboundDeathDrops reached the stash");

        event.drops().removeIf(itemEntity -> {
            ItemStack stack = itemEntity.getItem();
            if (!AdminTools.isAdminTool(stack)) {
                return false;
            }
            stash.add(stack);
            return true;
        });
    }

    private void onRespawn(SoulboundRespawn event) {
        OUT.debug("[Admin] SoulboundRespawn reached the stash");

        Player player = event.player();
        for (ItemStack stack : stash) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        stash.clear();
    }
}
