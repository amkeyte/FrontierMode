package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.trinket.Trinket;
import com.arryn.satchel.common.tunable.Tunable;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.entity.Mob;

import java.util.UUID;

/**
 * The {@code Persistent} ability (wiki/frontiermode/architecture/boss.md § The {@code Persistent} ability: a loss
 * returns the boss), a record-side ability a kind subscribes to the {@code BOSS_JIG} host. It makes the body
 * persistent when it materializes, and keeps the record through a loss and brings the boss back: a loss returns the
 * record to unmaterialized, and the ordinary materialization check respawns it at its recorded position once the
 * respawn delay has run down. The respawn state itself (the repeat-death count, the pending delay and the suspended
 * flag) is transient and stays on {@link Boss}; the settings that decide it, {@code respawnDelayTicks} and
 * {@code maxConsecutiveNonPlayerDeaths}, are this ability's.
 *
 * <p>Server only, and not {@code Ticking}: it materializes nothing itself ({@code Boss.advance} still runs the
 * materialization check for every kind and asks {@link Boss#respawnReady}). Holding its {@code Boss}, it tells the
 * boss when it attaches and detaches, so a loss reaches it and a boss without one is transient.
 */
public final class Persistent implements Trinket {

    private final Boss boss;

    public Persistent(Boss boss) {
        this.boss = boss;
    }

    @Override
    public void onAttach(ScopeInfo info) {
        boss.attachPersistent(this);
    }

    @Override
    public void onDetach(ScopeInfo info) {
        boss.detachPersistent(this);
    }

    /**
     * A body has materialized for this boss: a plain vanilla {@code Mob} is still subject to natural despawn unless
     * persistence is required, and Border Pregeneration can run minutes before a player arrives. Called from
     * the one place every materialization passes through, right after the kind's {@code spawn} returns and before
     * the mob's first tick.
     */
    void onMaterialized(Mob mob) {
        mob.setPersistenceRequired();
    }

    /**
     * A loss: no defeat, no payout. The record returns to unmaterialized (a duplicate event for one loss is a
     * no-op) and the ordinary lifecycle respawns the boss at home after the delay, or, at the repeat-death cap,
     * leaves it suspended until {@code /boss mob spawn}.
     */
    void onLoss(BossFixture fixture, String why) {
        UUID bossId = boss.bossId();
        if (!fixture.CRUD.dematerialize(bossId)) {
            return;
        }
        // The cleared bossEntityId is real state worth protecting: a crash before the next autosave would
        // restore a "materialized" record whose entity is dead.
        boss.scheduleFlush();

        int delayTicks = new Tunable("respawnDelayTicks", 1, 6000, 100).intValue();
        int cap = new Tunable("maxConsecutiveNonPlayerDeaths", 1, 100, 5).intValue();
        if (boss.countLoss(fixture, delayTicks, cap)) {
            OUT.info("[Boss] Persistent.onLoss(): bossId=" + Ids.shortId(bossId) + " " + why
                    + " -- respawning at its recorded position.");
        }
    }
}
