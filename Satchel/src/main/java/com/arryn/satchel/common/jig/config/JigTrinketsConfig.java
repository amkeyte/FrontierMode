package com.arryn.satchel.common.jig.config;

import com.arryn.satchel.common.trinket.Order;
import com.arryn.satchel.common.trinket.Trinket;
import com.arryn.satchel.common.trinket.TrinketDecl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The Trinkets a jig's scopes hold: for each, its type, its {@code order} and a factory.
 *
 * <p>
 * A Trinket is a unit of the API in its own right, so it has a lens of its own and is declared apart
 * from the bundle schema; a jig with no bundles declares Trinkets and nothing else. A pure
 * configuration category, like the others: no lifecycle logic and no engine coupling. Checked when
 * the config compiles ({@code JigConfigValidator}), not here.
 */
public abstract class JigTrinketsConfig {

    protected JigTrinketsConfig() {}

    /* =============================================================
     * Accessors
     * ========================================================== */

    /**
     * The Trinkets this jig declares, in declaration order. Empty if none.
     */
    public List<TrinketDecl<?>> declarations() {
        return Collections.unmodifiableList(presets().decls);
    }

    /* =============================================================
     * Mutators (fluent)
     * ========================================================== */

    /**
     * Declares a {@link Trinket}: Satchel calls {@code factory} once per scope when the scope loads
     * and drops the instance at unload. One declaration per type; declaration order is attach
     * order. Its {@code order} is {@link Order#NORMAL} unless set on the returned handle.
     */
    public <T extends Trinket> Declared<T> declare(Class<T> type, Supplier<? extends T> factory) {
        List<TrinketDecl<?>> decls = presets().decls;
        decls.add(new TrinketDecl<>(type, factory));
        return new Declared<>(decls, decls.size() - 1);
    }

    /**
     * Adds a Trinket a module contributes to a host jig: like {@link #declare}, plus the {@code side}
     * it exists on and the contributing {@code owner}, which {@code /satchel trinkets} prints. Called
     * by Satchel's host jigs, not by a module directly.
     */
    public <T extends Trinket> void contribute(
            String owner,
            JigPolicies.SideApplicability side,
            int order,
            Class<T> type,
            Supplier<? extends T> factory
    ) {
        Objects.requireNonNull(owner, "owner");
        presets().decls.add(new TrinketDecl<>(type, factory, order, side, owner));
    }

    /**
     * The handle {@link #declare} returns, to set what the declaration carries beyond its type and
     * factory.
     */
    public static final class Declared<T extends Trinket> {

        private final List<TrinketDecl<?>> decls;
        private final int index;

        private Declared(List<TrinketDecl<?>> decls, int index) {
            this.decls = decls;
            this.index = index;
        }

        /**
         * Sets this Trinket's {@code order}: an int, lower first, with anchors in {@link Order}.
         */
        @SuppressWarnings("unchecked")
        public Declared<T> order(int order) {
            decls.set(index, ((TrinketDecl<T>) decls.get(index)).withOrder(order));
            return this;
        }
    }

    /* =============================================================
     * Preset access
     * ========================================================== */

    protected abstract Presets presets();

    /* =============================================================
     * Preset node (owned by JigConfig)
     * ========================================================== */

    public static class Presets {

        /**
         * Declared Trinkets, in declaration order. Optional.
         */
        public final List<TrinketDecl<?>> decls = new ArrayList<>();
    }
}
