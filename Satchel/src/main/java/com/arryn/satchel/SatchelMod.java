package com.arryn.satchel;

import com.arryn.satchel.common.config.SatchelConfig;
import com.arryn.satchel.common.net.SatchelNetwork;
import com.arryn.satchel.common.tracking.SatchelHealth;
import com.arryn.satchel.common.util.out.OUT;
import com.arryn.satchel.server.commands.MobTrackCommands;
import com.arryn.satchel.server.commands.TunableCommands;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(SatchelMod.MODID)
public class SatchelMod {
    public static final String MODID = "satchel";

    public SatchelMod() {
        OUT.info("HELLO SATCHEL");

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SatchelConfig.SPEC);

        // SatchelNetwork.register() adds S2cBundleParcel's encoder/decoder/handler to CHANNEL --
        // without this, CHANNEL never learns about the message type, so every
        // SatchelNetwork.send() call from ParcelEgressSink (server-side flush -> sync) fails
        // silently/throws internally in Forge's SimpleChannel, and no client ScopeEngine ever
        // receives a parcel to hydrate a bundle with. This was never called anywhere in either
        // repo -- confirmed by grepping for SatchelNetwork.register and .register() generally.
        // Root cause of client-side fixtures (Border rings, growth particles) never populating
        // even after a bundle is created. See SAT_026.
        SatchelNetwork.register();

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::commonSetup);

        // /satchel mobtrack -- the in-game surface for exercising SatchelHealth's own
        // watch/unwatch/getFor without needing code access or a debugger.
        MinecraftForge.EVENT_BUS.addListener(MobTrackCommands::onRegisterCommands);

        // /satchel tunables -- tune, reload, and save Tunables without a restart (SAT_090).
        MinecraftForge.EVENT_BUS.addListener(TunableCommands::onRegisterCommands);
    }

    // ------------------------------------------------------------
    // Common Setup (runs once, both sides) -- SatchelConfig values are only safe to read from
    // here onward. ModLoadingContext.get().registerConfig(...) above only registers the spec;
    // it doesn't load the actual TOML file synchronously in the constructor.
    // ------------------------------------------------------------
    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // SAT_039/SAT_040/SAT_060 -- SatchelHealth is the single registration point for
            // every jig kind's health check: MobJig, LevelJig (both sideApplicability BOTH), and
            // PlayerJig (stays SERVER -- PlayerScope has no client-side existence to widen
            // into). Calling this (when SatchelConfig.HEALTH_ENABLED allows it) is what makes
            // all three exist as installed jigs at all -- without it the classes compile but
            // nothing ever reaches Satchel.registerJigConfig(...). See SatchelHealth's own class
            // doc for the full mechanism, and for why the MobJig half is expected to crash
            // loudly on a real connected client until RM_SAT_022 ("Roger") lands.
            SatchelHealth.init();
        });
    }
}
