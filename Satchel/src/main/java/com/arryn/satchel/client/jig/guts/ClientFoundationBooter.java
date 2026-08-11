package com.arryn.satchel.client.jig.guts;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.ASatchelFoundationBooter;
import com.arryn.satchel.common.jig.guts.LogicalFoundation;
import com.arryn.satchel.common.jig.guts.LogicalSideContext;
import com.arryn.satchel.common.jig.guts.ScopeEngine;
import com.arryn.satchel.common.newconfig.newnew.CompiledJigConfig;
import com.arryn.satchel.common.newconfig.newnew.JigConfigCompiler;
import net.minecraftforge.fml.LogicalSide;

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

        // Compile + install config for this side
        CompiledJigConfig config =
                JigConfigCompiler.compileForSide(LogicalSide.CLIENT);

        foundation.installConfigs(config);

        // Glue-only installs (no logic)
        foundation.installEventBus();
        foundation.installFoundationLifecycle();
        foundation.installScopeLifecycle();
        foundation.installBundleLifecycle();

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
