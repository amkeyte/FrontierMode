package test.arryn.satchel.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import test.arryn.satchel.platform.PlatformBoundScanner.BaselineDiff;
import test.arryn.satchel.platform.PlatformBoundScanner.ImportOffender;
import test.arryn.satchel.platform.PlatformBoundScanner.ImportResult;
import test.arryn.satchel.platform.PlatformBoundScanner.Root;
import test.arryn.satchel.platform.PlatformBoundScanner.Rule;

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
    void a_neoforge_import_is_a_platform_import_too() {
        assertEquals("net.neoforged.neoforge.registries.DeferredRegister", onlyOffender(scan("""
                import net.neoforged.neoforge.registries.DeferredRegister;
                public class A { }
                """)).imported());

        // And so is one named in code.
        assertEquals("net.neoforged.bus.api.IEventBus", onlyOffender(scan("""
                public class A { net.neoforged.bus.api.IEventBus bus; }
                """)).imported());
    }

    @Test
    void imports_outside_net_minecraft_are_not_platform_imports() {
        ImportResult result = scan("""
                import java.util.List;
                import com.mojang.brigadier.CommandDispatcher;
                import com.arryn.satchel.Satchel;
                import net.minecraftish.Thing;
                import net.neoforgedish.Thing;
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
     * Client and server packages
     * --------------------------------------------------------- */

    @Test
    void a_server_package_may_import_the_servers_minecraft_classes() {
        ImportResult result = scan("""
                package m.server.level;
                import net.minecraft.server.level.ServerLevel;
                import net.minecraft.server.MinecraftServer;
                public class A { net.minecraft.server.level.ServerPlayer p; }
                """);

        assertTrue(result.offenders().isEmpty(), result.offenders()::toString);
        assertEquals(3, result.platformClasses());
    }

    @Test
    void a_client_package_may_import_the_clients_minecraft_classes() {
        assertTrue(scan("""
                package m.client;
                import net.minecraft.client.Minecraft;
                public class A { }
                """).offenders().isEmpty());
    }

    @Test
    void the_same_import_outside_its_side_fails() {
        assertEquals("net.minecraft.server.level.ServerLevel", onlyOffender(scan("""
                package m.common.level;
                import net.minecraft.server.level.ServerLevel;
                public class A { }
                """)).imported());

        // Each side is its own: a server package does not take the client's classes, nor the reverse.
        assertEquals("net.minecraft.client.Minecraft", onlyOffender(scan("""
                package m.server;
                import net.minecraft.client.Minecraft;
                public class A { }
                """)).imported());
        assertEquals("net.minecraft.server.MinecraftServer", onlyOffender(scan("""
                package m.client;
                import net.minecraft.server.MinecraftServer;
                public class A { }
                """)).imported());
    }

    @Test
    void a_side_package_covers_only_its_own_minecraft_classes() {
        // A loader class of the side, and any other class off the list, still need the list or a marker.
        assertEquals(List.of("net.minecraftforge.server.ServerLifecycleHooks", "net.minecraft.world.entity.Mob"),
                scan("""
                        package m.server;
                        import net.minecraftforge.server.ServerLifecycleHooks;
                        import net.minecraft.world.entity.Mob;
                        public class A { }
                        """).offenders().stream().map(ImportOffender::imported).toList());
    }

    @Test
    void a_package_import_of_a_side_package_never_passes() {
        assertEquals("net.minecraft.server.level.*", onlyOffender(scan("""
                package m.server;
                import net.minecraft.server.level.*;
                public class A { }
                """)).imported());
    }

    @Test
    void the_side_is_a_package_segment_and_not_part_of_a_name() {
        assertEquals(1, scan("""
                package m.servers;
                import net.minecraft.server.level.ServerLevel;
                public class A { }
                """).offenders().size());

        // A segment deeper in the package counts.
        assertTrue(scan("""
                package m.server.tracking;
                import net.minecraft.server.level.ServerLevel;
                public class A { }
                """).offenders().isEmpty());

        // No package at all is no side.
        assertEquals(1, scan("""
                import net.minecraft.server.level.ServerLevel;
                public class A { }
                """).offenders().size());
    }

    /* ------------------------------------------------------------
     * Two roots: a shared folder and a line folder
     * --------------------------------------------------------- */

    private static void write(Path root, String file, String source) throws IOException {
        Path path = root.resolve(file);
        Files.createDirectories(path.getParent());
        Files.writeString(path, source);
    }

    private static ImportResult scanRoots(Path shared, Path line) {
        return PlatformBoundScanner.scanImportRoots(List.of(
                new Root("shared", shared, Rule.SHARED),
                new Root("forge-1.20.1", line, Rule.LINE)), AUTHORIZED);
    }

    @Test
    void a_loader_class_is_never_allowed_in_shared_but_a_marker_passes_it_in_a_line_folder(@TempDir Path dir)
            throws IOException {
        Path shared = dir.resolve("shared");
        Path line = dir.resolve("line");
        write(shared, "m/Marked.java", """
                package m;
                import net.minecraftforge.event.TickEvent;
                @PlatformBound(reason = "ingress")
                public class Marked { }
                """);
        write(shared, "m/Extending.java", """
                package m;
                import com.arryn.satchel.common.fixture.SatchelFixture;
                import net.neoforged.bus.api.Event;
                public class Extending extends SatchelFixture { }
                """);
        write(line, "m/Marked.java", """
                package m;
                import net.minecraftforge.event.TickEvent;
                @PlatformBound(reason = "ingress")
                public class MarkedInLine { }
                """);

        ImportResult result = scanRoots(shared, line);

        assertEquals(List.of(
                        "shared/m/Extending.java  net.neoforged.bus.api.Event",
                        "shared/m/Marked.java  net.minecraftforge.event.TickEvent"),
                baseline(result).stream().sorted().toList());
        assertTrue(result.offenders().stream().allMatch(o -> o.problem().contains("cannot be named in shared")),
                result.offenders()::toString);
    }

    @Test
    void a_marker_excuses_no_minecraft_class_in_shared_but_does_in_a_line_folder(@TempDir Path dir)
            throws IOException {
        Path shared = dir.resolve("shared");
        Path line = dir.resolve("line");
        String marked = """
                package m;
                import net.minecraft.world.entity.LivingEntity;
                @PlatformBound(reason = "entity")
                public class A { }
                """;
        write(shared, "m/A.java", marked);
        write(line, "m/B.java", marked.replace("class A", "class B"));

        ImportResult result = scanRoots(shared, line);

        assertEquals(List.of("shared/m/A.java  net.minecraft.world.entity.LivingEntity"), baseline(result));
        assertTrue(onlyOffender(result).problem().contains("a marker does not excuse it"), result.offenders()::toString);
    }

    @Test
    void shared_still_passes_the_list_a_side_package_and_a_class_that_extends_a_satchel_type(@TempDir Path dir)
            throws IOException {
        Path shared = dir.resolve("shared");
        write(shared, "m/Listed.java", """
                package m;
                import net.minecraft.world.level.Level;
                public class Listed { }
                """);
        write(shared, "m/server/Side.java", """
                package m.server;
                import net.minecraft.server.level.ServerLevel;
                public class Side { }
                """);
        write(shared, "m/Extending.java", """
                package m;
                import com.arryn.satchel.common.fixture.SatchelFixture;
                import net.minecraft.world.entity.LivingEntity;
                import net.minecraft.world.entity.*;
                public class Extending extends SatchelFixture { }
                """);

        ImportResult result = PlatformBoundScanner.scanImportRoots(
                List.of(new Root("", shared, Rule.SHARED)), AUTHORIZED);

        assertTrue(result.offenders().isEmpty(), result.offenders()::toString);
        assertEquals(4, result.platformClasses());
    }

    @Test
    void a_loader_package_import_in_shared_fails_even_for_a_class_that_extends_a_satchel_type(@TempDir Path dir)
            throws IOException {
        Path shared = dir.resolve("shared");
        write(shared, "m/A.java", """
                package m;
                import com.arryn.satchel.common.fixture.SatchelFixture;
                import net.minecraftforge.event.*;
                public class A extends SatchelFixture { }
                """);

        assertEquals("net.minecraftforge.event.*", onlyOffender(PlatformBoundScanner.scanImportRoots(
                List.of(new Root("", shared, Rule.SHARED)), AUTHORIZED)).imported());
    }

    @Test
    void the_roots_are_read_together_and_a_report_names_a_file_by_its_root(@TempDir Path dir) throws IOException {
        Path shared = dir.resolve("shared");
        Path line = dir.resolve("line");
        // A line class that extends a shared class that extends a Satchel type passes, and the reverse.
        write(shared, "m/Base.java", """
                package m;
                import com.arryn.satchel.common.fixture.SatchelFixture;
                public class Base extends SatchelFixture { }
                """);
        write(line, "m/Impl.java", """
                package m;
                import net.minecraft.world.entity.Mob;
                public class Impl extends Base { }
                """);
        write(line, "m/Platform.java", """
                package m;
                import com.arryn.satchel.common.fixture.SatchelFixture;
                public class Platform extends SatchelFixture { }
                """);
        write(shared, "m/UsesPlatform.java", """
                package m;
                import net.minecraft.world.entity.Mob;
                public class UsesPlatform extends Platform { }
                """);
        write(shared, "m/Bad.java", """
                package m;
                import net.minecraft.world.entity.Mob;
                public class Bad { }
                """);

        ImportResult result = scanRoots(shared, line);

        assertEquals(List.of("shared/m/Bad.java  net.minecraft.world.entity.Mob"), baseline(result));
        assertEquals(3, result.platformClasses());
    }

    @Test
    void the_single_root_calls_keep_the_line_rule(@TempDir Path dir) throws IOException {
        Path root = dir.resolve("src");
        write(root, "m/A.java", """
                package m;
                import net.minecraftforge.event.TickEvent;
                @PlatformBound(reason = "ingress")
                public class A { }
                """);

        assertTrue(PlatformBoundScanner.scanImports(root, AUTHORIZED).offenders().isEmpty());
        assertTrue(PlatformBoundScanner.scanImportRoots(List.of(new Root("", root, Rule.LINE)), AUTHORIZED)
                .offenders().isEmpty());
        assertEquals(1, PlatformBoundScanner.scanImportRoots(List.of(new Root("", root, Rule.SHARED)), AUTHORIZED)
                .offenders().size());
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
     * Where a CompoundTag is imported
     * --------------------------------------------------------- */

    private static final Set<String> TAG_PACKAGES = Set.of("m.persistence");

    private static ImportResult scanTags(Path shared, Path line) {
        return PlatformBoundScanner.scanCompoundTagRoots(List.of(
                new Root("shared", shared, Rule.SHARED),
                new Root("forge-1.20.1", line, Rule.LINE)), TAG_PACKAGES);
    }

    @Test
    void a_compound_tag_in_shared_passes_in_an_allowed_package_and_fails_in_another(@TempDir Path dir)
            throws IOException {
        Path shared = dir.resolve("shared");
        write(shared, "m/persistence/Sink.java", """
                package m.persistence;
                import net.minecraft.nbt.CompoundTag;
                public class Sink { void emit(CompoundTag tag) { } }
                """);
        write(shared, "m/module/Mod.java", """
                package m.module;
                import net.minecraft.nbt.CompoundTag;
                public class Mod { CompoundTag data; }
                """);

        ImportResult result = scanTags(shared, dir.resolve("line"));

        assertEquals(List.of("shared/m/module/Mod.java  net.minecraft.nbt.CompoundTag"), baseline(result));
        assertTrue(onlyOffender(result).problem().contains("persistence packages"), result.offenders()::toString);
        assertEquals(2, result.platformClasses());
    }

    @Test
    void a_fully_qualified_compound_tag_in_the_code_counts_as_an_import(@TempDir Path dir) throws IOException {
        Path shared = dir.resolve("shared");
        write(shared, "m/module/Mod.java", """
                package m.module;
                public class Mod { net.minecraft.nbt.CompoundTag data = new net.minecraft.nbt.CompoundTag(); }
                """);

        assertEquals("net.minecraft.nbt.CompoundTag", onlyOffender(scanTags(shared, dir.resolve("line"))).imported());
    }

    @Test
    void a_subpackage_of_an_allowed_package_is_not_allowed(@TempDir Path dir) throws IOException {
        Path shared = dir.resolve("shared");
        write(shared, "m/persistence/deep/Deep.java", """
                package m.persistence.deep;
                import net.minecraft.nbt.CompoundTag;
                public class Deep { CompoundTag data; }
                """);

        assertEquals(List.of("shared/m/persistence/deep/Deep.java  net.minecraft.nbt.CompoundTag"),
                baseline(scanTags(shared, dir.resolve("line"))));
    }

    @Test
    void only_shared_roots_are_read_for_a_compound_tag(@TempDir Path dir) throws IOException {
        Path shared = dir.resolve("shared");
        Path line = dir.resolve("line");
        write(shared, "m/persistence/Sink.java", """
                package m.persistence;
                import net.minecraft.nbt.CompoundTag;
                public class Sink { CompoundTag data; }
                """);
        write(line, "m/module/Platform.java", """
                package m.module;
                import net.minecraft.nbt.CompoundTag;
                public class Platform { CompoundTag data; }
                """);

        ImportResult result = scanTags(shared, line);

        assertTrue(result.offenders().isEmpty(), result.offenders()::toString);
        assertEquals(1, result.platformClasses());
    }

    @Test
    void another_nbt_class_and_a_mention_in_a_comment_are_not_a_compound_tag(@TempDir Path dir) throws IOException {
        Path shared = dir.resolve("shared");
        write(shared, "m/module/Mod.java", """
                package m.module;
                import net.minecraft.nbt.Tag;
                // A CompoundTag is not named here, only mentioned: net.minecraft.nbt.CompoundTag
                public class Mod { Tag tag; }
                """);

        ImportResult result = scanTags(shared, dir.resolve("line"));

        assertTrue(result.offenders().isEmpty(), result.offenders()::toString);
        assertEquals(0, result.platformClasses());
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
            // A loader class on the list would let shared code name it unchecked.
            assertTrue(entry.startsWith("net.minecraft."), () -> entry + " is not a Minecraft class");

            // An entry that is a nested class would be checked as its outer class and never match.
            ImportResult result = PlatformBoundScanner.scanImportSources(
                    Map.of("T.java", "import " + entry + ";\nclass A { }\n"), list);
            assertTrue(result.offenders().isEmpty(), () -> entry + " is not an outermost class");
            assertEquals(1, result.platformClasses());
        }
    }
}
