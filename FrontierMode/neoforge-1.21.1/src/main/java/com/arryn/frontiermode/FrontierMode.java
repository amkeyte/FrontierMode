package com.arryn.frontiermode;

import com.arryn.frontiermode.admin.AdminIngress;
import com.arryn.frontiermode.border.server.BorderIngress;
import com.arryn.frontiermode.boss.BossIngress;
import com.arryn.frontiermode.bosstell.BossTellIngress;
import com.arryn.frontiermode.coordination.SpawnIngress;
import com.arryn.frontiermode.core.CoreIngress;
import com.arryn.frontiermode.core.FrontierIds;
import com.arryn.frontiermode.core.FrontierLine;
import com.arryn.frontiermode.core.FrontierRegistrar;
import com.arryn.frontiermode.core.NeoForgeLineSource;
import com.arryn.frontiermode.core.NeoForgeRegistrarSource;
import com.arryn.frontiermode.core.server.FrontierServerLine;
import com.arryn.frontiermode.difficulty.DifficultyIngress;
import com.arryn.frontiermode.feral.FeralIngress;
import com.arryn.frontiermode.keeper.KeeperIngress;
import com.arryn.frontiermode.sentinel.SentinelIngress;
import com.arryn.frontiermode.siting.SitingIngress;
import com.arryn.frontiermode.structure.StructureIngress;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.platform.PlatformBound;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * The class NeoForge constructs for the mod on {@code neoforge-1.21.1} (Two Lines § The mod entry). It hooks the two
 * listeners and hands the rest to {@link FrontierBoot} and {@link FrontierCommands}, which both lines share.
 */
@PlatformBound(reason = "registration")
@Mod(FrontierIds.MOD_ID)
public final class FrontierMode {

    public FrontierMode(IEventBus modBus) {

        // First: installs the registrar every registers class registers through, and puts its registers on the bus.
        NeoForgeRegistrarSource registrar = new NeoForgeRegistrarSource();
        FrontierRegistrar.install(registrar);
        registrar.register(modBus);

        // The calls into Minecraft whose form differs by line.
        NeoForgeLineSource line = new NeoForgeLineSource();
        FrontierLine.install(line);
        FrontierServerLine.install(line);

        // Hook lifecycle events
        modBus.addListener(this::commonSetup);

        // Register server-side events
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);

        FrontierBoot.init();
        registerIngresses();
    }

    /**
     * Registers each module's ingress at the side the module's page gives, in the order of the module inits
     * (Module Graph). An ingress is the line's own: it translates the loader's events to the module events.
     */
    private static void registerIngresses() {
        Satchel.registerIngress(JigPolicies.SideApplicability.BOTH, () -> CoreIngress.build());
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> BorderIngress.build());
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> BossIngress.build());
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> SitingIngress.build());
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> DifficultyIngress.build());
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> KeeperIngress.build());
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> SentinelIngress.build());
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> BossTellIngress.build());
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> StructureIngress.build());
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> FeralIngress.build());
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> AdminIngress.build());
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> SpawnIngress.build());
    }

    // Common Setup (runs once on both sides)
    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(FrontierBoot::commonSetup);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        FrontierCommands.register(event.getDispatcher());
    }
}
