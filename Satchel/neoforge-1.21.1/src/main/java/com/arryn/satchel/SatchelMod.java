package com.arryn.satchel;

import com.arryn.satchel.common.config.SatchelConfig;
import com.arryn.satchel.common.jig.host.HostJigs;
import com.arryn.satchel.common.net.SatchelNetwork;
import com.arryn.satchel.common.platform.ForgeEnvironment;
import com.arryn.satchel.common.platform.PlatformBound;
import com.arryn.satchel.common.platform.SatchelEnvironment;
import com.arryn.satchel.common.tracking.CanaryEventIngress;
import com.arryn.satchel.common.tracking.HotEventIngress;
import com.arryn.satchel.common.tracking.MobJoinIngress;
import com.arryn.satchel.common.tracking.ModuleEventIngress;
import com.arryn.satchel.common.tracking.PlayerLifeIngress;
import com.arryn.satchel.common.tracking.RealEventIngress;
import com.arryn.satchel.common.tracking.ResultIngress;
import com.arryn.satchel.common.tracking.SatchelHealth;
import com.arryn.satchel.common.tracking.SpawnGateIngress;
import com.arryn.satchel.common.tunable.TunableManager;
import com.arryn.satchel.common.util.MobDataStore;
import com.arryn.satchel.common.util.out.OUT;
import com.arryn.satchel.server.commands.MobTrackCommands;
import com.arryn.satchel.server.commands.TrinketCommands;
import com.arryn.satchel.server.commands.TunableCommands;
import com.arryn.satchel.server.commands.TuningTableCommands;
import com.arryn.satchel.server.tracking.ServerHealth;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@PlatformBound(reason = "registration")
@Mod(SatchelMod.MODID)
public class SatchelMod {
    public static final String MODID = "satchel_ecosystem";

    public SatchelMod(IEventBus modBus, ModContainer container) {
        OUT.info("HELLO SATCHEL");

        // First, so every later ask of the loader (the dedicated-server check, the config folder, a mod's
        // shipped files, the running server) finds an answer.
        SatchelEnvironment.install(new ForgeEnvironment());

        container.registerConfig(ModConfig.Type.COMMON, SatchelConfig.SPEC);

        // Satchel's payloads (the bundle parcel and the world-identity token). Without this no client
        // ScopeEngine ever receives a parcel to hydrate a bundle with (SAT_026).
        modBus.addListener(SatchelNetwork::register);

        modBus.addListener(this::commonSetup);

        // SAT_094: MobData's container, the satchel:mob_data data attachment.
        MobDataStore.register(modBus);

        // /satchel mobtrack -- the in-game surface for exercising SatchelHealth's own
        // watch/unwatch/getFor without needing code access or a debugger.
        NeoForge.EVENT_BUS.addListener(MobTrackCommands::onRegisterCommands);

        // /satchel tunables -- tune, reload, and save Tunables without a restart (SAT_090).
        NeoForge.EVENT_BUS.addListener(TunableCommands::onRegisterCommands);

        // /satchel tables -- list and reload TuningTables without a restart (SAT_092).
        NeoForge.EVENT_BUS.addListener(TuningTableCommands::onRegisterCommands);

        // /satchel trinkets -- lists every attached Trinket per jig and scope (SAT_097).
        NeoForge.EVENT_BUS.addListener(TrinketCommands::onRegisterCommands);
    }

    // ------------------------------------------------------------
    // Common Setup (runs once, both sides) -- SatchelConfig values are only safe to read from
    // here onward. container.registerConfig(...) above only registers the spec; it doesn't load
    // the actual TOML file synchronously in the constructor.
    // ------------------------------------------------------------
    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // The level and player host jigs, which modules contribute Trinkets to. Registered
            // first so they come first in the jig walk.
            HostJigs.register();

            // SatchelHealth is the single registration point for every jig kind's health check
            // (MobJig, LevelJig and PlayerJig, each registered BOTH) and for its probes. Calling
            // this (when SatchelConfig.HEALTH_ENABLED allows it) is what makes them exist as
            // installed jigs at all -- without it the classes compile but nothing ever reaches
            // Satchel.registerJig(...). See SatchelHealth's own class doc for the full mechanism.
            SatchelHealth.init();
            // The probes that need the server's own classes (server/tracking), after the shared ones.
            ServerHealth.init();
            // This line's half of the probes: the ingress that turns Forge's events into their event
            // interfaces, and the loader calls two of them make (SAT_189).
            registerHealthIngress();

            // SAT_093: Tunables load once per game, here -- before any world exists, so a Tunable
            // read during world creation (the new-world spawn search) sees the CSV, and a bad row
            // is a mod-loading error. ServerLifecycle re-reads them before each server starts
            // and saves them as it stops.
            TunableManager.load();
        });
    }

    /**
     * Forge's adapters for SatchelHealth's probes, whose checks are shared: each registers its ingress, and the
     * spawn-gate and result adapters also hand their checks Forge's calls. Under the same switch as
     * {@code SatchelHealth.init()}.
     */
    private static void registerHealthIngress() {
        if (!SatchelConfig.HEALTH_ENABLED.get()) return;

        MobJoinIngress.register();
        PlayerLifeIngress.register();
        ModuleEventIngress.register();
        ResultIngress.register();
        HotEventIngress.register();
        CanaryEventIngress.register();
        RealEventIngress.register();
        SpawnGateIngress.register();
    }
}
