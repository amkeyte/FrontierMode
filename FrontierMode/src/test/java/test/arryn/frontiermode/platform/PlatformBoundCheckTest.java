package test.arryn.frontiermode.platform;

import org.junit.jupiter.api.Test;
import test.arryn.satchel.platform.PlatformBoundScanner;
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
 * Platform-Bound Code § The check, the first rule, on FrontierMode's source: a class with a direct Forge
 * listener carries {@code @PlatformBound} with a reason. The scanner is Satchel's own, pulled in at build time
 * by {@code syncPlatformCheck} (build.gradle), so there is no copy to drift.
 *
 * <p>
 * Classes not yet marked are listed in {@code platform/pending-platform-bound.txt}, a baseline that may only
 * shrink: a listener class outside it fails here, and so does a listed file that no longer offends.
 */
class PlatformBoundCheckTest {

    private static final String BASELINE = "/platform/pending-platform-bound.txt";

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

    /** Gradle runs a test in the project directory; an IDE may run it from the repository root. */
    private static Path sourceRoot() {
        Path here = Path.of("").toAbsolutePath();
        for (Path candidate : List.of(here.resolve("src/main/java"), here.resolve("FrontierMode/src/main/java"))) {
            if (Files.isDirectory(candidate)) return candidate;
        }
        throw new IllegalStateException("Cannot find FrontierMode's src/main/java from " + here);
    }
}
