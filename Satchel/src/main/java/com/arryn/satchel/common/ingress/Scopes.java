package com.arryn.satchel.common.ingress;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.identity.JigKey;
import com.arryn.satchel.common.jig.guts.JigInfo;
import com.arryn.satchel.common.jig.guts.LogicalFoundation;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.mob.MobJig;
import net.minecraft.world.entity.Mob;

import java.util.Optional;

/**
 * Resolver helpers for {@link IngressConfig#translate}: the common shape of "which scope of this
 * jig does this source belong to", for a resolver to return as its context.
 */
public final class Scopes {

    private Scopes() {
    }

    /**
     * The ready scope {@code jigKey} holds for {@code source} (a {@code Level}, a player, a mob), on
     * the foundation bound to the calling thread. Empty when there is no such scope: no foundation
     * bound or ready, the jig isn't installed on this side, the jig doesn't recognize the source, or
     * the source has no scope yet or it isn't ready. An empty answer ends the firing before anything
     * is built.
     *
     * <p>
     * Cheap enough for a resolver, which runs for every firing of its native event: a lookup by the id
     * the jig derives from the source, with no scope built to look it up with. For a mob jig the answer
     * is admission: see {@code MobJig.infoFor(Mob)}.
     */
    public static Optional<ScopeInfo> of(JigKey<?> jigKey, Object source) {
        if (source == null) return Optional.empty();

        LogicalFoundation foundation = Satchel.peekFoundation();
        if (foundation == null || !foundation.isReady()) return Optional.empty();

        Optional<JigInfo> jigInfo = foundation.askJigInfo(jigKey);
        if (jigInfo.isEmpty()) return Optional.empty();

        JigInfo ji = jigInfo.get();

        // A mob jig answers by admission: an admitted mob in the level gets its scope built on the
        // spot, so an event for it is delivered even if it lands before the sweep has built one, and
        // a mob the jig does not admit never reaches the event.
        if (ji.jig instanceof MobJig mobJig) {
            if (!(source instanceof Mob mob)) return Optional.empty();
            return mobJig.infoFor(mob).filter(ScopeInfo::isReady);
        }

        return ji.scopeInfoFor(source).filter(ScopeInfo::isReady);
    }
}
