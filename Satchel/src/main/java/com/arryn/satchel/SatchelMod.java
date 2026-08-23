package com.arryn.satchel;

import com.arryn.satchel.common.net.SatchelNetwork;
import com.arryn.satchel.common.newconfig.MobTrackingModule;
import com.arryn.satchel.common.newconfig.PlayerTrackingModule;
import com.arryn.satchel.common.newconfig.TrackingModule;
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

        //set up the tracking module.
        TrackingModule.init();

        // RM_SAT_020 verification aid -- see PlayerTrackingModule's own class doc. Real consumer
        // (RM_FRO_006) hasn't landed yet; this is what lets the login/dimension-change/logout
        // checklist in FRO_023 actually be checked before then.
        PlayerTrackingModule.init();

        // RM_SAT_021 ("Frank") verification aid -- see MobTrackingModule's own class doc. Real
        // consumer (Boss, RM_FRO_018) hasn't landed yet. Registering this is what makes MobJig
        // exist as an installed jig at all -- without this call the class compiles but nothing
        // ever reaches Satchel.registerJigConfig(...), so MobJig never actually runs.
        MobTrackingModule.init();

        // /satchel mobtrack -- the in-game surface for exercising MobTrackingModule's
        // watch/unwatch/getFor without needing code access or a debugger.
        MinecraftForge.EVENT_BUS.addListener(MobTrackCommands::onRegisterCommands);
    }
}
