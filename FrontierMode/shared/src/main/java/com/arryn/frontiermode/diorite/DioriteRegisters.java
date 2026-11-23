package com.arryn.frontiermode.diorite;

import com.arryn.frontiermode.core.FrontierRegistrar;
import com.arryn.frontiermode.diorite.common.entities.EdgeStone;
import com.arryn.frontiermode.diorite.common.items.DioriteStrikerItem;
import com.arryn.frontiermode.diorite.common.items.EdgeStoneItem;
import com.arryn.satchel.common.platform.PlatformBound;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;

import java.util.function.Supplier;

/**
 * What the diorite module registers: its items (diorite-tools.md § Module shape) and the thrown Edge Stone's entity
 * type (§ The Edge Stone), each through {@link FrontierRegistrar}.
 */
@PlatformBound(reason = "registration")
public final class DioriteRegisters {

    private DioriteRegisters() {
    }

    /** Loads this class, which registers on load. {@code FrontierBoot} calls it once the registrar is installed. */
    public static void load() {
    }

    /** A shapeless 1:1 craft from a diorite block; an ingredient for the Edge Stone, Wick and Keeper loot. */
    public static final Supplier<Item> DIORITE_DUST =
            FrontierRegistrar.item("diorite_dust", () -> new Item(new Item.Properties()));

    /** A rare drop from mining diorite (the {@code blocks/diorite} loot table override). */
    public static final Supplier<Item> DIORITE_SHARD =
            FrontierRegistrar.item("diorite_shard", () -> new Item(new Item.Properties()));

    /** Reads how dangerous a patch of claimed ground is: 64 durability, like flint and steel. */
    public static final Supplier<Item> DIORITE_STRIKER =
            FrontierRegistrar.item("diorite_striker", () -> new DioriteStrikerItem(new Item.Properties().durability(64)));

    /** A throwable that poofs when it crosses the Frontier line; shapeless from flint and diorite dust. */
    public static final Supplier<Item> EDGE_STONE =
            FrontierRegistrar.item("edge_stone", () -> new EdgeStoneItem(new Item.Properties()));

    /** The thrown Edge Stone: a snowball-sized projectile. */
    public static final Supplier<EntityType<EdgeStone>> EDGE_STONE_ENTITY =
            FrontierRegistrar.entityType("edge_stone", () -> EntityType.Builder.<EdgeStone>of(EdgeStone::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("edge_stone"));
}
