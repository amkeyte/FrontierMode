package com.arryn.frontiermode.boss;

import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.mob.MobScope;
import com.arryn.satchel.common.trinket.Trinket;
import net.minecraft.world.entity.Mob;

/**
 * Always on, on the server, for every kind: a boss rides nothing and nothing rides it (wiki/frontiermode/architecture/
 * boss.md § NoRiding; FRO_246). The mob-abilities Trinket subscribes it itself, before the kind's own abilities.
 *
 * <p>At attach it separates the boss from any vehicle or rider: vanilla's setup can hand a boss one before the mob has
 * a scope (a Spider can arrive with a Skeleton on its back, and a baby Zombie on a Chicken). After attach it cancels a
 * mount, in either direction, through {@link BossMount}, and never a dismount, so a rider can always get off.
 */
public final class NoRiding implements Trinket {

    @Override
    public void onAttach(ScopeInfo info) {
        MobScope scope = info.scopeAs();
        Mob mob = scope.mob();
        mob.stopRiding();
        mob.ejectPassengers();
        info.listenFor(BossMount.class, this::onMount);
    }

    private void onMount(BossMount event) {
        if (event.isMounting()) {
            event.cancel();
        }
    }
}
