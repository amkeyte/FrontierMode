package com.arryn.satchel.common.tunable;

/**
 * One registered tunable's on-record state. {@code min}/{@code max} are {@code null} for {@link
 * TunableType#STRING} -- no range concept for a string. {@code def} is write-only, carried
 * through purely for a human comparing the written-out CSV against the current code; it's never
 * read back or compared against anything.
 *
 * <p>
 * {@code min}/{@code max}/{@code def}/{@code value} are boxed ({@code Float}/{@code Integer}/
 * {@code String} depending on {@code type}) rather than stored as CSV-formatted strings, so shape
 * comparison ({@link TunableManager#register}) is exact {@code equals()} on the parsed value, not
 * fragile text-formatting comparison on a float's string form.
 */
record TunableRecord(TunableType type, Object min, Object max, Object def, Object value) {
}
