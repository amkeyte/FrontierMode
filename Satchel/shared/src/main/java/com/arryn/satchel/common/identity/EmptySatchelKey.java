package com.arryn.satchel.common.identity;

import java.util.UUID;

public final class EmptySatchelKey extends SatchelKey<Object> {

    private static final EmptySatchelKey INSTANCE =
            new EmptySatchelKey();

    private EmptySatchelKey() {
        super(
                new UUID(0L, 0L),
                "<empty>",
                Object.class
        );
    }

    @SuppressWarnings("unchecked")
    public static <T extends SatchelKey<?>> T instance() {
        return (T) INSTANCE;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public String toString() {
        return "SatchelKey[EMPTY]";
    }
}
