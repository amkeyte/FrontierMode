package com.arryn.frontiermode.admin;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.jetbrains.annotations.NotNull;

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

    /**
     * FRO_126: without this override, {@code Enchantment}'s base implementation returns 0, and
     * Death Touch supplies no bonus toward {@code Player#attack()}'s own pre-{@code hurt()} check
     * (raw {@code Attributes.ATTACK_DAMAGE} contribution {@code > 0} OR enchant bonus {@code > 0}).
     * Deep enough into the Exterior, Frontier Sickness's Weakness effect floors the attribute
     * contribution to 0 -- with no enchant bonus either, vanilla's check fails and
     * {@code target.hurt()} (and so {@code LivingHurtEvent}, the actual kill mechanism in
     * {@link AdminToolsModule.Listener#onLivingHurt}) never fires at all, silently no-op'ing the
     * whole enchant. A flat, level/mob-type-independent bonus just needs to clear that gate --
     * the real kill still happens entirely through {@code onLivingHurt}'s
     * {@code Float.MAX_VALUE} override, never through this bonus actually landing as damage.
     */
    @SuppressWarnings("deprecation")
    @Override
    public float getDamageBonus(int level, @NotNull MobType mobType) {
        return 1.0F;
    }
}
