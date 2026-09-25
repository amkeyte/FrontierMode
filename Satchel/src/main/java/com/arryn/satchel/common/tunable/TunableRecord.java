package com.arryn.satchel.common.tunable;

/**
 * One registered tunable's on-record state. {@code min}/{@code max} are {@code null} for {@link
 * TunableType#STRING} -- no range concept for a string. {@code def} is write-only, carried
 * through purely for a human comparing the written-out CSV against the current code; it's never
 * read back or compared against anything.
 *
 * <p>
 * {@code shipped} (SAT_082) is the seed's value for this row as of the last merge, or {@code null}
 * if there's no record of one (a row registered by code this session, or read from a pre-{@code
 * shipped} file). A live row is "never hand-edited" exactly when {@code shipped != null &&
 * value.equals(shipped)} -- the only case a newer seed value is allowed to overwrite it.
 *
 * <p>
 * {@code min}/{@code max}/{@code def}/{@code value}/{@code shipped} are boxed ({@code
 * Float}/{@code Integer}/{@code String} depending on {@code type}) rather than stored as
 * CSV-formatted strings, so shape comparison ({@link TunableManager#register}) is exact {@code
 * equals()} on the parsed value, not fragile text-formatting comparison on a float's string form.
 */
record TunableRecord(TunableType type, Object min, Object max, Object def, Object value, Object shipped) {

    TunableRecord withShipped(Object newShipped) {
        return new TunableRecord(type, min, max, def, value, newShipped);
    }
}
