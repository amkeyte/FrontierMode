package com.arryn.frontiermode.structure;

import com.arryn.frontiermode.boss.BossPositionListeners;
import com.arryn.frontiermode.structure.server.commands.StructureCommands;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.util.out.OUT;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;

/**
 * Entry point for {@code StructureModule} -- Cairn's own third
 * home, not {@code BossBundle} and not {@code BossTellModule}, per
 * wiki/frontiermode/architecture/cairn.md#module-structuremodule-not-bossbundle-or-bosstellmodule.
 * Reaches {@code BossAPI}/{@code BorderAPI} from outside, the same one-directional relationship
 * Feral/BossTellModule already have with both -- Boss/Border never import this module. See
 * {@link com.arryn.frontiermode.boss.BossPositionListeners} for how record creation still reacts
 * to a boss's position finalizing without Boss importing this module directly.
 *
 * <p>The module is a root: the jig and ingress registrations, and command
 * registration. The Cairn cap is registered in {@link StructureRegisters}. Placement is {@link CairnPlacementTick} and {@link CairnPlacer}, the pulse is
 * {@link CairnPulse}, the cap-break reaction is {@link CairnHandlers}, the boss-positioned reaction is
 * {@link StructureBossHandlers}, and the Cairn records are read through {@link StructureAPI}.
 */
public final class StructureModule {

    private StructureModule() {
    }

    public static void init() {
        OUT.info("[Structure] init(): Module initiating.");
        Satchel.registerJig(JigPolicies.SideApplicability.SERVER, () -> StructureJigConfig.build());
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> StructureIngress.build());
        BossPositionListeners.register(StructureBossHandlers::onBossPositioned);
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        StructureCommands.register(dispatcher);
    }
}
