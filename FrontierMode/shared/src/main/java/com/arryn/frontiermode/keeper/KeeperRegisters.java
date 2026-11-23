package com.arryn.frontiermode.keeper;

import com.arryn.frontiermode.core.FrontierRegistrar;
import com.arryn.frontiermode.keeper.common.items.SunWardItem;
import com.arryn.satchel.common.platform.PlatformBound;
import net.minecraft.world.item.Item;

import java.util.function.Supplier;

/**
 * What the Keeper module registers: the Sun Ward (keeper.md § Fire and sun), through {@link FrontierRegistrar}. The
 * equip call that uses it is {@link KeeperItems}.
 */
@PlatformBound(reason = "registration")
public final class KeeperRegisters {

    private KeeperRegisters() {
    }

    /** Loads this class, which registers on load. {@code FrontierBoot} calls it once the registrar is installed. */
    public static void load() {
    }

    public static final Supplier<Item> SUN_WARD = FrontierRegistrar.item("sun_ward", SunWardItem::new);
}
