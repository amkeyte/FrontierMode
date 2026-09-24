package com.arryn.frontiermode.bosstell.common;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jetbrains.annotations.NotNull;

/**
 * FRO_121 (Tier 5 of RM_FRO_033 "Marilyn"): Resonance, per
 * wiki/frontiermode/architecture/special-compass.md#intuition-and-resonance-what-the-enchant-classes-need-to-do-and-dont.
 * Thin, same reasoning as {@link IntuitionEnchantment}'s own doc.
 *
 * <p>Cost curve copied from vanilla's {@code MendingEnchantment} -- the closest real treasure-
 * only parallel (flat single level, {@code RARE}): {@code getMinCost(level) = level * 25}. Base
 * {@link Enchantment#getMaxLevel()} already defaults to 1 -- not overridden, per that class's
 * own doc ("flat, no Resonance II").
 */
public class ResonanceEnchantment extends Enchantment {

    public ResonanceEnchantment() {
        super(Rarity.RARE, AmethystCompassItem.ENCHANT_CATEGORY,
                new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND});
    }

    @Override
    public int getMinCost(int level) {
        return level * 25;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 50;
    }

    /** Never offered at the enchant table -- loot/trade/fishing-only find, same flag Mending/
     * Soul Speed use. */
    @Override
    public boolean isTreasureOnly() {
        return true;
    }

    @Override
    protected boolean checkCompatibility(@NotNull Enchantment other) {
        return !(other instanceof IntuitionEnchantment);
    }
}
