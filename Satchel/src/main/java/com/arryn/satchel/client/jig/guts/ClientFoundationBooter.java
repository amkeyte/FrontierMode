package com.arryn.satchel.client.jig.guts;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.ASatchelFoundationBooter;
import com.arryn.satchel.common.jig.guts.LogicalFoundation;
import com.arryn.satchel.common.jig.guts.LogicalSideContext;
import com.arryn.satchel.common.jig.guts.ScopeEngine;
import com.arryn.satchel.common.newconfig.newnew.CompiledJigConfig;
import com.arryn.satchel.common.newconfig.newnew.JigConfigCompiler;
import net.minecraftforge.fml.LogicalSide;

import java.util.List;

public final class ClientFoundationBooter extends ASatchelFoundationBooter {

    private static ClientFoundationBooter instance;

    private ClientFoundationBooter() {}

    public static ClientFoundationBooter INSTANCE() {
        if (instance == null) {
            instance = new ClientFoundationBooter();
        }
        return instance;
    }

    /* =============================================================
     * Side binding
     * ========================================================== */

    @Override
    public void bindFoundation() {
        LogicalSideContext.bind(LogicalSide.CLIENT);
    }

    /* =============================================================
     * Foundation install
     * ========================================================== */

    @Override
    public void installFoundation() {

        LogicalFoundation foundation =
                new LogicalFoundation(LogicalSide.CLIENT);

        // Make foundation globally visible
        Satchel.installFoundation(foundation);

        // Install booter FIRST so engine() is available
        foundation.installBooter(this);

        // Glue-only installs (no logic) -- must precede installConfigs(): it installs
        // each jig's EventHandlers onto the event bus as it's processed, so the bus has
        // to exist first. Matches ServerFoundationBooter's order (SAT_011).
        foundation.installEventBus();
        foundation.installFoundationLifecycle();
        foundation.installScopeLifecycle();
        foundation.installBundleLifecycle();

        // Compile + install config for this side
        List<CompiledJigConfig> configs =
                JigConfigCompiler.compileForSide(LogicalSide.CLIENT);

        foundation.installConfigs(configs);

        // NOTE:
        // No jig materialization
        // No scope introduction
        // No lifecycle firing
        // That all waits for real client world events
    }

    /* =============================================================
     * Engine
     * ========================================================== */

    @Override
    public ScopeEngine engine() {
        return new ScopeEngine_Client();
    }
}
