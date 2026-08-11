package com.arryn.satchel.server.jig.guts.model;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.LogicalFoundation;
import com.arryn.satchel.common.jig.guts.LogicalSideContext;
import com.arryn.satchel.common.jig.model.ModelScope;
import com.arryn.satchel.common.jig.model.ModelSource;
import net.minecraftforge.fml.LogicalSide;

public final class ServerModelIngress {

    private ServerModelIngress() {}

    /* =============================================================
     * LOAD
     * ========================================================== */

    public static void onModelSourceLoad(ModelSource source) {
        LogicalSideContext.bind(LogicalSide.SERVER);

        LogicalFoundation foundation = Satchel.require();

        foundation.introduceSource(source);
    }


    /* =============================================================
     * TICK
     * ========================================================== */

    public static void onModelSourceTick(ModelSource source) {
        LogicalSideContext.bind(LogicalSide.SERVER);

        LogicalFoundation foundation = Satchel.require();

        var jigInfo = foundation.requireJigInfo(source.key());

        var scope = new ModelScope(source);

        jigInfo.scopeInfo(scope)
                .ifPresent(info ->
                        jigInfo.jig.onTick(info)
                );
    }

    /* =============================================================
     * UNLOAD
     * ========================================================== */

    public static void onModelSourceUnload(ModelSource source) {
        LogicalSideContext.bind(LogicalSide.SERVER);

        LogicalFoundation foundation = Satchel.require();

        var jigInfo = foundation.requireJigInfo(source.key());

        var scope = new ModelScope(source);

        jigInfo.scopeInfo(scope)
                .ifPresent(info ->
                        jigInfo.jig.onUnload(info)
                );
    }
}
