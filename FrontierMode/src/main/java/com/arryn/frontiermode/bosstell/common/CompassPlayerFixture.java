package com.arryn.frontiermode.bosstell.common;

import com.arryn.frontiermode.bosstell.common.items.AmethystCompassItem;
import com.arryn.satchel.common.fixture.SatchelFixture;
import com.arryn.satchel.common.util.schedule.TickThrottler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Player-scoped, non-persisted fixture that live-refreshes every attuned compass a player
 * currently holds -- per
 * wiki/frontiermode/architecture/special-compass.md#playerscope-fixture-live-refresh-not-the-pickup-block.
 * Its own new bundle ({@link CompassPlayerBundle}), not folded into {@code BorderPlayerBundle} (a
 * different concern) -- mirrors {@code BorderPlayerStatusFixture}'s "not persisted,
 * live-recomputed" shape exactly, so no {@code capabilities()}/{@code persistence()} override is
 * needed on this fixture's {@code JigConfig}.
 *
 * <p>Only refreshes what's actively held -- a dropped or chested compass isn't watched by anyone
 * until it's picked back up, per that same section's own reasoning. This is what lets the
 * ownership block (blocked, never destroyed -- gated on SAT_072, not yet built) leave a
 * not-picked-up compass sitting inert in the world with nothing trying to refresh it.
 */
public final class CompassPlayerFixture extends SatchelFixture {

    // Throttled tick reused, per special-compass.md's "Item class" section -- gates how often
    // the inventory scan+refresh below actually runs. One instance per player: this fixture is
    // already PlayerJig-scoped, so unlike FeralModule.REENTRY_SWEEP_THROTTLERS (one throttler per
    // level, held in a map), no separate map/key is needed here.
    private static final int REFRESH_INTERVAL_TICKS = 20;

    private TickThrottler throttler;

    public void tick(ServerPlayer player) {
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

    private void refreshIfAttuned(ItemStack stack, ServerPlayer player) {
        // Bug fix (playtest, same class of bug as AmethystCompassItem.refreshOrRelock's own
        // fix): this used to also gate on CompassAttunement.isAttuned(stack) here, before ever
        // calling refreshOrRelock -- so a fresh, never-attuned compass carrying a real
        // Intuition/Resonance enchant never reached the method that would have noticed the
        // enchant at all. refreshOrRelock already knows how to tell "nothing to do" from "an
        // enchant that needs a first lock" -- this caller shouldn't re-decide that upfront.
        if (stack.getItem() instanceof AmethystCompassItem) {
            AmethystCompassItem.refreshOrRelock(stack, player.serverLevel(), player);
        }
    }
}
