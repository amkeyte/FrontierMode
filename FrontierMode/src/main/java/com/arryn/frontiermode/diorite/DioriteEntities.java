package com.arryn.frontiermode.diorite;

import com.arryn.frontiermode.FrontierMode;
import com.arryn.frontiermode.diorite.common.EdgeStone;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The diorite module's entity types (diorite-tools.md § The Edge Stone), on a register this holder
 * owns and {@code FrontierMode}'s constructor wires onto the mod bus, like {@link DioriteItems}.
 */
public final class DioriteEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, FrontierMode.MODID);

    /** The thrown Edge Stone: a snowball-sized projectile. */
    public static final RegistryObject<EntityType<EdgeStone>> EDGE_STONE =
            ENTITY_TYPES.register("edge_stone", () -> EntityType.Builder.<EdgeStone>of(EdgeStone::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("edge_stone"));

    private DioriteEntities() {
    }
}
