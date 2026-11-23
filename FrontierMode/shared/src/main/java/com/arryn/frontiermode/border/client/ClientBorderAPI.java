package com.arryn.frontiermode.border.client;

import com.arryn.frontiermode.border.client.render.level.WorldBordersRenderer;

import java.util.function.BooleanSupplier;

/**
 * The client half of Border's API: what another module's client code may ask of Border's client classes.
 */
public final class ClientBorderAPI {

    private ClientBorderAPI() {
    }

    /**
     * Sets when the Border rings draw. The rings draw only while {@code supplier} answers true; until a module sets
     * one, nothing draws (admin-tools.md, Debug view).
     */
    public static void setRingsVisibleWhen(BooleanSupplier supplier) {
        WorldBordersRenderer.setVisibleWhen(supplier);
    }
}
