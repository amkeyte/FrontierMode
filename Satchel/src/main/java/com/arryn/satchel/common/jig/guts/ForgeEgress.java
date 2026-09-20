package com.arryn.satchel.common.jig.guts;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
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

    /**
     * SAT_071: a second, deliberately level-independent lookup -- {@link #getEntity} needs the
     * caller to already know which {@link Level} to look in, which doesn't fit a player: unlike
     * a tracked mob, a player can be in any dimension at any moment, and the caller (e.g.
     * {@code PlayerScope}) has no cached reference left to ask "which level are you in right now"
     * once this is the only resolution path -- that's the whole point. Server-side, this reaches
     * the online-player list directly (global, not per-level); client-side, it's a UUID check
     * against the one local player. Not folded into {@link #getEntity}'s own signature: a
     * {@code null}/sentinel {@code Level} for "search everywhere" would be a worse interface than
     * a second, honestly-named method.
     */
    Optional<Player> getPlayer(UUID uuid);
}
