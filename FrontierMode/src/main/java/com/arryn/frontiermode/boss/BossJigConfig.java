package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.boss.common.bundle.BossBundle;
import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.satchel.common.jig.config.JigBundles;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.level.LevelJigConfig;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.lifecycle.EventHandlers;
import com.arryn.satchel.common.lifecycle.MobDied;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import com.arryn.satchel.common.trinket.DynamicTrinket;
import com.arryn.satchel.common.trinket.Order;

import java.util.List;

/**
 * Builds {@code BOSS_JIG} (wiki/frontiermode/architecture/boss.md § Module wiring): a {@code LevelJigConfig} holding the
 * base records in {@link BossBundle}, persisted and networked, with the roster and the record-abilities Trinket.
 * Registered as a {@code BOTH} factory from {@code BossModule.init()}, so each side builds its own config and the
 * registration supplies the side.
 */
final class BossJigConfig {

    private BossJigConfig() {
    }

    static LevelJigConfig build() {
        var bossFixture =
                new JigBundles.FixtureDecl<>(
                        FrontierKeys.BOSS,
                        BossFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );
        var bossBundle =
                new JigBundles.BundleDecl<>(
                        FrontierKeys.BOSS_BUNDLE,
                        (LevelScope scope) -> new BossBundle(scope, FrontierKeys.BOSS_BUNDLE),
                        List.of(bossFixture)
                );

        EventHandlers eventHandlers =
                EventHandlers.builder()
                        // MobDied is a bare, unscoped signal -- declared here only because this is the topically
                        // closest config, not a claim it is BOSS_JIG-scoped.
                        .on(MobDied.class, BossJigHandlers::onMobDied)
                        // The client roster cache holds the last level: evict it when the level's scope unloads
                        // (new-module-checklist.md item 9). The per-side bus fires this for every scope.
                        .on(ScopeEvent.Unloaded.class, BossMarkers::onScopeUnloaded)
                        .build();

        LevelJigConfig config = new LevelJigConfig(FrontierKeys.BOSS_JIG);
        config.bundles().schema(new JigBundles.Schema<>(List.of(bossBundle)));
        // BossFixture is the sole durable record of boss identity -- needs real persistence -- and it is
        // networked: the client receives the fixture, positions included, and runs the same code over it
        // (boss.md § Module wiring). Networking is declared in two places, both required -- the second capability
        // flag and BossBundle.required().
        config.policies().capabilities(new JigPolicies.Capabilities(true, true, false));
        config.policies().persistence(
                new JigPolicies.Persistence(
                        true,
                        JigPolicies.FlushPolicy.EXECUTION_PULSE,
                        JigPolicies.MissingDataPolicy.WARN));
        // withExecutionPulse(true) is required alongside withTick(true) -- persistence flush only
        // runs from inside onExecutionPulse.
        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults().withTick(true).withExecutionPulse(true))
                .eventHandlers(eventHandlers);
        // The roster ticks before the mob abilities (Module Graph § Trinket tick order).
        config.trinkets().declare(BossRoster.class, BossRoster::new).order(Order.NORMAL - 10);
        // The record host, then the record-abilities Trinket that the roster hands each Boss (boss.md § Abilities).
        config.trinkets().declare(DynamicTrinket.class, DynamicTrinket::new);
        config.trinkets().declare(RecordAbilities.class, RecordAbilities::new);

        return config;
    }
}
