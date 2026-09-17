package com.arryn.satchel.common.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Satchel's own Forge config -- {@code COMMON} type, registered from {@code SatchelMod}'s
 * constructor via {@code ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON,
 * SatchelConfig.SPEC)}, mirroring FrontierMode's own {@code Config}/{@code FrontierMode}
 * registration shape. One value today: {@link #HEALTH_ENABLED}, the real switch for whether
 * {@code SatchelHealth} registers anything at all -- read once, in {@code SatchelHealth.init()},
 * before any jig config is registered.
 */
public final class SatchelConfig {

    private SatchelConfig() {}

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue HEALTH_ENABLED =
            BUILDER.comment(
                            "Whether SatchelHealth's run-monitoring/self-verification module "
                                    + "registers at all. SatchelHealth synthetically exercises "
                                    + "persistence, tick/lifecycle, and (where wired) networking "
                                    + "for its own tracker bundles, and crashes loudly on a "
                                    + "detected teardown-contract violation -- disable for a "
                                    + "release/production profile, or whenever that synthetic "
                                    + "activity is unwanted."
                    )
                    .define("healthEnabled", true);

    public static final ForgeConfigSpec SPEC = BUILDER.build();
}
