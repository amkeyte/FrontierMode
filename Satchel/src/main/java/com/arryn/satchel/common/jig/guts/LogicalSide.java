package com.arryn.satchel.common.jig.guts;

/**
 * Which logical side a foundation, an ingress or a thread belongs to: Satchel's own value, not the loader's.
 *
 * <p>Nothing converts between this and the loader's side type. Satchel's own booters supply {@link #SERVER} and
 * {@link #CLIENT}, so no loader value is ever read, and a file that names this type compiles on every loader.
 *
 * <p>Module code does not need it: a thread asks which side it is on with {@code Satchel.isClient()} and
 * {@code Satchel.isServer()}. The members that expose it are deprecated for that reason.
 */
public enum LogicalSide {

    /** The logical client. */
    CLIENT,

    /** The logical server, dedicated or integrated. */
    SERVER;

    /** {@code true} for {@link #CLIENT}. */
    public boolean isClient() {
        return this == CLIENT;
    }

    /** {@code true} for {@link #SERVER}. */
    public boolean isServer() {
        return this == SERVER;
    }
}
