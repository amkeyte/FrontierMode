package com.arryn.frontiermode.keeper;

import com.arryn.frontiermode.boss.Boss;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.mob.MobScope;
import com.arryn.satchel.common.trinket.Ticking;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * Sentinels defend their Keeper (sentinels.md § Defending the boss; FRO_197), a Keeper ability the Keeper kind
 * subscribes on the server (wiki/frontiermode/architecture/keeper.md § Abilities). Each tick the boss is checked for a
 * new hit credited to a player; on one, its Sentinels near the fight turn on that player, once per hit.
 *
 * <p>Its one field is the boss's last-seen hurt timestamp, seeded at attach so a hit from before the attach never
 * counts. The hit is announced as a {@link KeeperHit} on Satchel's bus, which the Sentinel module answers.
 */
final class Defense implements Ticking {

    private final Boss boss;

    // The boss's last-hurt timestamp as last seen, so each hit is noticed once.
    private int lastSeenHurtTimestamp;

    Defense(Boss boss) {
        this.boss = boss;
    }

    @Override
    public void onAttach(ScopeInfo info) {
        MobScope scope = info.scopeAs();
        lastSeenHurtTimestamp = scope.mob().getLastHurtByMobTimestamp();
    }

    @Override
    public void onTickAfter(ScopeInfo info) {
        MobScope scope = info.scopeAs();
        Mob mob = scope.mob();

        int stamp = mob.getLastHurtByMobTimestamp();
        if (stamp == lastSeenHurtTimestamp) {
            return;
        }
        lastSeenHurtTimestamp = stamp;
        if (mob.getKillCredit() instanceof Player credited) {
            Satchel.require().eventBus().post(new KeeperHit(mob.level(), boss.bossId(), mob, credited));
        }
    }
}
