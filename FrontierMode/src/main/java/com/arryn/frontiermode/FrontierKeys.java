package com.arryn.frontiermode;

import com.arryn.frontiermode.border.common.bundle.BordersBundle;
import com.arryn.frontiermode.border.common.fixture.BordersFixture;
import com.arryn.frontiermode.border.common.player.BorderPlayerBundle;
import com.arryn.frontiermode.border.common.player.BorderPlayerStatusFixture;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.identity.FixtureKey;
import com.arryn.satchel.common.identity.JigKey;
import com.arryn.satchel.common.jig.level.LevelJig;
import com.arryn.satchel.common.jig.player.PlayerJig;
import net.minecraftforge.fml.LogicalSide;


public final class FrontierKeys {

    public static final BundleKey<BordersBundle> BORDERS_BUNDLE =
            new BundleKey<>(
                    "frontiermode:borders_bundle",
                    BordersBundle.class
            );

    public static final FixtureKey<BordersFixture> BORDERS =
            new FixtureKey<>(
                    "frontiermode:borders_fixture",
                    BordersFixture.class
            );

    public static final JigKey<LevelJig> BORDERS_JIG =
            new JigKey<>(
                    "frontiermode:borders_jig",
                    LevelJig.class
            );

    // RM_FRO_006 (Sandra): per-player border evaluation, PlayerJig-scoped (see BorderModule.init()
    // for the schema/config wiring these three keys back).

    public static final BundleKey<BorderPlayerBundle> BORDER_PLAYER_BUNDLE =
            new BundleKey<>(
                    "frontiermode:border_player_bundle",
                    BorderPlayerBundle.class
            );

    public static final FixtureKey<BorderPlayerStatusFixture> BORDER_PLAYER_STATUS =
            new FixtureKey<>(
                    "frontiermode:border_player_status_fixture",
                    BorderPlayerStatusFixture.class
            );

    public static final JigKey<PlayerJig> BORDER_PLAYER_JIG =
            new JigKey<>(
                    "frontiermode:border_player_jig",
                    PlayerJig.class
            );



    private FrontierKeys() {}
}
