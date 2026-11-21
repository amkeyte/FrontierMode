package test.arryn.satchel.platform;

import org.junit.jupiter.api.Test;
import test.arryn.satchel.platform.PlatformBoundScanner.ImportResult;
import test.arryn.satchel.platform.PlatformBoundScanner.Result;
import test.arryn.satchel.platform.PlatformBoundScanner.UsageOffender;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Platform-Bound Code § The check on Satchel's own source. The first rule: a class with a direct Forge
 * listener carries {@code @PlatformBound} with a reason. The second: every platform import is on the authorized
 * list, or its file is marked, or extends a Satchel type, or sits in a side package naming its own side's classes;
 * Satchel has no baseline of pending imports, so there is none to list. A module's own test is a thin class like this one,
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

    @Test
    void every_platform_import_is_on_the_authorized_list_or_its_file_is_marked() {
        Path root = sourceRoot();

        ImportResult result = PlatformBoundScanner.scanImports(root, PlatformBoundScanner.authorizedClasses());

        assertTrue(result.platformClasses() > 0,
                "the scan found no platform import under " + root + ", so it checked nothing");
        assertTrue(result.offenders().isEmpty(), () -> "These platform imports are not on the authorized list "
                + "(authorized-classes.txt), and their file neither carries @PlatformBound with a reason nor "
                + "extends a Satchel type, nor is a server or client package naming its own side's classes "
                + "(Platform-Bound Code, The check):\n"
                + result.offenders().stream().map(Object::toString).collect(Collectors.joining("\n")));
    }

    @Test
    void no_class_builds_a_resource_location_with_its_constructor() {
        Path root = sourceRoot();

        List<UsageOffender> offenders = PlatformBoundScanner.scanResourceLocationConstructors(root);

        assertTrue(offenders.isEmpty(), () -> "ResourceLocation is free to import on the condition that an id is built "
                + "through Ids.id (authorized-classes.txt, Platform-Bound Code, The check):\n"
                + offenders.stream().map(Object::toString).collect(Collectors.joining("\n")));
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
