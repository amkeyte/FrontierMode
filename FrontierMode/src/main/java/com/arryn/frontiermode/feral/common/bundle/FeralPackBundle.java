package com.arryn.frontiermode.feral.common.bundle;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.feral.common.fixture.FeralPackFixture;
import com.arryn.satchel.common.bundle.Capability;
import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.level.LevelScope;

import java.util.Optional;

/** {@code FERAL_LEVEL_JIG}'s one bundle: the level's pack-member records, persisted. */
public final class FeralPackBundle extends SatchelBundle {

    public FeralPackBundle(LevelScope scope, BundleKey<FeralPackBundle> key) {
        super(scope, key);
    }

    @Override
    public int required() {
        return Capability.PERSISTENCE.bit();
    }

    public Optional<FeralPackFixture> pack() {
        return get(FrontierKeys.FERAL_PACK);
    }
}
