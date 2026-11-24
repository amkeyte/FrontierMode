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
        List<Path> all = new ArrayList<>(roots.lines());
        all.add(0, roots.shared());
        for (Path root : all) {
            Result result = PlatformBoundScanner.scanAll(root);
            offenders.addAll(result.offenders());
            listeners.addAll(result.listenerClasses());
        }

        assertFalse(listeners.isEmpty(), "the scan found no class with a listener under " + all
                + ", so it checked nothing");
        assertTrue(offenders.isEmpty(), () -> "These classes hold a direct loader listener and need "
                + "@PlatformBound(reason = \"...\") (Platform-Bound Code, The check):\n"
                + offenders.stream().map(Object::toString).collect(Collectors.joining("\n")));
    }

    @Test
    void every_import_passes_the_rule_of_the_folder_it_is_in() {
        Roots roots = roots();

        List<Root> scan = new ArrayList<>();
        scan.add(new Root("shared", roots.shared(), Rule.MODULE_SHARED));
        for (Path line : roots.lines()) {
            scan.add(new Root(line.getParent().getParent().getParent().getFileName().toString(), line, Rule.LINE));
        }
        ImportResult result = PlatformBoundScanner.scanImportRoots(scan, PlatformBoundScanner.authorizedClasses());

        assertTrue(result.platformClasses() > 0, "the scan found no platform import under " + scan
                + ", so it checked nothing");
        assertTrue(result.offenders().isEmpty(), () -> "These imports break the rule of their folder (Platform-Bound "
                + "Code, The check). In shared, name no loader class and, outside a server or client package, no "
                + "net.minecraft.server or net.minecraft.client class; in a line folder, a Minecraft or "
                + "loader class is on the authorized list or its file carries @PlatformBound with a reason:\n"
                + result.offenders().stream().map(o -> o.baselineLine() + "    # line " + o.line())
                        .collect(Collectors.joining("\n")));
    }

    /** The tree's source roots: {@code shared}, and every line folder beside it. */
    private record Roots(Path shared, List<Path> lines) {
    }

    /**
     * Gradle runs a test in a project directory: a line folder ({@code forge-1.20.1}) or {@code shared} itself, so
     * {@code shared} is found from either, and an IDE may run it from the repository root. Every line folder beside
     * {@code shared} is checked, whichever line runs the test. A missing {@code shared} or a tree with no line fails
     * here, so the check cannot pass by reading nothing.
     */
    private static Roots roots() {
        Path here = Path.of("").toAbsolutePath();
        Path shared = null;
        for (Path candidate : List.of(
                here.resolveSibling("shared").resolve("src/main/java"),
                here.resolve("src/main/java"),
                here.resolve("FrontierMode/shared/src/main/java"))) {
            if (Files.isDirectory(candidate) && candidate.getParent().getParent().getParent().getFileName().toString().equals("shared")) {
                shared = candidate;
                break;
            }
        }
        if (shared == null) {
            throw new IllegalStateException("Cannot find FrontierMode's shared src/main/java from " + here);
        }
        Path tree = shared.getParent().getParent().getParent().getParent();
        List<Path> lines = new ArrayList<>();
        try (var siblings = Files.list(tree)) {
            siblings.filter(Files::isDirectory)
                    .filter(dir -> !dir.getFileName().toString().equals("shared"))
                    .map(dir -> dir.resolve("src/main/java"))
                    .filter(Files::isDirectory)
                    .sorted()
                    .forEach(lines::add);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        if (lines.isEmpty()) {
            throw new IllegalStateException("Cannot find a line folder's src/main/java beside " + shared);
        }
        return new Roots(shared, lines);
    }
}
