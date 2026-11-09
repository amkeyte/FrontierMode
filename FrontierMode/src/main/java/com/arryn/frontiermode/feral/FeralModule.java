package com.arryn.frontiermode.feral;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.feral.server.commands.FeralCommands;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.trinket.Order;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * Entry point for Feral -- corrupted hostile mobs native to the Wilds, as tough
 * as the path tip's position (the highest level the player has attained) and, while that mechanic is
 * on, denser the farther out. See wiki/frontiermode/architecture/feral.md for the technical shape.
 *
 * <p><b>Its own module, deliberately, not folded into {@code BorderModule}.</b> Feral reads
 * {@link BorderAPI#frontierDistanceAt(net.minecraft.world.level.Level, net.minecraft.core.BlockPos)}
 * (Border) through its public API, and depends on Border, never the reverse. Kept separate on purpose:
 * this feature is expected to grow, and containment now is cheaper than untangling it from Border's
 * files later.
 *
 * <p>Feral persists nothing of its own. Which mobs are Feral, and which of those are pack members, is the
 * record on the mob ({@link FeralData}); the Feral mobs in the level now are {@link FeralPresence}, a
 * Trinket on the level host; everything else is resolved live.
 *
 * <p>The module is a root: the ingress ({@link FeralIngress}), the two Trinkets ({@link FeralPresence}
 * and {@link FeralPlayerTick}) and command registration, which is Feral's one direct Forge touch
 * ({@link #onRegisterCommands}, a permanently-accepted exception per
 * wiki/satchel/spec/forge-integration.md). It registers no jig. The two entry points
 * {@code SpawnCoordinator} calls are {@link FeralAPI}.
 */
public final class FeralModule {

    private FeralModule() {
    }

    public static void init() {
        OUT.info("[Feral] init(): Module initiating.");
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> FeralIngress.build());
        Satchel.contributeLevelTrinket("frontiermode:feral", JigPolicies.SideApplicability.SERVER, Order.EARLY,
                FeralPresence.class, () -> new FeralPresence());
        Satchel.contributePlayerTrinket("frontiermode:feral", JigPolicies.SideApplicability.SERVER, Order.NORMAL,
                FeralPlayerTick.class, () -> new FeralPlayerTick());
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        FeralCommands.register(event.getDispatcher());
    }
}
