package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.boss.common.BossMobTypes;
import com.arryn.frontiermode.boss.common.fixture.BossPublicFixture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * FRO_145/FRO_148: side-agnostic "is this entity a boss, and how big" lookup over
 * {@link BossPublicFixture} -- the one place the eye render layer, the hitbox {@code Size} handler,
 * and the render-scale handler all ask. No client classes here, so it loads on a dedicated server.
 *
 * <p>Bosses are only ever one of {@link BossMobTypes#ELIGIBLE}, so anything else is rejected before any
 * fixture lookup -- these are called from hot paths (every entity's dimension refresh, every
 * rendered frame).
 */
public final class BossMarkers {

    private static final ClientFixtureCache<BossPublicFixture> CLIENT_FIXTURE =
            new ClientFixtureCache<>(BossAPI::publicMarkers);

    private BossMarkers() {
    }

    // level() can be null for an entity built off-world (dimension queries, registry probes) -- the
    // Size event fires from the Entity constructor, so it has to tolerate that.
    public static boolean isEligible(Entity entity) {
        return BossMobTypes.isEligible(entity.getType()) && entity.level() != null;
    }

    /** True if {@code entity} is a currently-tracked, alive boss. */
    public static boolean isLiveBoss(Entity entity) {
        return isEligible(entity)
                && fixture(entity.level()).map(f -> f.isLiveBoss(entity.getUUID())).orElse(false);
    }

    /** The boss's size multiplier, or {@code 1.0} for anything that isn't a tracked boss. */
    public static float scaleOf(Entity entity) {
        if (!isEligible(entity)) {
            return 1.0F;
        }
        return fixture(entity.level()).map(f -> f.scaleOf(entity.getUUID())).orElse(1.0F);
    }

    // The server side isn't cached -- it only asks on dimension refresh, not per frame.
    private static Optional<BossPublicFixture> fixture(Level level) {
        return level.isClientSide ? CLIENT_FIXTURE.get(level) : BossAPI.publicMarkers(level);
    }
}
