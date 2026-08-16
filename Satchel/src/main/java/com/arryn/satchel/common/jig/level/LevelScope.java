package com.arryn.satchel.common.jig.level;


import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.guts.ASatchelScope;
import com.arryn.satchel.common.stitch.PersistStitch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

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
        // LevelResolver.determineUUID is the one real UUID-derivation implementation (dimension
        // + bound world-identity token; throws SatchelException.NotReady if the token isn't
        // bound yet) -- LevelScope no longer keeps its own copy. Calling a static method on
        // another class is fine in a super(...) argument list; the restriction constructors have
        // is on calling *this* instance's own methods before super() completes, which this isn't.
        super(LevelResolver.determineUUID(level));
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
        return LevelResolver.determineUUID(source);
    }

    @Override
    public Optional<CompoundTag> loadBundleTag(BundleKey<?> key) {
        return Optional.empty();
    }
}
