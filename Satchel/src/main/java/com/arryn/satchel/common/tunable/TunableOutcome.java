package com.arryn.satchel.common.tunable;

import java.util.Objects;

/**
 * The result of a runtime {@link TunableManager} or {@link TuningTables} operation -- {@code tune},
 * {@code reset}, {@code reload}, {@code write}, or a lookup -- for the caller (the {@code /satchel
 * tunables} and {@code /satchel tables} commands) to switch on. Every non-{@link Kind#OK} outcome
 * means nothing changed.
 */
public final class TunableOutcome {

    public enum Kind {
        /** The operation applied (or, for a lookup, found the row). */
        OK,
        /** No row (or tuning table) with that name is registered. */
        UNKNOWN_NAME,
        /** A reload named a row (or class) the live file doesn't hold. */
        NOT_IN_FILE,
        /** A value or file row failed validation, or the file couldn't be read. */
        REJECTED,
        /** No server has started, so there's no registry or live file to act on. */
        NOT_RUNNING
    }

    private final Kind kind;
    private final String message;

    private TunableOutcome(Kind kind, String message) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.message = Objects.requireNonNull(message, "message");
    }

    static TunableOutcome ok(String message) {
        return new TunableOutcome(Kind.OK, message);
    }

    static TunableOutcome unknownName(String name) {
        return new TunableOutcome(Kind.UNKNOWN_NAME, "No tunable '" + name + "' is registered.");
    }

    static TunableOutcome unknownTable(String id) {
        return new TunableOutcome(Kind.UNKNOWN_NAME, "No tuning table '" + id + "' is declared.");
    }

    static TunableOutcome notInFile(String message) {
        return new TunableOutcome(Kind.NOT_IN_FILE, message);
    }

    static TunableOutcome rejected(String message) {
        return new TunableOutcome(Kind.REJECTED, message + " Nothing changed.");
    }

    static TunableOutcome notRunning() {
        return new TunableOutcome(Kind.NOT_RUNNING, "No server has started -- no tunables are loaded.");
    }

    public Kind kind() {
        return kind;
    }

    public boolean isOk() {
        return kind == Kind.OK;
    }

    /** A one-line, human-readable description of what happened, for chat. */
    public String message() {
        return message;
    }
}
