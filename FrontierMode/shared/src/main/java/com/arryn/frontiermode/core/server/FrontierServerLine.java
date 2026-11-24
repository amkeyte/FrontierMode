package com.arryn.frontiermode.core.server;

import com.arryn.satchel.common.platform.PlatformBound;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.Objects;
import java.util.Optional;

/**
 * The calls into Minecraft that shared logic makes on a {@link ServerLevel} and whose form differs by line (Two Lines
 * The vanilla calls that differ). It has {@link com.arryn.frontiermode.core.FrontierLine}'s shape, with its own
 * {@link Source}; each line's one source class implements both, and the mod entry installs it in both before
 * anything calls here.
 *
 * <p>
 * Asking before a source is installed throws. That is a programming error, and it crashes in every build.
 */
@PlatformBound(reason = "vanilla calls that differ by line")
public final class FrontierServerLine {

    /** How a line makes each call. Implemented once per line. */
    public interface Source {

        void finalizeSpawn(Mob mob, ServerLevel level, BlockPos pos, MobSpawns.Setup setup);

        Optional<LootTable> lootTable(ServerLevel level, ResourceLocation id);

        Optional<ConfiguredFeature<?, ?>> configuredFeature(ServerLevel level, ResourceLocation id);
    }

    private static volatile Source source;

    private FrontierServerLine() {
    }

    /** Installs the line's calls. Once: a second call throws. */
    public static synchronized void install(Source installed) {
        Objects.requireNonNull(installed, "installed");
        if (source != null) {
            throw new IllegalStateException("The line is already installed (" + source.getClass().getName() + ")");
        }
        source = installed;
    }

    /**
     * Runs {@code setup} on a mob that has been created and placed: the loader's finalize-spawn event and then the
     * vanilla setup for {@link MobSpawns.Setup#DISPATCHED}, the vanilla setup alone for {@code VANILLA}.
     */
    public static void finalizeSpawn(Mob mob, ServerLevel level, BlockPos pos, MobSpawns.Setup setup) {
        required().finalizeSpawn(mob, level, pos, setup);
    }

    /** The loot table {@code id} names, or empty if the server has none. */
    public static Optional<LootTable> lootTable(ServerLevel level, ResourceLocation id) {
        return required().lootTable(level, id);
    }

    /** The configured feature {@code id} names, or empty if none is registered. */
    public static Optional<ConfiguredFeature<?, ?>> configuredFeature(ServerLevel level, ResourceLocation id) {
        return required().configuredFeature(level, id);
    }

    private static Source required() {
        Source installed = source;
        if (installed == null) {
            throw new IllegalStateException("No line is installed: the mod entry installs one first");
        }
        return installed;
    }
}
