package com.arryn.frontiermode;

import com.arryn.frontiermode.border.common.bundle.BordersBundle;
import com.arryn.frontiermode.border.common.fixture.BordersFixture;
import com.arryn.frontiermode.border.common.fixture.NavigatorFixture;
import com.arryn.frontiermode.border.common.fixture.BorderCurveFixture;
import com.arryn.frontiermode.border.common.fixture.BorderPregenFixture;
import com.arryn.frontiermode.border.common.player.BorderPlayerBundle;
import com.arryn.frontiermode.border.common.player.BorderPlayerStatusFixture;
import com.arryn.frontiermode.boss.common.bundle.BossBundle;
import com.arryn.frontiermode.boss.common.bundle.BossMobBundle;
import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.common.fixture.BossMobFixture;
import com.arryn.frontiermode.boss.common.fixture.BossTellFixture;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.identity.FixtureKey;
import com.arryn.satchel.common.identity.JigKey;
import com.arryn.satchel.common.jig.level.LevelJig;
import com.arryn.satchel.common.jig.mob.MobJig;
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

    // RM_FRO_026 ("Dorothy"): Navigator target-resolution fixture -- a sibling fixture inside
    // BORDERS_BUNDLE itself, not a new bundle/jig. See BorderModule.init() for the schema wiring
    // and wiki/frontiermode/architecture/discovery-systems.md#navigation-lives-in-border for the
    // design this backs.

    public static final FixtureKey<NavigatorFixture> NAVIGATOR =
            new FixtureKey<>(
                    "frontiermode:navigator_fixture",
                    NavigatorFixture.class
            );

    // RM_FRO_027 ("Janet"): BorderCurve intensity-curve fixture -- another sibling fixture inside
    // BORDERS_BUNDLE. See BorderModule.init() for the schema wiring and
    // wiki/frontiermode/architecture/border-curve.md for the design this backs.

    public static final FixtureKey<BorderCurveFixture> CURVE =
            new FixtureKey<>(
                    "frontiermode:curve_fixture",
                    BorderCurveFixture.class
            );

    // RM_FRO_028 ("Diane"): BorderPregen terrain pregeneration fixture -- a fourth sibling
    // fixture inside BORDERS_BUNDLE. See BorderModule.init() for the schema wiring and
    // wiki/frontiermode/architecture/border-pregeneration.md for the design this backs.

    public static final FixtureKey<BorderPregenFixture> PREGEN =
            new FixtureKey<>(
                    "frontiermode:pregen_fixture",
                    BorderPregenFixture.class
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

    // RM_FRO_018 (Shirley): Boss entity/spawn system. Two independent jig configs -- BossFixture
    // is LevelJig-scoped (one record collection per level, persisted), BossMobFixture is
    // MobJig-scoped (one live view per currently-materialized boss entity, not persisted). See
    // BossModule.init() for the schema/config wiring, and wiki/frontiermode/architecture/boss.md
    // for the design these keys back.

    public static final BundleKey<BossBundle> BOSS_BUNDLE =
            new BundleKey<>(
                    "frontiermode:boss_bundle",
                    BossBundle.class
            );

    public static final FixtureKey<BossFixture> BOSS =
            new FixtureKey<>(
                    "frontiermode:boss_fixture",
                    BossFixture.class
            );

    public static final JigKey<LevelJig> BOSS_JIG =
            new JigKey<>(
                    "frontiermode:boss_jig",
                    LevelJig.class
            );

    public static final BundleKey<BossMobBundle> BOSS_MOB_BUNDLE =
            new BundleKey<>(
                    "frontiermode:boss_mob_bundle",
                    BossMobBundle.class
            );

    public static final FixtureKey<BossMobFixture> BOSS_MOB =
            new FixtureKey<>(
                    "frontiermode:boss_mob_fixture",
                    BossMobFixture.class
            );

    public static final JigKey<MobJig> BOSS_MOB_JIG =
            new JigKey<>(
                    "frontiermode:boss_mob_jig",
                    MobJig.class
            );

    // RM_FRO_030 (Janice): BossTellFixture -- sibling fixture in BOSS_BUNDLE alongside
    // BossFixture itself. See BossModule.registerBossJig() for the schema wiring and
    // wiki/frontiermode/architecture/discovery-systems.md#environmental-tells for the design.

    public static final FixtureKey<BossTellFixture> BOSS_TELL =
            new FixtureKey<>(
                    "frontiermode:boss_tell_fixture",
                    BossTellFixture.class
            );



    private FrontierKeys() {}
}
