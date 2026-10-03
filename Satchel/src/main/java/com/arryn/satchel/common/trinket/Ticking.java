package com.arryn.satchel.common.trinket;

import com.arryn.satchel.common.jig.guts.ScopeInfo;

/**
 * Ability: the Trinket is ticked by its jig, in two phases around the scope's bundle tick.
 * A Trinket ticks only if its jig ticks ({@code JigPolicies.Lifecycle#withTick(true)}); declaring a
 * {@code Ticking} Trinket on a jig that doesn't is a config error.
 *
 * <p>
 * Both phases run inside the coupler's own tick, so a Trinket's writes reach disk and the client on
 * the next execution pulse, not at the tick: the phase decides what fixtures see, not when anything
 * is saved.
 *
 * <p>
 * <b>Unstable:</b> see {@link Trinket}.
 */
public interface Ticking extends Trinket {

    /**
     * Before the scope's bundles and fixtures tick -- the phase for writing into fixtures.
     */
    default void onTickBefore(ScopeInfo info) {
    }

    /**
     * After the scope's bundles and fixtures tick -- the phase for reading this tick's fixture state.
     */
    default void onTickAfter(ScopeInfo info) {
    }
}
