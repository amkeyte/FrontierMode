package com.arryn.frontiermode.feral;

import com.arryn.satchel.common.util.MobData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.Optional;

/**
 * A Feral mob's record on the mob: the {@code frontiermode:feral} MobData namespace (feral.md § The
 * Feral record on the mob). "Is this Feral" is "does the mob carry the namespace". The record is the
 * identity, not the stat rung: Feral's multipliers sit in the {@code frontiermode:ladder} namespace
 * under the {@code feral} rung.
 *
 * <ul>
 *   <li>{@code distance}: the frontier distance, in blocks past the Frontier, at which the mob was
 *   tagged. Always present and always positive, which keeps the compound non-empty (MobData removes a
 *   namespace whose compound is empty). It drives no stat.</li>
 *   <li>{@code pack}: {@code true} for a pack member, absent for an ambient Feral.</li>
 * </ul>
 *
 * <p>Server-only: MobData is not synced. The record dies with the mob, so no record outside it can go
 * stale. Module code touches the mob's data only through {@link MobData}.
 */
public final class FeralData {

    public static final ResourceLocation NAMESPACE = new ResourceLocation("frontiermode", "feral");

    private static final String KEY_DISTANCE = "distance";
    private static final String KEY_PACK = "pack";

    /** What the record holds. */
    public record Facts(int distance, boolean pack) {
    }

    private FeralData() {
    }

    /** True if {@code entity} is a mob that carries the record. */
    public static boolean has(Entity entity) {
        return entity instanceof Mob mob && MobData.has(mob, NAMESPACE);
    }

    /** The record on {@code mob}, if it carries one. */
    public static Optional<Facts> read(Mob mob) {
        CompoundTag tag = MobData.read(mob, NAMESPACE);
        if (!tag.contains(KEY_DISTANCE)) {
            return Optional.empty();
        }
        return Optional.of(new Facts(tag.getInt(KEY_DISTANCE), tag.getBoolean(KEY_PACK)));
    }

    /** True if {@code mob} carries the record and it marks a pack member. */
    public static boolean isPack(Mob mob) {
        return MobData.read(mob, NAMESPACE).getBoolean(KEY_PACK);
    }

    /**
     * Writes the record unless {@code mob} already carries one; {@code true} if it wrote.
     * {@code distance} must be positive.
     */
    public static boolean writeIfAbsent(Mob mob, int distance, boolean pack) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(KEY_DISTANCE, distance);
        if (pack) {
            tag.putBoolean(KEY_PACK, true);
        }
        return MobData.writeIfAbsent(mob, NAMESPACE, tag);
    }
}
