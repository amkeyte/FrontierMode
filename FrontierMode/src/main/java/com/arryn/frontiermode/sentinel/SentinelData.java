package com.arryn.frontiermode.sentinel;

import com.arryn.satchel.common.util.MobData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.Optional;
import java.util.UUID;

/**
 * A Sentinel's record on the mob: the {@code frontiermode:sentinel} MobData namespace holding
 * {@code bossId} and {@code scale} (sentinels.md § The Sentinel record on the mob). "Is a Sentinel" is
 * "carries the namespace". The server's answer is this record; the client's is the synced
 * {@code SentinelFixture}, because MobData is server-only. Module code touches the mob's data only
 * through {@link MobData}.
 *
 * <p>Written once, before the mob joins the level, with {@code writeIfAbsent}: the first conversion
 * wins. The writer then calls {@code mob.refreshDimensions()}, because {@code EntityEvent.Size} fires
 * inside the {@code Entity} constructor, before MobData's container is attached.
 */
public final class SentinelData {

    public static final ResourceLocation NAMESPACE = new ResourceLocation("frontiermode", "sentinel");

    private static final String KEY_BOSS_ID = "bossId";
    private static final String KEY_SCALE = "scale";

    /** What the record holds. {@code bossId} never leaves the server; {@code scale} is fixed at conversion. */
    public record Facts(UUID bossId, float scale) {
    }

    private SentinelData() {
    }

    /** True if {@code entity} is a mob that carries the record. Safe before MobData's container is attached. */
    public static boolean has(Entity entity) {
        return entity instanceof Mob mob && MobData.has(mob, NAMESPACE);
    }

    /** The record on {@code mob}, if it carries one. */
    public static Optional<Facts> read(Mob mob) {
        CompoundTag tag = MobData.read(mob, NAMESPACE);
        if (!tag.hasUUID(KEY_BOSS_ID)) {
            return Optional.empty();
        }
        return Optional.of(new Facts(tag.getUUID(KEY_BOSS_ID), tag.getFloat(KEY_SCALE)));
    }

    /** The Sentinel's size multiplier, or {@code 1.0} for an entity that carries no record. */
    public static float scaleOf(Entity entity) {
        if (!has(entity)) {
            return 1.0F;
        }
        return MobData.read((Mob) entity, NAMESPACE).getFloat(KEY_SCALE);
    }

    /** Writes the record unless {@code mob} already carries one; {@code true} if it wrote. */
    public static boolean writeIfAbsent(Mob mob, UUID bossId, float scale) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(KEY_BOSS_ID, bossId);
        tag.putFloat(KEY_SCALE, scale);
        return MobData.writeIfAbsent(mob, NAMESPACE, tag);
    }
}
