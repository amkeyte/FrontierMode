package com.arryn.satchel.common.jig.mob;

import net.minecraft.server.level.ServerLevel;

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
 * Typed against {@link ServerLevel}, not the common {@code Level}, because
 * {@code Level.getEntity(UUID)} -- the lookup {@link MobJig#reconcile} performs every cycle -- is
 * a server-only capability in vanilla Minecraft/Forge, backed by {@code ServerLevel}'s own entity
 * manager. This is a real, current limitation of the poll mechanism, not an oversight: a future
 * client-side consumer of {@code MobJig} would need its own resolution mechanism, not this one.
 */
@FunctionalInterface
public interface MobInterestSupplier {

    Map<ServerLevel, Set<UUID>> interestedMobs();

    MobInterestSupplier NONE = Map::of;
}
