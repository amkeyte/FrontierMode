package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.boss.common.BossDeath;
import com.arryn.frontiermode.boss.common.BossKind;
import com.arryn.frontiermode.boss.common.BossKinds;
import com.arryn.frontiermode.boss.common.DeathVerdict;
import com.arryn.frontiermode.boss.common.SiteOutcome;
import com.arryn.frontiermode.boss.common.Spawned;
import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.frontiermode.core.Dismissal;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.LeaveReason;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.mob.MobScope;
import com.arryn.satchel.common.trinket.Leaving;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import com.arryn.satchel.common.util.schedule.SchedulerClock;
import com.arryn.satchel.common.util.schedule.TickDelay;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * A boss's runtime view (wiki/frontiermode/architecture/boss.md § The Boss object and the roster): built by
 * the {@link BossRoster} from a whole entry, never saved. It holds the boss's identity and kind, the mob (empty
 * until the mob loads), and the transient state the lifecycle needs: the damage tally, the respawn delay, the
 * repeat-death count, the suspended flag and the retiring flag. It reads the base record from the fixture and
 * keeps no copy.
 *
 * <p>It runs the base lifecycle on the server (position, site, spawn) and the endings: a death is settled at
 * the death, and the endings that are not deaths reach it through {@link Leaving}. A loss goes to the
 * {@link Persistent} ability when the kind subscribed one: the record returns to unmaterialized and the boss
 * respawns at home. Without one the boss is transient, and a defeat or a loss drops its record.
 */
public final class Boss implements Leaving {

    private final UUID bossId;
    private final String kindKey;
    private final BossRoster roster;

    // Set when the mob's scope attaches this Boss to the mob-abilities host, cleared when it detaches.
    private Mob mob;

    // Health players' own hits have removed from this boss's body (boss.md § Player credit and non-player
    // death). Survives a chunk unload, resets when a new body materializes, and starts over on a restart.
    private double playerDamage;

    // Whether the death event for the current body has been settled, so a Leaving(KILLED) that follows it
    // does nothing.
    private boolean deathHandled;

    // Set by the project's own removals before they discard the body, so the generic rule never reads them
    // as a loss.
    private boolean retiring;

    // The Persistent ability the kind subscribed to the record host, set at its attach and cleared at its
    // detach; null means the boss is transient.
    private Persistent persistent;

    // The respawn state of the Persistent loss path (boss.md § Player credit and non-player death).
    private int nonPlayerDeaths;
    private TickDelay respawnDelay;
    private boolean respawnSuspended;

    Boss(UUID bossId, String kindKey, BossRoster roster) {
        this.bossId = bossId;
        this.kindKey = kindKey;
        this.roster = roster;
    }

    public UUID bossId() {
        return bossId;
    }

    /** The key of the {@link BossKind} that owns this boss. */
    public String kind() {
        return kindKey;
    }

    /** The boss's live mob, empty until its scope attaches. */
    public Optional<Mob> mob() {
        return Optional.ofNullable(mob);
    }

    /**
     * What this {@code Boss} knows that its record does not, for the Info report: the player-damage tally, the respawn
     * state, and whether the kind subscribed {@code Persistent}.
     */
    public String describe() {
        String respawn;
        if (respawnSuspended) {
            respawn = "suspended (" + nonPlayerDeaths + " non-player deaths)";
        } else if (respawnDelay != null) {
            respawn = "pending (" + nonPlayerDeaths + " non-player deaths)";
        } else {
            respawn = "ready";
        }
        return String.format("player damage %.1f, respawn %s, %s", playerDamage, respawn,
                persistent != null ? "persistent" : "transient");
    }

    /** Adds health a player's own hit actually removed (after armor, capped at what the boss had left). */
    public void addPlayerDamage(float healthRemoved) {
        playerDamage += healthRemoved;
    }

    /** Total health players' own damage has removed from this boss's current body so far. */
    public double playerDamage() {
        return playerDamage;
    }

    /** Marks the boss as being removed by the project, ahead of discarding its body. */
    public void retire() {
        retiring = true;
    }

    public boolean retiring() {
        return retiring;
    }

    // ------------------------------------------------------------------
    // Respawn state -- transient, so a restart just makes a delayed or suspended boss materialize as soon as
    // its home chunk loads.
    // ------------------------------------------------------------------

    /**
     * Whether the boss may materialize this tick: false while auto-respawn is suspended by the repeat-death
     * cap or while its respawn delay is still running. Counted once per tick per unmaterialized record, so
     * the delay runs down in real ticks even while the home chunk is unloaded. A boss with no pending state
     * is always ready.
     */
    public boolean respawnReady() {
        if (respawnSuspended) {
            return false;
        }
        if (respawnDelay == null) {
            return true;
        }
        if (respawnDelay.allow()) {
            respawnDelay = null;
            return true;
        }
        return false;
    }

    /**
     * Forgets the consecutive-death count, pending delay and suspension: on a defeat, a record removal and
     * an admin {@code /boss mob spawn}.
     */
    public void clearRespawnState() {
        nonPlayerDeaths = 0;
        respawnDelay = null;
        respawnSuspended = false;
    }

    /**
     * Counts a loss against the repeat-death cap; under the cap, arms the respawn delay, at the cap suspends
     * auto-respawn (the record stays alive and unmaterialized until {@code /boss mob spawn}). The two settings
     * are {@code Persistent}'s, which calls this.
     *
     * @return true if a respawn is now pending; false if the cap was reached and it's suspended
     */
    boolean countLoss(BossFixture fixture, int delayTicks, int cap) {
        nonPlayerDeaths++;
        if (nonPlayerDeaths >= cap) {
            respawnSuspended = true;
            respawnDelay = null;
            BlockPos position = fixture.get(bossId).map(BossRecord::position).orElse(null);
            OUT.warn("[Boss] countLoss(): bossId=" + Ids.shortId(bossId) + " (position " + position
                    + ") has died to non-player causes " + nonPlayerDeaths + " times in a row -- auto-respawn"
                    + " suspended. Use /boss mob spawn to bring it back (that resets the count).");
            return false;
        }
        respawnDelay = new TickDelay(Math.max(1, delayTicks), new SchedulerClock.AutoClock());
        return true;
    }

    /** Called by the {@code Persistent} ability as it attaches. */
    void attachPersistent(Persistent ability) {
        persistent = ability;
    }

    /** Called by the {@code Persistent} ability as it detaches; only its own attach is cleared. */
    void detachPersistent(Persistent ability) {
        if (persistent == ability) {
            persistent = null;
        }
    }

    /** Schedules the roster's delayed, coalesced durability flush (boss.md § Cascade durability). */
    void scheduleFlush() {
        roster.scheduleFlush();
    }

    // ------------------------------------------------------------------
    // The base lifecycle -- on the server, one Boss at a time, from the roster's tick
    // ------------------------------------------------------------------

    /**
     * One step of the lifecycle (boss.md § Three questions). A record with no position asks the kind's
     * {@code positionReady}, and commits what it answers. A record that is alive, positioned and has no
     * entity waits out its respawn delay and for its chunk to load (a non-forcing check), then has its site
     * prepared and its mob spawned by the kind. Both steps run in one tick, so a Border that finishes
     * pregenerating this tick doesn't lose an extra cycle.
     */
    void advance(Level level, BossFixture fixture) {
        Optional<BossRecord> recordOpt = fixture.get(bossId);
        if (recordOpt.isEmpty() || !recordOpt.get().alive()) {
            return;
        }
        BossKind kind = BossKinds.get(kindKey).orElseThrow();

        if (!recordOpt.get().positioned()) {
            Optional<BlockPos> ready = kind.positionReady(level, bossId);
            if (ready.isEmpty()) {
                return;
            }
            BlockPos chosen = ready.get();
            fixture.CRUD.finalizePosition(bossId, chosen);
            OUT.info("[Boss] advance(): boss " + Ids.shortId(bossId) + " (kind " + kindKey + ") position finalized at "
                    + chosen.getX() + ", " + chosen.getY() + ", " + chosen.getZ()
                    + ". Will materialize once that chunk is loaded.");

            // FRO_120 (Cairn): "the moment a qualifying boss's own position commits" is exactly this point.
            // finalizePosition already committed it, so this re-read reflects the real, final state.
            fixture.CRUD.get(bossId).ifPresent(updated -> BossPositionListeners.fire(level, updated));
            recordOpt = fixture.get(bossId);
            if (recordOpt.isEmpty()) {
                return;
            }
        }

        BossRecord record = recordOpt.get();
        if (!record.positioned() || record.materialized()) {
            return;
        }
        // FRO_160: a boss returned here by a loss waits out its respawn delay (or stays suspended at the
        // repeat-death cap). Polled before the isLoaded check so the delay counts real ticks.
        if (!respawnReady()) {
            return;
        }
        BlockPos position = record.position();
        if (!level.isLoaded(position)) {
            return;
        }
        if (kind.prepareSite(level, bossId, position) == SiteOutcome.WAIT) {
            return;
        }
        Optional<Spawned> spawned = kind.spawn(level, bossId, position);
        if (spawned.isEmpty()) {
            OUT.warn("[Boss] advance(): kind " + kindKey + " declined to spawn bossId=" + Ids.shortId(bossId)
                    + " at " + position + " -- will try again.");
            return;
        }
        roster.materialized(this, fixture, spawned.get());
    }

    /**
     * A new body materialized: its tally starts at zero, its death has not been settled, and a boss that has the
     * {@code Persistent} ability makes the body persistent.
     */
    void onMaterialized(Mob mob) {
        playerDamage = 0.0;
        deathHandled = false;
        if (persistent != null) {
            persistent.onMaterialized(mob);
        }
    }

    // ------------------------------------------------------------------
    // The endings -- on the server
    // ------------------------------------------------------------------

    /**
     * A death is settled at the death, once, at the top of the combat path before {@code markDefeated}
     * (boss.md § Player credit and non-player death). The kind judges it: a defeat marks the record defeated,
     * schedules the flush and calls the kind's {@code onDefeat}, after which a transient boss drops its record; a
     * death that is not a defeat is a loss. A second death event for one death is a no-op ({@code markDefeated}
     * and {@code dematerialize} refuse it).
     */
    void onDeath(Mob dying) {
        Level level = dying.level();
        Optional<BossFixture> fixtureOpt = BossAPI.bosses(level);
        if (fixtureOpt.isEmpty()) {
            OUT.warn("[Boss] onDeath(): BossFixture not available for level " + level.dimension().location()
                    + " -- can't settle bossId=" + bossId + ".");
            return;
        }
        BossFixture fixture = fixtureOpt.get();
        deathHandled = true;

        // FRO_160: vanilla's own kill credit -- the player who last damaged the mob, still set only while
        // vanilla's 100-tick (5 s) window is open; a tamed pet's hit credits its online owner.
        Player credited = dying.getKillCredit() instanceof Player player ? player : null;

        BossKind kind = BossKinds.get(kindKey).orElseThrow();
        DeathVerdict verdict = kind.judgeDeath(level, bossId, new BossDeath(
                credited != null, playerDamage, dying.getMaxHealth(), dying.blockPosition()));

        if (verdict == DeathVerdict.LOSS) {
            lose(fixture, "died with no player credit at " + dying.blockPosition());
            return;
        }

        if (!fixture.CRUD.markDefeated(bossId)) {
            return;
        }
        clearRespawnState();
        roster.scheduleFlush();
        kind.onDefeat(level, bossId, dying.blockPosition(), Optional.of(dying), Optional.ofNullable(credited));
        dropIfTransient(fixture, "was defeated");
    }

    /**
     * A loss: no defeat, no payout. A boss with the {@code Persistent} ability hands it the loss, which returns
     * the record to unmaterialized for the ordinary lifecycle to respawn; a boss without one is transient and
     * drops its record.
     */
    private void lose(BossFixture fixture, String why) {
        if (persistent != null) {
            persistent.onLoss(fixture, why);
            return;
        }
        dropRecord(fixture, why);
    }

    /**
     * After a defeat, forced or in combat: a transient boss (one with no {@code Persistent}) drops its record. The
     * transient default (boss.md § How a boss ends): a boss no kind keeps has its record dropped when it is
     * defeated or lost. It is marked retiring first, as {@code removeBoss} does, so a later leave for the same
     * body is not a second loss. No body is discarded: it is already gone or leaving, and a changed-level copy
     * is dismissed by the leave handling before this runs. The kind's {@code onRemoved} runs through the fixture,
     * and the roster drops this {@code Boss} on its next reconcile.
     */
    void dropIfTransient(BossFixture fixture, String why) {
        if (persistent == null) {
            dropRecord(fixture, why);
        }
    }

    // Marks the Boss retiring, removes its base record and flushes; see dropIfTransient for when.
    private void dropRecord(BossFixture fixture, String why) {
        retire();
        if (fixture.CRUD.remove(bossId)) {
            roster.scheduleFlush();
            OUT.info("[Boss] dropRecord(): bossId=" + Ids.shortId(bossId) + " " + why
                    + " -- not Persistent, record removed.");
        }
    }

    @Override
    public void onAttach(ScopeInfo info) {
        MobScope scope = info.scopeAs();
        mob = scope.mob();

        // A defeated record whose body loads (a forced defeat while it was unloaded) leaves in a puff; the
        // record keeps naming the dead body, and the roster stops holding interest in it.
        Level level = mob.level();
        boolean defeated = BossAPI.bosses(level).flatMap(fixture -> fixture.get(bossId))
                .map(record -> !record.alive()).orElse(false);
        if (defeated) {
            Dismissal.dismiss(mob);
            roster.endInterest(bossId, mob.getUUID());
        }
    }

    @Override
    public void onDetach(ScopeInfo info) {
        // A respawned body may have attached this Boss to its own scope before the old scope detaches.
        if (mob == ((MobScope) info.scope()).mob()) {
            mob = null;
        }
    }

    /**
     * The endings that are not deaths (boss.md § How a boss ends). {@code KILLED}: nothing if the death was
     * handled, otherwise a loss. {@code DISCARDED}: nothing if the boss is retiring or its record is gone or
     * defeated, otherwise a loss. {@code CHANGED_LEVEL}: a loss, and the copy in the destination level is
     * discarded. {@code UNLOADED} and {@code UNKNOWN}: nothing. A terminal reason ends the roster's interest.
     */
    @Override
    public void onLeave(ScopeInfo info, LeaveReason reason) {
        if (reason == null) {
            return;
        }
        Mob leaving = ((MobScope) info.scope()).mob();
        Level level = leaving.level();
        UUID entityId = leaving.getUUID();

        switch (reason) {
            case KILLED -> {
                if (!deathHandled) {
                    loseOnLeave(level, entityId, "was killed with no death event");
                }
                roster.endInterest(bossId, entityId);
            }
            case DISCARDED -> {
                if (!retiring && recordAlive(level)) {
                    loseOnLeave(level, entityId, "was discarded");
                }
                roster.endInterest(bossId, entityId);
            }
            case CHANGED_LEVEL -> {
                loseOnLeave(level, entityId, "changed level");
                dismissCopyElsewhere(leaving, level);
                roster.endInterest(bossId, entityId);
            }
            default -> {
                // UNLOADED and UNKNOWN: a chunk unload keeps the record, and the boss re-attaches when the
                // chunk returns.
            }
        }
    }

    private boolean recordAlive(Level level) {
        return BossAPI.bosses(level).flatMap(fixture -> fixture.get(bossId)).map(BossRecord::alive).orElse(false);
    }

    /**
     * A leave is a loss only for the body the record still names: a body that left after the record had
     * already moved on to a respawned one is not this boss's current one.
     */
    private void loseOnLeave(Level level, UUID entityId, String why) {
        BossAPI.bosses(level).ifPresent(fixture -> {
            boolean current = fixture.get(bossId).map(record -> entityId.equals(record.bossEntityId())).orElse(false);
            if (current) {
                lose(fixture, why);
            }
        });
    }

    /**
     * A boss lives with its level. Vanilla creates the copy in the destination with the same UUID and adds it
     * before it removes the original, so the copy exists when the leave is reported: look the UUID up in every
     * other level and dismiss it.
     */
    private void dismissCopyElsewhere(Mob original, Level originLevel) {
        MinecraftServer server = original.getServer();
        if (server == null) {
            return;
        }
        for (Level other : server.getAllLevels()) {
            if (other == originLevel) {
                continue;
            }
            Satchel.require().egress().getEntity(other, original.getUUID())
                    .ifPresent(copy -> {
                        if (copy instanceof Mob copyMob) {
                            Dismissal.dismiss(copyMob);
                        }
                    });
        }
    }
}
