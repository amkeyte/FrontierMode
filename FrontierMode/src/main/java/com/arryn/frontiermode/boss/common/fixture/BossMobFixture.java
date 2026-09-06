package com.arryn.frontiermode.boss.common.fixture;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.boss.common.bundle.BossMobBundle;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.fixture.SatchelFixture;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.mob.MobJig;
import com.arryn.satchel.common.jig.mob.MobScope;
import net.minecraft.world.entity.Mob;

import java.util.Optional;
import java.util.UUID;

/**
 * {@code MobJig}-scoped, live view attached to a materialized boss entity itself -- the reverse
 * pointer ("which boss record does this entity belong to") that lets a future defeat handler
 * answer "is this dying entity a tracked boss" in O(1) from the entity alone, without scanning
 * every level's {@link BossFixture}. See wiki/frontiermode/architecture/boss.md's "Data model"
 * section.
 *
 * <p><b>Deliberately not persisted</b> -- cheap to rebuild from {@link BossFixture}'s own record
 * whenever a boss's entity happens to be present again, the same "cheap to recompute, don't
 * bother saving it" shape {@code BorderPlayerStatusFixture} already established in this codebase.
 * It exists only while the entity is confirmed live and disappears cleanly when it isn't -- no
 * data is ever lost by that, since it was never the authoritative copy of anything.
 */
public final class BossMobFixture extends SatchelFixture {

    private UUID bossId;

    public BossMobFixture() {
    }

    /**
     * Set once, by the attach handler, at the moment {@code MobJig} confirms this entity is
     * present and this fixture is created for it (see {@code BossModule}'s
     * {@code ScopeEvent.Loaded} handler on {@code BOSS_MOB_JIG}).
     */
    public void attachTo(UUID bossId) {
        this.bossId = bossId;
    }

    public UUID bossId() {
        return bossId;
    }

    /**
     * Resolves the dying entity's boss id, if it's a tracked boss. Checks the entity's existing
     * {@link BossMobFixture} first; if MobJig hasn't attached one yet, falls back to a
     * synchronous {@link MobScope#getFor(Mob)} call before concluding it's genuinely not tracked.
     * Safe to call on the death event since the entity is loaded by definition.
     */
    public static Optional<UUID> resolveBossId(Mob mob) {
        Optional<UUID> existing = existingBossId(mob);
        if (existing.isPresent()) {
            return existing;
        }
        MobScope.getFor(mob);
        return existingBossId(mob);
    }

    private static Optional<UUID> existingBossId(Mob mob) {
        if (!Satchel.isReady()) {
            return Optional.empty();
        }
        MobScope scope = new MobScope(mob);
        Optional<ScopeInfo> infoOpt =
                Satchel.require().tryScopeInfo(FrontierKeys.BOSS_MOB_JIG, scope);
        if (infoOpt.isEmpty() || !infoOpt.get().isReady()) {
            return Optional.empty();
        }
        var jig = (MobJig) infoOpt.get().jigInfo().jig;
        BossMobBundle bundle = jig.getOrCreate(scope, FrontierKeys.BOSS_MOB_BUNDLE);
        BossMobFixture fixture = bundle.getOrCreateFixture(FrontierKeys.BOSS_MOB, BossMobFixture::new);
        return Optional.ofNullable(fixture.bossId());
    }
}