package com.arryn.frontiermode.bosstell;

import com.arryn.frontiermode.bosstell.common.CompassRefresh;
import com.arryn.frontiermode.bosstell.server.commands.BossTellCommands;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.trinket.Order;
import com.arryn.satchel.common.util.out.OUT;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;

/**
 * Entry point for {@code BossTellModule} -- the Admin Compass and
 * the shared core underneath it, per
 * wiki/frontiermode/architecture/special-compass.md#lives-in-bosstellmodule-not-bossbundle-and-not-a-bordermodule-file.
 * Its own top-level module, sibling to {@code BorderModule}/{@code BossModule}/{@code FeralModule}
 * -- not folded into either, since the compass has no {@code BossBundle}-hosted fixture to reach
 * and isn't a Border-core concept (see that page's own reasoning).
 *
 * <p>Item and entity-type registration lives in {@link BossTellRegisters}, not on the mod's own
 * {@code FrontierRegisters} -- project owner's call, per special-compass.md's "Registration" section: this item
 * belongs to the module, not the mod's general cross-cutting infrastructure. The enchantment ids are there too, and
 * each line registers the enchantments in its own form.
 *
 * <p>The module is a root: the ingress and the Trinket
 * ({@link BossTellIngress}, {@code CompassRefresh}) and command registration. It registers no jig.
 */
public final class BossTellModule {

    private BossTellModule() {
    }

    public static void init() {
        OUT.info("[BossTell] init(): Module initiating.");
        Satchel.registerJig(JigPolicies.SideApplicability.SERVER, () -> TellJigConfig.build());
        Satchel.contributePlayerTrinket("frontiermode:bosstell", JigPolicies.SideApplicability.SERVER, Order.NORMAL,
                CompassRefresh.class, () -> new CompassRefresh());
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        BossTellCommands.register(dispatcher);
    }
}
