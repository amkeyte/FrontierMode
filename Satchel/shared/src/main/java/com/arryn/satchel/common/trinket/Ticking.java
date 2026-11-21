package com.arryn.satchel.common.trinket;

import com.arryn.satchel.common.jig.guts.ScopeInfo;

/**
 * Ability: the Trinket runs each tick, in the foundation's one global, sorted Trinket list. A Trinket
 * ticks only if its jig ticks ({@code JigPolicies.Lifecycle#withTick(true)}); declaring a {@code
 * Ticking} Trinket on a jig that doesn't is a config error.
 *
 * <p>
 * The pulse runs the list in three steps: every {@link #onTickBefore}, then the internal jig walk
 * (scope convergence, bundle and fixture ticks, the persist-and-sync flush), then every {@link
 * #onTickAfter}. The list is sorted by each Trinket's {@code order}, so module code is ordered across
 * scopes, jigs and modules (see {@link Order} and {@link TrinketTick}). A Trinket attached during a
 * tick first runs in the next one.
 *
 * <p>
 * A Trinket's writes reach disk and the client on the next execution pulse, not at the tick: the
 * phase decides what fixtures see, not when anything is saved.
 *
 * <p>
 * <b>Unstable:</b> see {@link Trinket}.
 */
public interface Ticking extends Trinket {

    /**
     * Before any scope's bundles and fixtures tick -- the phase for writing into fixtures.
     */
    default void onTickBefore(ScopeInfo info) {
    }

    /**
     * After the internal jig walk has finished -- the phase for reading this tick's fixture state,
     * and where module logic runs.
     */
    default void onTickAfter(ScopeInfo info) {
    }
}
