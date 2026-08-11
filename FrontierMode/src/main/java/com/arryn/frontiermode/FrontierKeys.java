package com.arryn.frontiermode;

import com.arryn.frontiermode.border.common.bundle.BordersBundle;
import com.arryn.frontiermode.border.common.fixture.BordersFixture;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.identity.FixtureKey;
import com.arryn.satchel.common.identity.JigKey;
import com.arryn.satchel.common.jig.guts.SatchelJigRegistrar;
import com.arryn.satchel.common.jig.level.LevelJig;
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



    private FrontierKeys() {}
}
