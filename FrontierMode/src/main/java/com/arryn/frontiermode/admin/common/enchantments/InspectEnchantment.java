package com.arryn.frontiermode.admin.common.enchantments;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.jetbrains.annotations.NotNull;

/**
 * Admin/debug enchant: a hit landed with a stick carrying it is cancelled before it happens, and the
 * holder is told what the target is (see {@code AdminToolHandlers#onPlayerAttack}, the actual
 * mechanism; enchant classes get no combat hook from Forge). See wiki admin-tools.md.
 *
 * <p>Treasure-only and not discoverable -- never offered at a table, found as loot, or traded; the
 * only sanctioned path onto a stack is {@code /admin inspectstick}. Declares itself incompatible with
 * {@link DeathTouchEnchantment}: vanilla checks compatibility from both sides, so a stick is one tool
 * or the other.
 */
public class InspectEnchantment extends Enchantment {

    public InspectEnchantment() {
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

    @Override
    protected boolean checkCompatibility(@NotNull Enchantment other) {
        return !(other instanceof DeathTouchEnchantment) && super.checkCompatibility(other);
    }
}
