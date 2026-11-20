package com.arryn.satchel.common.platform;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * The questions Satchel asks of the game's loader, so that no class meant to be shared reads the loader itself:
 * is this a dedicated server, where is the config folder, where is a file a mod ships, and which server run is
 * this. One {@link Source} answers them, installed once by the loader-bound code when the mod is constructed
 * (on Forge, {@code SatchelMod} installs a {@code ForgeEnvironment}). A port to another loader supplies its own
 * source and changes no caller.
 *
 * <p>
 * Asking before a source is installed throws. That is a programming error, and it crashes in every build.
 */
public final class SatchelEnvironment {

    /** What the loader answers. Implemented once per loader. */
    public interface Source {

        /** Whether this process is a dedicated server: no client code, and no integrated server. */
        boolean isDedicatedServer();

        /** The loader's config folder, the one a player or a developer edits. */
        Path configDir();

        /**
         * The path of a file shipped in the resources of the mod {@code modId}, made of {@code segments} below
         * the resource root, or empty if no such mod is loaded. A path comes back whether or not the mod ships
         * that file, so the caller checks it exists.
         */
        Optional<Path> modResource(String modId, String... segments);

        /**
         * An object that stands for the server now running: the same object for as long as that server runs, a
         * different one for the next, and {@code null} while none runs. It is compared by identity and never
         * inspected, so a caller can tell a new server from the same one without naming the server's type.
         */
        Object serverRun();
    }

    private static volatile Source source;

    private SatchelEnvironment() {}

    /** Installs the loader's answers. Once: a second call throws. */
    public static synchronized void install(Source installed) {
        Objects.requireNonNull(installed, "installed");
        if (source != null) {
            throw new IllegalStateException(
                    "The environment is already installed (" + source.getClass().getName() + ")");
        }
        source = installed;
    }

    /** Whether this process is a dedicated server. */
    public static boolean isDedicatedServer() {
        return required().isDedicatedServer();
    }

    /** The loader's config folder. */
    public static Path configDir() {
        return required().configDir();
    }

    /** The path of a file shipped in the resources of the mod {@code modId}, or empty if no such mod is loaded. */
    public static Optional<Path> modResource(String modId, String... segments) {
        return required().modResource(modId, segments);
    }

    /** The object standing for the server now running, or {@code null} while none runs. */
    public static Object serverRun() {
        return required().serverRun();
    }

    private static Source required() {
        Source installed = source;
        if (installed == null) {
            throw new IllegalStateException(
                    "The environment was asked before it was installed -- SatchelMod installs it first in its constructor");
        }
        return installed;
    }
}
