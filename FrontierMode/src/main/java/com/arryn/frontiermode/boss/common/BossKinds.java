package com.arryn.frontiermode.boss.common;

import net.minecraft.world.entity.EntityType;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The registry of {@link BossKind}s, keyed by {@link BossKind#key()} (boss.md § BossKind). Each kind's module
 * registers its kind from its {@code init()}; the base looks one up by the key a record names.
 */
public final class BossKinds {

    private static final Map<String, BossKind> KINDS = new ConcurrentHashMap<>();

    private BossKinds() {
    }

    /** Registers {@code kind} under its key. A second kind with the same key is a programming error. */
    public static void register(BossKind kind) {
        BossKind previous = KINDS.putIfAbsent(kind.key(), kind);
        if (previous != null) {
            throw new IllegalStateException("[Boss] register(): a boss kind is already registered under \""
                    + kind.key() + "\".");
        }
    }

    /** The kind registered under {@code key}, or empty if none is. */
    public static Optional<BossKind> get(String key) {
        return Optional.ofNullable(KINDS.get(key));
    }

    /**
     * Whether any registered kind can have a body of {@code type}: the base's cheap type gate. Nothing is registered
     * until the kinds' modules have initialized, so before that no type is a body.
     */
    public static boolean anyBody(EntityType<?> type) {
        for (BossKind kind : KINDS.values()) {
            if (kind.canBeBody(type)) {
                return true;
            }
        }
        return false;
    }
}
