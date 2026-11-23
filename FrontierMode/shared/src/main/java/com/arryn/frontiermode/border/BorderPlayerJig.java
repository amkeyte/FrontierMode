package com.arryn.frontiermode.border;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.border.common.player.BorderPlayerBundle;
import com.arryn.frontiermode.border.common.player.BorderPlayerStatusFixture;
import com.arryn.frontiermode.border.common.player.WildsTellFixture;
import com.arryn.frontiermode.border.server.player.BorderPlayerTick;
import com.arryn.satchel.common.jig.config.JigBundles;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.player.PlayerJigConfig;
import com.arryn.satchel.common.jig.player.PlayerScope;

import java.util.List;

/**
 * Builds {@code BORDER_PLAYER_JIG}: a {@code PlayerJigConfig} holding {@link BorderPlayerBundle}, the per-player
 * border status and Wilds tell (border.md § Runtime wiring), and declaring {@link BorderPlayerTick}. Registered as a
 * {@code SERVER} factory from {@code BorderModule.init()}. Per-player state is identity-tied, so it is a real
 * {@code PlayerScope} and follows the player across dimensions. Neither persisted nor networked: the status is
 * recomputed every tick, so there is no capability or persistence override.
 */
final class BorderPlayerJig {

    private BorderPlayerJig() {
    }

    static PlayerJigConfig build() {
        var borderPlayerFixture =
                new JigBundles.FixtureDecl<>(
                        FrontierKeys.BORDER_PLAYER_STATUS,
                        BorderPlayerStatusFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );

        // Sick Wildlife's cosmetic tell. Ticks through the fixture dispatch once this jig ticks.
        var wildsTellFixture =
                new JigBundles.FixtureDecl<>(
                        FrontierKeys.WILDS_TELL,
                        WildsTellFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );

        var borderPlayerBundle =
                new JigBundles.BundleDecl<>(
                        FrontierKeys.BORDER_PLAYER_BUNDLE,
                        (PlayerScope scope) -> new BorderPlayerBundle(scope, FrontierKeys.BORDER_PLAYER_BUNDLE),
                        List.of(borderPlayerFixture, wildsTellFixture)
                );

        // PlayerJigConfig already pins the jig's types, scope resolver and uuid determiner, and its lifecycle
        // preset already ticks; only the bundles and the Trinket are per-module. withTick(true) stays: the
        // fixtures' own onJigTick (WildsTellFixture) rides the jig's tick.
        PlayerJigConfig config = new PlayerJigConfig(FrontierKeys.BORDER_PLAYER_JIG);
        config.bundles().schema(new JigBundles.Schema<>(List.of(borderPlayerBundle)));
        config.execution().lifecycle(JigPolicies.Lifecycle.defaults().withTick(true));
        // The jig is SERVER only, so the Trinket needs no side guard.
        config.trinkets().declare(BorderPlayerTick.class, BorderPlayerTick::new);
        return config;
    }
}
