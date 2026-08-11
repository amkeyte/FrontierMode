package com.arryn.frontiermode;

import com.arryn.frontiermode.border.BorderModule;
import com.arryn.frontiermode.border.server.commands.BorderSelectorArgumentType;
import com.arryn.frontiermode.border.server.commands.BorderSelectorArgumentTypeInfo;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@SuppressWarnings("'getProposal()' is deprecated since version 1.21.1 and marked for removal")
@Mod(FrontierMode.MODID)
public final class FrontierMode {

    public static final String MODID = "frontiermode";

    // ------------------------------------------------------------
    // Command Argument Type Registration (Forge Registry)
    // ------------------------------------------------------------
    public static final DeferredRegister<ArgumentTypeInfo<?, ?>> ARGUMENT_TYPES =
            DeferredRegister.create(ForgeRegistries.COMMAND_ARGUMENT_TYPES, MODID);

    public static final RegistryObject<ArgumentTypeInfo<?, ?>> BORDER_SELECTOR =
            ARGUMENT_TYPES.register("border_selector", BorderSelectorArgumentTypeInfo::new);

    // ------------------------------------------------------------
    // Constructor – module bootstrap
    // ------------------------------------------------------------
    public FrontierMode() {

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register custom argument types with Forge
        ARGUMENT_TYPES.register(modBus);

        // Register configuration
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // Hook lifecycle events
        modBus.addListener(this::commonSetup);

        // Register server-side events (@SubscribeEvent methods)
        MinecraftForge.EVENT_BUS.register(this);

        OUT.info("FrontierMode: Module initialization started.");

        // Initialize the Border module (fixtures, tick handler, commands)
        BorderModule.init();
    }

    // ------------------------------------------------------------
    // Common Setup (runs once on both sides)
    // ------------------------------------------------------------
    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ArgumentTypeInfos.registerByClass(
                    BorderSelectorArgumentType.class,
                    (ArgumentTypeInfo<BorderSelectorArgumentType, BorderSelectorArgumentTypeInfo.Template>)
                            FrontierMode.BORDER_SELECTOR.get()
            );
        });
    }


    // ------------------------------------------------------------
    // Command Registration
    // Delegated fully to BorderModule
    // ------------------------------------------------------------
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        BorderModule.onRegisterCommands(event);
    }


}
