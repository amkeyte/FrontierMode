package com.arryn.frontiermode.core;

import com.arryn.frontiermode.admin.AdminToolsRegisters;
import com.arryn.frontiermode.admin.common.enchantments.DeathTouchEnchantment;
import com.arryn.frontiermode.admin.common.enchantments.InspectEnchantment;
import com.arryn.frontiermode.bosstell.BossTellRegisters;
import com.arryn.frontiermode.bosstell.common.enchantments.IntuitionEnchantment;
import com.arryn.frontiermode.bosstell.common.enchantments.ResonanceEnchantment;
import com.arryn.satchel.common.platform.PlatformBound;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * {@code forge-1.20.1}'s enchantments: the four classes, registered under the ids the modules' registers classes
 * hold. Shared code names each by id and never by object (Two Lines, Enchantments). {@code FrontierMode} puts the
 * register on the mod bus.
 */
@PlatformBound(reason = "enchantment")
public final class ForgeEnchantments {

    private static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, FrontierIds.MOD_ID);

    static {
        ENCHANTMENTS.register(AdminToolsRegisters.DEATH_TOUCH.getPath(), DeathTouchEnchantment::new);
        ENCHANTMENTS.register(AdminToolsRegisters.INSPECT.getPath(), InspectEnchantment::new);
        ENCHANTMENTS.register(BossTellRegisters.INTUITION.getPath(), IntuitionEnchantment::new);
        ENCHANTMENTS.register(BossTellRegisters.RESONANCE.getPath(), ResonanceEnchantment::new);
    }

    private ForgeEnchantments() {
    }

    /** Puts the register on the mod bus. */
    public static void register(IEventBus modBus) {
        ENCHANTMENTS.register(modBus);
    }
}
