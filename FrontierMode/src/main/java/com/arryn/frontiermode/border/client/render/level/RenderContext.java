package com.arryn.frontiermode.border.client.render.level;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.common.fixture.BordersFixture;
import com.arryn.frontiermode.border.common.fixture.BordersRevisionMonitor;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.level.LevelScope;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class RenderContext {

    // ---------------------------------------------------------------------
    // Cached instances (per level)
    // ---------------------------------------------------------------------

    private static final Map<LevelScope, RenderContext> CACHE = new HashMap<>();
    // ---------------------------------------------------------------------
    // Instance state
    // ---------------------------------------------------------------------
    public final LevelScope scope;
    private final BordersRevisionMonitor revisionMonitor;
    private List<Border> cachedBorders = List.of();
    private Optional<Border> cachedPathTip = Optional.empty();

    private RenderContext(LevelScope scope) {
        this.scope = scope;
        this.revisionMonitor = new BordersRevisionMonitor(20);
    }

    /**
     * Try to pull an instance of the renderContext if the context is
     * ready and client side.
     *
     * @return
     */
    public static Optional<RenderContext> getInstance() {
        // Gate: must be valid context and client-side
        if(isNotClientSide()) return Optional.empty();

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        //level cannot be null if client context is ready, but let's clear the warning.
        //oops somehow it is null during tick. I don't think tick should be dispatching
        //in satchel until the level has loaded and ungated the lifetime.
        // check hub and also see if there's something wierd going on with the frontier tick handler
        if (level == null) throw new IllegalStateException("Minecraft returned null level");
        LevelScope scope = new LevelScope(level);

        return Optional.of(CACHE.computeIfAbsent(scope, RenderContext::new));
    }

    //fixture has to lazy load because render tick will be called
    //before satchel is available
    private Optional<BordersFixture> fixture = Optional.empty();

    private Optional<BordersFixture> fixture() {
        if (fixture.isEmpty()) {
            fixture = BorderAPI.borders(scope);
        }
        return fixture;
    }


    // ---------------------------------------------------------------------
    // Accessors (defensive)
    // ---------------------------------------------------------------------

    public Camera camera() {
        if(isNotClientSide())
            throw  new IllegalStateException("No access from non client/render thread.");
        Minecraft mc = Minecraft.getInstance();
        return mc.gameRenderer.getMainCamera();
    }

    public MultiBufferSource.BufferSource buffers() {
        if(isNotClientSide())
            throw  new IllegalStateException("No access from non client/render thread.");

        Minecraft mc = Minecraft.getInstance();
        return mc.renderBuffers().bufferSource();
    }

    //
    public List<Border> borders() {
        cachedBorders = fixture()
                .map(b -> b.CRUD.all())
                .orElseGet(List::of);
        return cachedBorders;
    }

    public Optional<Border> pathTip() {
        cachedPathTip = fixture()
                .flatMap(b -> b.PATH.tip());

        return cachedPathTip;
    }

    // ---------------------------------------------------------------------
    // Render gating / cache reconciliation
    // ---------------------------------------------------------------------

    /**
     * @return true if rendering should no-op this frame
     */
    public boolean standby() {

        Optional<BordersFixture> opt = fixture();

        // No fixture yet → standby
        if (opt.isEmpty()) {
            return true;
        }

        BordersFixture f = opt.get();

        // Fixture exists but not ready → standby
        if (!f.isReady()) {
            return true;
        }

        // Refresh if:
        // 1) revision poll
        // 2) cold start after hydration
        if (revisionMonitor.poll(f.INFO.level()) || cachedBorders.isEmpty()) {
            cachedBorders = f.CRUD.all();
            cachedPathTip = f.PATH.tip();
        }

        // Still nothing to draw
        return cachedBorders.isEmpty();
    }


    static Boolean isNotClientSide() {
        return Satchel.foundation()
                .filter(f -> f.side().isServer())
                .isPresent();
    }
}
