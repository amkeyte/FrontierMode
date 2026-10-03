package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.boss.common.fixture.BossSentinelsFixture;
import com.arryn.frontiermode.boss.common.fixture.SentinelPublicFixture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * Side-agnostic "is this entity a sentinel, and how big" lookup -- the one place the scale handlers
 * ask. On the server it reads {@link BossSentinelsFixture} (records and staged entries, so a
 * sentinel enters the world already at its sentinel size); on the client it reads the synced
 * {@link SentinelPublicFixture}. Nothing else reads either fixture directly. Same shape as
 * {@link BossMarkers}; no client classes here, so it loads on a dedicated server.
 *
 * <p>Reached from the {@code Entity} constructor (the {@code Size} event), so it tolerates a null
 * level, and from every rendered frame, so anything that can't be a sentinel is rejected before any
 * fixture lookup.
 */
public final class SentinelMarkers {

    private static final ClientFixtureCache<SentinelPublicFixture> CLIENT_FIXTURE =
            new ClientFixtureCache<>(BossAPI::sentinelMarkers);

    private SentinelMarkers() {
    }

    /**
     * True if {@code entity} could be a sentinel: a {@code MONSTER}-category mob on a real level.
     * Conversion admits only {@code MONSTER}-category mobs, so nothing else can be one.
     */
    public static boolean isCandidate(Entity entity) {
        return entity instanceof Mob
                && entity.getType().getCategory() == MobCategory.MONSTER
                && entity.level() != null;
    }

    /** True if {@code entity} is a known sentinel. */
    public static boolean isSentinel(Entity entity) {
        if (!isCandidate(entity)) {
            return false;
        }
        Level level = entity.level();
        return level.isClientSide
                ? CLIENT_FIXTURE.get(level).map(f -> f.isSentinel(entity.getUUID())).orElse(false)
                : BossAPI.sentinelsQuiet(level).map(f -> f.isSentinel(entity.getUUID())).orElse(false);
    }

    /** The sentinel's size multiplier, or {@code 1.0} for anything that isn't a known sentinel. */
    public static float scaleOf(Entity entity) {
        if (!isCandidate(entity)) {
            return 1.0F;
        }
        Level level = entity.level();
        return level.isClientSide
                ? CLIENT_FIXTURE.get(level).map(f -> f.scaleOf(entity.getUUID())).orElse(1.0F)
                : BossAPI.sentinelsQuiet(level).map(f -> f.scaleOf(entity.getUUID())).orElse(1.0F);
    }

    /** The client's synced sentinel fixture, cached; for the trail's tick. Empty until it arrives. */
    public static Optional<SentinelPublicFixture> clientFixture(Level level) {
        return CLIENT_FIXTURE.get(level);
    }
}
