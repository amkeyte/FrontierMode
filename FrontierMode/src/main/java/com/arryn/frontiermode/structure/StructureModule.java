package com.arryn.frontiermode.structure;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.FrontierMode;
import com.arryn.frontiermode.boss.BossPositionListeners;
import com.arryn.frontiermode.structure.common.CairnFixture;
import com.arryn.frontiermode.structure.common.StructureBundle;
import com.arryn.frontiermode.structure.common.blocks.CairnCapBlock;
import com.arryn.frontiermode.structure.server.commands.StructureCommands;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.config.JigBundles;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.level.LevelJigConfig;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/**
 * Entry point for {@code StructureModule} -- Cairn's own third
 * home, not {@code BossBundle} and not {@code BossTellModule}, per
 * wiki/frontiermode/architecture/cairn.md#module-structuremodule-not-bossbundle-or-bosstellmodule.
 * Reaches {@code BossAPI}/{@code BorderAPI} from outside, the same one-directional relationship
 * Feral/BossTellModule already have with both -- Boss/Border never import this module. See
 * {@link com.arryn.frontiermode.boss.BossPositionListeners} for how record creation still reacts
 * to a boss's position finalizing without Boss importing this module directly.
 *
 * <p>The module is a root: block registration, the jig and ingress registrations, and command
 * registration. Placement is {@link CairnPlacementTick} and {@link CairnPlacer}, the pulse is
 * {@link CairnPulse}, the cap-break reaction is {@link CairnHandlers}, the boss-positioned reaction is
 * {@link StructureBossHandlers}, and the Cairn records are read through {@link StructureAPI}.
 */
public final class StructureModule {

    private StructureModule() {
    }

    // ------------------------------------------------------------
    // Block registration -- Chiseled Smooth Basalt is a new asset, out of scope (cairn.md); the
    // real block copies vanilla Chiseled Stone Bricks' properties as the stand-in.
    // ------------------------------------------------------------

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, FrontierMode.MODID);

    public static final RegistryObject<Block> CAIRN_CAP =
            BLOCKS.register("cairn_cap", () -> new CairnCapBlock(
                    BlockBehaviour.Properties.copy(Blocks.CHISELED_STONE_BRICKS).noLootTable()));

    public static void init() {
        OUT.info("FrontierMode - StructureModule Initiating.");
        Satchel.registerJig(JigPolicies.SideApplicability.SERVER, () -> buildStructureJig());
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> StructureIngress.build());
        BossPositionListeners.register(StructureBossHandlers::onBossPositioned);
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        StructureCommands.register(event.getDispatcher());
    }

    // ------------------------------------------------------------
    // Jig wiring
    // ------------------------------------------------------------

    private static LevelJigConfig buildStructureJig() {
        var cairnFixture =
                new JigBundles.FixtureDecl<>(
                        FrontierKeys.CAIRN,
                        CairnFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );

        var structureBundle =
                new JigBundles.BundleDecl<>(
                        FrontierKeys.STRUCTURE_BUNDLE,
                        (LevelScope scope) -> new StructureBundle(scope, FrontierKeys.STRUCTURE_BUNDLE),
                        List.of(cairnFixture)
                );

        JigBundles.Schema<LevelScope> schema =
                new JigBundles.Schema<>(List.of(structureBundle));

        LevelJigConfig config = new LevelJigConfig(FrontierKeys.STRUCTURE_JIG);
        config.bundles().schema(schema);
        // CairnFixture is real, persisted, world-anchored state -- both calls needed explicitly,
        // the same two-call requirement boss.md's own "Module wiring" flags as having been
        // missed once already.
        config.policies().capabilities(new JigPolicies.Capabilities(true, false, false));
        config.policies().persistence(
                new JigPolicies.Persistence(
                        true,
                        JigPolicies.FlushPolicy.EXECUTION_PULSE,
                        JigPolicies.MissingDataPolicy.WARN));
        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults().withTick(true).withExecutionPulse(true));
        config.trinkets().declare(CairnPlacementTick.class, CairnPlacementTick::new);

        return config;
    }
}
