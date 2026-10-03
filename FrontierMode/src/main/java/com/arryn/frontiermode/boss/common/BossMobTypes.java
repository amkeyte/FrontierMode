package com.arryn.frontiermode.boss.common;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

import java.util.List;
import java.util.Set;

/**
 * The one list of boss-eligible mob types, on the common side so the server and the client both
 * reach it: the client's "is this type eligible" check is {@link #isEligible}, and the eye-texture
 * wiring walks {@link #ELIGIBLE} (and fails loudly at startup for a type with no texture). It is the
 * union of the entity types in the {@code keeper-bodies} table's default rows (boss.md § Client-side
 * visual marker, § Keeper stats): the table is server-side, so its types are mirrored here. Adding a
 * Keeper body with a new entity type means adding it here and an eye texture named for its registry
 * path. The Killer Bunny is a Rabbit variant, so it adds no type.
 */
public final class BossMobTypes {

    public static final List<EntityType<? extends Mob>> ELIGIBLE = List.of(
            EntityType.RABBIT,
            EntityType.SPIDER,
            EntityType.ZOMBIE,
            EntityType.SKELETON,
            EntityType.ZOMBIFIED_PIGLIN,
            EntityType.PILLAGER,
            EntityType.VINDICATOR,
            EntityType.EVOKER
    );

    private static final Set<EntityType<?>> ELIGIBLE_SET = Set.copyOf(ELIGIBLE);

    private BossMobTypes() {
    }

    public static boolean isEligible(EntityType<?> type) {
        return ELIGIBLE_SET.contains(type);
    }
}
