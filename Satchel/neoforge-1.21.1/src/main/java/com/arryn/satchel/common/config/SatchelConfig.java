package com.arryn.satchel.common.config;

import com.arryn.satchel.common.platform.PlatformBound;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Satchel's own Forge config -- {@code COMMON} type, registered from {@code SatchelMod}'s
 * constructor via {@code ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON,
 * SatchelConfig.SPEC)}, mirroring FrontierMode's own {@code Config}/{@code FrontierMode}
 * registration shape. One value today: {@link #HEALTH_ENABLED}, the real switch for whether
 * {@code SatchelHealth} registers anything at all -- read in {@code SatchelHealth.init()} and {@code
 * ServerHealth.init()}, before any jig config is registered.
 */
@PlatformBound(reason = "config")
public final class SatchelConfig {

    private SatchelConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue HEALTH_ENABLED =
            BUILDER.comment(
                            "Whether SatchelHealth's run-monitoring/self-verification module "
                                    + "registers at all. SatchelHealth synthetically exercises "
                                    + "persistence, tick/lifecycle, and (where wired) networking "
                                    + "for its own tracker bundles, and logs a VIOLATION error "
                                    + "for each broken invariant it detects -- disable it "
                                    + "whenever that synthetic activity is unwanted."
                    )
                    .define("healthEnabled", true);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
