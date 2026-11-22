package com.arryn.satchel.common.util;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Display-only shortening for {@link UUID}s. Full UUIDs are unreasonable to eyeball or diff in
 * logs -- two ids differ only in a handful of characters buried in the middle, so a reader has
 * to line up 36 characters by hand to tell them apart. {@link #shortId(UUID)} keeps just the
 * last 8 hex characters (the final group of a standard UUID string), which is enough entropy to
 * distinguish entities in a single log session without the visual noise of the rest.
 * <br><br>
 * For display only -- never parse a shortened id back, never use it as a lookup key, and never
 * assume it is collision-free across a large population. It is a reading aid, not an identifier.
 * <br><br>
 * Pure static utility -- no fixture, no bundle, no {@code JigConfig}. Nothing identified needs
 * state beyond the input passed in.
 * <br><br>
 * It is also where a {@link ResourceLocation} is built, {@link #id(String, String)}, and where text is parsed
 * into one, {@link Ids#parse(String)}.
 */
public final class Ids {

    private Ids() {
    }

    /**
     * The id {@code namespace:path}. Satchel and the modules built on it make an id here and nowhere else (the
     * platform check fails a direct {@code new ResourceLocation(}), so a version that changes how an id is built,
     * as 1.21 does by making the constructor private, changes this class and nothing else. It goes through
     * {@link Ids#parse(String)}.
     *
     * @throws NullPointerException     if either is null
     * @throws IllegalArgumentException if {@code namespace:path} is not a valid id: a programming error
     */
    public static ResourceLocation id(String namespace, String path) {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        return parse(namespace + ":" + path)
                .orElseThrow(() -> new IllegalArgumentException("'" + namespace + ":" + path + "' is not a valid id"));
    }

    /**
     * The id that {@code text} names, or empty if it is not a valid id. An id that arrives as text, a cell of a
     * data file or a saved key, is parsed here, by Satchel and by the modules built on it; this is the one place
     * {@code ResourceLocation.tryParse} is called. Text with no namespace is read in {@code minecraft}'s, as
     * {@code tryParse} reads it.
     *
     * @throws NullPointerException if text is null
     */
    public static Optional<ResourceLocation> parse(String text) {
        Objects.requireNonNull(text, "text");
        return Optional.ofNullable(ResourceLocation.tryParse(text));
    }

    /**
     * Returns the last 8 characters of {@code id}'s standard string form, for display in logs.
     *
     * @throws NullPointerException if id is null
     */
    public static String shortId(UUID id) {
        Objects.requireNonNull(id, "id");
        String s = id.toString();
        return s.substring(s.length() - 8);
    }
}
