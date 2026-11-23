package com.arryn.frontiermode.border.client;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * The client half of Border's API: what another module's client code may ask of Border's client classes.
 */
public final class ClientBorderAPI {

    // Whether the rings draw is the debug view's call (admin-tools.md, Debug view): a deliberate player choice, not a
    // data-readiness check, which is RenderContext.standby()'s. Border never imports Admin, so the admin client
    // registers this supplier once at startup (AdminViewStartup). Until then, and by default, nothing draws.
    private static BooleanSupplier ringsVisibleWhen = () -> false;

    private ClientBorderAPI() {
    }

    /**
     * Sets when the Border rings draw. The rings draw only while {@code supplier} answers true; until a module sets
     * one, nothing draws (admin-tools.md, Debug view).
     */
    public static void setRingsVisibleWhen(BooleanSupplier supplier) {
        ringsVisibleWhen = Objects.requireNonNull(supplier, "supplier");
    }

    /** Whether the rings draw now: what each line's {@code WorldBordersRenderer} asks before it draws. */
    public static boolean ringsVisible() {
        return ringsVisibleWhen.getAsBoolean();
    }
}
