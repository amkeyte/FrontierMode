package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.mob.MobScope;
import com.arryn.satchel.common.trinket.DynamicTrinket;
import com.arryn.satchel.common.trinket.Trinket;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.entity.Mob;

import java.util.Optional;

/**
 * The mob-abilities Trinket on {@code BOSS_MOB_JIG} (wiki/frontiermode/architecture/boss.md § Abilities).
 * Satchel's {@link DynamicTrinket} is the host that holds the subscribers and forwards {@code Leaving} to
 * them, declared before this Trinket on the jig; this one, once a mob scope attaches, finds the mob's
 * {@link Boss} through the roster and subscribes it. No {@code Loaded} or {@code Unloaded} handler is
 * involved, and the subscription lives with the scope.
 */
public final class MobAbilities implements Trinket {

    @Override
    public void onAttach(ScopeInfo info) {
        MobScope scope = info.scopeAs();
        Mob mob = scope.mob();

        Optional<Boss> bossOpt = BossAPI.roster(mob.level()).flatMap(roster -> roster.bossFor(mob.getUUID()));
        if (bossOpt.isEmpty()) {
            OUT.debug("[Boss] MobAbilities.onAttach(): no Boss for entity " + mob.getUUID()
                    + " -- not a tracked boss, ignoring.");
            return;
        }

        // The endings are the server's: a client reads DISCARDED for a mob that merely leaves tracking range,
        // so it never acts on Leaving.
        if (!Satchel.isServer()) {
            return;
        }
        DynamicTrinket host = Satchel.trinket(FrontierKeys.BOSS_MOB_JIG, scope, DynamicTrinket.class)
                .orElseThrow(() -> new IllegalStateException(
                        "[Boss] MobAbilities.onAttach(): the mob-abilities host is not attached for entity "
                                + mob.getUUID()));
        host.subscribe(bossOpt.get());
    }
}
