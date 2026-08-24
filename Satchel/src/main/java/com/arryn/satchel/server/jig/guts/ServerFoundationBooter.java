package com.arryn.satchel.server.jig.guts;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.ASatchelFoundationBooter;
import com.arryn.satchel.common.jig.guts.LogicalFoundation;
import com.arryn.satchel.common.jig.guts.LogicalSideContext;
import com.arryn.satchel.common.jig.guts.ScopeEngine;
import com.arryn.satchel.common.newconfig.newnew.CompiledJigConfig;
import com.arryn.satchel.server.lifecycle.ServerForgeEgress;
import com.arryn.satchel.common.newconfig.newnew.JigConfigCompiler;
import net.minecraftforge.fml.LogicalSide;

import java.util.List;

public class ServerFoundationBooter extends ASatchelFoundationBooter {
    private static ServerFoundationBooter instance;


    private  ServerFoundationBooter() {
    }

    public void bindFoundation() {
        LogicalSideContext.bind(LogicalSide.SERVER);
    }

    public static ServerFoundationBooter INSTANCE() {
        if (instance == null) instance = new ServerFoundationBooter();
        return instance;
    }


    public void installFoundation() {

        LogicalFoundation foundation =
                new LogicalFoundation(LogicalSide.SERVER);

        Satchel.installFoundation(foundation);
        foundation.installBooter(this);
        foundation.installEgress(new ServerForgeEgress());
        foundation.installEventBus();
        foundation.installFoundationLifecycle();
        foundation.installScopeLifecycle();
        foundation.installBundleLifecycle();

        List<CompiledJigConfig> configs =
                JigConfigCompiler.compileForSide(LogicalSide.SERVER);

        foundation.installConfigs(configs);
    }

    @Override
    public ScopeEngine engine() {
        return new ScopeEngine_Server();
    }
}
