package com.arryn.frontiermode.core;

import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A question put to every module on the bus: "what do you know about these subjects?". The poster (the
 * Inspect Stick) loads the subjects and posts the request through {@link InfoPrinter}; each module with
 * something to say has a consumer on its own ingress that adds a finished string, built by its own
 * formatter, and the printer prints the strings in the order they arrived (wiki admin-tools.md, "The Info
 * report").
 *
 * <p>Not a module event: it has no native Forge event, so it does not extend {@code ModuleSignal}.
 * Append-only and server thread only. A subject is any object the poster loaded; a consumer tests whether
 * it cares ({@code instanceof} for the types it knows) and adds nothing for the rest.
 */
public final class InfoRequest {

    /** One string a module added about a subject. The work happens in {@code text}, once per report. */
    record Entry(Object subject, String source, Supplier<String> text) {
    }

    private final Level level;
    private final List<Object> subjects;
    private final List<Entry> entries = new ArrayList<>();

    public InfoRequest(Level level, List<Object> subjects) {
        this.level = Objects.requireNonNull(level, "level");
        this.subjects = List.copyOf(subjects);
    }

    /** The level the subjects are in. */
    public Level level() {
        return level;
    }

    /** What the poster loaded: an entity today, later a block or a module's own record. */
    public List<Object> subjects() {
        return subjects;
    }

    /**
     * Adds a finished string about {@code subject}. {@code source} names the module and is used only in an
     * error line. {@code text} is evaluated once, on the server thread, when the report prints; a string
     * may hold several lines, and an empty or null answer adds no line. Any module may add any number.
     */
    public void add(Object subject, String source, Supplier<String> text) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(text, "text");
        entries.add(new Entry(subject, source, text));
    }

    /** The strings added about {@code subject}, in arrival order. */
    List<Entry> entriesFor(Object subject) {
        List<Entry> found = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.subject() == subject) {
                found.add(entry);
            }
        }
        return Collections.unmodifiableList(found);
    }
}
