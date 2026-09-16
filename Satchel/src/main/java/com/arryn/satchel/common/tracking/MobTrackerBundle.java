package com.arryn.satchel.common.tracking;

import com.arryn.satchel.common.bundle.Capability;
import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.guts.SatchelScope;

public final class MobTrackerBundle extends SatchelBundle {

    public MobTrackerBundle(
            SatchelScope scope,
            BundleKey<MobTrackerBundle> key
    ) {
        super(scope, key);
    }

    @Override
    public int required() {
        return Capability.PERSISTENCE.bit();
    }
}
