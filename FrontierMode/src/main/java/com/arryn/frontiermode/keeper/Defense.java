package com.arryn.frontiermode.keeper;

import com.arryn.frontiermode.boss.Boss;
import com.arryn.frontiermode.boss.BossAPI;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.mob.MobScope;
import com.arryn.satchel.common.trinket.Ticking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

/**
 * Sentinels defend their Keeper (sentinels.md § Defending the boss; FRO_197), a Keeper ability the Keeper kind
 * subscribes on the server (wiki/frontiermode/architecture/keeper.md § Abilities). Each tick the boss is checked for a
 * new hit credited to a player; on one, its Sentinels near the fight turn on that player, once per hit.
 *
 * <p>Its one field is the boss's last-seen hurt timestamp, seeded at attach so a hit from before the attach never
 * counts. It calls the Sentinel fixture directly; the Keeper events replace that call later.
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
        if (mob.getKillCredit() instanceof ServerPlayer credited) {
            BossAPI.sentinels(mob.level()).ifPresent(sentinels ->
                    sentinels.alertSentinelsOf(boss.bossId(), mob, credited));
        }
    }
}
