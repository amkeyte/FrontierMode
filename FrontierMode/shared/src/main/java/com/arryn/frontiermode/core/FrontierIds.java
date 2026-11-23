package com.arryn.frontiermode.core;

import com.arryn.satchel.common.util.Ids;
import net.minecraft.resources.ResourceLocation;

/**
 * The one place module code builds an identifier (Code Conventions § An identifier is built in one place), and
 * where the mod id is named. Each method calls Satchel's {@link Ids#id(String, String)}, which builds the id with a
 * call both Minecraft versions have, so this is a plain shared class: where the {@code ResourceLocation}
 * constructor is private (1.21), nothing here changes.
 */
public final class FrontierIds {

    /** The mod id. Shared code names it here, and each line's mod entry reads it. */
    public static final String MOD_ID = "frontiermode";

    private FrontierIds() {
    }

    /**
     * The id {@code frontiermode:path}, in the mod's own namespace.
     *
     * @throws IllegalArgumentException if the result is not a valid id: a programming error
     */
    public static ResourceLocation of(String path) {
        return Ids.id(MOD_ID, path);
    }

    /**
     * The id {@code namespace:path}, in another namespace ({@code minecraft:bonus_chest}).
     *
     * @throws IllegalArgumentException if the result is not a valid id: a programming error
     */
    public static ResourceLocation of(String namespace, String path) {
        return Ids.id(namespace, path);
    }
}
