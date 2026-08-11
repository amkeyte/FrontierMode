package com.arryn.satchel.common.jig.level;

import com.arryn.satchel.common.identity.JigKey;
import net.minecraft.world.level.Level;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public final class LevelResolver {

    private LevelResolver() {}

    public static LevelScope resolveScope(Object source) {
        Objects.requireNonNull(source, "source");

        if (source instanceof Level level) {
            return new LevelScope(level);
        }

        return null;
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

        return UUID.nameUUIDFromBytes(
                level.dimension().toString().getBytes(StandardCharsets.UTF_8)
        );
    }
}
