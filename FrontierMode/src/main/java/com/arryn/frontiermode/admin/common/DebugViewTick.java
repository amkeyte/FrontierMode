package com.arryn.frontiermode.admin.common;

import com.arryn.frontiermode.admin.client.AdminViewClient;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.host.HostJigs;
import com.arryn.satchel.common.jig.player.PlayerScope;
import com.arryn.satchel.common.trinket.Ticking;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The debug view's tick and all of its state, on the client player host. The state is the on/off flag, the set of
 * mobs it has shown a name tag on, and this tick's marked mobs with a role each, so it ends with the client's player
 * scope: a quit, a new world and a join to a server each start with the view off, and a change of dimension or a
 * respawn keeps it. Each client tick, while the view is on, it rebuilds the map of marked mobs; turning the view off
 * is noticed on the next tick, which puts back the name tags it touched and clears the map.
 *
 * <p>Its own class names only common types, and calls {@link AdminViewClient}'s static methods from its
 * method bodies: a contribution names its class at mod init on every side, and a dedicated server must never
 * load a client-only type through it. See wiki admin-tools.md, "Debug view".
 */
public final class DebugViewTick implements Ticking {

    // Whether the view is on. Default off, and a field of this Trinket, so it is not persisted and ends with the scope.
    private boolean on;

    // Entities this view has shown a name tag on, weakly held, so turning it off can put them back.
    private final Set<Entity> touched = Collections.newSetFromMap(new WeakHashMap<>());

    // This tick's marked mobs and their roles, weakly held; rebuilt every tick while on.
    private final Map<Mob, Role> marked = new WeakHashMap<>();

    /**
     * The debug view of {@code player}'s scope; empty while Satchel or the player's scope isn't ready ("standby,
     * don't crash"). Render or client tick thread.
     */
    public static Optional<DebugViewTick> of(Player player) {
        if (!Satchel.isReady()) {
            return Optional.empty();
        }
        return Satchel.trinket(HostJigs.PLAYER_HOST, new PlayerScope(player), DebugViewTick.class);
    }

    /** Whether the view is on. */
    public boolean isOn() {
        return on;
    }

    /** Turns the view on or off; the next tick notices an off. */
    public void set(boolean value) {
        on = value;
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
        if (!on) {
            if (!touched.isEmpty() || !marked.isEmpty()) {
                for (Entity entity : touched) {
                    entity.setCustomNameVisible(false);
                }
                touched.clear();
                marked.clear();
            }
            return;
        }
        PlayerScope scope = info.scopeAs();
        AdminViewClient.mark(scope.player().level(), marked, touched);
    }
}
