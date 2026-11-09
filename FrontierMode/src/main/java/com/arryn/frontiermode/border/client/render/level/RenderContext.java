package com.arryn.frontiermode.border.client.render.level;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.common.fixture.BordersCrudFacet;
import com.arryn.frontiermode.border.common.fixture.BordersInfoFacet;
import com.arryn.frontiermode.border.common.fixture.BordersPathFacet;
import com.arryn.frontiermode.border.common.fixture.BordersRevisionMonitor;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.trinket.Trinket;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * The client's draw cache for one level (wiki/frontiermode/architecture/border.md § Trinket ticks): the cached
 * borders, the path tip and the revision monitor, as instance state of a Trinket on {@code BORDERS_JIG}, so the
 * state ends with the client's level scope and needs no static map or unload handler. Not {@code Ticking}: the
 * ring is drawn from Forge's render event, which polls the revision.
 *
 * <p>{@code BORDERS_JIG} is {@code BOTH}, so a dedicated server constructs one too, holding nothing and never
 * asked (the lookup answers empty off the client). It therefore names no type that exists on the client only: not
 * in a field, a signature or a body. The camera, the buffer source and the {@code Minecraft} read belong to
 * {@link WorldBordersRenderer}, which asks {@code Minecraft} itself and hands {@link #getInstance} the level. The
 * class carries no {@code @OnlyIn}, which would crash the dedicated server at first load. It is public only so
 * {@code BorderJig} can declare it; everything it offers is package-private.
 */
public final class RenderContext implements Trinket {

    private LevelScope scope;
    private final BordersRevisionMonitor revisionMonitor = new BordersRevisionMonitor(20);
    private List<Border> cachedBorders = List.of();
    private Optional<Border> cachedPathTip = Optional.empty();

    /**
     * Creates this scope's {@code BordersBundle} at attach, the pattern {@code ScopeInfo.getOrCreate} documents for
     * a Trinket that needs its bundle then. A client bundle exists only once something creates it, and {@link
     * #isReady} waits for its first parcel, so without this call nothing would ask for the bundle and the ring would
     * never draw ({@code BossRoster} had exactly this fault, FRO_295 finding 2).
     */
    @Override
    public void onAttach(ScopeInfo info) {
        OUT.debug("[Border] RenderContext attached");
        scope = info.scopeAs();
        info.getOrCreate(FrontierKeys.BORDERS_BUNDLE);
    }

    /** Ready once the bundle's first parcel has arrived and the borders fixture is. */
    @Override
    public boolean isReady(ScopeInfo info) {
        LevelScope levelScope = info.scopeAs();
        return BorderAPI.INFO(levelScope.level())
                .map(BordersInfoFacet::isReady)
                .orElse(false);
    }

    /**
     * The draw cache of {@code level}, if this is the client and the level's scope and bundle are ready. Satchel
     * binds a thread to a side only once it has booted, so {@code isReady()} comes before {@code isClient()}, and
     * the scope is built only once the world-identity token is bound.
     */
    static Optional<RenderContext> getInstance(Level level) {
        if (!Satchel.isReady() || !Satchel.isClient()) {
            return Optional.empty();
        }
        return Satchel.trinket(FrontierKeys.BORDERS_JIG, new LevelScope(level), RenderContext.class);
    }

    // FRO_047: three facets instead of one raw BordersFixture -- BordersFixture is no longer
    // reachable from outside border.common.fixture (see that class's own doc for why the Java
    // modifier itself can't follow). Each lazy-loads and memoizes independently: retry
    // while empty, keep once resolved.
    private Optional<BordersCrudFacet> crud = Optional.empty();
    private Optional<BordersPathFacet> path = Optional.empty();
    private Optional<BordersInfoFacet> info = Optional.empty();

    private Optional<BordersCrudFacet> crud() {
        if (crud.isEmpty()) {
            crud = BorderAPI.CRUD(scope.level());
        }
        return crud;
    }

    private Optional<BordersPathFacet> path() {
        if (path.isEmpty()) {
            path = BorderAPI.PATH(scope.level());
        }
        return path;
    }

    private Optional<BordersInfoFacet> info() {
        if (info.isEmpty()) {
            info = BorderAPI.INFO(scope.level());
        }
        return info;
    }

    // ---------------------------------------------------------------------
    // Accessors (defensive)
    // ---------------------------------------------------------------------

    List<Border> borders() {
        cachedBorders = crud()
                .map(BordersCrudFacet::all)
                .orElseGet(List::of);
        return cachedBorders;
    }

    Optional<Border> pathTip() {
        cachedPathTip = path()
                .flatMap(BordersPathFacet::tip);

        return cachedPathTip;
    }

    // ---------------------------------------------------------------------
    // Render gating / cache reconciliation
    // ---------------------------------------------------------------------

    /**
     * @return true if rendering should no-op this frame
     */
    @SuppressWarnings({"inverted", "BooleanMethodIsAlwaysInverted"})
    boolean standby() {

        Optional<BordersInfoFacet> infoOpt = info();

        // No fixture yet → standby
        if (infoOpt.isEmpty()) {
            return true;
        }

        BordersInfoFacet infoFacet = infoOpt.get();

        // Fixture exists but not ready → standby
        if (!infoFacet.isReady()) {
            return true;
        }

        // Refresh if:
        // 1) revision poll
        // 2) cold start after hydration
        if (revisionMonitor.poll(infoFacet.level()) || cachedBorders.isEmpty()) {
            cachedBorders = crud().map(BordersCrudFacet::all).orElseGet(List::of);
            cachedPathTip = path().flatMap(BordersPathFacet::tip);
        }

        // Still nothing to draw
        return cachedBorders.isEmpty();
    }
}
