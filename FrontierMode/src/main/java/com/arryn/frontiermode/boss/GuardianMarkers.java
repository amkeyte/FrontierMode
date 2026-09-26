package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.boss.common.fixture.BossGuardiansFixture;
import com.arryn.frontiermode.boss.common.fixture.GuardianPublicFixture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * Side-agnostic "is this entity a guardian, and how big" lookup -- the one place the scale handlers
 * ask. On the server it reads {@link BossGuardiansFixture} (records and staged entries, so a
 * guardian enters the world already at its guardian size); on the client it reads the synced
 * {@link GuardianPublicFixture}. Nothing else reads either fixture directly. Same shape as
 * {@link BossMarkers}; no client classes here, so it loads on a dedicated server.
 *
 * <p>Reached from the {@code Entity} constructor (the {@code Size} event), so it tolerates a null
 * level, and from every rendered frame, so anything that can't be a guardian is rejected before any
 * fixture lookup.
 */
public final class GuardianMarkers {

    private static final ClientFixtureCache<GuardianPublicFixture> CLIENT_FIXTURE =
            new ClientFixtureCache<>(BossAPI::guardianMarkers);

    private GuardianMarkers() {
    }

    /**
     * True if {@code entity} could be a guardian: a {@code MONSTER}-category mob on a real level.
     * Conversion admits only {@code MONSTER}-category mobs, so nothing else can be one.
     */
    public static boolean isCandidate(Entity entity) {
        return entity instanceof Mob
                && entity.getType().getCategory() == MobCategory.MONSTER
                && entity.level() != null;
    }

    /** True if {@code entity} is a known guardian. */
    public static boolean isGuardian(Entity entity) {
        if (!isCandidate(entity)) {
            return false;
        }
        Level level = entity.level();
        return level.isClientSide
                ? CLIENT_FIXTURE.get(level).map(f -> f.isGuardian(entity.getUUID())).orElse(false)
                : BossAPI.guardiansQuiet(level).map(f -> f.isGuardian(entity.getUUID())).orElse(false);
    }

    /** The guardian's size multiplier, or {@code 1.0} for anything that isn't a known guardian. */
    public static float scaleOf(Entity entity) {
        if (!isCandidate(entity)) {
            return 1.0F;
        }
        Level level = entity.level();
        return level.isClientSide
                ? CLIENT_FIXTURE.get(level).map(f -> f.scaleOf(entity.getUUID())).orElse(1.0F)
                : BossAPI.guardiansQuiet(level).map(f -> f.scaleOf(entity.getUUID())).orElse(1.0F);
    }

    /** The client's synced guardian fixture, cached; for the trail's tick. Empty until it arrives. */
    public static Optional<GuardianPublicFixture> clientFixture(Level level) {
        return CLIENT_FIXTURE.get(level);
    }
}
