package com.arryn.satchel.common.tracking;

import com.arryn.satchel.common.bundle.Capability;
import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.guts.SatchelScope;

public final class TrinketProbeBundle extends SatchelBundle {

    public TrinketProbeBundle(
            SatchelScope scope,
            BundleKey<TrinketProbeBundle> key
    ) {
        super(scope, key);
    }

    @Override
    public int required() {
        return Capability.PERSISTENCE.bit();
    }
}
