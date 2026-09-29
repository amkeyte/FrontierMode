package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.boss.common.bundle.BossMobBundle;
import com.arryn.frontiermode.boss.common.fixture.BossMobFixture;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.mob.MobJig;
import com.arryn.satchel.common.jig.mob.MobScope;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import com.arryn.satchel.common.util.Ids;
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
            OUT.debug("[Boss] onScopeLoaded: no BossFixture record for entity " + Ids.shortId(entityId)
                    + " -- not a tracked boss, ignoring.");
            return;
        }

        // A boss whose record is already defeated (a forced /boss defeat while it was unloaded) leaves
        // in a puff instead of attaching; a real defeat kills the body, so this never sees one.
        if (!recordOpt.get().alive()) {
            BossAPI.bosses(mob.level()).ifPresent(f -> f.RULES.dismiss(mob));
            return;
        }

        var jig = (MobJig) info.jigInfo().jig;
        BossMobBundle bundle = jig.getOrCreate(scope, FrontierKeys.BOSS_MOB_BUNDLE);
        BossMobFixture fixture = bundle.getOrCreateFixture(FrontierKeys.BOSS_MOB, BossMobFixture::new);
        fixture.attachTo(recordOpt.get().bossId(), mob);

        // FRO_145: publish "this entity is a boss" to clients -- no position, just the id pair.
        // FRO_148: plus its size multiplier, computed once from the record's layer. The entity was
        // constructed before it was known to be a boss, so its hitbox is still unscaled -- refresh
        // it now that the marker (which the Size handler reads) exists.
        BossRecord record = recordOpt.get();
        float scale = BossAPI.bosses(mob.level())
                .map(f -> f.RULES.scaleForLayer(record.layer())).orElse(1.0F);
        BossAPI.publicMarkers(mob.level()).ifPresent(m -> m.add(record.bossId(), entityId, scale));
        mob.refreshDimensions();
    }

    /** Chunk unload and genuine removal are indistinguishable here by design -- see boss.md. */
    static void onScopeUnloaded(ScopeEvent.MobLostInterest event) {
        if (!event.isJig(FrontierKeys.BOSS_MOB_JIG)) return;
        ScopeInfo info = event.info();
        MobScope scope = (MobScope) info.scope();
        OUT.debug("[Boss] Boss mob scope unloaded (chunk unload or removal, indistinguishable"
                + " here): " + Ids.shortId(scope.uuid()));

        // FRO_145: retire the client marker at the same release hook it was published at.
        BossAPI.publicMarkers(scope.mob().level()).ifPresent(m -> m.removeByMob(scope.uuid()));
    }
}
