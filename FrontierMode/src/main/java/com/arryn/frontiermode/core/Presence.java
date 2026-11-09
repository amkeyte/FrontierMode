package com.arryn.frontiermode.core;

import com.arryn.satchel.Satchel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The mobs of one kind that are in a level now: the shared presence helper two or more modules use
 * (core.md § Residents). It holds mobs and knows nothing of what they are; what adoption and removal
 * mean is the caller's, through {@link Hooks}.
 *
 * <ul>
 *   <li>{@link #join} queues a mob that has joined the level. Forge posts the join before the entity is
 *   in the level lookup and rejects a duplicate UUID after it, so a join is only a claim that the mob is
 *   about to be in the level.</li>
 *   <li>{@link #tick} drains the queue. For each queued mob it asks Satchel's egress for the entity by
 *   UUID and requires <em>the same object</em>: a cancelled or rejected join fails that and is dropped.
 *   It then asks {@link Hooks#adopt}, and a refusal leaves the mob out. It also drops every present mob
 *   whose {@code isRemoved()} is true (a death, a despawn, a chunk unload) and calls {@link Hooks#onDrop}.
 *   A mob that merely stops resolving is not dropped.</li>
 * </ul>
 *
 * <p>An instance is a field of its caller's per-level holder and ends with it; nothing here is static.
 * Server thread only.
 */
public final class Presence {

    /** What a caller does when a mob enters presence and when it leaves. */
    public interface Hooks {

        /**
         * A queued mob that passed the same-object check. {@code fresh} is true for a mob that spawned
         * just now, false for one loaded from disk. Return true to adopt it, false to leave it out.
         */
        boolean adopt(Mob mob, boolean fresh);

        /** A present mob reported {@code isRemoved()} and has left presence. */
        void onDrop(Mob mob);
    }

    private record Joined(Mob mob, boolean fresh) {
    }

    private final Map<UUID, Mob> present = new LinkedHashMap<>();
    private final List<Joined> queue = new ArrayList<>();

    /** Queues {@code mob}, which has just joined the level. */
    public void join(Mob mob, boolean fresh) {
        queue.add(new Joined(mob, fresh));
    }

    /** Adopts the queued mobs, then drops the ones that were removed. */
    public void tick(Level level, Hooks hooks) {
        if (!queue.isEmpty()) {
            List<Joined> joined = List.copyOf(queue);
            queue.clear();
            for (Joined join : joined) {
                adoptQueued(level, hooks, join);
            }
        }
        dropRemoved(hooks);
    }

    private void adoptQueued(Level level, Hooks hooks, Joined join) {
        Mob mob = join.mob();
        UUID uuid = mob.getUUID();

        Optional<Entity> resolved = Satchel.require().egress().getEntity(level, uuid);
        if (resolved.isEmpty() || resolved.get() != mob) {
            return;
        }

        Mob known = present.get(uuid);
        if (known == mob) {
            return;
        }
        if (known != null) {
            // A different object under the same UUID: the old one left, and this one replaces it.
            present.remove(uuid);
            hooks.onDrop(known);
        }

        if (hooks.adopt(mob, join.fresh())) {
            present.put(uuid, mob);
        }
    }

    private void dropRemoved(Hooks hooks) {
        Iterator<Mob> it = present.values().iterator();
        while (it.hasNext()) {
            Mob mob = it.next();
            if (mob.isRemoved()) {
                it.remove();
                hooks.onDrop(mob);
            }
        }
    }

    /** The mobs present now, in the order they were adopted. A live view; do not change presence while iterating. */
    public Collection<Mob> mobs() {
        return Collections.unmodifiableCollection(present.values());
    }

    public Optional<Mob> get(UUID uuid) {
        return Optional.ofNullable(present.get(uuid));
    }

    public boolean contains(UUID uuid) {
        return present.containsKey(uuid);
    }

    /** How many mobs are present. */
    public int count() {
        return present.size();
    }

    /** How many joins are queued and not yet adopted or refused. */
    public int queued() {
        return queue.size();
    }
}
