package com.arryn.satchel.common.trinket;

import com.arryn.satchel.common.jig.guts.ScopeInfo;

/**
 * A declared, scope-bound runtime component: runtime state and behavior for one scope, held by the
 * scope's coupler, never saved or synced. The runtime counterpart of a {@code SatchelFixture}, which
 * is persisted state inside a bundle.
 *
 * <p>
 * A module declares a Trinket (type and factory) beside its bundle schema on its jig config
 * ({@code JigBundlesConfig#trinket}); Satchel constructs every declared Trinket when the scope loads
 * and drops it when the scope unloads, so module code never attaches one. Module code reaches one
 * through {@code Satchel.trinket(jigKey, scope, Type)}, usually behind its own resolver.
 *
 * <p>
 * Anything beyond this base is an opt-in <i>ability</i> interface ({@link Ticking}), added only when
 * a consumer needs it.
 *
 * <p>
 * <b>Rules for Trinket code.</b> Write it as common code typed on {@code Level} and {@link
 * ScopeInfo}: no server types and no casts, side-specific work behind {@code level.isClientSide()}.
 * Vanilla API calls are allowed; Forge-specific APIs (events, capabilities, registries) are not --
 * those stay in Satchel's platform layer. A Trinket that throws crashes, uncaught, like a fixture's
 * tick.
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
    default void onAttach(ScopeInfo info) {
    }

    /**
     * The scope is unloading. Runs before the scope's bundles unload; a fixture write made here marks
     * its bundle dirty and is saved by the engine's terminal flush. No teardown reason is given.
     */
    default void onDetach(ScopeInfo info) {
    }

    /**
     * One line for the {@code /satchel trinkets} dump.
     */
    default String describe() {
        return "";
    }
}
