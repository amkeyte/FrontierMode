package com.arryn.satchel.common.jig.mob;

import net.minecraft.world.level.Level;

import java.util.Set;
import java.util.UUID;

/**
 * The per-level form of a consumer's mob-interest registration: "which UUIDs do you care about in
 * this level?" -- answered from the module's own state, so the module keeps no static
 * {@code Map<Level, Set<UUID>>} of its own. Registered with {@link
 * MobInterestRegistry#registerPerLevel}.
 *
 * <p>
 * <b>Must be a cheap lookup that returns a live view.</b> {@link
 * MobInterestRegistry#isAnyInterested(Level, UUID)} calls this on every mob death and every mob
 * leaving a level, for every registered consumer, before any other filter can reject the event. So
 * return a view over the module's own set (a fixture's record keys, say) and never rebuild a set per
 * call or resolve a fixture through a slow path each time. Return an empty set, never {@code null},
 * for a level the module has nothing for. The returned set is read from the server thread and must
 * not be mutated through this method.
 */
@FunctionalInterface
public interface MobInterestPerLevel {

    Set<UUID> interestedIn(Level level);
}
