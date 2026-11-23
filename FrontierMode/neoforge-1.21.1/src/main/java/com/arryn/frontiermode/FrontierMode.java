package com.arryn.frontiermode;

import com.arryn.frontiermode.core.FrontierIds;
import com.arryn.frontiermode.core.FrontierLine;
import com.arryn.frontiermode.core.FrontierRegistrar;
import com.arryn.frontiermode.core.NeoForgeLineSource;
import com.arryn.frontiermode.core.NeoForgeRegistrarSource;
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
        FrontierLine.install(new NeoForgeLineSource());

        // Hook lifecycle events
        modBus.addListener(this::commonSetup);

        // Register server-side events
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);

        FrontierBoot.init();
    }

    // Common Setup (runs once on both sides)
    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(FrontierBoot::commonSetup);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        FrontierCommands.register(event.getDispatcher());
    }
}
