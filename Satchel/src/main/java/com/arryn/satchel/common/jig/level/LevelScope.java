package com.arryn.satchel.common.jig.level;


import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.guts.ASatchelScope;
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
        return UUID.nameUUIDFromBytes(
                source.dimension().toString().getBytes(StandardCharsets.UTF_8)
        );
    }

    @Override
    public Optional<CompoundTag> loadBundleTag(BundleKey<?> key) {
        return Optional.empty();
    }
}
