package com.arryn.satchel.common.stitch;

/**
 * A bundle-implementable capability marker that, if implemented and returning {@code true}, permits
 * {@link com.arryn.satchel.common.persistence.FixtureHydrator#hydrateAllowingCreate(FixtureKey, Supplier)} to
 * create a missing fixture during hydration rather than returning {@code null}/the existing one.
 *
 * <p>This interface currently has <strong>zero implementers</strong> in either the Satchel or
 * FrontierMode source trees. As a result, the {@code instanceof CreateAtHydrateStitch}
 * check in {@link com.arryn.satchel.common.persistence.FixtureHydrator#FixtureHydrator(SatchelBundle, FixtureHydrationSource)}
 * always evaluates {@code false} today — the create-permitting branch is wired but unreachable
 * in practice, not just unused by convention.
 *
 * <p>Additionally, {@link com.arryn.satchel.common.persistence.FixtureHydrator#hydrateAllowingCreate(FixtureKey, Supplier)}
 * itself is never called anywhere in either repo either — so the entire mechanism is doubly inert
 * right now (no implementer to make the flag true, and no caller of the method the flag would affect).
 *
 * <p>This interface is explicitly named in ticket {@code SAT_053} ("Redesign engine-level lifecycle
 * promotion / hydration coupling") as a flagged-for-removal housekeeping item alongside
 * {@link com.arryn.satchel.common.stitch.PersistStitch}, but has not yet been deleted.
 */
public interface CreateAtHydrateStitch {

    /**
     * @return true if persistence may create missing facets during hydrate
     */
    boolean allowCreateAtHydrate();
}
