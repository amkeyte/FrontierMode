package com.arryn.frontiermode.core;

import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The list of {@link ScaleSource}s and the one size multiplier the hitbox and render handlers apply to any entity
 * (wiki/frontiermode/architecture/core.md § Scale sources). The one static in {@code core}: it holds code registered
 * at {@code init()}, never world data.
 *
 * <p>Registered at {@code init()}, frozen at the first read, which is after every module's {@code init()}: a late
 * registration throws, like a late Trinket contribution. A mob is a boss or a Sentinel, never both, so the sources are
 * exclusive and nothing compounds.
 */
public final class EntityScales {

    private static final List<ScaleSource> SOURCES = new CopyOnWriteArrayList<>();

    private static volatile boolean frozen;

    private EntityScales() {
    }

    /**
     * Adds a source. Called from a module's {@code init()}.
     *
     * @throws IllegalStateException after the first read
     */
    public static void register(ScaleSource source) {
        if (frozen) {
            throw new IllegalStateException("[Core] register(): the scale sources are frozen; a source must be"
                    + " registered from its module's init(), before anything reads one.");
        }
        SOURCES.add(source);
    }

    /** True when any source's gate is: the cheap test that an ordinary entity bounces off before anything is built. */
    public static boolean canBeScaled(Entity entity) {
        freeze();
        for (ScaleSource source : SOURCES) {
            if (source.mayScale(entity)) {
                return true;
            }
        }
        return false;
    }

    /** The first answer that is not {@code 1.0F}, or {@code 1.0F} when no source claims the entity. */
    public static float scaleOf(Entity entity) {
        freeze();
        for (ScaleSource source : SOURCES) {
            if (!source.mayScale(entity)) {
                continue;
            }
            float scale = source.scaleOf(entity);
            if (scale != 1.0F) {
                return scale;
            }
        }
        return 1.0F;
    }

    private static void freeze() {
        if (!frozen) {
            frozen = true;
        }
    }
}
