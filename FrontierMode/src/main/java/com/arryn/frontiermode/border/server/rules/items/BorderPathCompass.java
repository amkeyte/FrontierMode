package com.arryn.frontiermode.border.server.rules.items;

import com.arryn.satchel.Satchel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.LogicalSide;

/**
 * Utility helpers for creating, locating, and updating the Frontier Path Compass.
 *
 * Uses vanilla lodestone-backed compass behavior with a custom NBT marker
 * to ensure exactly one logical compass per player.
 *
 * Server-side only.
 */
public final class BorderPathCompass {

    /** Custom NBT marker used to identify the frontier compass */
    private static final String TAG_FRONTIER = "FrontierCompass";

    private BorderPathCompass() {}

    /* --------------------------------------------------------------------- */
    /* Public API                                                            */
    /* --------------------------------------------------------------------- */

    /**
     * Ensures the player has a frontier compass pointing at {@code tipCenter}.
     * If one already exists, it is updated in-place.
     * Otherwise, a new compass is granted (or dropped if inventory is full).
     */
    public static void giveOrUpdate(Player player, BlockPos tipCenter) {
        if(Satchel.require().side()== LogicalSide.CLIENT)
            throw new IllegalStateException("Server only. Go away.");

        if(!(player instanceof ServerPlayer serverPlayer))
            throw new IllegalStateException("ServerPlayer only. Go away.");

        ServerLevel level = serverPlayer.serverLevel();

        ItemStack compass = find(serverPlayer);

        if (compass.isEmpty()) {
            compass = create(level, tipCenter);

            boolean added = player.getInventory().add(compass);
            if (!added) {
                player.drop(compass, false);
            }
        } else {
            bindToTip(level, compass, tipCenter);
        }
    }

    /**
     * Attempts to locate the frontier compass in the player's inventory.
     * Returns {@link ItemStack#EMPTY} if not found.
     */
    public static ItemStack find(Player player) {
        if(Satchel.require().side()== LogicalSide.CLIENT)
            throw new IllegalStateException("Server only. Go away.");

        if(!(player instanceof ServerPlayer serverPlayer))
            throw new IllegalStateException("ServerPlayer only. Go away.");

        for (ItemStack stack : serverPlayer.getInventory().items) {
            if (isFrontierCompass(stack)) {
                return stack;
            }
        }

        // RM_FRO_013: was main-inventory-only -- BordersTriggers.updateFinderItems polls this
        // every 5 ticks via giveOrUpdate, so a compass sitting in the offhand slot
        // (Inventory.offhand, a separate field from .items in Forge 1.20.1's Inventory) was
        // reported as ItemStack.EMPTY on every poll, and giveOrUpdate granted a brand new one
        // each time -- a live, trivially reproducible item-duplication bug (move the compass to
        // offhand, wait, watch the inventory fill with duplicates).
        for (ItemStack stack : serverPlayer.getInventory().offhand) {
            if (isFrontierCompass(stack)) {
                return stack;
            }
        }

        return ItemStack.EMPTY;
    }

    /**
     * Returns true if the given stack is a frontier compass.
     */
    private static boolean isFrontierCompass(ItemStack stack) {
        if (!stack.is(Items.COMPASS)) return false;

        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(TAG_FRONTIER);
    }

    /* --------------------------------------------------------------------- */
    /* Creation / Binding                                                     */
    /* --------------------------------------------------------------------- */

    /**
     * Creates a new frontier compass bound to the given tip location.
     */
    private static ItemStack create(ServerLevel level, BlockPos tipCenter) {
        ItemStack compass = new ItemStack(Items.COMPASS);
        CompoundTag tag = compass.getOrCreateTag();

        tag.putBoolean(TAG_FRONTIER, true);

        bindToTip(level, compass, tipCenter);

        compass.setHoverName(Component.literal("Frontier Compass"));

        return compass;
    }

    /**
     * Rebinds an existing compass to point at the given tip location.
     * Uses vanilla lodestone compass semantics.
     */
    private static void bindToTip(ServerLevel level, ItemStack compass, BlockPos tipCenter) {
        CompoundTag tag = compass.getOrCreateTag();

        // Do not track an actual lodestone block
        tag.putBoolean("LodestoneTracked", false);

        tag.put("LodestonePos", NbtUtils.writeBlockPos(tipCenter));
        tag.putString(
                "LodestoneDimension",
                level.dimension().location().toString()
        );
    }



}
