package com.arryn.satchel;

import com.arryn.satchel.common.newconfig.TrackingModule;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraftforge.fml.common.Mod;

@Mod(SatchelMod.MODID)
public class SatchelMod {
    public static final String MODID = "satchel";

    public SatchelMod() {
        OUT.info("HELLO SATCHEL");

        //set up the tracking module.
        TrackingModule.init();

    }
}
