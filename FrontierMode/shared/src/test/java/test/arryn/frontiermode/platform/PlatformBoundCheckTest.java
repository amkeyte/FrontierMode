package test.arryn.frontiermode.platform;

import org.junit.jupiter.api.Test;
import test.arryn.satchel.platform.PlatformBoundScanner;
import test.arryn.satchel.platform.PlatformBoundScanner.BaselineDiff;
import test.arryn.satchel.platform.PlatformBoundScanner.ImportResult;
import test.arryn.satchel.platform.PlatformBoundScanner.Offender;
import test.arryn.satchel.platform.PlatformBoundScanner.Result;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Platform-Bound Code § The check, both rules, on FrontierMode's source. The first: a class with a direct Forge
 * listener carries {@code @PlatformBound} with a reason. The second: every {@code net.minecraft} and
 * {@code net.minecraftforge} class a file imports is on Satchel's authorized list, or the file carries
 * {@code @PlatformBound} with a reason, or a class in it extends or implements a Satchel type. The scanner and
 * the list are Satchel's own, pulled in at build time by {@code syncPlatformCheck} (build.gradle), so there is
 * no copy to drift.
 *
 * <p>
 * Each rule has a baseline that may only shrink. Classes not yet marked are listed in
 * {@code platform/pending-platform-bound.txt} (the first rule, by file), and imports not yet cleared in
 * {@code platform/pending-platform-imports.txt} (the second, by file and class). An offender outside its
 * baseline fails here, and so does a listed line that no longer offends.
 */
class PlatformBoundCheckTest {

    private static final String BASELINE = "/platform/pending-platform-bound.txt";
    private static final String IMPORT_BASELINE = "/platform/pending-platform-imports.txt";

    @Test
    void every_class_with_a_direct_forge_listener_carries_platform_bound_with_a_reason() {
        Path root = sourceRoot();

        Result result = PlatformBoundScanner.scanAll(root);

        assertFalse(result.listenerClasses().isEmpty(),
                "the scan found no class with a listener under " + root + ", so it checked nothing");

        Set<String> pending = pendingFiles();
        Set<String> offending = result.offenders().stream().map(Offender::file)
                .collect(Collectors.toCollection(TreeSet::new));

        List<Offender> fresh = result.offenders().stream().filter(o -> !pending.contains(o.file())).toList();
        assertTrue(fresh.isEmpty(), () -> "These classes hold a direct Forge listener and need "
                + "@PlatformBound(reason = \"...\") (Platform-Bound Code, The check):\n"
                + fresh.stream().map(Object::toString).collect(Collectors.joining("\n")));

        Set<String> stale = new TreeSet<>(pending);
        stale.removeAll(offending);
        assertTrue(stale.isEmpty(), () -> "These files in " + BASELINE + " now pass the check or no longer exist; "
                + "remove them from the baseline:\n" + String.join("\n", stale));
    }

    @Test
    void every_platform_import_is_listed_or_its_file_is_marked_or_extends_a_satchel_type() {
        Path root = sourceRoot();

        ImportResult result = PlatformBoundScanner.scanImports(root, PlatformBoundScanner.authorizedClasses());

        assertTrue(result.platformClasses() > 0,
                "the scan found no platform import under " + root + ", so it checked nothing");

        BaselineDiff diff = PlatformBoundScanner.againstBaseline(result.offenders(), importBaseline());
        assertTrue(diff.fresh().isEmpty(), () -> "These imports are not on the authorized list and their file is neither "
                + "marked nor a Satchel type (Platform-Bound Code, The check). Fix them, or add the lines to "
                + "platform/pending-platform-imports.txt:\n"
                + diff.fresh().stream().map(o -> o.baselineLine() + "    # line " + o.line())
                        .collect(Collectors.joining("\n")));
        assertTrue(diff.stale().isEmpty(), () -> "These lines in platform/pending-platform-imports.txt no longer offend; "
                + "remove them:\n" + String.join("\n", diff.stale()));
    }

    private static Set<String> importBaseline() {
        try (InputStream in = PlatformBoundCheckTest.class.getResourceAsStream(IMPORT_BASELINE)) {
            assertNotNull(in, "missing test resource " + IMPORT_BASELINE);
            return PlatformBoundScanner.baselineLines(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Set<String> pendingFiles() {
        try (InputStream in = PlatformBoundCheckTest.class.getResourceAsStream(BASELINE)) {
            assertNotNull(in, "missing test resource " + BASELINE);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .collect(Collectors.toCollection(TreeSet::new));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * FrontierMode's source lives in {@code shared}, which sits beside each line folder. Gradle runs a test in the
     * line project's directory ({@code forge-1.20.1}), so {@code shared} is its sibling; an IDE may run it from
     * {@code shared} itself or from the repository root. A missing {@code shared} fails here, so the check cannot
     * pass by reading nothing.
     */
    private static Path sourceRoot() {
        Path here = Path.of("").toAbsolutePath();
        for (Path candidate : List.of(
                here.resolveSibling("shared").resolve("src/main/java"),
                here.resolve("src/main/java"),
                here.resolve("FrontierMode/shared/src/main/java"))) {
            if (Files.isDirectory(candidate)) return candidate;
        }
        throw new IllegalStateException("Cannot find FrontierMode's shared src/main/java from " + here);
    }
}
