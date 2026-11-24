package test.arryn.frontiermode.platform;

import org.junit.jupiter.api.Test;
import test.arryn.satchel.platform.PlatformBoundScanner;
import test.arryn.satchel.platform.PlatformBoundScanner.ImportResult;
import test.arryn.satchel.platform.PlatformBoundScanner.Offender;
import test.arryn.satchel.platform.PlatformBoundScanner.Result;
import test.arryn.satchel.platform.PlatformBoundScanner.Root;
import test.arryn.satchel.platform.PlatformBoundScanner.Rule;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Platform-Bound Code, The check, both rules, on FrontierMode's two source roots: {@code shared}, which every line
 * compiles, and the line folder this test runs in. The first rule: a class with a direct loader listener carries
 * {@code @PlatformBound} with a reason. The second: what a file may import. In {@code shared} (the module form of the
 * rule) a loader class is never allowed, a marker excuses nothing, and a Minecraft class of one side is named only
 * in a {@code server} or {@code client} package. In the line folder a platform import is on Satchel's authorized
 * list, or its file carries {@code @PlatformBound} with a reason, or a class in it extends or implements a Satchel
 * type. The scanner and the list are Satchel's own, pulled in at build time by {@code syncPlatformCheck}
 * (build.gradle), so there is no copy to drift.
 *
 * <p>
 * There is no baseline: an offender fails here.
 */
class PlatformBoundCheckTest {

    @Test
    void every_class_with_a_direct_loader_listener_carries_platform_bound_with_a_reason() {
        Roots roots = roots();

        List<Offender> offenders = new ArrayList<>();
        List<String> listeners = new ArrayList<>();
        for (Path root : List.of(roots.shared(), roots.line())) {
            Result result = PlatformBoundScanner.scanAll(root);
            offenders.addAll(result.offenders());
            listeners.addAll(result.listenerClasses());
        }

        assertFalse(listeners.isEmpty(), "the scan found no class with a listener under " + roots.shared() + " or "
                + roots.line() + ", so it checked nothing");
        assertTrue(offenders.isEmpty(), () -> "These classes hold a direct loader listener and need "
                + "@PlatformBound(reason = \"...\") (Platform-Bound Code, The check):\n"
                + offenders.stream().map(Object::toString).collect(Collectors.joining("\n")));
    }

    @Test
    void every_import_passes_the_rule_of_the_folder_it_is_in() {
        Roots roots = roots();

        ImportResult result = PlatformBoundScanner.scanImportRoots(
                List.of(new Root("shared", roots.shared(), Rule.MODULE_SHARED),
                        new Root(roots.lineName(), roots.line(), Rule.LINE)),
                PlatformBoundScanner.authorizedClasses());

        assertTrue(result.platformClasses() > 0, "the scan found no platform import under " + roots.shared() + " or "
                + roots.line() + ", so it checked nothing");
        assertTrue(result.offenders().isEmpty(), () -> "These imports break the rule of their folder (Platform-Bound "
                + "Code, The check). In shared, name no loader class and, outside a server or client package, no "
                + "net.minecraft.server or net.minecraft.client class; in " + roots.lineName() + ", a Minecraft or "
                + "loader class is on the authorized list or its file carries @PlatformBound with a reason:\n"
                + result.offenders().stream().map(o -> o.baselineLine() + "    # line " + o.line())
                        .collect(Collectors.joining("\n")));
    }

    /** The two source roots of the tree: {@code shared}, and the line folder this test runs in. */
    private record Roots(Path shared, Path line, String lineName) {
    }

    /**
     * Gradle runs a test in the project directory, which is a line folder ({@code forge-1.20.1} or
     * {@code neoforge-1.21.1}); {@code shared} sits beside it. Both must exist: a missing {@code shared} would leave
     * the check reading one root and passing, so it fails here instead.
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
            throw new IllegalStateException("Cannot find FrontierMode's shared src/main/java at " + shared);
        }
        return new Roots(shared, line, project.getFileName().toString());
    }
}
