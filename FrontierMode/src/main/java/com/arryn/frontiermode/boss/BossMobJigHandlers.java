package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.boss.common.bundle.BossMobBundle;
import com.arryn.frontiermode.boss.common.fixture.BossMobFixture;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.mob.MobJig;
import com.arryn.satchel.common.jig.mob.MobScope;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.entity.Mob;

import java.util.Optional;
import java.util.UUID;

/** Event handlers for {@code BOSS_MOB_JIG}: mob gained/lost interest. */
final class BossMobJigHandlers {

    private BossMobJigHandlers() {
    }

    /**
     * Attaches {@link BossMobFixture} once MobJig confirms a tracked boss entity is present --
     * fires both for a freshly materialized boss and for a previously materialized boss whose
     * chunk just reloaded.
     */
    static void onScopeLoaded(ScopeEvent.MobGainedInterest event) {
        if (!event.isJig(FrontierKeys.BOSS_MOB_JIG)) return;
        ScopeInfo info = event.info();
        MobScope scope = (MobScope) info.scope();
        Mob mob = scope.mob();
        UUID entityId = mob.getUUID();

        Optional<BossRecord> recordOpt = BossAPI.bosses(mob.level())
                .flatMap(fixture -> fixture.CRUD.all().stream()
                        .filter(r -> entityId.equals(r.bossEntityId()))
                        .findFirst());

        if (recordOpt.isEmpty()) {
            OUT.debug("[Boss] onScopeLoaded: no BossFixture record for entity " + entityId
                    + " -- not a tracked boss, ignoring.");
            return;
        }

        var jig = (MobJig) info.jigInfo().jig;
        BossMobBundle bundle = jig.getOrCreate(scope, FrontierKeys.BOSS_MOB_BUNDLE);
        BossMobFixture fixture = bundle.getOrCreateFixture(FrontierKeys.BOSS_MOB, BossMobFixture::new);
        fixture.attachTo(recordOpt.get().bossId());
    }

    /** Chunk unload and genuine removal are indistinguishable here by design -- see boss.md. */
    static void onScopeUnloaded(ScopeEvent.MobLostInterest event) {
        if (!event.isJig(FrontierKeys.BOSS_MOB_JIG)) return;
        ScopeInfo info = event.info();
        MobScope scope = (MobScope) info.scope();
        OUT.debug("[Boss] Boss mob scope unloaded (chunk unload or removal, indistinguishable"
                + " here): " + scope.uuid());
    }
}
