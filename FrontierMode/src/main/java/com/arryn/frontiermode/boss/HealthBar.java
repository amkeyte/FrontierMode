package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.boss.common.fixture.BossRulesFacet;
import com.arryn.frontiermode.boss.server.rules.DefaultBossRules;
import com.arryn.frontiermode.effects.common.BossBarHandle;
import com.arryn.frontiermode.effects.common.EffectsAPI;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.mob.MobScope;
import com.arryn.satchel.common.trinket.Ticking;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Mob;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A boss's health bar (wiki/frontiermode/architecture/boss.md § Health bar; FRO_189, FRO_209), on or off per kind: the
 * Keeper subscribes it only for a milestone Keeper. Each tick, a player holding the boss's kill credit -- their hit,
 * projectile, or tamed pet -- starts watching; a viewer stops {@code bossBarLingerTicks} after they last held it, or at
 * once if they die, disconnect, or leave the level. The one audience that exists is the credited players.
 *
 * <p>The bar and its viewers are this ability's own state, and live and die with the mob's scope: a respawned boss is a
 * new entity with a fresh scope and a fresh bar, and the bar goes away for everyone when the scope detaches.
 */
public final class HealthBar implements Ticking {

    private final Boss boss;

    // Created on the first viewer; null until then.
    private BossBarHandle healthBar;
    // Viewing player -> game time they last held the boss's kill credit.
    private final Map<UUID, Long> barViewers = new HashMap<>();

    public HealthBar(Boss boss) {
        this.boss = boss;
    }

    @Override
    public void onTickAfter(ScopeInfo info) {
        MobScope scope = info.scopeAs();
        Mob mob = scope.mob();
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        Optional<BossRulesFacet> rules = BossAPI.RULES(level);
        if (rules.isEmpty()) {
            return;
        }
        int lingerTicks = rules.get().bossBarLingerTicks();

        if (!mob.isAlive()) {
            hide();
            return;
        }

        long now = level.getGameTime();
        if (mob.getKillCredit() instanceof ServerPlayer credited) {
            if (healthBar == null) {
                healthBar = EffectsAPI.createBossBar(Component.literal(DefaultBossRules.BOSS_NAME),
                        BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
            }
            if (barViewers.put(credited.getUUID(), now) == null) {
                healthBar.addPlayer(credited);
                OUT.debug("[Boss] onTickAfter(): showing bossId=" + Ids.shortId(boss.bossId()) + " to "
                        + credited.getGameProfile().getName());
            }
        }

        Iterator<Map.Entry<UUID, Long>> it = barViewers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> viewer = it.next();
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(viewer.getKey());
            boolean stillWatching = player != null
                    && !player.isDeadOrDying()
                    && player.level() == level
                    && now - viewer.getValue() <= lingerTicks;
            if (!stillWatching) {
                healthBar.removePlayer(viewer.getKey());
                it.remove();
            }
        }

        if (healthBar != null) {
            healthBar.setProgress(mob.getHealth() / mob.getMaxHealth());
        }
    }

    /** The boss left (death, dismissal, unload): its bar goes with it, for everyone. */
    @Override
    public void onDetach(ScopeInfo info) {
        hide();
    }

    private void hide() {
        if (healthBar != null) {
            healthBar.removeAll();
        }
        barViewers.clear();
    }
}
