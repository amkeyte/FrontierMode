package com.arryn.frontiermode.bosstell.common.bundle;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.bosstell.common.fixture.BossTellFixture;
import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.level.LevelScope;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * The Tell module's level-scoped bundle on {@code TELL_JIG}, hosting {@link BossTellFixture}
 * (wiki/frontiermode/architecture/discovery-systems.md § Environmental Tells). Intentionally boring: no logic, no state
 * beyond its fixture. It requires nothing of Satchel: the fixture keeps nothing durable (its cadence counter, RNG and
 * per-boss sound rotation are all transient), and the Border's {@code "tell"} curve lives in Border's own curve fixture.
 */
public final class TellBundle extends SatchelBundle {

    public TellBundle(LevelScope scope, BundleKey<TellBundle> key) {
        super(scope, key);
    }

    public Level level() {
        return ((LevelScope) scope).level();
    }

    public Optional<BossTellFixture> tell() {
        return get(FrontierKeys.BOSS_TELL);
    }
}
