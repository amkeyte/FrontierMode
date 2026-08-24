package com.arryn.satchel.common.jig.mob;

import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-consumer registration surface for MobJig's poll-driven presence model: "here are the UUIDs
 * I care about, per level." {@link MobJig#reconcile} queries the registered supplier fresh every
 * reconciliation cycle -- no caching contract, so a consumer (e.g. a boss module) can add or drop
 * interest over time without re-registering its {@code MobJigConfig}.
 *
 * <p>
 * Typed against the common {@link Level}, not {@code ServerLevel} -- RM_SAT_022 ("Roger").
 * {@code Level#getEntity(UUID)} genuinely doesn't exist ({@code ServerLevel} has its own
 * UUID-keyed index; {@code ClientLevel} tracks entities by network ID with no equivalent), but
 * that's now handled behind {@link com.arryn.satchel.common.jig.guts.ForgeEgress}, resolved per
 * side at foundation boot -- the same shape {@code ScopeEngine} already uses for side divergence.
 * A consumer registering a {@code Level}-keyed interest map works unchanged on either side;
 * {@link MobJig#reconcile} is what actually resolves each UUID, through {@code ForgeEgress}, not
 * this interface.
 */
@FunctionalInterface
public interface MobInterestSupplier {

    Map<Level, Set<UUID>> interestedMobs();

    MobInterestSupplier NONE = Map::of;
}
