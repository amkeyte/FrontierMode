package com.arryn.satchel.common.jig.guts;

/**
 * Why a mob scope unloaded, as Satchel reports it. <b>Advisory:</b> nobody can say for certain why a
 * mob left. On the server the removal reason is exact; on the client a mob leaving tracking range
 * arrives as {@link #DISCARDED}, the same word as a destroyed one; and a leave can carry no reason at
 * all, as at shutdown. Satchel unloads the scope the same way for every reason and never acts on one;
 * a module decides what it means for its mob.
 *
 * <p>
 * The reason is set on the scope's {@link ScopeInfoImpl#leaveReason()} by the mob jig, from the mob's
 * removal reason, before {@code ScopeEvent.Unloaded} fires, and a Trinket that implements {@code
 * Leaving} is handed it before its {@code onDetach}. The vanilla removal-reason type stays inside
 * Satchel's platform layer.
 */
public enum LeaveReason {

    /** The mob died. */
    KILLED,

    /** The mob was discarded: despawned, destroyed, or, on a client, left tracking range. */
    DISCARDED,

    /** The mob moved to another level; it is re-created there with the same UUID. */
    CHANGED_LEVEL,

    /** The mob's chunk (or the player carrying it) unloaded. */
    UNLOADED,

    /**
     * No reason was recorded. A real chunk unload may read this rather than {@link #UNLOADED}; a
     * module treats the two alike.
     */
    UNKNOWN
}
