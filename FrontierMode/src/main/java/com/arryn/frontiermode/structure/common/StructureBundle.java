package com.arryn.frontiermode.structure.common;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.guts.SatchelScope;

import java.util.Optional;

/**
 * Level-scoped bundle hosting {@link CairnFixture} -- named for the concern ("structures"), not
 * the fixture, per cairn.md's "Module" section: the same way {@code BossBundle} started with one
 * fixture and picked up two more without a rename, so a future second structure fixture doesn't
 * force a rename here either.
 */
public final class StructureBundle extends SatchelBundle {

    public StructureBundle(
            SatchelScope owner,
            BundleKey<StructureBundle> key
    ) {
        super(owner, key);
    }

    public Optional<CairnFixture> cairn() {
        return get(FrontierKeys.CAIRN);
    }
}
