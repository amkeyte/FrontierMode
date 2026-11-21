package com.arryn.satchel.common.tracking;

import com.arryn.satchel.common.bundle.Capability;
import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.guts.SatchelScope;

public final class PlayerTrackerBundle extends SatchelBundle {

    public PlayerTrackerBundle(
            SatchelScope scope,
            BundleKey<PlayerTrackerBundle> key
    ) {
        super(scope, key);
    }

    @Override
    public int required() {
        return Capability.PERSISTENCE.bit() | Capability.NETWORKING.bit();
    }
}
