package com.arryn.satchel.common.jig.mob;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The mob UUIDs one mob jig holds interest in: an entry for each mob a module cares about, whether
 * or not the mob is in the level, each with the level it was last seen in as a hint for the sweep.
 *
 * <p>
 * A concurrent key set, so changing the list while the sweep or a dispatch is under way is safe:
 * {@link #remove} takes effect at once, and the sweep walks a {@link #snapshot()}, so an entry
 * removed after the snapshot is still visited but then no longer interesting to the caller that
 * checks {@link #contains}. Pure Java (the level type is a parameter), so it is testable without a
 * Minecraft bootstrap.
 *
 * @param <L> the type of the level hint
 */
public final class InterestList<L> {

    private static final class Entry<L> {
        volatile L lastLevel;
    }

    private final ConcurrentHashMap<UUID, Entry<L>> entries = new ConcurrentHashMap<>();

    /**
     * @return true if {@code uuid} was not already listed
     */
    public boolean add(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        return entries.putIfAbsent(uuid, new Entry<>()) == null;
    }

    /**
     * Stops caring: idempotent, immediate, safe from inside a handler.
     *
     * @return true if {@code uuid} was listed
     */
    public boolean remove(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        return entries.remove(uuid) != null;
    }

    public boolean contains(UUID uuid) {
        return uuid != null && entries.containsKey(uuid);
    }

    public int size() {
        return entries.size();
    }

    /**
     * Whether adding {@code uuid} would take the list past {@code cap}: it is not listed already and
     * the list is full.
     */
    public boolean wouldExceed(UUID uuid, int cap) {
        return !contains(uuid) && entries.size() >= cap;
    }

    /**
     * A stable copy of the listed UUIDs, for a pass that may change the list as it goes.
     */
    public List<UUID> snapshot() {
        return new ArrayList<>(entries.keySet());
    }

    /**
     * Up to {@code limit} listed UUIDs, for a message that names a few.
     */
    public List<UUID> sample(int limit) {
        List<UUID> out = new ArrayList<>(Math.min(limit, entries.size()));
        for (UUID uuid : entries.keySet()) {
            if (out.size() >= limit) break;
            out.add(uuid);
        }
        return out;
    }

    /**
     * The level {@code uuid} was last seen in, or null if it never was or is not listed.
     */
    public L hint(UUID uuid) {
        Entry<L> entry = entries.get(uuid);
        return entry == null ? null : entry.lastLevel;
    }

    /**
     * Records where {@code uuid} was last seen; ignored if it is no longer listed.
     */
    public void setHint(UUID uuid, L level) {
        Entry<L> entry = entries.get(uuid);
        if (entry != null) entry.lastLevel = level;
    }

    public void clear() {
        entries.clear();
    }
}
