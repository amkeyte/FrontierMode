package com.arryn.frontiermode.keeper;

import com.arryn.frontiermode.FrontierMode;
import com.arryn.frontiermode.keeper.common.items.SunWardItem;
import com.arryn.satchel.common.util.StackData;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The Keeper's items (wiki/frontiermode/architecture/keeper.md § Fire and sun): the holder class beside the Sun Ward,
 * and the equip call the Sentinel module uses too. Still has to be registered onto the mod event bus from
 * {@code FrontierMode}'s constructor, the only place the mod bus instance exists.
 */
public final class KeeperItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, FrontierMode.MODID);

    public static final RegistryObject<Item> SUN_WARD = ITEMS.register("sun_ward", SunWardItem::new);

    private KeeperItems() {
    }

    /**
     * Equips {@code mob} with the sun ward: an invisible, non-damageable, Curse-of-Binding item in its head slot with a
     * drop chance of 0, so vanilla's sun-ignition check never burns it.
     */
    public static void equipSunWard(Mob mob) {
        ItemStack ward = new ItemStack(SUN_WARD.get());
        // Mob#canReplaceCurrentItem refuses to replace an item with Curse of Binding, so a
        // sentinel that picks up a real helmet keeps the ward.
        StackData.enchant(ward, Enchantments.BINDING_CURSE, 1);
        mob.setItemSlot(EquipmentSlot.HEAD, ward);
        mob.setDropChance(EquipmentSlot.HEAD, 0.0F);
    }
}
