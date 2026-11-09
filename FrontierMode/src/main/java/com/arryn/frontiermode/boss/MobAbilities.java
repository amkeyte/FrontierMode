package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.boss.common.BossKind;
import com.arryn.frontiermode.boss.common.BossKinds;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.mob.MobScope;
import com.arryn.satchel.common.trinket.DynamicTrinket;
import com.arryn.satchel.common.trinket.Trinket;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * The mob-abilities Trinket on {@code BOSS_MOB_JIG} (wiki/frontiermode/architecture/boss.md § Abilities). Satchel's
 * {@link DynamicTrinket} is the host that holds the subscribers and forwards {@code Leaving} to them, declared before
 * this Trinket on the jig; this one, once a mob scope attaches, finds the mob's {@link Boss} through the roster and
 * subscribes, in order: the {@code Boss} itself (server only), {@link NoRiding} (server only, always, so no kind opts
 * in or out), then the kind's own abilities through {@link BossKind#subscribeAbilities}, each on the side it applies
 * to. No {@code Loaded} or {@code Unloaded} handler is involved, and the subscriptions live with the scope.
 *
 * <p>A boss whose record is defeated (a forced defeat while it was unloaded) subscribes only the {@code Boss}, which
 * dismisses the body at attach; nothing else subscribes.
 */
public final class MobAbilities implements Trinket {

    @Override
    public void onAttach(ScopeInfo info) {
        MobScope scope = info.scopeAs();
        Mob mob = scope.mob();
        Level level = mob.level();

        Optional<Boss> bossOpt = BossAPI.roster(level).flatMap(roster -> roster.bossFor(mob.getUUID()));
        if (bossOpt.isEmpty()) {
            OUT.debug("[Boss] MobAbilities.onAttach(): no Boss for entity " + mob.getUUID()
                    + " -- not a tracked boss, ignoring.");
            return;
        }
        Boss boss = bossOpt.get();

        DynamicTrinket host = Satchel.trinket(FrontierKeys.BOSS_MOB_JIG, scope, DynamicTrinket.class)
                .orElseThrow(() -> new IllegalStateException(
                        "[Boss] MobAbilities.onAttach(): the mob-abilities host is not attached for entity "
                                + mob.getUUID()));
        boolean server = Satchel.isServer();
        boolean alive = BossAPI.bosses(level).flatMap(fixture -> fixture.get(boss.bossId()))
                .map(BossRecord::alive).orElse(false);

        // The endings are the server's: a client reads DISCARDED for a mob that merely leaves tracking range, so it
        // never acts on Leaving.
        if (server) {
            host.subscribe(boss);
        }
        if (!alive) {
            return;
        }
        if (server) {
            host.subscribe(new NoRiding());
        }
        BossKinds.get(boss.kind()).orElseThrow().subscribeAbilities(level, boss, host);
    }
}
