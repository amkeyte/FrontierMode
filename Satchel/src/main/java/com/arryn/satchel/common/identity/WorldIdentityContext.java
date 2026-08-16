package com.arryn.satchel.common.identity;

import com.arryn.satchel.common.jig.guts.LogicalSideContext;
import net.minecraftforge.fml.LogicalSide;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * RM_SAT_019 — per-world identity token, side-bound.
 *
 * <p>
 * Defense-in-depth on top of RM_SAT_014's real fix (proper scope teardown on level unload).
 * {@link com.arryn.satchel.common.jig.level.LevelScope}'s scope UUID is deterministic from the
 * dimension key alone ({@code LevelResolver.determineUUID}/{@code LevelScope}'s own internal
 * derivation) — correct and necessary for one persistent world reconnecting across server
 * restarts, but it means two *different* worlds that happen to share dimension names are only
 * distinguishable from each other if teardown between them is never missed. This token adds a
 * second, explicit signal: a random UUID generated once per world (server-side, persisted) and
 * pushed to the client, folded into the scope UUID alongside the dimension key so two worlds
 * sharing a dimension name can no longer collide even if a teardown edge case is ever missed.
 *
 * <p>
 * Mirrors {@link LogicalSideContext}'s shape (plain static state, side-branching accessor) rather
 * than introducing a new context-passing mechanism. Does NOT itself talk to Forge, NBT, or the
 * network — {@code WorldIdentitySavedData} (server) and the S2C token packet handler (client) are
 * the only writers.
 *
 * <p>
 * <b>Server:</b> {@code serverToken} is set once per foundation lifetime, the first time any
 * dimension loads (see {@code ServerForgeIngress.onLevelDiscover}) — always available
 * synchronously, no network round-trip needed, since the server owns the persisted record.
 * <p>
 * <b>Client:</b> {@code clientToken} starts unset every session and is only ever set by the S2C
 * token packet's handler, once the server has actually sent it. Deliberately session-scoped, not
 * persisted: {@code ClientForgeIngress}'s logout handler clears it, so a later connection to a
 * different server can never see a stale token from a previous session.
 */
public final class WorldIdentityContext {

    private WorldIdentityContext() {}

    private static volatile UUID serverToken;
    private static volatile UUID clientToken;

    /**
     * Server-side only. Idempotent — safe to call on every level load with the same persisted
     * value; only the first call for a given foundation lifetime actually changes anything
     * observable.
     */
    public static void bindServerToken(UUID token) {
        serverToken = Objects.requireNonNull(token, "token");
    }

    /**
     * Client-side only. Called from the S2C token packet's handler once per receipt (join, and
     * again on each dimension change per the sync policy — always the same value for one session,
     * re-binding is harmless).
     */
    public static void bindClientToken(UUID token) {
        clientToken = Objects.requireNonNull(token, "token");
    }

    /**
     * Client-side only. Called on logout/disconnect so a later session (possibly a different
     * server, possibly the same one) never starts with a stale token left over from before.
     */
    public static void clearClientToken() {
        clientToken = null;
    }

    /**
     * The token for whichever {@link LogicalSide} is currently bound to this thread, or empty if
     * unavailable yet -- server-side this should only be empty in the narrow window before the
     * very first level-load's token establishment runs; client-side this is legitimately empty
     * from session start until the S2C packet arrives, which is the actual condition
     * {@code LevelResolver.resolveScope} defers on.
     *
     * @throws IllegalStateException if no {@link LogicalSide} is bound to this thread at all --
     *         matches {@link LogicalSideContext#require()}'s own contract; a caller reaching here
     *         from an unbound thread has a bug well upstream of this class.
     */
    public static Optional<UUID> current() {
        LogicalSide side = LogicalSideContext.require();
        return Optional.ofNullable(side == LogicalSide.SERVER ? serverToken : clientToken);
    }
}
