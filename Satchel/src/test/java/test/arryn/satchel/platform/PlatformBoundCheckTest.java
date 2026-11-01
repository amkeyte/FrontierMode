package test.arryn.satchel.platform;

import org.junit.jupiter.api.Test;
import test.arryn.satchel.platform.PlatformBoundScanner.Result;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Platform-Bound Code § The check, the first rule, on Satchel's own source: a class with a direct Forge
 * listener carries {@code @PlatformBound} with a reason. A module's own test is a thin class like this one,
 * pointed at its source. FrontierMode does not copy {@link PlatformBoundScanner}: its build pulls this file
 * and {@code authorized-classes.txt} from here before its tests compile (FRO_267).
 */
class PlatformBoundCheckTest {

    @Test
    void every_class_with_a_direct_forge_listener_carries_platform_bound_with_a_reason() {
        Path root = sourceRoot();

        Result result = PlatformBoundScanner.scanAll(root);

        assertFalse(result.listenerClasses().isEmpty(),
                "the scan found no class with a listener under " + root + ", so it checked nothing");
        assertTrue(result.offenders().isEmpty(), () -> "These classes hold a direct Forge listener and need "
                + "@PlatformBound(reason = \"...\") (Platform-Bound Code, The check):\n"
                + result.offenders().stream().map(Object::toString).collect(Collectors.joining("\n")));
    }

    /** Gradle runs a test in the project directory; an IDE may run it from the repository root. */
    private static Path sourceRoot() {
        Path here = Path.of("").toAbsolutePath();
        for (Path candidate : List.of(here.resolve("src/main/java"), here.resolve("Satchel/src/main/java"))) {
            if (Files.isDirectory(candidate)) return candidate;
        }
        throw new IllegalStateException("Cannot find Satchel's src/main/java from " + here);
    }
}
