package com.arryn.frontiermode.sentinel;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.core.ClientFixtureCache;
import com.arryn.frontiermode.sentinel.common.fixture.SentinelFixture;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.level.LevelJig;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * Side-agnostic "is this entity a sentinel, and how big" lookup -- the one place the scale handlers
 * ask. On the server it reads the record on the mob ({@link SentinelData}), so a sentinel enters the
 * world at its sentinel size once its writer has refreshed its dimensions; on the client it reads the
 * synced {@link SentinelFixture}. Nothing else reads either directly. Same shape as
 * {@code BossMarkers}; no client classes here, so it loads on a dedicated server.
 *
 * <p>Reached from the {@code Entity} constructor (the {@code Size} event), so it tolerates a null
 * level, and from every rendered frame, so anything that can't be a sentinel is rejected before any
 * fixture lookup.
 */
public final class SentinelMarkers {

    private static final ClientFixtureCache<SentinelFixture> CLIENT_FIXTURE =
            new ClientFixtureCache<>(SentinelMarkers::fixture);

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
        if (!isCandidate(entity) || !Satchel.isReady()) {
            return false;
        }
        Level level = entity.level();
        return Satchel.isClient()
                ? CLIENT_FIXTURE.get(level).map(f -> f.isSentinel(entity.getUUID())).orElse(false)
                : SentinelData.has(entity);
    }

    /** The sentinel's size multiplier, or {@code 1.0} for anything that isn't a known sentinel. */
    public static float scaleOf(Entity entity) {
        if (!isCandidate(entity) || !Satchel.isReady()) {
            return 1.0F;
        }
        Level level = entity.level();
        // The side check is Satchel's, and only once Satchel is ready: isClient() throws on a thread no side is
        // bound to, which an Entity constructor can run on before the foundation is up.
        return Satchel.isClient()
                ? CLIENT_FIXTURE.get(level).map(f -> f.scaleOf(entity.getUUID())).orElse(1.0F)
                : SentinelData.scaleOf(entity);
    }

    /**
     * Subscribed to {@code ScopeEvent.Unloaded} in {@code SentinelJigConfig} (new-module-checklist.md item 9): the
     * client cache holds the last level and its fixture, so that level's unload drops it. The per-side bus fires this
     * for every scope on the side; the cache's own level check makes every scope but the cached client level a no-op.
     */
    static void onScopeUnloaded(ScopeEvent.Unloaded event) {
        if (event.info().scope() instanceof LevelScope scope) {
            CLIENT_FIXTURE.evict(scope.level());
        }
    }

    /** The client's synced sentinel fixture, cached; for the client Trinket's tick. Empty until it arrives. */
    static Optional<SentinelFixture> clientFixture(Level level) {
        return CLIENT_FIXTURE.get(level);
    }

    /**
     * The level's {@link SentinelFixture} on either side, or empty while Satchel or the level's scope isn't ready. Like
     * {@code BossAPI.roster} it never logs: "not ready yet" is the normal state for the first moments after
     * joining a world, and this is called from render and tick paths.
     */
    static Optional<SentinelFixture> fixture(Level level) {
        if (!Satchel.isReady()) {
            return Optional.empty();
        }
        LevelScope scope = new LevelScope(level);
        if (Satchel.tryReadyScopeInfo(FrontierKeys.SENTINEL_JIG, scope).isEmpty()) {
            return Optional.empty();
        }
        var jig = (LevelJig) Satchel.require().requireJigInfo(FrontierKeys.SENTINEL_JIG).jig;
        return jig.getOrCreate(scope, FrontierKeys.SENTINEL_BUNDLE).get(FrontierKeys.SENTINEL);
    }
}
