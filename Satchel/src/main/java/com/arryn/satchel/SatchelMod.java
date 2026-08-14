package com.arryn.satchel;

import com.arryn.satchel.common.net.SatchelNetwork;
import com.arryn.satchel.common.newconfig.TrackingModule;
import com.arryn.satchel.common.util.out.OUT;
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

    }
}
