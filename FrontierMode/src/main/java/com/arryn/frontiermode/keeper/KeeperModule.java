package com.arryn.frontiermode.keeper;

import com.arryn.frontiermode.border.common.navigator.TargetResolverRegistry;
import com.arryn.frontiermode.border.common.navigator.TargetType;
import com.arryn.frontiermode.boss.BossAPI;
import com.arryn.frontiermode.boss.BossPositionListeners;
import com.arryn.frontiermode.boss.common.BossKinds;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.util.out.OUT;

/**
 * Entry point for the Keeper module (wiki/frontiermode/architecture/keeper.md): the Boss base's first kind.
 * Depends on Boss and Border, so it initializes after {@code BorderModule.init()} and
 * {@code BossModule.init()}, never before. The module is a root: registration lines only, and the Boss resolver
 * Border's navigator asks for.
 */
public final class KeeperModule {

    private KeeperModule() {
    }

    public static void init() {
        OUT.info("FrontierMode - Keeper Module Initiating.");
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> KeeperIngress.build());
        Satchel.registerJig(JigPolicies.SideApplicability.BOTH, () -> KeeperJigConfig.build());
        BossKinds.register(new KeeperKind());
        BossPositionListeners.register(KeeperPositionListener::onBossPositioned);
        registerNavigatorResolver();
    }

    // TargetType.BOSS resolver: where a boss is, for Border's navigator.
    private static void registerNavigatorResolver() {
        TargetResolverRegistry.register(TargetType.BOSS, (level, bossId) ->
                BossAPI.bosses(level)
                        .flatMap(fixture -> fixture.CRUD.get(bossId))
                        .map(BossRecord::position));
    }
}
