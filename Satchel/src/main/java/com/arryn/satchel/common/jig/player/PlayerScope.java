package com.arryn.satchel.common.jig.player;

import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.guts.ASatchelScope;
import com.arryn.satchel.common.stitch.PersistStitch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * SatchelScope representing a logged-in player. RM_SAT_020 rebuild -- see
 * {@link PlayerResolver} for why this doesn't resurrect the old, pre-rename
 * {@code common.requireJig.player.PlayerScope}. Mirrors
 * {@link com.arryn.satchel.common.jig.level.LevelScope}'s shape.
 *
 * <p>
 * Holds a {@link ServerPlayer} directly, not the abstract {@code Player} the old sketch used --
 * server-only by design (see {@code PlayerJigConfig}), matching every real consumer named so far
 * (RM_FRO_006's per-player border evaluation is server-authoritative, same as Border's own rule
 * evaluation).
 */
public final class PlayerScope extends ASatchelScope implements PersistStitch {

    private final ServerPlayer player;

    public PlayerScope(ServerPlayer player) {
        // PlayerResolver.determineUUID is the one real UUID-derivation implementation --
        // PlayerScope keeps no copy of its own, same discipline LevelScope follows for
        // LevelResolver.determineUUID.
        super(PlayerResolver.determineUUID(player));
        this.player = Objects.requireNonNull(player);
    }

    public ServerPlayer player() {
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
