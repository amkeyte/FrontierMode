package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.core.EntityScales;
import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.server.commands.BossCommands;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * Entry point for initializing and hooking the Boss subsystem. Depends on Border -- must be
 * initialized after {@code BorderModule.init()}, never the reverse. See
 * wiki/frontiermode/architecture/boss.md for the design. The module is a root: one line per config, each config a class
 * beside its handlers ({@link BossJigConfig}, {@link BossMobJigConfig}), the commands, and the registration of
 * {@link BossScaleSource}.
 *
 * <p><b>Known limitation:</b> a boss removed without a wired {@code LivingDeathEvent} (bare
 * {@code /kill}, external world editing, unrelated-mod bug) is not detected -- {@link BossFixture}
 * keeps {@code alive: true} and nothing here clears it. Distinguishing that from a chunk unload
 * needs either a real death signal or a "expected but absent for N ticks" heuristic.
 */
public final class BossModule {

    private BossModule() {
    }

    public static void init() {
        OUT.info("[Boss] init(): Module initiating.");
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> BossIngress.build());
        Satchel.registerJig(JigPolicies.SideApplicability.BOTH, () -> BossJigConfig.build());
        Satchel.registerJig(JigPolicies.SideApplicability.BOTH, () -> BossMobJigConfig.build());
        EntityScales.register(new BossScaleSource());
    }

    /** Registers {@code /boss} commands. */
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        BossCommands.register(event.getDispatcher());
    }
}
