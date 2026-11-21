package test.arryn.satchel.platform;

import org.junit.jupiter.api.Test;
import test.arryn.satchel.platform.PlatformBoundScanner.ImportResult;
import test.arryn.satchel.platform.PlatformBoundScanner.Offender;
import test.arryn.satchel.platform.PlatformBoundScanner.Result;
import test.arryn.satchel.platform.PlatformBoundScanner.Root;
import test.arryn.satchel.platform.PlatformBoundScanner.Rule;
import test.arryn.satchel.platform.PlatformBoundScanner.UsageOffender;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Platform-Bound Code § The check on Satchel's own source, on both folders of the tree: {@code shared}, which
 * every line compiles, under the {@code SHARED} rule, and the line folder under the {@code LINE} rule. The first
 * rule: a class with a direct Forge listener carries {@code @PlatformBound} with a reason. The second: every
 * platform import is on the authorized list, or its file is marked (a line file only), or extends a Satchel
 * type, or sits in a side package naming its own side's classes; a loader class is never allowed in
 * {@code shared}, and a marker excuses nothing there. Satchel has no baseline of pending imports, so there is
 * none to list. The third: no class builds a {@code ResourceLocation} with its constructor. A module's own test
 * is a thin class like this one, pointed at its source. FrontierMode does not copy {@link PlatformBoundScanner}:
 * its build pulls this file and {@code authorized-classes.txt} from here before its tests compile (FRO_267).
 */
class PlatformBoundCheckTest {

    @Test
    void every_class_with_a_direct_forge_listener_carries_platform_bound_with_a_reason() {
        Roots roots = roots();

        List<Offender> offenders = new ArrayList<>();
        int listenerClasses = 0;
        for (Path root : List.of(roots.shared(), roots.line())) {
            Result result = PlatformBoundScanner.scanAll(root);
            offenders.addAll(result.offenders());
            listenerClasses += result.listenerClasses().size();
        }

        assertTrue(listenerClasses > 0,
                "the scan found no class with a listener under " + roots.shared() + " or " + roots.line()
                        + ", so it checked nothing");
        assertTrue(offenders.isEmpty(), () -> "These classes hold a direct Forge listener and need "
                + "@PlatformBound(reason = \"...\") (Platform-Bound Code, The check):\n"
                + offenders.stream().map(Object::toString).collect(Collectors.joining("\n")));
    }

    @Test
    void every_platform_import_is_on_the_authorized_list_or_its_file_is_marked() {
        Roots roots = roots();

        ImportResult result = PlatformBoundScanner.scanImportRoots(
                List.of(new Root("shared", roots.shared(), Rule.SHARED),
                        new Root(roots.lineName(), roots.line(), Rule.LINE)),
                PlatformBoundScanner.authorizedClasses());

        assertTrue(result.platformClasses() > 0,
                "the scan found no platform import under " + roots.shared() + " or " + roots.line()
                        + ", so it checked nothing");
        assertTrue(result.offenders().isEmpty(), () -> "These platform imports are not allowed where they are. "
                + "In shared, a loader class is never allowed and a marker excuses nothing: the file belongs in "
                + roots.lineName() + ". Anywhere, a Minecraft class must be on the authorized list "
                + "(authorized-classes.txt), or its file carries @PlatformBound with a reason (a line file), or "
                + "extends a Satchel type, or is a server or client package naming its own side's classes "
                + "(Platform-Bound Code, The check):\n"
                + result.offenders().stream().map(Object::toString).collect(Collectors.joining("\n")));
    }

    @Test
    void no_class_builds_a_resource_location_with_its_constructor() {
        Roots roots = roots();

        List<UsageOffender> offenders = new ArrayList<>();
        offenders.addAll(PlatformBoundScanner.scanResourceLocationConstructors(roots.shared()));
        offenders.addAll(PlatformBoundScanner.scanResourceLocationConstructors(roots.line()));

        assertTrue(offenders.isEmpty(), () -> "ResourceLocation is free to import on the condition that an id is built "
                + "through Ids.id (authorized-classes.txt, Platform-Bound Code, The check):\n"
                + offenders.stream().map(Object::toString).collect(Collectors.joining("\n")));
    }

    /** The two source roots of the tree: Satchel's shared folder, and the line folder this test runs in. */
    private record Roots(Path shared, Path line, String lineName) {
    }

    /**
     * Gradle runs a test in the project directory, which is a line folder ({@code forge-1.20.1}); {@code shared}
     * sits beside it. Both must exist: a missing {@code shared} would leave the check reading one root and
     * passing, so it fails here instead.
     */
    private static Roots roots() {
        Path project = Path.of("").toAbsolutePath();
        Path line = project.resolve("src/main/java");
        Path shared = project.resolveSibling("shared").resolve("src/main/java");
        if (!Files.isDirectory(line)) {
            throw new IllegalStateException("Cannot find the line folder's src/main/java from " + project
                    + ": run the test from the line project's directory");
        }
        if (!Files.isDirectory(shared)) {
            throw new IllegalStateException("Cannot find Satchel's shared src/main/java at " + shared);
        }
        return new Roots(shared, line, project.getFileName().toString());
    }
}
