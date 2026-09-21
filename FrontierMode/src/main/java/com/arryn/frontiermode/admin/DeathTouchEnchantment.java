package com.arryn.frontiermode.admin;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * Admin/debug enchant -- any hit landed with a weapon carrying it kills the target outright
 * (see {@link AdminToolsModule#onLivingHurt}, the actual mechanism; enchant classes get no
 * generic combat hook from Forge, same reasoning as {@code IntuitionEnchantment}'s own doc).
 * {@code EnchantmentCategory.WEAPON} (vanilla's own category) rather than a new one restricted
 * to a single item -- unlike the Amethyst Compass's enchants, this is meant to work on whatever
 * weapon carries it, per the project owner's own "kill anything" framing, not just one admin
 * item.
 *
 * <p>Treasure-only and {@code VERY_RARE} -- never offered at an enchant table or from any
 * vanilla trade/loot pool; the only sanctioned path onto a stack is
 * {@code AdminCommands.deathStick}'s direct construction.
 */
public class DeathTouchEnchantment extends Enchantment {

    public DeathTouchEnchantment() {
        super(Rarity.VERY_RARE, EnchantmentCategory.WEAPON,
                new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND});
    }

    @Override
    public boolean isTreasureOnly() {
        return true;
    }

    @Override
    public boolean isDiscoverable() {
        return false;
    }
}
