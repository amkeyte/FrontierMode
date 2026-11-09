package com.arryn.frontiermode.border;

import com.arryn.frontiermode.border.server.BorderIngress;
import com.arryn.frontiermode.border.server.commands.BorderCommands;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * Entry point for the Border module (wiki/frontiermode/architecture/border.md): the foundation every other
 * module builds on, so it initializes first. The module is a root: registration lines and command registration
 * only.
 */
public final class BorderModule {
    private BorderModule() {
    }

    public static void init() {
        OUT.info("[Border] init(): Module initiating.");
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> BorderIngress.build());
        Satchel.registerJig(JigPolicies.SideApplicability.BOTH, () -> BorderJig.build());
        Satchel.registerJig(JigPolicies.SideApplicability.SERVER, () -> BorderPlayerJig.build());
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        BorderCommands.register(event.getDispatcher());
    }
}
