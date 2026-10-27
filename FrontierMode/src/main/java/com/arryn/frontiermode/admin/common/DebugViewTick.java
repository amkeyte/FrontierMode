package com.arryn.frontiermode.admin.common;

import com.arryn.frontiermode.admin.client.AdminViewClient;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.host.HostJigs;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.trinket.Ticking;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The debug view's tick and its state, on the client level host. Each client tick, while the view is on, it
 * rebuilds the map of marked mobs with a role each; turning the view off is noticed on the next tick, which
 * puts back the name tags it touched and clears the map. The state ends with the client level's scope.
 *
 * <p>Its own class names only common types, and calls {@link AdminViewClient}'s static methods from its
 * method bodies: a contribution names its class at mod init on every side, and a dedicated server must never
 * load a client-only type through it. See wiki admin-tools.md, "Debug view".
 */
public final class DebugViewTick implements Ticking {

    // Entities this view has shown a name tag on, weakly held, so turning it off can put them back.
    private final Set<Entity> touched = Collections.newSetFromMap(new WeakHashMap<>());

    // This tick's marked mobs and their roles, weakly held; rebuilt every tick while on.
    private final Map<Mob, Role> marked = new WeakHashMap<>();

    /**
     * This level's debug view; empty while Satchel or the level's scope isn't ready ("standby, don't
     * crash"). Render or client tick thread.
     */
    public static Optional<DebugViewTick> of(Level level) {
        if (!Satchel.isReady()) {
            return Optional.empty();
        }
        return Satchel.trinket(HostJigs.LEVEL_HOST, new LevelScope(level), DebugViewTick.class);
    }

    /** The mobs to mark this frame and their roles; empty while the view is off. */
    public Map<Mob, Role> marked() {
        return Collections.unmodifiableMap(marked);
    }

    @Override
    public void onAttach(ScopeInfo info) {
        OUT.debug("[Admin] DebugViewTick attached to " + info.debugName());
    }

    @Override
    public void onTickAfter(ScopeInfo info) {
        if (!AdminViewClient.enabled()) {
            if (!touched.isEmpty() || !marked.isEmpty()) {
                for (Entity entity : touched) {
                    entity.setCustomNameVisible(false);
                }
                touched.clear();
                marked.clear();
            }
            return;
        }
        LevelScope scope = info.scopeAs();
        AdminViewClient.mark(scope.level(), marked, touched);
    }
}
