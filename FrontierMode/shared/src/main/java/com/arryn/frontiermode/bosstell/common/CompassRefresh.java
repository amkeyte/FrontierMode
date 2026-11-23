package com.arryn.frontiermode.bosstell.common;

import com.arryn.frontiermode.bosstell.common.items.AmethystCompassItem;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.player.PlayerScope;
import com.arryn.satchel.common.trinket.Ticking;
import com.arryn.satchel.common.util.out.OUT;
import com.arryn.satchel.common.util.schedule.TickThrottler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Live-refreshes every attuned compass a player currently holds, on that player's own tick. A Trinket
 * contributed to the player host: it saves nothing and syncs nothing, so it needs no bundle and no jig
 * (wiki special-compass.md, "Live refresh and the pickup block").
 *
 * <p>Only refreshes what is actively held: a dropped or chested compass is not watched by anyone until
 * it is picked back up, which is what lets the ownership block leave a compass inert in the world.
 * The player's scope outlives death, respawn and a dimension change and looks its player up fresh on
 * every call, so one instance serves a player for the whole login.
 */
public final class CompassRefresh implements Ticking {

    private static final int REFRESH_INTERVAL_TICKS = 20;

    private TickThrottler throttler;

    @Override
    public void onAttach(ScopeInfo info) {
        OUT.debug("[BossTell] CompassRefresh attached to " + info.debugName());
    }

    @Override
    public void onTickAfter(ScopeInfo info) {
        PlayerScope scope = info.scopeAs();
        Player player = scope.player();

        if (throttler == null) {
            //noinspection resource
            throttler = new TickThrottler(REFRESH_INTERVAL_TICKS, player.level()::getGameTime);
        }
        if (!throttler.allow()) {
            return;
        }

        for (ItemStack stack : player.getInventory().items) {
            refreshIfAttuned(stack, player);
        }
        for (ItemStack stack : player.getInventory().offhand) {
            refreshIfAttuned(stack, player);
        }
    }

    private static void refreshIfAttuned(ItemStack stack, Player player) {
        // refreshOrRelock decides "nothing to do" from "an enchant that needs a first lock" itself,
        // so a fresh, never-attuned compass carrying an Intuition or Resonance enchant still reaches
        // it; this caller must not re-decide that upfront.
        if (stack.getItem() instanceof AmethystCompassItem) {
            AmethystCompassItem.refreshOrRelock(stack, player.level(), player);
        }
    }
}
