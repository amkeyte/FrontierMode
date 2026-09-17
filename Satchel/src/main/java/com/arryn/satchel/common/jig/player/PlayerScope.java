package com.arryn.satchel.common.jig.player;

import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.guts.ASatchelScope;
import com.arryn.satchel.common.stitch.PersistStitch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * SatchelScope representing a logged-in player, either side. RM_SAT_020's rebuild originally
 * narrowed this to {@code ServerPlayer} specifically ("server-only by design"), matching the
 * only real consumers that existed at the time. SAT_063 widens it back to the abstract {@link
 * Player} -- the common supertype of {@code ServerPlayer} (server) and {@code LocalPlayer}
 * (client) -- since nothing about a player's identity is actually server-only; that was never
 * an architectural ceiling, just the only side anyone had built. Mirrors {@link
 * com.arryn.satchel.common.jig.level.LevelScope}'s shape, which already holds the equally
 * side-agnostic {@code Level}.
 *
 * <p>
 * Server-side call sites needing {@code ServerPlayer}-specific behavior (e.g. {@code
 * serverLevel()}) cast down explicitly -- safe wherever the caller already knows it's only ever
 * reached server-side (every {@code PlayerJig} consumer as of SAT_063 still declares {@code
 * sideApplicability(SERVER)} unless it deliberately opts into {@code BOTH}).
 */
public final class PlayerScope extends ASatchelScope implements PersistStitch {

    private final Player player;

    public PlayerScope(Player player) {
        // PlayerResolver.determineUUID is the one real UUID-derivation implementation --
        // PlayerScope keeps no copy of its own, same discipline LevelScope follows for
        // LevelResolver.determineUUID.
        super(PlayerResolver.determineUUID(player));
        this.player = Objects.requireNonNull(player);
    }

    public Player player() {
        return player;
    }

    @Override
    public String debugName() {
        return this.getClass().getSimpleName()
                + "[" + player.getGameProfile().getName() + "]"
                + uuid()
                + "@" + System.identityHashCode(this);
    }

    protected UUID determineUUID(Object source) {
        return PlayerResolver.determineUUID(source);
    }

    @Override
    public Optional<CompoundTag> loadBundleTag(BundleKey<?> key) {
        return Optional.empty();
    }
}
