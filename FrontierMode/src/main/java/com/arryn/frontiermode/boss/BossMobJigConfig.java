package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.satchel.common.jig.config.JigBundles;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.mob.MobJigConfig;
import com.arryn.satchel.common.trinket.DynamicTrinket;

import java.util.List;

/**
 * Builds {@code BOSS_MOB_JIG} (wiki/frontiermode/architecture/boss.md § Module wiring): the mob jig that holds the
 * mob-side abilities, {@code BOTH} like {@code BOSS_JIG} and registered after it. Each side's roster pushes that side's
 * interest, and the kind subscribes its abilities once, each on the side it applies to. The jig holds no bundle and no
 * handler on {@code MobGainedInterest} or {@code MobLostInterest}: the mob scope's Trinket attach, {@code Leaving} and
 * detach are the hooks.
 */
final class BossMobJigConfig {

    private BossMobJigConfig() {
    }

    static MobJigConfig build() {
        MobJigConfig config = new MobJigConfig(FrontierKeys.BOSS_MOB_JIG);
        config.bundles().schema(new JigBundles.Schema<>(List.of()));
        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults().withTick(true));

        // The dynamic host holds the Boss and the kind's abilities as subscribers and forwards Leaving to them;
        // MobAbilities, declared after it, finds the mob's Boss through the roster and subscribes them.
        config.trinkets().declare(DynamicTrinket.class, DynamicTrinket::new);
        config.trinkets().declare(MobAbilities.class, MobAbilities::new);
        return config;
    }
}
