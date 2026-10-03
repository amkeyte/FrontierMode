package com.arryn.satchel.common.jig.mob;

import com.arryn.satchel.common.identity.JigKey;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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

    private static final Map<JigKey<?>, MobInterestPerLevel> PER_LEVEL =
            new ConcurrentHashMap<>();

    public static void register(JigKey<?> key, MobInterestSupplier supplier) {
        SUPPLIERS.put(
                Objects.requireNonNull(key, "key"),
                Objects.requireNonNull(supplier, "supplier")
        );
    }

    /**
     * Registers the per-level form of interest beside {@link #register}: Satchel asks {@code
     * perLevel} which UUIDs the consumer cares about in each level it polls, and in the level of a
     * mob that dies or leaves, so the consumer needs no static map. A key may hold both forms; their
     * interest is the union. See {@link MobInterestPerLevel} for the cost the supplier must keep
     * to -- it runs on every mob death and every mob leaving a level.
     */
    public static void registerPerLevel(JigKey<?> key, MobInterestPerLevel perLevel) {
        PER_LEVEL.put(
                Objects.requireNonNull(key, "key"),
                Objects.requireNonNull(perLevel, "perLevel")
        );
    }

    /**
     * Removes whatever this key registered, in either form.
     */
    public static void unregister(JigKey<?> key) {
        Objects.requireNonNull(key, "key");
        SUPPLIERS.remove(key);
        PER_LEVEL.remove(key);
    }

    /**
     * The per-level registration for {@code key}, or {@code null} if it registered only the old
     * form (or nothing).
     */
    public static MobInterestPerLevel getPerLevel(JigKey<?> key) {
        Objects.requireNonNull(key, "key");
        return PER_LEVEL.get(key);
    }

    public static MobInterestSupplier get(JigKey<?> key) {
        Objects.requireNonNull(key, "key");
        return SUPPLIERS.getOrDefault(key, MobInterestSupplier.NONE);
    }

    /**
     * Returns {@code true} if the given UUID appears in the interest set of any
     * currently-registered supplier, across all levels.
     *
     * <p>
     * Called live from {@code ServerForgeIngress.onLivingDeath} as the gate before
     * constructing and posting a {@link com.arryn.satchel.common.lifecycle.MobDied}
     * event. Suppliers are queried fresh on every call -- no cached snapshot -- so
     * interest registered via {@code materialize()} is visible immediately (SAT_044).
     *
     * <p>
     * The overwhelming common case (every ordinary mob death in the world) returns
     * {@code false} quickly: if {@code SUPPLIERS} is empty the loop body never runs.
     */
    public static boolean isAnyInterested(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        for (MobInterestSupplier supplier : SUPPLIERS.values()) {
            for (Set<UUID> set : supplier.interestedMobs().values()) {
                if (set.contains(uuid)) return true;
            }
        }
        return false;
    }

    /**
     * As {@link #isAnyInterested(UUID)}, and additionally {@code true} if any per-level
     * registration names {@code uuid} in {@code level} -- the mob's own level. The gate {@code
     * ServerForgeIngress} uses for {@code MobDied}/{@code MobRemoved}; the one-argument method
     * above keeps its old-form-only behavior for anyone still calling it.
     */
    public static boolean isAnyInterested(Level level, UUID uuid) {
        Objects.requireNonNull(level, "level");
        if (isAnyInterested(uuid)) return true;
        for (MobInterestPerLevel perLevel : PER_LEVEL.values()) {
            if (perLevel.interestedIn(level).contains(uuid)) return true;
        }
        return false;
    }
}
