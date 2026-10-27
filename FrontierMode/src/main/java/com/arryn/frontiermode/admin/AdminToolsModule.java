package com.arryn.frontiermode.admin;

import com.arryn.frontiermode.FrontierMode;
import com.arryn.frontiermode.admin.common.DebugViewTick;
import com.arryn.frontiermode.admin.common.SoulboundStash;
import com.arryn.frontiermode.admin.common.enchantments.DeathTouchEnchantment;
import com.arryn.frontiermode.admin.common.enchantments.InspectEnchantment;
import com.arryn.frontiermode.admin.server.commands.AdminCommands;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.trinket.Order;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Entry point for the small admin/debug tool set: a plain stick carrying an admin-only enchantment,
 * handed out by {@code /admin}. Its own top-level module, sibling to {@code BossTellModule} and
 * {@code StructureModule} -- not boss, border or structure content. A root and nothing more: the
 * enchantment registers, the ingress and the two Trinkets, and the {@code /admin} command registration.
 * It registers no jig. Consumer bodies are {@link AdminToolHandlers}; the report is the Info report in
 * {@code core}. See wiki admin-tools.md.
 */
public final class AdminToolsModule {

    private AdminToolsModule() {
    }

    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, FrontierMode.MODID);

    public static final RegistryObject<Enchantment> DEATH_TOUCH =
            ENCHANTMENTS.register("death_touch", DeathTouchEnchantment::new);

    public static final RegistryObject<Enchantment> INSPECT =
            ENCHANTMENTS.register("inspect", InspectEnchantment::new);

    public static void init() {
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> AdminIngress.build());
        Satchel.contributePlayerTrinket("frontiermode:admin", JigPolicies.SideApplicability.SERVER, Order.NORMAL,
                SoulboundStash.class, () -> new SoulboundStash());
        Satchel.contributeLevelTrinket("frontiermode:admin", JigPolicies.SideApplicability.CLIENT, Order.NORMAL,
                DebugViewTick.class, () -> new DebugViewTick());
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        AdminCommands.register(event.getDispatcher());
    }
}
