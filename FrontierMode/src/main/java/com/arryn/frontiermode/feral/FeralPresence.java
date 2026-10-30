package com.arryn.frontiermode.feral;

import com.arryn.frontiermode.core.Presence;
import com.arryn.frontiermode.feral.server.rules.FeralRules;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.host.HostJigs;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.trinket.Ticking;
import com.arryn.satchel.common.util.out.OUT;
import com.arryn.satchel.common.util.schedule.TickDelay;
import com.arryn.satchel.common.util.schedule.TickThrottler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The Feral mobs in the level now (feral.md § Presence), a Trinket contributed to the level host on the
 * server. It is the shared {@link Presence} helper with Feral's two hooks, plus the level-side tick work
 * that reads it: the re-entry sweep, both despawn rules and the pack diagnostic log, whose bodies are
 * {@link FeralCleanup}'s. Feral keeps no jig, so nothing here is declared on one and nothing is saved.
 *
 * <ul>
 *   <li>A join queues ({@link #join}, from {@link FeralTagging#onHostileJoining}); the tick adopts it, or
 *   drops a cancelled or rejected join.</li>
 *   <li>An adopted mob goes into the pack set (its record says {@code pack}) or the ambient set.</li>
 *   <li>The tick drops a mob when it reports {@code isRemoved()}.</li>
 *   <li>Everything that needs "the Feral mobs here" reads presence and scans nothing.</li>
 * </ul>
 *
 * <p>The throttlers and the despawn timers are fields, so they end with the level's scope. On its first
 * tick it joins the Feral mobs already in the level: a mob in a chunk loaded before this level's scope
 * was ready joined with no presence to take it.
 */
public final class FeralPresence implements Ticking {

    private final Presence present = new Presence();
    private final Set<UUID> packIds = new HashSet<>();

    // Per-mob lingering despawn timers, for the built-off linger rule (FeralCleanup). Entries end with
    // the mob, in onDrop.
    private final Map<UUID, TickDelay> despawnTimers = new HashMap<>();

    private TickThrottler reentrySweep;
    private TickThrottler diagnostics;
    private boolean swept;

    private final Presence.Hooks hooks = new Presence.Hooks() {
        @Override
        public boolean adopt(Mob mob, boolean fresh) {
            if (FeralData.isPack(mob)) {
                packIds.add(mob.getUUID());
            }
            return true;
        }

        @Override
        public void onDrop(Mob mob) {
            packIds.remove(mob.getUUID());
            despawnTimers.remove(mob.getUUID());
        }
    };

    /**
     * This level's presence; empty while Satchel or the level's scope isn't ready ("standby, don't
     * crash"). Server thread.
     */
    static Optional<FeralPresence> of(Level level) {
        if (!Satchel.isReady()) {
            return Optional.empty();
        }
        return Satchel.trinket(HostJigs.LEVEL_HOST, new LevelScope(level), FeralPresence.class);
    }

    /** A mob that carries the Feral record has joined the level, fresh or loaded from disk. */
    void join(Mob mob, boolean fresh) {
        present.join(mob, fresh);
    }

    /** The Feral mobs in the level now. A live view; do not change presence while iterating. */
    Collection<Mob> mobs() {
        return present.mobs();
    }

    int count() {
        return present.count();
    }

    int packCount() {
        return packIds.size();
    }

    boolean isPack(UUID uuid) {
        return packIds.contains(uuid);
    }

    Map<UUID, TickDelay> despawnTimers() {
        return despawnTimers;
    }

    TickThrottler diagnosticsThrottler(ServerLevel level) {
        if (diagnostics == null) {
            diagnostics = new TickThrottler(20, level::getGameTime);
        }
        return diagnostics;
    }

    @Override
    public void onAttach(ScopeInfo info) {
        OUT.debug("[Feral] FeralPresence attached to " + info.debugName());
    }

    @Override
    public void onTickAfter(ScopeInfo info) {
        if (!(info.level() instanceof ServerLevel level)) {
            return;
        }
        if (!swept) {
            swept = true;
            joinFeralAlreadyInLevel(level);
        }
        present.tick(level, hooks);

        FeralCleanup.logPackDiagnostics(level, this);

        if (reentrySweep == null) {
            reentrySweep = new TickThrottler(FeralRules.reentrySweepIntervalTicks(), level::getGameTime);
        }
        if (reentrySweep.allow()) {
            FeralCleanup.sweep(level, this);
        }
    }

    /**
     * The one catch-up scan, run on the first tick and never repeated (feral.md § Presence): a Feral mob in
     * a chunk that loaded before this level's scope was ready joined with no presence to take it, so this
     * finds the ones that carry the record and queues them as loaded from disk. Every later join reaches
     * presence through {@link HostileJoining}.
     */
    private void joinFeralAlreadyInLevel(ServerLevel level) {
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Mob mob && FeralData.has(mob)) {
                present.join(mob, false);
            }
        }
    }

    @Override
    public String describe() {
        return "Feral presence: " + present.count() + " present (" + packIds.size() + " in the pack)";
    }
}
