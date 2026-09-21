package com.arryn.frontiermode.bosstell.common;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * FRO_121 (Tiers 2-4 of RM_FRO_033 "Marilyn"): Intuition I-III, per
 * wiki/frontiermode/architecture/special-compass.md#intuition-and-resonance-what-the-enchant-classes-need-to-do-and-dont.
 * Thin by design -- Forge gives enchantments no generic "do something" hook, and Intuition isn't
 * a combat enchantment, so there's no vanilla override point to plug real logic into anyway. All
 * of its actual behavior (the ring-jitter point, re-roll detection) lives in
 * {@code AmethystCompassItem}/{@code CompassPlayerFixture}'s own throttled refresh, keyed off
 * {@code EnchantmentHelper.getItemEnchantmentLevel} -- this class is configuration only.
 *
 * <p>Cost curve copied from vanilla's {@code FishingSpeedEnchantment} ("Lure") -- the closest
 * real parallel per the ticket's own instruction (multi-level, non-combat, comparable rarity):
 * same {@code getMaxLevel()=3} and {@code getMinCost(level) = 15 + (level-1)*9} formula, a
 * playtest-adjustable starting point, not a final balance decision.
 */
public class IntuitionEnchantment extends Enchantment {

    public IntuitionEnchantment() {
        super(Rarity.RARE, AmethystCompassItem.ENCHANT_CATEGORY,
                new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND});
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public int getMinCost(int level) {
        return 15 + (level - 1) * 9;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 50;
    }

    /** Ordinary enchant-table/anvil availability -- not treasure-only (base default already
     * returns {@code false}; stated explicitly since Resonance, its counterpart, is the
     * opposite). */
    @Override
    public boolean isTreasureOnly() {
        return false;
    }

    @Override
    protected boolean checkCompatibility(Enchantment other) {
        return !(other instanceof ResonanceEnchantment);
    }
}
