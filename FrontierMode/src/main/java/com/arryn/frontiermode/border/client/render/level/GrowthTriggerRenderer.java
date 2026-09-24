package com.arryn.frontiermode.border.client.render.level;

import com.arryn.satchel.common.jig.level.LevelScope;

/**
 * Public seam into this package's client-render-only state.
 * <p>
 * This class used to emit a ring of white END_ROD particles around an approximated boss position
 * (FRO_094) -- a legacy tell that no longer served a purpose and read as erroneous in playtest
 * (FRO_159, project owner: eliminate it). That emission is gone; what's left is only the eviction
 * seam below, kept here because {@link RenderContext} is package-private by design and
 * {@code Rendering} lives in a different package. The name is a leftover -- a rename is a cleanup
 * candidate.
 */
public final class GrowthTriggerRenderer {

    private GrowthTriggerRenderer() {
    }

    /**
     * RM_FRO_012: entry point for {@code Rendering.onClientUnload} (subscribed to
     * {@code ScopeEvent.Unloaded} via {@code BorderModule}'s {@code EventHandlers}) to evict this
     * level's {@link RenderContext}.
     */
    public static void onUnload(LevelScope scope) {
        RenderContext.evict(scope);
    }
}
