package com.arryn.satchel.common.jig.guts;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * RM_SAT_022 ("Roger"): the side-resolved lookup abstraction that lets {@code MobJig}'s poll ask
 * "does this UUID resolve to a live entity in this level" without caring which side it's on.
 * Mirrors the existing {@code ScopeEngine}/{@code ASatchelFoundationBooter#engine()} precedent
 * exactly -- one interface, one implementation per side, chosen at foundation boot -- rather than
 * inventing a second mechanism for side divergence.
 *
 * <p>
 * Deliberately {@code Entity}-typed, not {@code Mob}-typed: this is a general "resolve a UUID in
 * a level" capability, not something {@code MobJig}-specific, so it lives in {@code
 * common/jig/guts/} beside {@code ScopeEngine} rather than in {@code common/jig/mob/}. {@code
 * MobJig} (the only caller today) narrows the result to {@code Mob} itself after the fact.
 *
 * <p>
 * Reached via {@link LogicalFoundation#egress()} directly, not through {@code booter()} --
 * {@code LogicalFoundation.booter()} is private (see SAT_038), and this interface's whole reason
 * for existing is to give side-agnostic callers like {@code MobJig} a path that never needs it.
 */
public interface ForgeEgress {

    Optional<Entity> getEntity(Level level, UUID uuid);
}
