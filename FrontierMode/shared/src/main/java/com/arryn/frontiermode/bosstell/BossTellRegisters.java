package com.arryn.frontiermode.bosstell;

import com.arryn.frontiermode.bosstell.common.entities.AmethystEye;
import com.arryn.frontiermode.bosstell.common.items.AmethystCompassItem;
import com.arryn.frontiermode.bosstell.common.items.AmethystEyeItem;
import com.arryn.frontiermode.core.FrontierRegistrar;
import com.arryn.satchel.common.platform.PlatformBound;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;

import java.util.function.Supplier;

/**
 * What the Amethyst compass module registers: the compass, the Amethyst Eye and its thrown entity, each through
 * {@link FrontierRegistrar} (special-compass.md § Registration). The module's enchantments are registered apart, on
 * {@link BossTellModule}.
 */
@PlatformBound(reason = "registration")
public final class BossTellRegisters {

    private BossTellRegisters() {
    }

    /** Loads this class, which registers on load. {@code FrontierBoot} calls it once the registrar is installed. */
    public static void load() {
    }

    public static final Supplier<Item> AMETHYST_COMPASS =
            FrontierRegistrar.item("amethyst_compass", () -> new AmethystCompassItem(new Item.Properties()));

    /** The Amethyst Eye (amethyst-eye.md): a thrown, used-up Keeper finder, a stack of 64. */
    public static final Supplier<Item> AMETHYST_EYE =
            FrontierRegistrar.item("amethyst_eye", () -> new AmethystEyeItem(new Item.Properties()));

    /** The thrown Amethyst Eye, sized and tracked as vanilla's own Eye of Ender is. */
    public static final Supplier<EntityType<AmethystEye>> AMETHYST_EYE_ENTITY =
            FrontierRegistrar.entityType("amethyst_eye",
                    () -> EntityType.Builder.<AmethystEye>of(AmethystEye::new, MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(4)
                            .updateInterval(4)
                            .build("amethyst_eye"));
}
