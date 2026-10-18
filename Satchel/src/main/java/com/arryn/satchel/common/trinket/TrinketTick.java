package com.arryn.satchel.common.trinket;

import com.arryn.satchel.common.jig.guts.ScopeCoupler;
import com.arryn.satchel.common.jig.guts.ScopeInfo;

import java.util.Comparator;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One foundation's global list of {@link Ticking} Trinkets, kept in a total order and run by the
 * pulse in two of its three steps: every {@code onTickBefore}, then the internal jig walk, then every
 * {@code onTickAfter}.
 *
 * <p>
 * Every ticking Trinket of every ready scope is an {@link Entry}, sorted by its {@link Key}. A tick
 * takes one {@link Snapshot} at its start and both steps walk it, so a Trinket attached or detached
 * during the tick changes the next snapshot, not this one. Each entry has a live flag checked before
 * the call, since an earlier Trinket can unload a later one's scope. A call goes through the owning
 * scope's coupler, which applies the scope's readiness gate, the jig's tick policy and the Trinket's
 * own {@link Trinket#isReady}.
 *
 * <p>
 * Thread-safe, though a foundation's adds, removals and ticks all run on its own side's thread.
 */
public final class TrinketTick {

    /**
     * The total order of the list. Compared field by field, top to bottom; no field depends on the
     * iteration order of a map.
     *
     * @param order                the Trinket's {@code order}, lower first
     * @param jigIndex             the owning jig's registration index
     * @param scopeSequence        the owning scope's creation sequence
     * @param declIndex            the Trinket's declaration index in its jig
     * @param subscriptionSequence for a dynamic subscriber, its subscription sequence on its host;
     *                             0 for a declared Trinket
     */
    public record Key(int order, int jigIndex, long scopeSequence, int declIndex, long subscriptionSequence)
            implements Comparable<Key> {

        @Override
        public int compareTo(Key o) {
            int c = Integer.compare(order, o.order);
            if (c != 0) return c;
            c = Integer.compare(jigIndex, o.jigIndex);
            if (c != 0) return c;
            c = Long.compare(scopeSequence, o.scopeSequence);
            if (c != 0) return c;
            c = Integer.compare(declIndex, o.declIndex);
            if (c != 0) return c;
            return Long.compare(subscriptionSequence, o.subscriptionSequence);
        }
    }

    /**
     * One ticking Trinket in the list.
     */
    public static final class Entry {

        private final TrinketTick owner;
        private final ScopeInfo info;
        private final Ticking trinket;
        private final Key key;
        private volatile boolean live = true;

        private Entry(TrinketTick owner, ScopeInfo info, Ticking trinket, Key key) {
            this.owner = owner;
            this.info = info;
            this.trinket = trinket;
            this.key = key;
        }

        public ScopeInfo info() {
            return info;
        }

        public Ticking trinket() {
            return trinket;
        }

        public Key key() {
            return key;
        }

        /**
         * False once the entry is retired: its scope unloaded, or its subscriber was removed.
         */
        public boolean isLive() {
            return live;
        }

        /**
         * Takes this entry out of the list. A running tick that already holds it skips it from its
         * next call; a later tick never sees it.
         */
        public void retire() {
            owner.remove(this);
        }
    }

    /**
     * The list as one tick sees it: fixed when the tick began.
     */
    public static final class Snapshot {

        private final long tickNumber;
        private final Entry[] entries;

        private Snapshot(long tickNumber, Entry[] entries) {
            this.tickNumber = tickNumber;
            this.entries = entries;
        }

        /**
         * This tick's number: 1 for the foundation's first, then counting up.
         */
        public long tickNumber() {
            return tickNumber;
        }

        public int size() {
            return entries.length;
        }

        /**
         * The tick's first step: every live entry's {@code onTickBefore}, in order.
         */
        public void runBefore() {
            for (Entry e : entries) {
                if (!e.live) continue;
                coupler(e).tickTrinketBefore(e.info, e.trinket);
            }
        }

        /**
         * The tick's last step: every live entry's {@code onTickAfter}, in order.
         */
        public void runAfter() {
            for (Entry e : entries) {
                if (!e.live) continue;
                coupler(e).tickTrinketAfter(e.info, e.trinket);
            }
        }

        private static ScopeCoupler coupler(Entry e) {
            return e.info.jigInfo().coupler();
        }
    }

    private static final Comparator<Entry> BY_KEY = Comparator.comparing(Entry::key);

    private final TreeSet<Entry> sorted = new TreeSet<>(BY_KEY);
    private Entry[] cached = new Entry[0];
    private boolean dirty;
    private long tickNumber;

    private final AtomicLong subscriptionSequence = new AtomicLong();

    /**
     * Adds a ticking Trinket of a ready scope. It first runs in the next tick.
     *
     * @param subscriptionSequence for a dynamic subscriber, a value from {@link
     *                             #nextSubscriptionSequence()}; 0 for a declared Trinket
     */
    public synchronized Entry add(
            ScopeInfo info,
            Ticking trinket,
            int order,
            int declIndex,
            long subscriptionSequence
    ) {
        Objects.requireNonNull(info, "info");
        Objects.requireNonNull(trinket, "trinket");

        Key key = new Key(
                order,
                info.jigInfo().registrationIndex(),
                info.creationSequence(),
                declIndex,
                subscriptionSequence);
        Entry entry = new Entry(this, info, trinket, key);
        if (!sorted.add(entry)) {
            throw new IllegalStateException(
                    "Two Trinkets share the tick-order key " + key + " on " + info.debugName());
        }
        dirty = true;
        return entry;
    }

    private synchronized void remove(Entry entry) {
        entry.live = false;
        if (sorted.remove(entry)) dirty = true;
    }

    /**
     * Starts a tick: counts it and fixes the list it will walk.
     */
    public synchronized Snapshot begin() {
        tickNumber++;
        if (dirty) {
            cached = sorted.toArray(new Entry[0]);
            dirty = false;
        }
        return new Snapshot(tickNumber, cached);
    }

    /**
     * The number of the tick most recently begun; 0 before the first.
     */
    public synchronized long tickNumber() {
        return tickNumber;
    }

    /**
     * The next value for a dynamic subscriber's subscription sequence. Unique within the foundation
     * and increasing, so subscribers of one host are ordered by when they subscribed.
     */
    public long nextSubscriptionSequence() {
        return subscriptionSequence.incrementAndGet();
    }
}
