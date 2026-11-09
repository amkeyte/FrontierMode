package com.arryn.satchel.common.trinket;

import com.arryn.satchel.common.jig.guts.LeaveReason;
import com.arryn.satchel.common.jig.guts.ScopeInfoImpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Trinkets one coupler holds for one scope: one instance per declared type, in declaration
 * order. Built and driven by {@code AScopeCoupler}; module code never sees one.
 *
 * <p>
 * The set attaches and detaches its Trinkets and registers the {@link Ticking} ones with the
 * foundation's {@link TrinketTick}, which runs them. Nothing here catches: a Trinket that throws
 * propagates to the caller, by design.
 */
public final class TrinketSet {

    /**
     * One held Trinket with what its declaration gave it.
     *
     * @param declIndex the position of its declaration among those that apply on this side
     * @param owner     the module that contributed it to a host jig, or null for a jig's own
     *                  declaration
     */
    public record Slot(Class<? extends Trinket> type, Trinket trinket, int order, int declIndex, String owner) {
    }

    private final Map<Class<? extends Trinket>, Slot> slots = new LinkedHashMap<>();
    private final List<TrinketTick.Entry> entries = new ArrayList<>();

    /**
     * Constructs every Trinket in {@code decls}, attaching none yet. The caller passes the
     * declarations that apply on its side, in declaration order.
     */
    public static TrinketSet construct(List<TrinketDecl<?>> decls) {
        TrinketSet set = new TrinketSet();
        int declIndex = 0;
        for (TrinketDecl<?> decl : decls) {
            Trinket t = decl.factory().get();
            if (t == null) {
                throw new IllegalStateException(
                        "Trinket factory for " + decl.type().getName() + " returned null");
            }
            if (!decl.type().isInstance(t)) {
                throw new IllegalStateException(
                        "Trinket factory for " + decl.type().getName() + " returned a "
                                + t.getClass().getName());
            }
            set.slots.put(decl.type(), new Slot(decl.type(), t, decl.order(), declIndex++, decl.owner()));
        }
        return set;
    }

    public void attachAll(ScopeInfoImpl info) {
        for (Slot s : slots.values()) s.trinket().onAttach(info);
    }

    public void detachAll(ScopeInfoImpl info) {
        for (Slot s : slots.values()) s.trinket().onDetach(info);
    }

    /**
     * Tells each {@link Leaving} Trinket why the scope is unloading, in declaration order, ahead of
     * {@link #detachAll}.
     */
    public void leaveAll(ScopeInfoImpl info, LeaveReason reason) {
        for (Slot s : slots.values()) {
            if (s.trinket() instanceof Leaving leaving) leaving.onLeave(info, reason);
        }
    }

    /**
     * Puts every {@link Ticking} Trinket in the foundation's global list. They first run in the next
     * tick, since a tick walks a snapshot taken before this call.
     */
    public void registerTicking(ScopeInfoImpl info, TrinketTick tick) {
        for (Slot s : slots.values()) {
            if (s.trinket() instanceof Ticking ticking) {
                entries.add(tick.add(info, ticking, s.order(), s.declIndex(), 0L));
            }
        }
    }

    /**
     * Takes this set's Trinkets, and the ticking subscribers of a {@link DynamicTrinket} it holds,
     * out of the global list. A tick already running skips them from its next call, and never runs
     * one after this returns.
     */
    public void retireTicking() {
        for (TrinketTick.Entry e : entries) e.retire();
        entries.clear();
        for (Slot s : slots.values()) {
            if (s.trinket() instanceof DynamicTrinket host) host.retireSubscribers();
        }
    }

    public <T extends Trinket> Optional<T> get(Class<T> type) {
        return Optional.ofNullable(slots.get(type)).map(s -> type.cast(s.trinket()));
    }

    public List<Trinket> all() {
        List<Trinket> out = new ArrayList<>(slots.size());
        for (Slot s : slots.values()) out.add(s.trinket());
        return Collections.unmodifiableList(out);
    }

    public List<Slot> slots() {
        return List.copyOf(slots.values());
    }

    /**
     * The ability interfaces a Trinket implements, for {@code /satchel trinkets}.
     */
    public static List<String> abilitiesOf(Trinket trinket) {
        List<String> out = new ArrayList<>();
        if (trinket instanceof Ticking) out.add("Ticking");
        if (trinket instanceof Leaving) out.add("Leaving");
        return out;
    }
}
