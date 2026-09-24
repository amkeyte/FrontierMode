package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.boss.common.fixture.BossPublicFixture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.Set;

/**
 * FRO_145/FRO_148: side-agnostic "is this entity a boss, and how big" lookup over
 * {@link BossPublicFixture} -- the one place the eye render layer, the hitbox {@code Size} handler,
 * and the render-scale handler all ask. No client classes here, so it loads on a dedicated server.
 *
 * <p>Bosses are only ever one of {@link #ELIGIBLE}, so anything else is rejected before any
 * fixture lookup -- these are called from hot paths (every entity's dimension refresh, every
 * rendered frame).
 */
public final class BossMarkers {

    /**
     * The boss-eligible mob types -- boss.md's layer table, Rabbit through Ravager. Mirrors
     * {@code DefaultBossRules}' own table and {@code BossClientEvents}' texture list: adding a
     * boss mob type means updating all three.
     */
    private static final Set<EntityType<?>> ELIGIBLE = Set.of(
            EntityType.RABBIT, EntityType.SPIDER, EntityType.ZOMBIE, EntityType.SKELETON,
            EntityType.ZOMBIFIED_PIGLIN, EntityType.PILLAGER, EntityType.VINDICATOR,
            EntityType.RAVAGER);

    // Client-side only: resolving the fixture goes through Satchel scope lookup, too much for every
    // zombie on every frame. Cached per level, and retried at most once a second while unresolved
    // (the normal state for the first moments after joining, before the first parcel lands). The
    // server side isn't cached -- it only asks on dimension refresh, not per frame.
    private static final long RETRY_TICKS = 20;
    private static Level cachedLevel;
    private static BossPublicFixture cachedFixture;
    private static long nextResolveTick;

    private BossMarkers() {
    }

    // level() can be null for an entity built off-world (dimension queries, registry probes) -- the
    // Size event fires from the Entity constructor, so it has to tolerate that.
    public static boolean isEligible(Entity entity) {
        return ELIGIBLE.contains(entity.getType()) && entity.level() != null;
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

    private static Optional<BossPublicFixture> fixture(Level level) {
        if (!level.isClientSide) {
            return BossAPI.publicMarkers(level);
        }
        if (level != cachedLevel) {
            cachedLevel = level;
            cachedFixture = null;
            nextResolveTick = 0;
        }
        if (cachedFixture == null) {
            long now = level.getGameTime();
            if (now < nextResolveTick) {
                return Optional.empty();
            }
            cachedFixture = BossAPI.publicMarkers(level).orElse(null);
            if (cachedFixture == null) {
                nextResolveTick = now + RETRY_TICKS;
                return Optional.empty();
            }
        }
        return Optional.of(cachedFixture);
    }
}
