package test.arryn.satchel.util;

import com.arryn.satchel.common.util.Ids;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** {@link Ids#id} and {@link Ids#parse}, the one place an id is built and the one place text is parsed into one. */
class IdsTest {

    @Test
    void an_id_is_its_namespace_and_its_path() {
        ResourceLocation id = Ids.id("satchel", "network");

        assertEquals("satchel", id.getNamespace());
        assertEquals("network", id.getPath());
        assertEquals("satchel:network", id.toString());
    }

    @Test
    void a_path_with_a_slash_is_kept_whole() {
        assertEquals("tables/keeper", Ids.id("satchel", "tables/keeper").getPath());
    }

    @Test
    void an_invalid_id_is_a_programming_error() {
        assertThrows(IllegalArgumentException.class, () -> Ids.id("Bad Namespace", "x"));
        assertThrows(IllegalArgumentException.class, () -> Ids.id("satchel", "Upper"));
        assertThrows(IllegalArgumentException.class, () -> Ids.id("satchel", "a:b"));
    }

    @Test
    void a_null_is_refused() {
        assertThrows(NullPointerException.class, () -> Ids.id(null, "x"));
        assertThrows(NullPointerException.class, () -> Ids.id("satchel", null));
    }

    @Test
    void text_that_names_an_id_parses_to_it() {
        assertEquals(Ids.id("satchel", "tables/keeper"), Ids.parse("satchel:tables/keeper").orElseThrow());
    }

    @Test
    void text_that_is_not_an_id_parses_to_empty() {
        assertTrue(Ids.parse("Bad Namespace:x").isEmpty());
        assertTrue(Ids.parse("satchel:a:b").isEmpty());
    }

    @Test
    void parse_refuses_a_null() {
        assertThrows(NullPointerException.class, () -> Ids.parse(null));
    }
}
