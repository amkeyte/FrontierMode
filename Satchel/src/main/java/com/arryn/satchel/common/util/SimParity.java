package com.arryn.satchel.common.util;

import java.util.Objects;
import java.util.UUID;

/**
 * Stateless simulation-parity primitive: lets server and client independently compute the
 * <em>same</em> answer from inputs both sides already have synced, with no packet in the loop.
 * A third axis alongside sidedness (which side am I on) and readiness (is state here yet) --
 * this one asks "do both sides agree", and answers it by construction rather than by broadcast.
 * <br><br>
 * Two independent pieces, meant to be combined at the call site:
 * <ul>
 *   <li>{@link #isCheckpoint(long, long)} -- a timing gate on {@code gameTime mod interval},
 *       never a raw tick-delta comparison. If both sides briefly disagree on the exact tick
 *       (a caught-up client, network jitter), they re-agree automatically at the next multiple
 *       of {@code interval} -- self-correcting by construction, not by explicit resync.</li>
 *   <li>{@link #parityValue(UUID, long, long)} / {@link #parityRoll(UUID, long, long)} -- a
 *       deterministic mix of a stable id, {@code GameTime}, and a per-call-site salt into a
 *       value both sides compute identically. {@code GameTime} only -- never wall-clock time,
 *       which is not reliably synced between server and client.</li>
 * </ul>
 * The mix is built entirely from primitive long arithmetic (the SplitMix64 finalizer, applied
 * to XOR-folded inputs) rather than {@code Object.hashCode()}/{@code UUID.hashCode()} -- this
 * makes bit-identical output across JVMs true by construction, not something to double-check
 * against a language-spec guarantee.
 * <br><br>
 * Pure static utility -- no fixture, no bundle, no {@code JigConfig}. Nothing identified needs
 * state beyond the inputs passed in.
 */
public final class SimParity {

    // SplitMix64 finalizer constants (Vigna, public domain).
    private static final long MIX_A = 0xbf58476d1ce4e5b9L;
    private static final long MIX_B = 0x94d049bb133111ebL;

    private SimParity() {
    }

    /**
     * Timing gate: true exactly on ticks where {@code gameTime} is a multiple of
     * {@code interval}. Gate on this, not on a raw "has N ticks passed since I last checked"
     * comparison -- the modulus is what lets both sides re-agree automatically after jitter,
     * since both are reading the same shared counter against the same fixed divisor rather than
     * tracking independent per-side state.
     *
     * @throws IllegalArgumentException if interval is not positive
     */
    public static boolean isCheckpoint(long gameTime, long interval) {
        if (interval <= 0) {
            throw new IllegalArgumentException("interval must be > 0");
        }
        return gameTime % interval == 0;
    }

    /**
     * Deterministically mixes a stable id, {@code GameTime}, and a salt into a 64-bit value.
     * Bit-identical given identical inputs, on any JVM -- pure long arithmetic, no reliance on
     * {@code hashCode()} implementations. {@code salt} distinguishes unrelated call sites that
     * might otherwise share an id and tick and accidentally correlate.
     */
    public static long parityValue(UUID id, long gameTime, long salt) {
        Objects.requireNonNull(id, "id");
        long h = mix64(id.getMostSignificantBits());
        h = mix64(h ^ id.getLeastSignificantBits());
        h = mix64(h ^ gameTime);
        h = mix64(h ^ salt);
        return h;
    }

    /**
     * {@link #parityValue(UUID, long, long)}, mapped to a double in {@code [0, 1)} using the
     * same top-53-bits technique {@code java.util.Random.nextDouble()} uses -- for callers that
     * want to compare against a probability/threshold directly.
     */
    public static double parityRoll(UUID id, long gameTime, long salt) {
        long v = parityValue(id, gameTime, salt);
        return (v >>> 11) * 0x1.0p-53;
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * MIX_A;
        z = (z ^ (z >>> 27)) * MIX_B;
        return z ^ (z >>> 31);
    }
}
