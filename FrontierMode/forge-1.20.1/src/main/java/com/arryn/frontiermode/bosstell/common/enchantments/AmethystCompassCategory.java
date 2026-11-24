package com.arryn.frontiermode.bosstell.common.enchantments;

import com.arryn.frontiermode.bosstell.common.items.AmethystCompassItem;
import com.arryn.satchel.common.platform.PlatformBound;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * What Intuition and Resonance may go on: the Amethyst compass and nothing else, not compasses generically and not
 * any other item (special-compass.md). On {@code neoforge-1.21.1} the same set is an item tag.
 */
@PlatformBound(reason = "enchantment")
public final class AmethystCompassCategory {

    private AmethystCompassCategory() {
    }

    public static final EnchantmentCategory CATEGORY =
            EnchantmentCategory.create("amethyst_compass", item -> item instanceof AmethystCompassItem);
}
