package com.arryn.satchel.common.jig.level;

import com.arryn.satchel.common.identity.JigKey;
import com.arryn.satchel.common.identity.WorldIdentityContext;
import com.arryn.satchel.common.jig.guts.LogicalSideContext;
import net.minecraftforge.fml.LogicalSide;
import net.minecraft.world.level.Level;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class LevelResolver {

    private LevelResolver() {}

    public static LevelScope resolveScope(Object source) {
        Objects.requireNonNull(source, "source");

        if (!(source instanceof Level level)) {
            return null;
        }

        // RM_SAT_019: block/defer recognizing this source on the client until the server's
        // world-identity token has been received and cached. LevelScope's UUID folds the token
        // in (see LevelScope.determineUUID) -- constructing one before the token arrives would
        // compute a token-less scope UUID that would then need to be silently swapped out later.
        // Returning null here reuses this method's existing "unrecognized source" contract
        // rather than inventing a new state; ScopeEngine_Client's tick pulse re-triggers
        // introduceSource() once the token arrives (see ClientForgeIngress), so this is a
        // one-tick-or-so delay at world join, not a permanent block.
        //
        // Server never hits this: ServerForgeIngress binds the token before introducing any
        // source, so WorldIdentityContext.current() is always present by the time this runs
        // server-side.
        if (LogicalSideContext.require() == LogicalSide.CLIENT
                && WorldIdentityContext.current().isEmpty()) {
            return null;
        }

        return new LevelScope(level);
    }

    public static Class<Level> scppeType(){
        return Level.class;
    }

    public static UUID determineUUID(Object source) {
        if (!(source instanceof Level level)) {
            throw new IllegalStateException(
                    "LevelJig source must be a Level, got: "
                            + source.getClass().getName()
            );
        }

        // RM_SAT_019: fold in the world-identity token when available, same non-throwing/
        // non-blocking fallback as LevelScope's own (actually load-bearing) private static
        // determineUUID -- kept in sync here even though this particular overload is currently
        // unused (see LevelScope's constructor for why: it resolves to its own private static
        // overload, not this one) so it doesn't silently rot into a misleading duplicate.
        String dimensionKey = level.dimension().toString();
        Optional<UUID> token = safeCurrentToken();

        return token
                .map(t -> UUID.nameUUIDFromBytes((t + ":" + dimensionKey).getBytes(StandardCharsets.UTF_8)))
                .orElseGet(() -> UUID.nameUUIDFromBytes(dimensionKey.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Non-throwing token lookup -- {@link WorldIdentityContext#current()} requires a
     * {@link LogicalSideContext} bound to the calling thread, which callers of a UUID-derivation
     * method (as opposed to this class's own {@code resolveScope}, called only from
     * Satchel-aware ingress) shouldn't be assumed to guarantee.
     */
    private static Optional<UUID> safeCurrentToken() {
        if (LogicalSideContext.current().isEmpty()) {
            return Optional.empty();
        }
        return WorldIdentityContext.current();
    }
}
