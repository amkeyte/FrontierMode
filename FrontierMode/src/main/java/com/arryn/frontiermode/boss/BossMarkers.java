package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.boss.common.BossKinds;
import com.arryn.frontiermode.core.ClientFixtureCache;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * FRO_145/FRO_148: side-agnostic "is this entity a boss, and how big" lookup over the {@link BossRoster} -- the one place
 * the eye render layer, the hitbox {@code Size} handler, and the render-scale handler all ask. A live boss is an entity
 * the roster holds whose record is alive; its size is the record's {@code scale}. No client classes here, so it loads on
 * a dedicated server.
 *
 * <p>Bosses are only ever a type some kind can have a body of ({@link BossKinds#anyBody}), so anything else is
 * rejected before any roster lookup -- these are called from hot paths (every entity's dimension refresh, every
 * rendered frame).
 */
public final class BossMarkers {

    private static final ClientFixtureCache<BossRoster> CLIENT_ROSTER = new ClientFixtureCache<>(BossAPI::roster);

    private BossMarkers() {
    }

    // level() can be null for an entity built off-world (dimension queries, registry probes) -- the
    // Size event fires from the Entity constructor, so it has to tolerate that.
    public static boolean isEligible(Entity entity) {
        return BossKinds.anyBody(entity.getType()) && entity.level() != null;
    }

    /** True if {@code entity} is a currently-tracked, alive boss. */
    public static boolean isLiveBoss(Entity entity) {
        return isEligible(entity)
                && roster(entity.level()).map(roster -> roster.isLive(entity.getUUID())).orElse(false);
    }

    /** The kind key of the live boss this entity is, or empty for anything that isn't a tracked, alive boss. */
    public static Optional<String> kindOf(Entity entity) {
        if (!isEligible(entity)) {
            return Optional.empty();
        }
        return roster(entity.level()).flatMap(roster -> roster.kindOf(entity.getUUID()));
    }

    /** The boss's size multiplier, or {@code 1.0} for anything that isn't a tracked boss. */
    public static float scaleOf(Entity entity) {
        if (!isEligible(entity)) {
            return 1.0F;
        }
        return roster(entity.level()).map(roster -> roster.scaleOf(entity.getUUID())).orElse(1.0F);
    }

    /**
     * Subscribed to {@code ScopeEvent.Unloaded} in {@code BossJigConfig} (new-module-checklist.md item 9): the client
     * cache holds the last level and its roster, so that level's unload drops it. The per-side bus fires this for every
     * scope on the side; the cache's own level check makes every scope but the cached client level a no-op.
     */
    static void onScopeUnloaded(ScopeEvent.Unloaded event) {
        if (event.info().scope() instanceof LevelScope scope) {
            CLIENT_ROSTER.evict(scope.level());
        }
    }

    // The side check is Satchel's, and only once Satchel is ready: isClient() throws on a thread no side is bound to,
    // which an Entity constructor can run on before the foundation is up. The server isn't cached -- it only asks on
    // dimension refresh, not per frame; the client asks for every drawn entity.
    private static Optional<BossRoster> roster(Level level) {
        if (!Satchel.isReady()) {
            return Optional.empty();
        }
        return Satchel.isClient() ? CLIENT_ROSTER.get(level) : BossAPI.roster(level);
    }
}
