package com.arryn.satchel.common.stitch;

/**
 * A capability marker that a scope could implement to report whether it is server-side or
 * client-side logically (distinct from any networking/thread concern -- purely a self-reported
 * classification).
 *
 * <p>Currently has zero implementers in either repo. Its only reference point,
 * {@link com.arryn.satchel.common.bundle.SatchelBundle#sideString()}, is itself never called
 * anywhere -- so the {@code instanceof} check against this interface never actually executes in
 * practice, not just "always evaluates false." Unlike PersistStitch and CreateAtHydrateStitch,
 * this interface isn't named in any ticket -- it's undocumented dead surface, not a tracked
 * cleanup item.
 *
 * @author Arryn
 * @version 2026-09-15
 */
public interface LogicalSideStitch {

    /**
     * Factory method to obtain a {@code LogicalSideStitch} from a candidate object.
     *
     * @param logicalSideStitchCandidate the object to check
     * @return the {@code LogicalSideStitch} instance
     * @throws IllegalStateException if the candidate does not implement this interface
     * Note: this factory is never called anywhere in either repo today.
     */
    static LogicalSideStitch from(Object logicalSideStitchCandidate) {
        if (!(logicalSideStitchCandidate instanceof LogicalSideStitch logicalSideProvider)) {
            throw new IllegalStateException("Candidate does not provide logical sides");
        }
        return logicalSideProvider;
    }

    /**
     * Checks whether the scope is server-side logically.
     *
     * @return {@code true} if the scope is server-side, {@code false} otherwise
     * Note: this method would report logical server-side status if implemented; however, the
     *     interface has no implementers and its only reference point is dead code.
     */
    boolean isServerSide();

    /**
     * Checks whether the scope is client-side logically.
     *
     * @return {@code true} if the scope is client-side, {@code false} otherwise
     * Note: this method would report logical client-side status if implemented; however, the
     *     interface has no implementers and its only reference point is dead code.
     */
    boolean isClientSide();
}
