package com.arryn.satchel.common.tunable;

import java.lang.StackWalker.StackFrame;

/**
 * A dev-only balance-tuning value -- not a long-lived object to hold onto. Construct one fresh
 * wherever you need the current value (a cheap map lookup underneath); self-registers with {@link
 * TunableManager} at construction, backed by a CSV a developer hand-edits and diffs across saves
 * (loaded once at server start, written once at server stop -- no hot-reload, on purpose).
 *
 * <p>
 * <b>Convention, not enforced here:</b> a {@code Tunable} should be constructed inside a module's
 * own Rules class (e.g. {@code FeralRules}). Outside one is a smell.
 *
 * <p>
 * The registry key is {@code CallingClassName:name}, not the bare {@code name} passed in --
 * derived from the caller's own class via a single shallow {@link StackWalker} frame. This is what
 * makes two unrelated Rules classes coincidentally choosing the same short name a non-issue: they
 * land on different keys automatically, no cross-call-site check needed. It's class-level only,
 * not class+method -- reusing a name for two different things <em>within</em> the same class is
 * trusted not to happen, not guarded against.
 *
 * <p>
 * Redeclaring an existing name with a different {@code min}/{@code max} (whether the mismatch is
 * against a value loaded from the CSV or against an earlier registration this same session)
 * throws -- either fix the code to match the csv's intent, or fix/delete the csv row. {@code
 * default} is write-only: recorded for a human comparing the written-out CSV against current code,
 * never read back or validated.
 */
public final class Tunable {

    private static final StackWalker WALKER = StackWalker.getInstance();

    private final TunableType type;
    private final Object resolvedValue;

    public Tunable(String name, float min, float max, float def) {
        this.type = TunableType.FLOAT;
        this.resolvedValue = TunableManager.register(deriveKey(name), type, min, max, def);
    }

    public Tunable(String name, int min, int max, int def) {
        this.type = TunableType.INT;
        this.resolvedValue = TunableManager.register(deriveKey(name), type, min, max, def);
    }

    public Tunable(String name, String def) {
        this.type = TunableType.STRING;
        this.resolvedValue = TunableManager.register(deriveKey(name), type, null, null, def);
    }

    public float floatValue() {
        requireType(TunableType.FLOAT);
        return (Float) resolvedValue;
    }

    public int intValue() {
        requireType(TunableType.INT);
        return (Integer) resolvedValue;
    }

    public String stringValue() {
        requireType(TunableType.STRING);
        return (String) resolvedValue;
    }

    private void requireType(TunableType expected) {
        if (type != expected) {
            throw new IllegalStateException("Tunable is " + type + ", not " + expected);
        }
    }

    /** {@code CallingClassName:name} -- see this class's own doc for why the caller's class is
     * folded into the key rather than tracked as a separate comparison. */
    private static String deriveKey(String name) {
        String callerClass = WALKER.walk(frames -> frames.skip(2).findFirst())
                .map(StackFrame::getClassName)
                .orElseThrow(() -> new IllegalStateException("Tunable '" + name + "': couldn't resolve calling class"));

        String simpleName = callerClass.substring(callerClass.lastIndexOf('.') + 1);
        return simpleName + ":" + name;
    }
}
