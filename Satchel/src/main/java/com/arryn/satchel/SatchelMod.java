package com.arryn.satchel;

import com.arryn.satchel.common.net.SatchelNetwork;
import com.arryn.satchel.common.tracking.SatchelHealth;
import com.arryn.satchel.common.util.out.OUT;
import com.arryn.satchel.server.commands.MobTrackCommands;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;

@Mod(SatchelMod.MODID)
public class SatchelMod {
    public static final String MODID = "satchel";

    public SatchelMod() {
        OUT.info("HELLO SATCHEL");

        // SatchelNetwork.register() adds S2cBundleParcel's encoder/decoder/handler to CHANNEL --
        // without this, CHANNEL never learns about the message type, so every
        // SatchelNetwork.send() call from ParcelEgressSink (server-side flush -> sync) fails
        // silently/throws internally in Forge's SimpleChannel, and no client ScopeEngine ever
        // receives a parcel to hydrate a bundle with. This was never called anywhere in either
        // repo -- confirmed by grepping for SatchelNetwork.register and .register() generally.
        // Root cause of client-side fixtures (Border rings, growth particles) never populating
        // even after a bundle is created. See SAT_026.
        SatchelNetwork.register();

        // SAT_039/SAT_040 -- SatchelHealth is now the single registration point for every jig
        // kind's health check: MobJig (relocated from MobTrackingModule, sideApplicability
        // widened SERVER -> BOTH, SAT_039), LevelJig (relocated from TrackingModule, widened
        // SERVER -> BOTH, SAT_040), and PlayerJig (relocated from PlayerTrackingModule, stays
        // SERVER -- PlayerScope has no client-side existence to widen into). Registering this is
        // what makes all three exist as installed jigs at all -- without this call the classes
        // compile but nothing ever reaches Satchel.registerJigConfig(...). See SatchelHealth's
        // own class doc for the full mechanism, and for why the MobJig half is expected to crash
        // loudly on a real connected client until RM_SAT_022 ("Roger") lands.
        SatchelHealth.init();

        // /satchel mobtrack -- the in-game surface for exercising SatchelHealth's own
        // watch/unwatch/getFor without needing code access or a debugger.
        MinecraftForge.EVENT_BUS.addListener(MobTrackCommands::onRegisterCommands);
    }
}
