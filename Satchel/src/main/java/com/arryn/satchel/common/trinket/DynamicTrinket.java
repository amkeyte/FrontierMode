package com.arryn.satchel.common.trinket;

import com.arryn.satchel.common.jig.guts.SatchelException;
import com.arryn.satchel.common.jig.guts.ScopeInfo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A Satchel-provided Trinket that holds <b>subscribers</b>: ordinary {@link Trinket} instances a
 * module adds to one scope while it runs. A jig declares it once, like any Trinket. It is the
 * sanctioned home for per-instance variation: the declared set of a jig stays fixed, and a kind of
 * tracked mob subscribes the behaviors it wants.
 *
 * <ul>
 *   <li><b>Fan-out.</b> The host forwards {@code onAttach} and {@code onDetach} to its subscribers
 *   in subscription order. A subscriber that implements {@link Ticking} is not ticked by the host: it
 *   is its own entry in the foundation's global, sorted list ({@link TrinketTick}), with the {@code
 *   order} it subscribed with, so it runs where that order puts it.</li>
 *   <li><b>Subscribing to a loaded scope</b> attaches the subscriber at once. A subscriber added in
 *   the middle of a tick first runs in the next one.</li>
 *   <li><b>Changes apply between callbacks.</b> A subscribe or unsubscribe made from inside a
 *   subscriber's own {@code onAttach} or {@code onDetach} is queued and applied when the host's
 *   current fan-out finishes, never while the host iterates.</li>
 *   <li><b>A subscriber lives with the scope.</b> The host detaches its subscribers with the other
 *   Trinkets at unload, before the bundles unload. Subscribers are never saved: a module that needs
 *   one back after a restart keeps the data in a fixture and subscribes again when its scope
 *   loads.</li>
 *   <li><b>Checked at subscribe, not at boot.</b> A {@code Ticking} subscriber on a jig that doesn't
 *   tick throws, naming the jig and the subscriber.</li>
 * </ul>
 *
 * <p>
 * Looked up through the host: {@code Satchel.trinket(jigKey, scope, DynamicTrinket.class)} returns
 * it, and {@link #subscribers(Class)} finds a scope's subscribers of a type.
 *
 * <p>
 * Used from the thread of the scope's own side, like every Trinket. <b>Unstable:</b> see {@link
 * Trinket}.
 */
public final class DynamicTrinket implements Trinket {

    private enum Phase {NEW, ATTACHED, DETACHING, DETACHED}

    private enum State {PENDING, ATTACHED, DETACHED}

    private static final class Subscriber {

        final Trinket trinket;
        final int order;
        State state = State.PENDING;
        TrinketTick.Entry entry;

        Subscriber(Trinket trinket, int order) {
            this.trinket = trinket;
            this.order = order;
        }
    }

    /** In subscription order, including a subscriber whose attach is queued. */
    private final List<Subscriber> subscribers = new ArrayList<>();
    private final ArrayDeque<Runnable> queued = new ArrayDeque<>();

    private Phase phase = Phase.NEW;
    private ScopeInfo info;
    private int declIndex = -1;

    /** How many callbacks into subscribers are in progress; changes made while above zero queue. */
    private int busy;
    private boolean draining;

    // =================================================================
    // Module API
    // =================================================================

    /**
     * Subscribes {@code trinket} at {@link Order#NORMAL}.
     */
    public void subscribe(Trinket trinket) {
        subscribe(trinket, Order.NORMAL);
    }

    /**
     * Adds a subscriber. On a loaded scope it gets {@code onAttach} at once, unless this is called
     * from inside another subscriber's callback, in which case it gets it as soon as that fan-out
     * finishes. {@code order} places a {@link Ticking} subscriber in the global tick list; see {@link
     * Order}.
     *
     * @throws SatchelException.Generic if {@code trinket} is {@code Ticking} and the jig doesn't tick
     * @throws IllegalStateException    if the scope is unloading, or {@code trinket} is already
     *                                  subscribed or is this host
     */
    public void subscribe(Trinket trinket, int order) {
        Objects.requireNonNull(trinket, "trinket");
        if (trinket == this) {
            throw new IllegalStateException("A DynamicTrinket cannot subscribe itself");
        }
        if (phase == Phase.DETACHING || phase == Phase.DETACHED) {
            throw new IllegalStateException(
                    "Cannot subscribe " + trinket.getClass().getName() + ": the scope is unloading");
        }
        if (find(trinket) != null) {
            throw new IllegalStateException(
                    "Already subscribed: " + trinket.getClass().getName());
        }

        Subscriber s = new Subscriber(trinket, order);

        if (phase == Phase.NEW) {
            // Not attached yet (a sibling subscribing from its own onAttach): the host attaches it,
            // in this order, when it attaches itself.
            subscribers.add(s);
            return;
        }

        checkTicking(trinket);
        subscribers.add(s);
        if (busy > 0) {
            queued.add(() -> attach(s));
        } else {
            attach(s);
        }
    }

    /**
     * Removes a subscriber, with {@code onDetach} if it had been attached. From inside another
     * subscriber's callback the removal is queued like a subscribe.
     *
     * @return false if {@code trinket} wasn't subscribed
     */
    public boolean unsubscribe(Trinket trinket) {
        Objects.requireNonNull(trinket, "trinket");
        if (phase == Phase.DETACHING || phase == Phase.DETACHED) return false;

        Subscriber s = find(trinket);
        if (s == null) return false;

        if (phase == Phase.NEW) {
            subscribers.remove(s);
            s.state = State.DETACHED;
        } else if (busy > 0) {
            queued.add(() -> detach(s));
        } else {
            detach(s);
        }
        return true;
    }

    /**
     * This scope's attached subscribers that are instances of {@code type}, in subscription order,
     * for a module's own resolver. A subscriber whose {@link Trinket#isReady} is false is left out,
     * the same gate every Trinket lookup applies.
     */
    public <T extends Trinket> List<T> subscribers(Class<T> type) {
        Objects.requireNonNull(type, "type");
        List<T> out = new ArrayList<>();
        for (Subscriber s : subscribers) {
            if (s.state == State.ATTACHED && type.isInstance(s.trinket) && s.trinket.isReady(info)) {
                out.add(type.cast(s.trinket));
            }
        }
        return out;
    }

    // =================================================================
    // Satchel's side: the scope's lifecycle
    // =================================================================

    @Override
    public void onAttach(ScopeInfo info) {
        this.info = info;
        this.phase = Phase.ATTACHED;
        this.declIndex = declarationIndex(info);

        fanOut(() -> {
            for (Subscriber s : new ArrayList<>(subscribers)) {
                checkTicking(s.trinket);
                attach(s);
            }
        });
    }

    @Override
    public void onDetach(ScopeInfo info) {
        phase = Phase.DETACHING;
        queued.clear();

        busy++;
        try {
            for (Subscriber s : new ArrayList<>(subscribers)) detach(s);
        } finally {
            busy--;
            subscribers.clear();
            phase = Phase.DETACHED;
        }
    }

    /**
     * Takes every ticking subscriber out of the global list, so a tick under way skips them. Called
     * by the scope's set when the scope starts to unload, ahead of {@link #onDetach}.
     */
    void retireSubscribers() {
        for (Subscriber s : subscribers) {
            if (s.entry != null) s.entry.retire();
        }
    }

    @Override
    public String describe() {
        if (subscribers.isEmpty()) return "subscribers=0";
        return "subscribers=" + subscribers.stream()
                .map(s -> s.trinket.getClass().getSimpleName()
                        + (s.trinket instanceof Ticking ? " (order " + s.order + ")" : "")
                        + (s.state == State.PENDING ? " pending" : ""))
                .collect(Collectors.joining(", ", "[", "]"));
    }

    // =================================================================
    // Machinery
    // =================================================================

    private Subscriber find(Trinket trinket) {
        for (Subscriber s : subscribers) {
            if (s.trinket == trinket) return s;
        }
        return null;
    }

    private void checkTicking(Trinket trinket) {
        if (trinket instanceof Ticking && !info.execution().lifecycle().participatesInTick()) {
            throw new SatchelException.Generic(
                    "Jig " + info.jigInfo().key.name + " does not tick, so it cannot host the Ticking "
                            + "subscriber " + trinket.getClass().getName()
                            + " -- set Lifecycle.withTick(true)");
        }
    }

    /**
     * Where this host sits in its jig's declarations: the third key of a subscriber's place in the
     * global list, after its order and its scope.
     */
    private int declarationIndex(ScopeInfo info) {
        for (TrinketSet.Slot slot : info.jigInfo().coupler().trinkets(info)) {
            if (slot.trinket() == this) return slot.declIndex();
        }
        throw new IllegalStateException("A DynamicTrinket attached to a scope that doesn't hold it: "
                + info.debugName());
    }

    private void attach(Subscriber s) {
        if (s.state != State.PENDING) return;
        s.state = State.ATTACHED;

        fanOut(() -> s.trinket.onAttach(info));

        // Joins the global list once attached, like a declared Trinket; first runs next tick. A
        // subscriber unsubscribed from inside its own onAttach is already detached by now.
        if (s.state == State.ATTACHED && s.trinket instanceof Ticking ticking) {
            TrinketTick tick = info.jigInfo().trinketTick();
            s.entry = tick.add(info, ticking, s.order, declIndex, tick.nextSubscriptionSequence());
        }
    }

    private void detach(Subscriber s) {
        if (s.state == State.DETACHED) return;
        State was = s.state;
        s.state = State.DETACHED;
        subscribers.remove(s);

        if (s.entry != null) s.entry.retire();
        if (was == State.ATTACHED) {
            fanOut(() -> s.trinket.onDetach(info));
        }
    }

    /**
     * Runs a callback into subscribers: anything they subscribe or unsubscribe meanwhile queues, and
     * is applied once the outermost fan-out has finished.
     */
    private void fanOut(Runnable callback) {
        busy++;
        try {
            callback.run();
        } finally {
            busy--;
        }
        if (busy == 0) drain();
    }

    private void drain() {
        if (draining) return;
        draining = true;
        try {
            Runnable next;
            while ((next = queued.poll()) != null) next.run();
        } finally {
            draining = false;
        }
    }
}
