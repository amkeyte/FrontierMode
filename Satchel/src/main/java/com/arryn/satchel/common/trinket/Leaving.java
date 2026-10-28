package com.arryn.satchel.common.trinket;

import com.arryn.satchel.common.jig.guts.LeaveReason;
import com.arryn.satchel.common.jig.guts.ScopeInfoImpl;

/**
 * Ability: the Trinket hears why a mob scope is unloading.
 *
 * <ul>
 *   <li><b>Mob scopes only.</b> A level or player scope has no reason, so {@code onLeave} is not
 *   called for it.</li>
 *   <li><b>Order.</b> {@code ScopeEvent.Unloaded}, then {@code onLeave} on each {@code Leaving}
 *   Trinket in declaration order, then {@code onDetach} on each Trinket, then the engine unload and
 *   its flush. By {@code onLeave} the scope has left its jig's list, so a Trinket reaches its own
 *   state through the {@code ScopeInfo} it is handed, not through {@code Satchel.trinket}.</li>
 *   <li><b>Advisory.</b> Satchel unloads the scope the same way for every reason and never acts on
 *   it. A chunk unload may read {@link LeaveReason#UNKNOWN} rather than {@link
 *   LeaveReason#UNLOADED}.</li>
 * </ul>
 *
 * A dynamic Trinket forwards {@code onLeave} to its subscribers that implement this.
 *
 * <p>
 * <b>Unstable:</b> see {@link Trinket}.
 */
public interface Leaving extends Trinket {

    /**
     * The scope is unloading for {@code reason}.
     */
    void onLeave(ScopeInfoImpl info, LeaveReason reason);
}
