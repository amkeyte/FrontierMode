package test.arryn.satchel.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import test.arryn.satchel.platform.PlatformBoundScanner.BaselineDiff;
import test.arryn.satchel.platform.PlatformBoundScanner.ImportOffender;
import test.arryn.satchel.platform.PlatformBoundScanner.ImportResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The second rule of Platform-Bound Code § The check against sources written here, so it is known to fail when it
 * should. The rule is not run on Satchel's own source, which is the platform layer, so no tree test would catch a
 * scanner that passed everything.
 */
class PlatformImportScannerTest {

    private static final Set<String> AUTHORIZED = Set.of(
            "net.minecraft.world.level.Level", "net.minecraft.core.BlockPos");

    private static ImportResult scan(String source) {
        return PlatformBoundScanner.scanImportSources(Map.of("Test.java", source), AUTHORIZED);
    }

    /** Files given as name, source, name, source, in that order. */
    private static ImportResult scanFiles(String... namesAndSources) {
        Map<String, String> sources = new LinkedHashMap<>();
        for (int i = 0; i < namesAndSources.length; i += 2) {
            sources.put(namesAndSources[i], namesAndSources[i + 1]);
        }
        return PlatformBoundScanner.scanImportSources(sources, AUTHORIZED);
    }

    private static ImportOffender onlyOffender(ImportResult result) {
        assertEquals(1, result.offenders().size(), result.offenders()::toString);
        return result.offenders().get(0);
    }

    private static List<String> baseline(ImportResult result) {
        return result.offenders().stream().map(ImportOffender::baselineLine).toList();
    }

    /* ------------------------------------------------------------
     * What counts as a platform import
     * --------------------------------------------------------- */

    @Test
    void an_import_on_the_list_passes() {
        ImportResult result = scan("""
                package a;
                import net.minecraft.world.level.Level;
                import net.minecraft.core.BlockPos;
                public class A { }
                """);

        assertTrue(result.offenders().isEmpty());
        assertEquals(2, result.platformClasses());
    }

    @Test
    void an_import_off_the_list_fails_with_its_line() {
        ImportOffender offender = onlyOffender(scan("""
                package a;
                import net.minecraft.world.entity.Mob;
                public class A { }
                """));

        assertEquals("Test.java", offender.file());
        assertEquals("net.minecraft.world.entity.Mob", offender.imported());
        assertEquals(2, offender.line());
        assertEquals("Test.java  net.minecraft.world.entity.Mob", offender.baselineLine());
    }

    @Test
    void a_forge_import_is_a_platform_import_too() {
        assertEquals("net.minecraftforge.registries.DeferredRegister", onlyOffender(scan("""
                import net.minecraftforge.registries.DeferredRegister;
                public class A { }
                """)).imported());
    }

    @Test
    void imports_outside_net_minecraft_are_not_platform_imports() {
        ImportResult result = scan("""
                import java.util.List;
                import com.mojang.brigadier.CommandDispatcher;
                import com.arryn.satchel.Satchel;
                import net.minecraftish.Thing;
                public class A { }
                """);

        assertTrue(result.offenders().isEmpty());
        assertEquals(0, result.platformClasses());
    }

    @Test
    void a_nested_class_import_is_an_import_of_its_outer_class() {
        assertTrue(scan("""
                import net.minecraft.world.level.Level.ExplosionInteraction;
                public class A { }
                """).offenders().isEmpty());

        assertEquals("net.minecraft.world.entity.Entity", onlyOffender(scan("""
                import net.minecraft.world.entity.Entity.RemovalReason;
                public class A { }
                """)).imported());
    }

    @Test
    void a_static_import_is_an_import_of_its_class() {
        assertEquals("net.minecraft.world.level.block.Blocks", onlyOffender(scan("""
                import static net.minecraft.world.level.block.Blocks.STONE;
                public class A { }
                """)).imported());
    }

    @Test
    void a_package_import_never_passes_but_a_class_wildcard_is_checked_as_its_class() {
        ImportOffender offender = onlyOffender(scan("""
                import net.minecraft.world.entity.*;
                public class A { }
                """));

        assertEquals("net.minecraft.world.entity.*", offender.imported());
        assertTrue(offender.problem().contains("by name"), offender.problem());

        assertTrue(scan("""
                import static net.minecraft.world.level.Level.*;
                public class A { }
                """).offenders().isEmpty());
    }

    @Test
    void a_class_is_reported_once_at_its_first_use() {
        ImportResult result = scan("""
                package a;
                import net.minecraft.world.entity.Mob;
                public class A { net.minecraft.world.entity.Mob m; }
                """);

        assertEquals(2, onlyOffender(result).line());
        assertEquals(1, result.platformClasses());
    }

    /* ------------------------------------------------------------
     * Names in code, and what the scan ignores
     * --------------------------------------------------------- */

    @Test
    void a_fully_qualified_name_in_code_counts_as_an_import() {
        ImportOffender offender = onlyOffender(scan("""
                public class A {
                    java.util.Optional<net.minecraft.world.entity.Entity> e;
                }
                """));

        assertEquals("net.minecraft.world.entity.Entity", offender.imported());
        assertEquals(2, offender.line());
    }

    @Test
    void a_fully_qualified_name_on_the_list_passes_and_a_nested_one_counts_as_its_outer_class() {
        assertTrue(scan("""
                public class A { net.minecraft.core.BlockPos p; }
                """).offenders().isEmpty());

        assertEquals("net.minecraft.world.item.Item", onlyOffender(scan("""
                public class A { Object p = new net.minecraft.world.item.Item.Properties(); }
                """)).imported());
    }

    @Test
    void comments_and_string_literals_name_no_platform_class() {
        ImportResult result = scan("""
                // import net.minecraft.world.entity.Mob;
                /* net.minecraft.world.entity.Entity */
                public class A {
                    static final String LOGGER = "net.minecraft.world.entity.LivingEntity";
                }
                """);

        assertTrue(result.offenders().isEmpty());
        assertEquals(0, result.platformClasses());
    }

    /* ------------------------------------------------------------
     * The marker
     * --------------------------------------------------------- */

    @Test
    void a_marker_with_a_reason_on_any_class_in_the_file_passes_every_import() {
        assertTrue(scan("""
                import net.minecraft.world.entity.Mob;
                import net.minecraft.world.entity.Entity;
                @PlatformBound(reason = "commands")
                public class A { }
                """).offenders().isEmpty());

        assertTrue(scan("""
                import net.minecraft.world.entity.Mob;
                class Plain { }
                @PlatformBound(reason = "commands")
                class Marked { }
                """).offenders().isEmpty());
    }

    @Test
    void a_marker_without_a_reason_passes_nothing() {
        ImportOffender offender = onlyOffender(scan("""
                import net.minecraft.world.entity.Mob;
                @PlatformBound
                public class A { }
                """));

        assertEquals("not on the authorized list, and the file's @PlatformBound has no reason", offender.problem());
    }

    /* ------------------------------------------------------------
     * Extending a Satchel type
     * --------------------------------------------------------- */

    @Test
    void a_class_that_extends_a_satchel_type_passes() {
        assertTrue(scan("""
                import com.arryn.satchel.common.fixture.SatchelFixture;
                import net.minecraft.world.entity.Mob;
                public class A extends SatchelFixture { }
                """).offenders().isEmpty());

        assertTrue(scan("""
                import net.minecraft.world.entity.Mob;
                public class A extends com.arryn.satchel.common.fixture.SatchelFixture { }
                """).offenders().isEmpty());
    }

    @Test
    void a_class_that_implements_a_satchel_type_passes() {
        assertTrue(scan("""
                import com.arryn.satchel.common.trinket.Trinket;
                import net.minecraft.world.entity.Mob;
                public class A implements Runnable, Trinket { }
                """).offenders().isEmpty());
    }

    @Test
    void a_record_with_a_satchel_interface_passes_and_a_generic_header_is_read_whole() {
        assertTrue(scan("""
                import com.arryn.satchel.common.trinket.Trinket;
                import net.minecraft.world.entity.Mob;
                record R(Mob mob, int n) implements Trinket { }
                """).offenders().isEmpty());

        assertTrue(scan("""
                import com.arryn.satchel.common.fixture.SatchelFixture;
                import net.minecraft.world.entity.Mob;
                class A<T extends Mob> extends SatchelFixture<T> { }
                """).offenders().isEmpty());
    }

    @Test
    void a_bound_in_the_generics_is_not_a_supertype() {
        assertEquals("net.minecraft.world.entity.Mob", onlyOffender(scan("""
                import com.arryn.satchel.common.fixture.SatchelFixture;
                import net.minecraft.world.entity.Mob;
                class A<T extends SatchelFixture> { }
                """)).imported());
    }

    @Test
    void a_nested_class_that_extends_a_satchel_type_covers_the_whole_file() {
        assertTrue(scan("""
                import com.arryn.satchel.common.fixture.SatchelFixture;
                import net.minecraft.world.entity.Mob;
                public class Outer {
                    static class Inner extends SatchelFixture { }
                }
                """).offenders().isEmpty());
    }

    @Test
    void extending_a_class_in_the_tree_that_extends_a_satchel_type_passes() {
        ImportResult result = scanFiles(
                "m/Base.java", """
                        package m;
                        import com.arryn.satchel.common.fixture.SatchelFixture;
                        public class Base extends SatchelFixture { }
                        """,
                "m/SamePackage.java", """
                        package m;
                        import net.minecraft.world.entity.Mob;
                        public class SamePackage extends Base { }
                        """,
                "n/Imported.java", """
                        package n;
                        import m.Base;
                        import net.minecraft.world.entity.Mob;
                        public class Imported extends Base { }
                        """,
                "n/Qualified.java", """
                        package n;
                        import net.minecraft.world.entity.Mob;
                        public class Qualified extends m.Base { }
                        """);

        assertTrue(result.offenders().isEmpty(), result.offenders()::toString);
    }

    @Test
    void extending_a_class_that_does_not_reach_a_satchel_type_fails() {
        ImportResult result = scanFiles(
                "m/Base.java", "package m;\npublic class Base { }\n",
                "m/Sub.java", """
                        package m;
                        import net.minecraft.world.entity.Mob;
                        public class Sub extends Base { }
                        """);

        assertEquals(List.of("m/Sub.java  net.minecraft.world.entity.Mob"), baseline(result));
    }

    @Test
    void a_cycle_in_the_hierarchy_ends_and_fails() {
        ImportResult result = scanFiles(
                "m/A.java", "package m;\nimport net.minecraft.world.entity.Mob;\nclass A extends B { }\n",
                "m/B.java", "package m;\nclass B extends A { }\n");

        assertEquals(List.of("m/A.java  net.minecraft.world.entity.Mob"), baseline(result));
    }

    /* ------------------------------------------------------------
     * Order, the count, and walking a tree
     * --------------------------------------------------------- */

    @Test
    void offenders_come_in_the_order_the_files_were_given_and_the_count_includes_allowed_classes() {
        ImportResult result = scanFiles(
                "b/B.java", """
                        import net.minecraft.world.entity.Mob;
                        import net.minecraft.core.BlockPos;
                        import net.minecraft.world.entity.Entity;
                        class B { }
                        """,
                "a/A.java", """
                        import net.minecraft.world.entity.Mob;
                        class A { }
                        """);

        assertEquals(List.of(
                        "b/B.java  net.minecraft.world.entity.Mob",
                        "b/B.java  net.minecraft.world.entity.Entity",
                        "a/A.java  net.minecraft.world.entity.Mob"),
                baseline(result));
        assertEquals(4, result.platformClasses());
    }

    @Test
    void a_tree_is_walked_and_its_files_are_read_together(@TempDir Path root) throws IOException {
        Path pkg = Files.createDirectories(root.resolve("m"));
        Files.writeString(pkg.resolve("Base.java"), """
                package m;
                import com.arryn.satchel.common.fixture.SatchelFixture;
                public class Base extends SatchelFixture { }
                """);
        Files.writeString(pkg.resolve("Sub.java"), """
                package m;
                import net.minecraft.world.entity.Mob;
                public class Sub extends Base { }
                """);
        Files.writeString(pkg.resolve("Bad.java"), """
                package m;
                import net.minecraft.world.entity.Mob;
                public class Bad { }
                """);
        Files.writeString(root.resolve("notes.txt"), "import net.minecraft.world.entity.Mob;");

        ImportResult result = PlatformBoundScanner.scanImports(root, AUTHORIZED);

        assertEquals(List.of("m/Bad.java  net.minecraft.world.entity.Mob"), baseline(result));
        assertEquals(2, result.platformClasses());
    }

    @Test
    void a_missing_root_is_an_error_and_not_an_empty_pass(@TempDir Path root) {
        assertThrows(UncheckedIOException.class,
                () -> PlatformBoundScanner.scanImports(root.resolve("nope"), AUTHORIZED));
    }

    /* ------------------------------------------------------------
     * The baseline and the list
     * --------------------------------------------------------- */

    @Test
    void a_baseline_may_only_shrink() {
        List<ImportOffender> offenders = List.of(
                new ImportOffender("a/A.java", 3, "net.minecraft.world.entity.Mob", "p"),
                new ImportOffender("a/A.java", 4, "net.minecraft.world.entity.Entity", "p"));
        Set<String> baseline = PlatformBoundScanner.baselineLines("""
                # known offenders
                a/A.java  net.minecraft.world.entity.Mob
                a/Gone.java  net.minecraft.world.entity.Mob   # went when the file did
                """);

        BaselineDiff diff = PlatformBoundScanner.againstBaseline(offenders, baseline);

        assertEquals(1, diff.fresh().size());
        assertEquals("net.minecraft.world.entity.Entity", diff.fresh().get(0).imported());
        assertEquals(List.of("a/Gone.java  net.minecraft.world.entity.Mob"), diff.stale());
    }

    @Test
    void a_baseline_that_matches_exactly_has_nothing_fresh_or_stale() {
        List<ImportOffender> offenders = List.of(
                new ImportOffender("a/A.java", 3, "net.minecraft.world.entity.Mob", "p"));

        BaselineDiff diff = PlatformBoundScanner.againstBaseline(offenders,
                PlatformBoundScanner.baselineLines("a/A.java   net.minecraft.world.entity.Mob\n"));

        assertTrue(diff.fresh().isEmpty());
        assertTrue(diff.stale().isEmpty());
    }

    @Test
    void a_baseline_line_with_one_word_is_an_error() {
        assertThrows(IllegalArgumentException.class, () -> PlatformBoundScanner.baselineLines("a/A.java\n"));
    }

    @Test
    void the_list_takes_the_first_word_of_each_entry() {
        assertEquals(Set.of("net.minecraft.core.BlockPos", "net.minecraft.world.level.Level"),
                PlatformBoundScanner.authorizedClasses("""
                        # a comment

                        net.minecraft.core.BlockPos  # immutable
                        net.minecraft.world.level.Level
                        """));
    }

    @Test
    void the_shipped_list_names_only_outermost_platform_classes() {
        Set<String> list = PlatformBoundScanner.authorizedClasses();

        assertFalse(list.isEmpty());
        for (String entry : list) {
            assertTrue(entry.startsWith("net.minecraft.") || entry.startsWith("net.minecraftforge."), entry);

            // An entry that is a nested class would be checked as its outer class and never match.
            ImportResult result = PlatformBoundScanner.scanImportSources(
                    Map.of("T.java", "import " + entry + ";\nclass A { }\n"), list);
            assertTrue(result.offenders().isEmpty(), () -> entry + " is not an outermost class");
            assertEquals(1, result.platformClasses());
        }
    }
}
