package com.arryn.satchel.common.trinket;

import com.arryn.satchel.common.jig.guts.ScopeInfo;

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
 * Nothing here catches: a Trinket that throws propagates to the caller, by design.
 */
public final class TrinketSet {

    private final Map<Class<? extends Trinket>, Trinket> trinkets = new LinkedHashMap<>();
    private final List<Ticking> tickers = new ArrayList<>();

    /**
     * Constructs every declared Trinket, attaching none yet.
     */
    public static TrinketSet construct(List<TrinketDecl<?>> decls) {
        TrinketSet set = new TrinketSet();
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
            set.trinkets.put(decl.type(), t);
            if (t instanceof Ticking ticking) set.tickers.add(ticking);
        }
        return set;
    }

    public void attachAll(ScopeInfo info) {
        for (Trinket t : trinkets.values()) t.onAttach(info);
    }

    public void detachAll(ScopeInfo info) {
        for (Trinket t : trinkets.values()) t.onDetach(info);
    }

    public void tickBefore(ScopeInfo info) {
        for (Ticking t : tickers) t.onTickBefore(info);
    }

    public void tickAfter(ScopeInfo info) {
        for (Ticking t : tickers) t.onTickAfter(info);
    }

    public <T extends Trinket> Optional<T> get(Class<T> type) {
        return Optional.ofNullable(trinkets.get(type)).map(type::cast);
    }

    public List<Trinket> all() {
        return Collections.unmodifiableList(new ArrayList<>(trinkets.values()));
    }

    /**
     * The ability interfaces a Trinket implements, for {@code /satchel trinkets}.
     */
    public static List<String> abilitiesOf(Trinket trinket) {
        List<String> out = new ArrayList<>();
        if (trinket instanceof Ticking) out.add("Ticking");
        return out;
    }
}
