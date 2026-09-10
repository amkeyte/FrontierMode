package com.arryn.satchel.common.bundle;

/**
 * Bitflag declaration of the capability axes a {@link SatchelBundle} subclass actually needs
 * satisfied before it can be promoted out of {@code CREATED}. Declared per-bundle by the bundle
 * author via {@link SatchelBundle#required()} -- deliberately NOT derived from jig-level
 * {@code JigPolicies.Capabilities}. That record answers a different, structural question ("can
 * this jig's engine wiring resolve a Level/network channel at all") from the one this enum
 * answers ("does this particular bundle want persistence or networking"). Conflating the two was
 * the root of SAT_049: a bundle that needed neither capability never left CREATED because
 * promotion was gated on a jig-level flag it never claimed to care about.
 *
 * Comparison against {@link SatchelBundle#required()} is strict/exact, not superset -- an
 * engine accumulates the bits it can currently satisfy and only promotes when the accumulated
 * mask equals the bundle's declared requirement exactly. See SAT_053.
 */
public enum Capability {
    PERSISTENCE(1),
    NETWORKING(1 << 1);

    /** No capability required -- a bundle declaring this promotes immediately on creation. */
    public static final int NONE = 0;

    private final int bit;

    Capability(int bit) {
        this.bit = bit;
    }

    public int bit() {
        return bit;
    }
}
