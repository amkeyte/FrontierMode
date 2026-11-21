package test.arryn.satchel.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import test.arryn.satchel.platform.PlatformBoundScanner.UsageOffender;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The usage rule for {@code ResourceLocation} against sources written here, so it is known to fail when it should:
 * the tree test ({@link PlatformBoundCheckTest}) passes on Satchel's own source, and a scan that read nothing would
 * pass it too.
 */
class PlatformUsageScannerTest {

    private static List<UsageOffender> scan(String source) {
        return PlatformBoundScanner.scanResourceLocationSources(Map.of("Test.java", source));
    }

    private static UsageOffender onlyOffender(String source) {
        List<UsageOffender> offenders = scan(source);
        assertEquals(1, offenders.size(), offenders::toString);
        return offenders.get(0);
    }

    @Test
    void a_direct_construction_fails_with_its_line() {
        UsageOffender offender = onlyOffender("""
                package a;
                public class A {
                    static final Object ID = new ResourceLocation("a", "b");
                }
                """);

        assertEquals("Test.java", offender.file());
        assertEquals(3, offender.line());
        assertEquals("new ResourceLocation(", offender.used());
        assertTrue(offender.problem().contains("Ids.id"), offender.problem());
    }

    @Test
    void the_one_argument_form_and_a_space_before_the_bracket_fail_too() {
        assertEquals(1, scan("class A { Object o = new ResourceLocation(\"a:b\"); }").size());
        assertEquals(1, scan("class A { Object o = new   ResourceLocation  (\"a:b\"); }").size());
    }

    @Test
    void a_name_written_out_in_full_fails() {
        assertEquals(1, scan("class A { Object o = new net.minecraft.resources.ResourceLocation(\"a\", \"b\"); }").size());
    }

    @Test
    void every_construction_is_reported_in_order() {
        List<UsageOffender> offenders = scan("""
                class A {
                    Object a = new ResourceLocation("a", "b");
                    Object b = Ids.id("a", "b");
                    Object c = new ResourceLocation("c", "d");
                }
                """);

        assertEquals(List.of(2, 4), offenders.stream().map(UsageOffender::line).toList());
    }

    @Test
    void the_helper_the_parse_calls_and_the_type_itself_pass() {
        assertTrue(scan("""
                import net.minecraft.resources.ResourceLocation;
                class A {
                    ResourceLocation a = Ids.id("a", "b");
                    ResourceLocation b = ResourceLocation.tryParse("a:b");
                    Class<?> c = ResourceLocation.class;
                    void m(ResourceLocation id) { }
                    Object e = new ResourceLocationException("bad");
                    Object f = new MyResourceLocation("a");
                }
                """).isEmpty());
    }

    @Test
    void comments_and_string_literals_are_not_code() {
        assertTrue(scan("""
                // Object a = new ResourceLocation("a", "b");
                /* new ResourceLocation("a", "b") */
                /** {@code new ResourceLocation(MODID, "x")} */
                class A {
                    static final String S = "new ResourceLocation(";
                }
                """).isEmpty());
    }

    @Test
    void a_tree_is_walked(@TempDir Path root) throws IOException {
        Path pkg = Files.createDirectories(root.resolve("m"));
        Files.writeString(pkg.resolve("Bad.java"), "package m;\nclass Bad { Object o = new ResourceLocation(\"a\", \"b\"); }\n");
        Files.writeString(pkg.resolve("Good.java"), "package m;\nclass Good { Object o = Ids.id(\"a\", \"b\"); }\n");
        Files.writeString(root.resolve("notes.txt"), "new ResourceLocation(\"a\", \"b\")");

        List<UsageOffender> offenders = PlatformBoundScanner.scanResourceLocationConstructors(root);

        assertEquals(List.of("m/Bad.java"), offenders.stream().map(UsageOffender::file).toList());
    }

    @Test
    void a_missing_root_is_an_error_and_not_an_empty_pass(@TempDir Path root) {
        assertThrows(UncheckedIOException.class,
                () -> PlatformBoundScanner.scanResourceLocationConstructors(root.resolve("nope")));
    }
}
