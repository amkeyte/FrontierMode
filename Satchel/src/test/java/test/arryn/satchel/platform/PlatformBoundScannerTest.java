package test.arryn.satchel.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import test.arryn.satchel.platform.PlatformBoundScanner.Offender;
import test.arryn.satchel.platform.PlatformBoundScanner.Result;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The scanner against sources written here, so it is known to fail when it should: the tree test
 * ({@link PlatformBoundCheckTest}) passes on Satchel's own source, and a scanner that read nothing would
 * pass it too.
 */
class PlatformBoundScannerTest {

    private static Result scan(String source) {
        return PlatformBoundScanner.scanSource("Test.java", source);
    }

    private static Offender onlyOffender(String source) {
        List<Offender> offenders = scan(source).offenders();
        assertEquals(1, offenders.size(), offenders::toString);
        return offenders.get(0);
    }

    /* ------------------------------------------------------------
     * What counts as a listener
     * --------------------------------------------------------- */

    @Test
    void a_marked_class_with_a_listener_passes() {
        Result result = scan("""
                @PlatformBound(reason = "registration")
                public class A {
                    A(IEventBus bus) { bus.addListener(this::on); }
                }
                """);

        assertTrue(result.offenders().isEmpty());
        assertEquals(List.of("Test.java:A"), result.listenerClasses());
    }

    @Test
    void an_unmarked_subscribe_event_fails() {
        Offender offender = onlyOffender("""
                public class A {
                    @SubscribeEvent
                    public static void on(Event e) { }
                }
                """);

        assertEquals("A", offender.type());
        assertEquals("@SubscribeEvent", offender.listener());
        assertEquals("no @PlatformBound", offender.problem());
        assertEquals(2, offender.line());
    }

    @Test
    void an_unmarked_event_bus_subscriber_fails_and_a_marked_one_passes() {
        String header = "@Mod.EventBusSubscriber(modid = \"x\", bus = Mod.EventBusSubscriber.Bus.FORGE)\n";

        Offender offender = onlyOffender(header + "public final class A { }\n");
        assertEquals("@Mod.EventBusSubscriber", offender.listener());

        assertTrue(scan("@PlatformBound(reason = \"r\")\n" + header + "public final class A { }\n")
                .offenders().isEmpty());
        assertTrue(scan(header + "@PlatformBound(reason = \"r\")\npublic final class A { }\n")
                .offenders().isEmpty());
    }

    @Test
    void a_fully_qualified_annotation_is_still_a_listener() {
        Offender offender = onlyOffender("""
                public class A {
                    @net.minecraftforge.eventbus.api.SubscribeEvent
                    public static void on(Event e) { }
                }
                """);

        assertEquals("@net.minecraftforge.eventbus.api.SubscribeEvent", offender.listener());
    }

    @Test
    void add_listener_and_add_generic_listener_calls_fail() {
        assertEquals("addListener(", onlyOffender("""
                public class A {
                    void f() { MinecraftForge.EVENT_BUS.addListener(A::on); }
                }
                """).listener());

        assertEquals("addGenericListener(", onlyOffender("""
                public class A {
                    void f() { bus
                        .addGenericListener(Foo.class, A::on); }
                }
                """).listener());
    }

    @Test
    void a_class_with_no_listener_needs_no_marker() {
        assertTrue(scan("""
                public class A {
                    void f() { bus.register(this); other.listener(); }
                }
                """).offenders().isEmpty());
    }

    @Test
    void every_listener_form_a_class_uses_is_named_once() {
        Offender offender = onlyOffender("""
                public class A {
                    @SubscribeEvent public static void a(Event e) { }
                    @SubscribeEvent public static void b(Event e) { }
                    void f() { bus.addListener(A::a); bus.addListener(A::b); }
                }
                """);

        assertEquals("@SubscribeEvent, addListener(", offender.listener());
    }

    /* ------------------------------------------------------------
     * The marker
     * --------------------------------------------------------- */

    @Test
    void a_marker_without_a_reason_fails() {
        String body = "public class A { void f() { bus.addListener(A::on); } }\n";

        assertEquals("@PlatformBound has no reason", onlyOffender("@PlatformBound\n" + body).problem());
        assertEquals("@PlatformBound has no reason", onlyOffender("@PlatformBound()\n" + body).problem());
        assertEquals("@PlatformBound has no reason", onlyOffender("@PlatformBound(reason = \"\")\n" + body).problem());
        assertEquals("@PlatformBound has no reason", onlyOffender("@PlatformBound(reason = \"  \")\n" + body).problem());
    }

    @Test
    void a_reason_that_is_a_constant_counts() {
        assertTrue(scan("""
                @PlatformBound(reason = Reasons.REGISTRATION)
                public class A { void f() { bus.addListener(A::on); } }
                """).offenders().isEmpty());
    }

    @Test
    void the_fully_qualified_marker_counts() {
        assertTrue(scan("""
                @com.arryn.satchel.common.platform.PlatformBound(reason = "render")
                public class A { void f() { bus.addListener(A::on); } }
                """).offenders().isEmpty());
    }

    @Test
    void an_enclosing_marker_covers_a_nested_listener() {
        assertTrue(scan("""
                @PlatformBound(reason = "registration")
                public class Outer {
                    static final class Inner {
                        void f() { bus.addListener(Inner::on); }
                    }
                }
                """).offenders().isEmpty());
    }

    @Test
    void a_marked_class_does_not_cover_an_unmarked_one_beside_it() {
        Offender offender = onlyOffender("""
                @PlatformBound(reason = "registration")
                class Marked { void f() { bus.addListener(Marked::on); } }

                class Unmarked { void f() { bus.addListener(Unmarked::on); } }
                """);

        assertEquals("Unmarked", offender.type());
        assertEquals(4, offender.line());
    }

    @Test
    void a_nested_class_with_a_listener_is_named_with_its_outer_class() {
        Offender offender = onlyOffender("""
                public class Outer {
                    static final class Inner {
                        void f() { bus.addListener(Inner::on); }
                    }
                }
                """);

        assertEquals("Outer.Inner", offender.type());
    }

    @Test
    void braces_inside_another_annotation_do_not_hide_the_marker() {
        String body = "public class A { void f() { bus.addListener(A::on); } }\n";

        assertTrue(scan("@PlatformBound(reason = \"r\")\n@Other({1, 2})\n" + body).offenders().isEmpty());
        assertTrue(scan("@Other({1, 2})\n@PlatformBound(reason = \"r\")\n" + body).offenders().isEmpty());
    }

    @Test
    void records_enums_interfaces_and_annotation_types_are_declarations_too() {
        assertTrue(scan("""
                @PlatformBound(reason = "r")
                record R(int a) { void f() { bus.addListener(R::on); } }
                """).offenders().isEmpty());
        assertEquals("R", onlyOffender("""
                record R(int a) { void f() { bus.addListener(R::on); } }
                """).type());

        assertEquals("E", onlyOffender("""
                enum E { ONE; void f() { bus.addListener(E::on); } }
                """).type());
        assertEquals("I", onlyOffender("""
                interface I { default void f() { bus.addListener(I::on); } }
                """).type());
    }

    /* ------------------------------------------------------------
     * What the scan ignores
     * --------------------------------------------------------- */

    @Test
    void comments_are_ignored() {
        assertTrue(scan("""
                /**
                 * A module never writes {@code @SubscribeEvent} or calls {@code .addListener(} itself.
                 */
                public class A {
                    // bus.addListener(A::on);
                    /* @SubscribeEvent */
                    void f() { }
                }
                """).offenders().isEmpty());
    }

    @Test
    void string_and_char_literals_and_text_blocks_are_ignored() {
        assertTrue(scan("""
                public class A {
                    String a = "bus.addListener(x) and @SubscribeEvent";
                    char b = '"';
                    String c = \"\"\"
                            @Mod.EventBusSubscriber
                            bus.addListener(x)
                            \"\"\";
                    String d = "a \\" .addListener( \\" b";
                }
                """).offenders().isEmpty());
    }

    @Test
    void a_class_literal_is_not_a_declaration() {
        // Foo.class must not open a class body, or the listener below would be read as outside any class.
        Result result = scan("""
                @PlatformBound(reason = "r")
                public class A {
                    Class<?> c = Foo.class;
                    void f() { bus.addListener(Foo.class, A::on); }
                }
                """);

        assertTrue(result.offenders().isEmpty());
        assertEquals(List.of("Test.java:A"), result.listenerClasses());
    }

    @Test
    void a_listener_outside_any_class_is_reported() {
        Offender offender = onlyOffender("bus.addListener(x);\n");

        assertEquals("<outside any class>", offender.type());
    }

    /* ------------------------------------------------------------
     * Walking a tree
     * --------------------------------------------------------- */

    @Test
    void a_tree_is_walked_and_files_are_named_relative_to_the_root(@TempDir Path root) throws IOException {
        Path package1 = Files.createDirectories(root.resolve("a/b"));
        Files.writeString(package1.resolve("Good.java"), """
                package a.b;
                @PlatformBound(reason = "r")
                public class Good { void f() { bus.addListener(Good::on); } }
                """);
        Files.writeString(package1.resolve("Bad.java"), """
                package a.b;
                public class Bad { void f() { bus.addListener(Bad::on); } }
                """);
        Files.writeString(root.resolve("notes.txt"), "bus.addListener(x);");

        Result result = PlatformBoundScanner.scanAll(root);

        assertEquals(2, result.listenerClasses().size());
        assertEquals(1, result.offenders().size());
        assertEquals("a/b/Bad.java", result.offenders().get(0).file());
        assertEquals(1, PlatformBoundScanner.scan(root).size());
    }

    @Test
    void a_missing_root_is_an_error_and_not_an_empty_pass(@TempDir Path root) {
        assertThrows(UncheckedIOException.class, () -> PlatformBoundScanner.scan(root.resolve("nope")));
    }
}
