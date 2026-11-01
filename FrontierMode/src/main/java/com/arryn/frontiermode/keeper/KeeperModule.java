package com.arryn.frontiermode.keeper;

import com.arryn.frontiermode.boss.common.BossKinds;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.util.out.OUT;

/**
 * Entry point for the Keeper module (wiki/frontiermode/architecture/keeper.md): the Boss base's first kind.
 * Depends on Boss and Border, so it initializes after {@code BorderModule.init()} and
 * {@code BossModule.init()}, never before. The module is a root: registration lines only.
 */
public final class KeeperModule {

    private KeeperModule() {
    }

    public static void init() {
        OUT.info("FrontierMode - Keeper Module Initiating.");
        Satchel.registerJig(JigPolicies.SideApplicability.BOTH, () -> KeeperJigConfig.build());
        BossKinds.register(new KeeperKind());
    }
}
