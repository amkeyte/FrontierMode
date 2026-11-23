package com.arryn.frontiermode.diorite;

import com.arryn.frontiermode.FrontierMode;
import com.arryn.frontiermode.diorite.common.items.DioriteStrikerItem;
import com.arryn.frontiermode.diorite.common.items.EdgeStoneItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The diorite module's items (diorite-tools.md § Module shape), on a register this holder owns and
 * {@code FrontierMode}'s constructor wires onto the mod bus like the mod's other registers.
 */
public final class DioriteItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, FrontierMode.MODID);

    /** A shapeless 1:1 craft from a diorite block; an ingredient for the Edge Stone, Wick and Keeper loot. */
    public static final RegistryObject<Item> DIORITE_DUST =
            ITEMS.register("diorite_dust", () -> new Item(new Item.Properties()));

    /** A rare drop from mining diorite (the {@code blocks/diorite} loot table override). */
    public static final RegistryObject<Item> DIORITE_SHARD =
            ITEMS.register("diorite_shard", () -> new Item(new Item.Properties()));

    /** Reads how dangerous a patch of claimed ground is: 64 durability, like flint and steel. */
    public static final RegistryObject<Item> DIORITE_STRIKER =
            ITEMS.register("diorite_striker", () -> new DioriteStrikerItem(new Item.Properties().durability(64)));

    /** A throwable that poofs when it crosses the Frontier line; shapeless from flint and diorite dust. */
    public static final RegistryObject<Item> EDGE_STONE =
            ITEMS.register("edge_stone", () -> new EdgeStoneItem(new Item.Properties()));

    private DioriteItems() {
    }
}
