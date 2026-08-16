package com.arryn.satchel.common.jig.level;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.identity.WorldIdentityContext;
import com.arryn.satchel.common.jig.guts.SatchelException;
import net.minecraft.world.level.Level;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public final class LevelResolver {

    private LevelResolver() {}

    public static LevelScope resolveScope(Object source) {
        Objects.requireNonNull(source, "source");

        if (!(source instanceof Level level)) {
            return null;
        }

        // isReady() gate (RM_SAT_019 world-identity token, generalized): defer recognizing this
        // source until Satchel.isReady() for this side -- on the client that means the token
        // hasn't been received yet. Returning null here reuses this method's existing
        // "unrecognized source" contract rather than let determineUUID() throw
        // SatchelException.NotReady; introduceSource() treats a null resolveScope result as "no
        // jig recognizes this source," the right degraded behavior for this window --
        // ClientForgeIngress's tick pulse re-triggers introduceSource() once ready (see
        // reannounceLevelIfTokenJustArrived).
        //
        // Server never actually returns null here: ServerForgeIngress binds the token before
        // introducing any source, so isReady() is always true by the time this runs server-side.
        if (!Satchel.isReady()) {
            return null;
        }

        return new LevelScope(level);
    }

    public static Class<Level> scppeType(){
        return Level.class;
    }

    /**
     * Deterministic UUID for a level scope -- dimension key folded with the bound world-identity
     * token. The one real implementation: {@link LevelScope}'s constructor calls this directly
     * ({@code super(LevelResolver.determineUUID(level))}) rather than duplicating the formula
     * itself -- this used to be the other way around (two independent, hand-synced copies of the
     * same logic, exactly the kind of thing that silently rots), and before that there were two
     * separately-duplicated non-throwing "safe token lookup" helpers on top of that. This is also
     * the method {@code LevelJigConfig.createPresets()}'s {@code uuidDeterminer} binding
     * (`LevelResolver::determineUUID`) was always meant to point at -- a private method on
     * {@code LevelScope} could never have satisfied that method-reference binding, so this
     * direction of delegation also happens to match the pre-existing config wiring's intent, not
     * just this pass's cleanup.
     *
     * <p>
     * Reached directly by {@code LevelScope}'s constructor and instance-level
     * {@code determineUUID(Object)} override, and indirectly via {@code LevelJig.determineUUID}
     * -- itself confirmed unreachable by anything live (see RM_SAT_019's investigation), kept
     * correct rather than deleted since it's a real {@code @Override} satisfying
     * {@code SatchelJig}'s contract.
     *
     * @throws SatchelException.NotReady if the world-identity token isn't bound yet for this side.
     *         {@code Satchel.isReady()} is the proactive check to avoid ever hitting this.
     */
    public static UUID determineUUID(Object source) {
        if (!(source instanceof Level level)) {
            throw new IllegalStateException(
                    "LevelJig source must be a Level, got: "
                            + source.getClass().getName()
            );
        }

        UUID token = WorldIdentityContext.current().orElseThrow(() ->
                new SatchelException.NotReady(
                        "LevelScope requested for " + level.dimension().location()
                                + " before the world-identity token is bound"
                )
        );

        String dimensionKey = level.dimension().toString();
        return UUID.nameUUIDFromBytes(
                (token + ":" + dimensionKey).getBytes(StandardCharsets.UTF_8)
        );
    }
}
