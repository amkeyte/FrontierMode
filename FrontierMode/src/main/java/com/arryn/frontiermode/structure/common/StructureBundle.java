package com.arryn.frontiermode.structure.common;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.satchel.common.bundle.Capability;
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

    /**
     * FRO_164: {@code CairnFixture} is real, persisted, world-anchored state (StructureModule's jig
     * allows persistence), so this bundle must declare the matching requirement itself. The engine
     * gates BOTH hydration and flush on the bundle's own {@code required()} (SAT_053) -- a bundle
     * that leaves it at the default {@code Capability.NONE} is never loaded from or written to
     * disk, no matter what its jig's policy says. Missing this meant every Cairn record was lost on
     * restart, leaving a placed Cairn orphaned in the world with nothing behind its cap block.
     */
    @Override
    public int required() {
        return Capability.PERSISTENCE.bit();
    }

    public Optional<CairnFixture> cairn() {
        return get(FrontierKeys.CAIRN);
    }
}
