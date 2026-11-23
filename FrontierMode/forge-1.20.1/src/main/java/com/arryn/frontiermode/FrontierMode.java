package com.arryn.frontiermode;

import com.arryn.frontiermode.admin.AdminToolsModule;
import com.arryn.frontiermode.bosstell.BossTellModule;
import com.arryn.frontiermode.core.ForgeRegistrarSource;
import com.arryn.frontiermode.core.FrontierIds;
import com.arryn.frontiermode.core.FrontierRegistrar;
import com.arryn.satchel.common.platform.PlatformBound;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * The class Forge constructs for the mod on {@code forge-1.20.1} (Two Lines § The mod entry). It puts the registers on
 * the mod bus, hooks the two listeners and hands the rest to {@link FrontierBoot} and {@link FrontierCommands}, which
 * both lines share.
 */
@PlatformBound(reason = "registration")
@Mod(FrontierIds.MOD_ID)
public final class FrontierMode {

    public FrontierMode(FMLJavaModLoadingContext context) {

        IEventBus modBus = context.getModEventBus();

        // First: installs the registrar every registers class registers through, and puts its registers on the bus.
        ForgeRegistrarSource registrar = new ForgeRegistrarSource();
        FrontierRegistrar.install(registrar);
        registrar.register(modBus);

        // The enchantments are registered apart from the registrar: a register needs the mod bus instance, which
        // only exists in this constructor.
        BossTellModule.ENCHANTMENTS.register(modBus);
        AdminToolsModule.ENCHANTMENTS.register(modBus);

        // Hook lifecycle events
        modBus.addListener(this::commonSetup);

        // Register server-side events (@SubscribeEvent methods)
        MinecraftForge.EVENT_BUS.register(this);

        FrontierBoot.init();
    }

    // Common Setup (runs once on both sides)
    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(FrontierBoot::commonSetup);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        FrontierCommands.register(event.getDispatcher());
    }
}
