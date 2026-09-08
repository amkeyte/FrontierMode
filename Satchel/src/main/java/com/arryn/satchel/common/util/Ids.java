package com.arryn.satchel.common.util;

import java.util.Objects;
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
 */
public final class Ids {

    private Ids() {
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
