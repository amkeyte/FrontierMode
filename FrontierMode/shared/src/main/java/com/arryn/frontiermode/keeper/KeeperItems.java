package com.arryn.frontiermode.keeper;

import com.arryn.frontiermode.core.FrontierIds;
import com.arryn.satchel.common.util.StackData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

/**
 * The Keeper's items (wiki/frontiermode/architecture/keeper.md § Fire and sun): the holder class beside the Sun Ward,
 * and the equip call the Sentinel module uses too. The Sun Ward itself is registered in {@link KeeperRegisters}.
 */
public final class KeeperItems {

    private static final ResourceLocation BINDING_CURSE = FrontierIds.of("minecraft", "binding_curse");

    private KeeperItems() {
    }

    /**
     * Equips {@code mob} with the sun ward: an invisible, non-damageable, Curse-of-Binding item in its head slot with a
     * drop chance of 0, so vanilla's sun-ignition check never burns it.
     */
    public static void equipSunWard(Mob mob) {
        ItemStack ward = new ItemStack(KeeperRegisters.SUN_WARD.get());
        // Mob#canReplaceCurrentItem refuses to replace an item with Curse of Binding, so a
        // sentinel that picks up a real helmet keeps the ward.
        StackData.enchant(ward, mob.level(), BINDING_CURSE, 1);
        mob.setItemSlot(EquipmentSlot.HEAD, ward);
        mob.setDropChance(EquipmentSlot.HEAD, 0.0F);
    }
}
