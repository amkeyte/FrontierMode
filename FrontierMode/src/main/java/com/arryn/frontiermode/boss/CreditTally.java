package com.arryn.frontiermode.boss;

import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.trinket.Trinket;
import net.minecraft.world.entity.player.Player;

/**
 * Feeds the damage tally held on the {@link Boss} (wiki/frontiermode/architecture/boss.md § Player credit and
 * non-player death). It counts damage whose causing entity is a player -- melee, arrows, tridents, TNT a player lit --
 * at {@link BossDamaged#healthRemoved()}: after armor, capped at the boss's remaining health. A pet's hit is not the
 * player's own. The kind decides what the tally is worth; the Keeper reads it in {@code judgeDeath}.
 *
 * <p>It hears {@link BossDamaged}, a module event scoped to the boss's mob scope, so it receives only its own boss's
 * damage. It keeps no state: the tally lives on the {@code Boss} and survives a chunk unload.
 */
public final class CreditTally implements Trinket {

    private final Boss boss;

    public CreditTally(Boss boss) {
        this.boss = boss;
    }

    @Override
    public void onAttach(ScopeInfo info) {
        info.listenFor(BossDamaged.class, this::onDamage);
    }

    private void onDamage(BossDamaged event) {
        if (event.source().getEntity() instanceof Player) {
            boss.addPlayerDamage(event.healthRemoved());
        }
    }
}
