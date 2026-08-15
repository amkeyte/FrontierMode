package com.arryn.satchel.common.jig.level;


import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.identity.WorldIdentityContext;
import com.arryn.satchel.common.jig.guts.ASatchelScope;
import com.arryn.satchel.common.jig.guts.LogicalSideContext;
import com.arryn.satchel.common.stitch.PersistStitch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * SatchelScope representing a Minecraft level.
 */
public final class LevelScope extends ASatchelScope
        implements
        PersistStitch {


    private final Level level;

    public LevelScope(Level level) {
        super(determineUUID(level));
        this.level = Objects.requireNonNull(level);
    }

    public LevelScope(LevelAccessor level) {
        this((Level) level);
    }


    public Level level() {
        return level;
    }

    @Override
    public String debugName() {
        return this.getClass().getSimpleName()
                + "[" + level.dimension().location() + "]"
                + uuid()
                + "@" + System.identityHashCode(this);
    }

    protected UUID determineUUID(Object source) {
        if (!(source instanceof Level sourceLevel)) {
            throw new IllegalStateException(
                    "LevelJig source must be a Level, got: "
                            + source.getClass().getName()
            );
        }

       return determineUUID(sourceLevel);
    }

    private static UUID determineUUID(Level source){
        // RM_SAT_019: fold in the world-identity token when available, opportunistically and
        // non-blocking. This is the actually load-bearing UUID derivation for LevelScope (see
        // the constructor above -- super(determineUUID(level)) resolves to this private static
        // overload, not the instance-level determineUUID(Object) below). It must stay
        // non-throwing and never defer construction: two FrontierMode call sites
        // (BorderAPI.determineScopeFromLevel, RenderContext's client render path) construct
        // LevelScope directly, bypassing LevelResolver.resolveScope entirely, and are out of
        // this pass's scope to touch or verify -- LevelResolver.resolveScope is where the actual
        // client-side block/defer behavior lives (it withholds recognizing a source at all until
        // the token is confirmed); this method just needs to keep working correctly for whatever
        // it's handed, token present or not.
        String dimensionKey = source.dimension().toString();
        Optional<UUID> token = safeCurrentToken();

        return token
                .map(t -> UUID.nameUUIDFromBytes((t + ":" + dimensionKey).getBytes(StandardCharsets.UTF_8)))
                .orElseGet(() -> UUID.nameUUIDFromBytes(dimensionKey.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Non-throwing token lookup -- see {@code LevelResolver.safeCurrentToken}, same reasoning.
     * Duplicated rather than shared because that one is {@code private} to its class and this
     * method must not add a new cross-class dependency just for a two-line null guard.
     */
    private static Optional<UUID> safeCurrentToken() {
        if (LogicalSideContext.current().isEmpty()) {
            return Optional.empty();
        }
        return WorldIdentityContext.current();
    }

    @Override
    public Optional<CompoundTag> loadBundleTag(BundleKey<?> key) {
        return Optional.empty();
    }
}
