package com.arryn.frontiermode;

import com.arryn.frontiermode.border.common.bundle.BordersBundle;
import com.arryn.frontiermode.border.common.fixture.BordersFixture;
import com.arryn.frontiermode.border.common.fixture.NavigatorFixture;
import com.arryn.frontiermode.border.common.fixture.BorderCurveFixture;
import com.arryn.frontiermode.border.common.fixture.BorderPregenFixture;
import com.arryn.frontiermode.border.common.player.BorderPlayerBundle;
import com.arryn.frontiermode.border.common.player.BorderPlayerStatusFixture;
import com.arryn.frontiermode.border.common.player.WildsTellFixture;
import com.arryn.frontiermode.boss.common.bundle.BossBundle;
import com.arryn.frontiermode.boss.common.bundle.BossMobBundle;
import com.arryn.frontiermode.boss.common.bundle.BossPublicBundle;
import com.arryn.frontiermode.boss.common.fixture.BossPublicFixture;
import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.common.bundle.SentinelPublicBundle;
import com.arryn.frontiermode.boss.common.fixture.BossSentinelsFixture;
import com.arryn.frontiermode.boss.common.fixture.SentinelPublicFixture;
import com.arryn.frontiermode.boss.common.fixture.BossMobFixture;
import com.arryn.frontiermode.boss.common.fixture.BossTellFixture;
import com.arryn.frontiermode.keeper.common.bundle.KeeperBundle;
import com.arryn.frontiermode.keeper.common.fixture.KeeperFixture;
import com.arryn.frontiermode.structure.common.CairnFixture;
import com.arryn.frontiermode.structure.common.StructureBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.identity.FixtureKey;
import com.arryn.satchel.common.identity.JigKey;
import com.arryn.satchel.common.ingress.IngressKey;
import com.arryn.satchel.common.jig.level.LevelJig;
import com.arryn.satchel.common.jig.mob.MobJig;
import com.arryn.satchel.common.jig.player.PlayerJig;


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

    // RM_FRO_037 ("Brenda," Frontier Sickness epoch 1): WildsTellFixture -- Sick Wildlife's
    // cosmetic tell, a sibling fixture inside BORDER_PLAYER_BUNDLE alongside
    // BorderPlayerStatusFixture. See BorderModule.init() for the schema wiring and
    // wiki/frontiermode/design/wilds.md#sensory-design for the design.

    public static final FixtureKey<WildsTellFixture> WILDS_TELL =
            new FixtureKey<>(
                    "frontiermode:wilds_tell_fixture",
                    WildsTellFixture.class
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

    // FRO_145: BossPublicFixture -- the client-syncable "this entity is a boss" signal, in its own
    // BOTH-applicability jig. See BossModule.registerBossPublicJig() and boss.md's "Client-side visual marker".

    public static final BundleKey<BossPublicBundle> BOSS_PUBLIC_BUNDLE =
            new BundleKey<>(
                    "frontiermode:boss_public_bundle",
                    BossPublicBundle.class
            );

    public static final FixtureKey<BossPublicFixture> BOSS_PUBLIC =
            new FixtureKey<>(
                    "frontiermode:boss_public_fixture",
                    BossPublicFixture.class
            );

    public static final JigKey<LevelJig> BOSS_PUBLIC_JIG =
            new JigKey<>(
                    "frontiermode:boss_public_jig",
                    LevelJig.class
            );

    // RM_FRO_030 (Janice): BossTellFixture -- sibling fixture in BOSS_BUNDLE alongside
    // BossFixture itself. See BossModule.registerBossJig() for the schema wiring and
    // wiki/frontiermode/architecture/discovery-systems.md#environmental-tells for the design.

    public static final FixtureKey<BossTellFixture> BOSS_TELL =
            new FixtureKey<>(
                    "frontiermode:boss_tell_fixture",
                    BossTellFixture.class
            );

    // RM_FRO_029 (Gloria): BossSentinelsFixture -- third sibling fixture in BOSS_BUNDLE alongside
    // BossFixture/BossTellFixture. See BossModule.registerBossJig() for the schema wiring and
    // wiki/frontiermode/architecture/sentinels.md for the design.

    public static final FixtureKey<BossSentinelsFixture> BOSS_SENTINELS =
            new FixtureKey<>(
                    "frontiermode:boss_sentinels_fixture",
                    BossSentinelsFixture.class
            );

    // RM_FRO_060 ("Carole"): the Keeper's own records -- KeeperFixture in KeeperBundle on KEEPER_JIG, a
    // LevelJig of its own beside BOSS_JIG. See KeeperModule.init() for the registration and
    // wiki/frontiermode/architecture/keeper.md#the-keeper-record for the design.

    public static final BundleKey<KeeperBundle> KEEPER_BUNDLE =
            new BundleKey<>(
                    "frontiermode:keeper_bundle",
                    KeeperBundle.class
            );

    public static final FixtureKey<KeeperFixture> KEEPER =
            new FixtureKey<>(
                    "frontiermode:keeper_fixture",
                    KeeperFixture.class
            );

    public static final JigKey<LevelJig> KEEPER_JIG =
            new JigKey<>(
                    "frontiermode:keeper_jig",
                    LevelJig.class
            );


    // FRO_167: the client-visible sentinel record -- its own BOTH-applicability bundle and jig so
    // sentinel churn never resends the boss records. See BossModule.registerSentinelPublicJig().

    public static final BundleKey<SentinelPublicBundle> SENTINEL_PUBLIC_BUNDLE =
            new BundleKey<>(
                    "frontiermode:sentinel_public_bundle",
                    SentinelPublicBundle.class
            );

    public static final FixtureKey<SentinelPublicFixture> SENTINEL_PUBLIC =
            new FixtureKey<>(
                    "frontiermode:sentinel_public_fixture",
                    SentinelPublicFixture.class
            );

    public static final JigKey<LevelJig> SENTINEL_PUBLIC_JIG =
            new JigKey<>(
                    "frontiermode:sentinel_public_jig",
                    LevelJig.class
            );


    // FRO_254: names the bosstell ingress, which carries CompassPickup and its one consumer. Wired in
    // BossTellIngress.build(), registered from BossTellModule.init().

    public static final IngressKey BOSSTELL_INGRESS =
            new IngressKey("frontiermode:bosstell");

    // FRO_255: names the structure ingress, which carries CairnCapBroken and its one consumer. Wired in
    // StructureIngress.build(), registered from StructureModule.init().

    public static final IngressKey STRUCTURE_INGRESS =
            new IngressKey("frontiermode:structure");

    // FRO_120 (Cairn, RM_FRO_039 "Helen"): StructureModule's own third home -- not BossBundle,
    // not BossTellModule's bundle. See BossTellModule.init()-equivalent (StructureModule.init())
    // for the schema/config wiring and wiki/frontiermode/architecture/cairn.md for the design.

    public static final BundleKey<StructureBundle> STRUCTURE_BUNDLE =
            new BundleKey<>(
                    "frontiermode:structure_bundle",
                    StructureBundle.class
            );

    public static final FixtureKey<CairnFixture> CAIRN =
            new FixtureKey<>(
                    "frontiermode:cairn_fixture",
                    CairnFixture.class
            );

    public static final JigKey<LevelJig> STRUCTURE_JIG =
            new JigKey<>(
                    "frontiermode:structure_jig",
                    LevelJig.class
            );

    // FRO_253: names the spawn coordination ingress, which carries MobSpawnFinalizing and its one
    // consumer. Wired in SpawnIngress.build(), registered from SpawnCoordinator.init().

    public static final IngressKey SPAWN_COORDINATION =
            new IngressKey("frontiermode:spawn_coordination");

    // FRO_256: names the admin ingress, which carries the five admin module events. Wired in
    // AdminIngress.build(), registered from AdminToolsModule.init().

    public static final IngressKey ADMIN_INGRESS =
            new IngressKey("frontiermode:admin");

    // FRO_256: the Info report's one-consumer ingresses, one per module that adds a line to it. Each is
    // wired in the module's own <Module>Ingress.build(), registered from the module's init().

    public static final IngressKey BOSS_INGRESS =
            new IngressKey("frontiermode:boss");

    public static final IngressKey BORDER_INGRESS =
            new IngressKey("frontiermode:border");

    // FRO_259: the feral ingress also carries HostileJoining, WildsPlacementCheck, WildsPositionCheck,
    // FeralDrops and FeralExperience with their consumers. Wired in FeralIngress.build(), registered from
    // FeralModule.init(); the player tick is FeralPlayerTick, a Trinket on the player host, so no jig.

    public static final IngressKey FERAL_INGRESS =
            new IngressKey("frontiermode:feral");

    // FRO_258: the difficulty ingress also carries MobJoining, LadderHit and WolfTaming with their
    // consumers. Wired in DifficultyIngress.build(), registered from DifficultyModule.init().

    public static final IngressKey DIFFICULTY_INGRESS =
            new IngressKey("frontiermode:difficulty");

    // FRO_257: names the siting ingress, which carries WorldSpawnChoosing and its one consumer, the
    // starting-site search. Wired in SitingIngress.build(), registered from SitingModule.init().

    public static final IngressKey SITING_INGRESS =
            new IngressKey("frontiermode:siting");

    private FrontierKeys() {}
}
