package com.arryn.frontiermode.boss;

import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.mob.MobScope;
import com.arryn.satchel.common.trinket.Trinket;

/**
 * Makes the entity's hitbox follow the record's {@code scale} (wiki/frontiermode/architecture/boss.md § Abilities), on
 * both sides. The mob was constructed before it was known to be a boss, so its hitbox is still unscaled: at attach it
 * refreshes its dimensions, and the scale source ({@link BossScaleSource}) answers from the record, which is written
 * before interest is pushed. It replaces the refresh the attach handler did and the client's per-tick poll.
 */
public final class BodyScale implements Trinket {

    @Override
    public void onAttach(ScopeInfo info) {
        MobScope scope = info.scopeAs();
        scope.mob().refreshDimensions();
    }
}
