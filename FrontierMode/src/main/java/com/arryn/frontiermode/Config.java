package com.arryn.frontiermode;

import net.minecraftforge.common.ForgeConfigSpec;

// RM_FRO_011: this was stock, uncustomized Forge MDK example boilerplate (LOG_DIRT_BLOCK,
// MAGIC_NUMBER, magicNumberIntroduction, the ITEM_STRINGS list, an onLoad @SubscribeEvent handler
// that only populated those fields, and a stray @SuppressWarnings referencing a Minecraft version
// ("1.20.6") this project doesn't target and isn't a recognized SuppressWarnings key either way)
// -- never adapted or removed, same category of leftover template scaffolding as README.txt (see
// FRO_001). No FrontierMode code read any of those fields. The @Mod.EventBusSubscriber annotation
// went with onLoad -- nothing left in this class to subscribe. SPEC is kept, empty, since
// FrontierMode.java's constructor still registers it with Forge (ModLoadingContext
// .registerConfig) -- real config values go here if/when this mod actually needs any.
public class Config
{
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    static final ForgeConfigSpec SPEC = BUILDER.build();
}
