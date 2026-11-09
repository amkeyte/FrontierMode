package com.arryn.frontiermode.bosstell.common.enchantments;

import com.arryn.frontiermode.bosstell.common.items.AmethystCompassItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jetbrains.annotations.NotNull;

/**
 * Intuition I-III, per
 * wiki/frontiermode/architecture/special-compass.md#intuition-and-resonance-what-the-enchant-classes-need-to-do-and-dont.
 * Thin by design -- Forge gives enchantments no generic "do something" hook, and Intuition isn't
 * a combat enchantment, so there's no vanilla override point to plug real logic into anyway. All
 * of its actual behavior (the ring-jitter point, re-roll detection) lives in
 * {@code AmethystCompassItem}/{@code CompassRefresh}'s own throttled refresh, keyed off
 * {@code EnchantmentHelper.getItemEnchantmentLevel} -- this class is configuration only.
 *
 * <p>Cost curve modeled on vanilla's {@code FishingSpeedEnchantment} ("Lure") --
 * the closest real parallel (multi-level, non-combat, comparable rarity), lowered for dev testing:
 * Lure's own {@code 15 + (level-1)*9} floor needs real bookshelf coverage to reach reliably (the
 * base enchanting-table roll caps around 8 at zero bookshelf power), which slows testing.
 * {@code 1 + (level-1)*5} is trivially reachable regardless of bookshelf setup -- a dev-test
 * convenience, not a final balance decision; revisit before this ships for real play.
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
        return 1 + (level - 1) * 5;
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
    protected boolean checkCompatibility(@NotNull Enchantment other) {
        return !(other instanceof ResonanceEnchantment);
    }
}
