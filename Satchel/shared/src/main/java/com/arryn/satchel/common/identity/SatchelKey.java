package com.arryn.satchel.common.identity;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public abstract class SatchelKey<T> {

    public final UUID id;
    public final String name;
    public final Class<T> type;

    protected SatchelKey(UUID id, String name, Class<T> type) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = validateName(name);
        this.type = Objects.requireNonNull(type, "type");

        UUID expected = deriveUuid(this.name);
        if (!this.id.equals(expected)) {
            throw new IllegalStateException(
                    getClass().getSimpleName() +
                            " identity mismatch: name=\"" + name +
                            "\" derives UUID=" + expected +
                            " but stored UUID=" + id
            );
        }
    }

    protected SatchelKey(String name, Class<T> type) {
        this(deriveUuid(name), name, type);
    }

    // ---------------------------------------------------------------------
    // Narrowing / typing
    // ---------------------------------------------------------------------

    public static UUID deriveUuid(String name) {
        return UUID.nameUUIDFromBytes(
                name.getBytes(StandardCharsets.UTF_8)
        );
    }

    protected static String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("SatchelKey name must be non-null and non-blank");
        }
        return name;
    }

    // ---------------------------------------------------------------------
    // Utilities
    // ---------------------------------------------------------------------

    public final <K extends SatchelKey<?>> K as(Class<K> keyType) {
        if (this.isEmpty()) {
            return EmptySatchelKey.instance();
        }
        if (keyType.isInstance(this)) {
            return keyType.cast(this);
        }
        return EmptySatchelKey.instance();
    }

    public boolean isEmpty() {
        return false;
    }

    @Override
    public final int hashCode() {
        return id.hashCode();
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || o.getClass() != getClass()) return false;
        SatchelKey<?> other = (SatchelKey<?>) o;
        return id.equals(other.id);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "[id=" + id +
                ", name=\"" + name + "\"" +
                ", scopeType=" + type.getSimpleName() +
                ']';
    }
    public final void requirePresent(String context) {
        if (isEmpty()) {
            throw new IllegalStateException(
                    "Missing SatchelKey in " + context
            );
        }
    }
}
