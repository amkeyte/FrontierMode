package com.arryn.satchel.common.jig.player;

import com.arryn.satchel.Satchel;
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
 *
 * <p>
 * <b>SAT_071: does not cache the backing {@link Player} object.</b> Only the player's own raw
 * UUID is held; {@link #player()} resolves fresh, every call, via {@code
 * Satchel.require().egress().getPlayer(uuid)}. This scope used to keep the {@link Player}
 * reference it was constructed with for its whole lifetime, which vanilla/Forge respawn broke
 * silently: {@code PlayerList.respawn} replaces the {@code ServerPlayer} *instance* for a
 * connection (same UUID, new object, which is exactly why Forge fires {@code
 * PlayerEvent.Clone} for mods to migrate per-instance data across the swap), and nothing in
 * Satchel listened for that -- the cached field kept pointing at a discarded entity indefinitely,
 * frozen mid-world, no longer ticking or receiving movement. Resolving fresh on every call closes
 * that whole class of bug rather than patching the one diagnosed instance-swap case: any future
 * scenario that swaps the underlying object (a different mod's own respawn-shaped trick, a
 * vanilla path nobody's hit yet) is handled for free, with no new event hook to add. The lookup
 * itself is cheap either side -- a UUID-keyed map lookup server-side, a direct field read
 * client-side -- so this isn't a cached-field-vs-recompute tradeoff worth agonizing over.
 */
public final class PlayerScope extends ASatchelScope implements PersistStitch {

    private final UUID playerUuid;

    public PlayerScope(Player player) {
        // PlayerResolver.determineUUID is the one real UUID-derivation implementation --
        // PlayerScope keeps no copy of its own, same discipline LevelScope follows for
        // LevelResolver.determineUUID. That's the scope's own hashed identity; playerUuid below
        // is the player's real, raw UUID, kept separately so there's something stable left to
        // re-resolve against once the Player object itself is no longer cached.
        super(PlayerResolver.determineUUID(player));
        Objects.requireNonNull(player, "player");
        this.playerUuid = player.getUUID();
    }

    /**
     * Resolves the live {@link Player} for this scope right now -- see this class's own doc for
     * why this is never cached. Throws rather than returning {@code Optional}: every real
     * consumer today already assumes a non-null result, and a {@link PlayerScope} failing to
     * resolve its own player while still loaded is a genuine invariant violation (the scope
     * outliving the real player, or a call from a thread never bound via {@code
     * LogicalSideContext}), not a normal timing window worth pushing onto every caller.
     */
    public Player player() {
        return Satchel.require().egress().getPlayer(playerUuid)
                .orElseThrow(() -> new IllegalStateException(
                        "PlayerScope for " + playerUuid + " could not resolve a live Player -- "
                                + "either this scope has outlived the real player, or this was "
                                + "called from a thread never bound via LogicalSideContext."));
    }

    @Override
    public String debugName() {
        // Deliberately does not call player() -- a debug-name helper should never throw, even
        // if this scope is in a genuinely broken state (exactly the kind of moment someone is
        // most likely to be reading a debug log for). Degrades to a placeholder instead.
        String name = Satchel.require().egress().getPlayer(playerUuid)
                .map(p -> p.getGameProfile().getName())
                .orElse("<unresolved>");

        return this.getClass().getSimpleName()
                + "[" + name + "]"
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
