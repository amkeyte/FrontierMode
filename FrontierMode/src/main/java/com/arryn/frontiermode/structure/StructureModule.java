package com.arryn.frontiermode.structure;

import com.arryn.frontiermode.FrontierMode;
import com.arryn.frontiermode.boss.BossPositionListeners;
import com.arryn.frontiermode.structure.common.blocks.CairnCapBlock;
import com.arryn.frontiermode.structure.server.commands.StructureCommands;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

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
        OUT.info("[Structure] init(): Module initiating.");
        Satchel.registerJig(JigPolicies.SideApplicability.SERVER, () -> StructureJigConfig.build());
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> StructureIngress.build());
        BossPositionListeners.register(StructureBossHandlers::onBossPositioned);
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        StructureCommands.register(event.getDispatcher());
    }
}
