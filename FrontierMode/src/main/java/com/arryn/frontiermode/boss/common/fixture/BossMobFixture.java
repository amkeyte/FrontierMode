package com.arryn.frontiermode.boss.common.fixture;

import com.arryn.satchel.common.fixture.SatchelFixture;

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
}
