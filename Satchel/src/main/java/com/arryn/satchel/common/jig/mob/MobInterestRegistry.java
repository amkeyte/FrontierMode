package com.arryn.satchel.common.jig.mob;

import com.arryn.satchel.common.identity.JigKey;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where a consumer's {@link MobInterestSupplier} lives, keyed by the {@link JigKey} of the
 * {@code MobJigConfig} it was registered against. Deliberately outside the {@code JigConfig}
 * framework: {@code JigConfig}/{@code Presets}/{@code CompiledJigConfig} have no generic "extra
 * data" slot, and -- the real constraint -- {@code JigInfo}, what {@link MobJig#reconcile}
 * actually receives each pulse, never holds a reference back to the original {@code MobJigConfig}
 * instance, only the compiled category configs. A registry keyed by {@code JigKey} sidesteps this
 * entirely: {@code reconcile} already has {@code JigInfo.key} in hand, the same field every
 * consumer's event handlers already read as {@code info.jigInfo().key} for the shared-bus event
 * filter.
 */
public final class MobInterestRegistry {

    private MobInterestRegistry() {}

    private static final Map<JigKey<?>, MobInterestSupplier> SUPPLIERS =
            new ConcurrentHashMap<>();

    public static void register(JigKey<?> key, MobInterestSupplier supplier) {
        SUPPLIERS.put(
                Objects.requireNonNull(key, "key"),
                Objects.requireNonNull(supplier, "supplier")
        );
    }

    public static void unregister(JigKey<?> key) {
        SUPPLIERS.remove(Objects.requireNonNull(key, "key"));
    }

    public static MobInterestSupplier get(JigKey<?> key) {
        Objects.requireNonNull(key, "key");
        return SUPPLIERS.getOrDefault(key, MobInterestSupplier.NONE);
    }
}
