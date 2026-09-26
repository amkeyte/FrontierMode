package com.arryn.frontiermode.boss.common;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

import java.util.List;
import java.util.Set;

/**
 * The one ordered list of boss-eligible mob types, on the common side so the server and the client
 * both reach it: the server's tier lookup indexes into {@link #ORDERED}, the client's "is this type
 * eligible" check is {@link #isEligible}, and the eye-texture wiring walks it (and fails loudly at
 * startup for a type with no texture). See boss.md's boss table and "Client-side visual marker".
 * Adding a boss mob type means adding it here and an eye texture named for its registry path.
 *
 * <p>Ordered as a ramp in what a player has to deal with: passive (Rabbit), conditional (Spider),
 * always-hostile melee (Zombie), ranged (Skeleton), then the rest. Indexed by tier, not layer. Not a
 * locked curve.
 */
public final class BossMobTypes {

    public static final List<EntityType<? extends Mob>> ORDERED = List.of(
            EntityType.RABBIT,
            EntityType.SPIDER,
            EntityType.ZOMBIE,
            EntityType.SKELETON,
            EntityType.ZOMBIFIED_PIGLIN,
            EntityType.PILLAGER,
            EntityType.VINDICATOR,
            EntityType.RAVAGER
    );

    private static final Set<EntityType<?>> ELIGIBLE = Set.copyOf(ORDERED);

    private BossMobTypes() {
    }

    public static boolean isEligible(EntityType<?> type) {
        return ELIGIBLE.contains(type);
    }
}
