package com.arryn.satchel.common.trinket;

import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.guts.ScopeInfoImpl;

/**
 * A declared, scope-bound runtime component: runtime state and behavior for one scope, held by the
 * scope's coupler, never saved or synced. The runtime counterpart of a {@code SatchelFixture}, which
 * is persisted state inside a bundle.
 *
 * <p>
 * A module declares a Trinket (type, factory and {@code order}) in its jig config's Trinkets lens
 * ({@code JigTrinketsConfig#declare}), apart from its bundle schema; Satchel constructs every declared Trinket when the scope loads
 * and drops it when the scope unloads, so module code never attaches one. Module code reaches one
 * through {@code Satchel.trinket(jigKey, scope, Type)}, usually behind its own resolver.
 *
 * <p>
 * Anything beyond this base is an opt-in <i>ability</i> interface ({@link Ticking}), added only when
 * a consumer needs it.
 *
 * <p>
 * <b>Unstable:</b> this API may change until a FrontierMode consumer has exercised it.
 */
public interface Trinket {

    /**
     * The scope loaded. Satchel has constructed every declared Trinket for this scope first, then
     * calls this on each in declaration order, so a sibling always exists here, though it may not have
     * been attached yet.
     */
    default void onAttach(ScopeInfoImpl info) {
    }

    /**
     * The scope is unloading. Runs before the scope's bundles unload; a fixture write made here marks
     * its bundle dirty and is saved by the engine's terminal flush. No teardown reason is given.
     */
    default void onDetach(ScopeInfoImpl info) {
    }

    /**
     * Whether this Trinket is trustworthy to use yet, the Trinket counterpart of {@code
     * SatchelFixture#isReady()}. Until it returns true the coupler skips the Trinket's tick and
     * answers its lookup ({@code Satchel.trinket}) empty. Existence is still guaranteed: Satchel has
     * constructed and attached it. Only participation is gated.
     *
     * <p>
     * A fixture's {@code isReady()} is a flag its lifecycle flips; a Trinket's is a predicate over
     * its scope, because a Trinket carries no lifecycle state of its own. Called every tick for a
     * {@link Ticking} Trinket and on every lookup, so keep it cheap. The default is true.
     */
    default boolean isReady(ScopeInfo info) {
        return true;
    }

    /**
     * One line for the {@code /satchel trinkets} dump.
     */
    default String describe() {
        return "";
    }
}
