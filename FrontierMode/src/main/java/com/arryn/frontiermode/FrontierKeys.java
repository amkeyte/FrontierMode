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
import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.bosstell.common.bundle.TellBundle;
import com.arryn.frontiermode.bosstell.common.fixture.BossTellFixture;
import com.arryn.frontiermode.keeper.common.bundle.KeeperBundle;
import com.arryn.frontiermode.keeper.common.fixture.KeeperFixture;
import com.arryn.frontiermode.sentinel.common.bundle.SentinelBundle;
import com.arryn.frontiermode.sentinel.common.fixture.SentinelFixture;
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

    // RM_FRO_018 (Shirley): Boss entity/spawn system. Two independent jigs -- BOSS_JIG is LevelJig-scoped (one
    // record collection per level, persisted and networked), BOSS_MOB_JIG is MobJig-scoped (the mob-side abilities
    // of each currently-present boss entity, with no bundle). See BossModule.init() for the wiring, and
    // wiki/frontiermode/architecture/boss.md for the design these keys back.

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

    public static final JigKey<MobJig> BOSS_MOB_JIG =
            new JigKey<>(
                    "frontiermode:boss_mob_jig",
                    MobJig.class
            );

    // RM_FRO_030 (Janice), moved by RM_FRO_065 ("Geraldine"): the Tell module's own jig. TELL_JIG is a SERVER-only
    // LevelJig holding BossTellFixture in TellBundle. See TellJigConfig for the wiring and
    // wiki/frontiermode/architecture/discovery-systems.md#environmental-tells for the design.

    public static final BundleKey<TellBundle> TELL_BUNDLE =
            new BundleKey<>(
                    "frontiermode:tell_bundle",
                    TellBundle.class
            );

    public static final FixtureKey<BossTellFixture> BOSS_TELL =
            new FixtureKey<>(
                    "frontiermode:boss_tell_fixture",
                    BossTellFixture.class
            );

    public static final JigKey<LevelJig> TELL_JIG =
            new JigKey<>(
                    "frontiermode:tell_jig",
                    LevelJig.class
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


    // RM_FRO_066 ("Evelyn"): the Sentinel module's jig -- SentinelFixture (the synced record of the Sentinels present) in
    // SentinelBundle on SENTINEL_JIG, BOTH, networked and not persisted, with SentinelPresence on the server and the
    // client Trinket. Its own jig so Sentinel churn never resends the boss records. See SentinelModule.init() for the
    // registration and wiki/frontiermode/architecture/sentinels.md#sentinelfixture-the-synced-record for the design.

    public static final BundleKey<SentinelBundle> SENTINEL_BUNDLE =
            new BundleKey<>(
                    "frontiermode:sentinel_bundle",
                    SentinelBundle.class
            );

    public static final FixtureKey<SentinelFixture> SENTINEL =
            new FixtureKey<>(
                    "frontiermode:sentinel_fixture",
                    SentinelFixture.class
            );

    public static final JigKey<LevelJig> SENTINEL_JIG =
            new JigKey<>(
                    "frontiermode:sentinel_jig",
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

    // RM_FRO_064 ("Charlotte"): names the Keeper's ingress, which carries the consumer of Border's BorderPathGrown and
    // the two module events the Keeper's mob abilities hear. Wired in KeeperIngress.build(), registered from
    // KeeperModule.init().

    public static final IngressKey KEEPER_INGRESS =
            new IngressKey("frontiermode:keeper");

    // RM_FRO_066 ("Evelyn"): names the Sentinel module's ingress, which carries its consumers of the Keeper's
    // KeeperCreated, KeeperHit and KeeperDefeated and of the Info report's InfoRequest. Wired in
    // SentinelIngress.build(), registered from SentinelModule.init().

    public static final IngressKey SENTINEL_INGRESS =
            new IngressKey("frontiermode:sentinel");

    // RM_FRO_062 ("Joanne"): names core's ingress, which carries HitboxResize and its one consumer. Wired in
    // CoreIngress.build(), registered from CoreModule.init().

    public static final IngressKey CORE_INGRESS =
            new IngressKey("frontiermode:core");

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
