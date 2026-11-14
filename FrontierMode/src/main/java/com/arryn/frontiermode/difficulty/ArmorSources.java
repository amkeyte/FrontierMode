package com.arryn.frontiermode.difficulty;

import com.arryn.frontiermode.difficulty.common.ArmorMath;
import com.arryn.frontiermode.difficulty.common.DifficultyRules;
import com.arryn.satchel.common.platform.PlatformBound;
import com.arryn.satchel.common.util.StackData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * The one class that reads a player's armor (difficulty.md § Armor answers back): the armor and toughness attributes,
 * and the Protection level of each armor stack through {@link StackData}. Only Protection counts, not Blast, Fire or
 * Projectile Protection and not Feather Falling. The arithmetic is {@link ArmorMath}'s.
 */
@PlatformBound(reason = "enchantment")
final class ArmorSources {

    private ArmorSources() {
    }

    /** {@code player}'s armor as one number from 0 (bare skin) to 1 (the best vanilla kit). */
    static double capabilityOf(Player player) {
        double armor = player.getAttributeValue(Attributes.ARMOR);
        double toughness = player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        int protection = 0;
        for (ItemStack stack : player.getArmorSlots()) {
            protection += StackData.enchantmentLevel(stack, Enchantments.ALL_DAMAGE_PROTECTION);
        }
        return ArmorMath.capability(armor, toughness, protection, DifficultyRules.armorReferenceHit());
    }
}
