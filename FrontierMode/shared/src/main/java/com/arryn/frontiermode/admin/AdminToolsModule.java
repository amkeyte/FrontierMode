package com.arryn.frontiermode.admin;

import com.arryn.frontiermode.admin.common.DebugViewTick;
import com.arryn.frontiermode.admin.common.SoulboundStash;
import com.arryn.frontiermode.admin.server.commands.AdminCommands;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.trinket.Order;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;

/**
 * Entry point for the small admin/debug tool set: a plain stick carrying an admin-only enchantment,
 * handed out by {@code /admin}. Its own top-level module, sibling to {@code BossTellModule} and
 * {@code StructureModule} -- not boss, border or structure content. A root and nothing more: the
 * ingress and the two Trinkets, and the {@code /admin} command registration.
 * It registers no jig. Consumer bodies are {@link AdminToolHandlers}; the report is the Info report in
 * {@code core}. See wiki admin-tools.md.
 */
public final class AdminToolsModule {

    private AdminToolsModule() {
    }

    public static void init() {
        Satchel.contributePlayerTrinket("frontiermode:admin", JigPolicies.SideApplicability.SERVER, Order.NORMAL,
                SoulboundStash.class, () -> new SoulboundStash());
        Satchel.contributePlayerTrinket("frontiermode:admin", JigPolicies.SideApplicability.CLIENT, Order.NORMAL,
                DebugViewTick.class, () -> new DebugViewTick());
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        AdminCommands.register(dispatcher);
    }
}
