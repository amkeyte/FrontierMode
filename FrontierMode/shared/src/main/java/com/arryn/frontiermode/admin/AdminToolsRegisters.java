package com.arryn.frontiermode.admin;

import com.arryn.frontiermode.core.FrontierIds;
import net.minecraft.resources.ResourceLocation;

/**
 * The admin module's enchantment ids. Shared code names an enchantment by id; each line registers the enchantment in
 * its own form (Two Lines, Enchantments), under these ids.
 */
public final class AdminToolsRegisters {

    private AdminToolsRegisters() {
    }

    /** Death Touch: kills what a stick carrying it hits. */
    public static final ResourceLocation DEATH_TOUCH = FrontierIds.of("death_touch");

    /** Inspect: stops the hit and reports the target. */
    public static final ResourceLocation INSPECT = FrontierIds.of("inspect");
}
