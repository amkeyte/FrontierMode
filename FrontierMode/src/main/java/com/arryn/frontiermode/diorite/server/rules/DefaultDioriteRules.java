package com.arryn.frontiermode.diorite.server.rules;

import com.arryn.satchel.common.tunable.Tunable;

/** Safe-baseline Striker numbers (diorite-tools.md), Tunable-backed and constructed at each read. */
public final class DefaultDioriteRules implements DioriteRules {

    @Override
    public float strikerSparkSize() {
        return new Tunable("strikerSparkSize", 0.25f, 4f, 1.0f).floatValue();
    }

    @Override
    public int strikerColorOnlySparks() {
        return new Tunable("strikerColorOnlySparks", 1, 10, 3).intValue();
    }

    @Override
    public float strikerBlastPower() {
        return new Tunable("strikerBlastPower", 0.1f, 4f, 1.0f).floatValue();
    }

    @Override
    public int strikerBlastDurability() {
        return new Tunable("strikerBlastDurability", 1, 64, 16).intValue();
    }
}
