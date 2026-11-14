package com.arryn.frontiermode.boss;

import com.arryn.satchel.common.platform.PlatformBound;
import com.arryn.satchel.common.util.MobData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

import java.util.Optional;

/**
 * What a boss's body carries: the {@code frontiermode:boss} MobData namespace. It holds the damage tally,
 * {@code playerDamage}, the health players' own hits have removed from this body less what has healed back (boss.md §
 * What counts as a defeat), and the armor reading the body is held to, {@code armorCapability} and
 * {@code armorMultiplier} (boss.md § The ArmorAnswer ability). They are the durable copies of the working copies on
 * {@link Boss}: written whenever a working copy changes and read back when the body attaches, so a chunk unload, a
 * quit and a restart keep them. They are saved with the body and die with it, so no record outside the body can go
 * stale. A new body has no namespace, and a body with none reads as a zero tally and no reading.
 *
 * <p>Each writer reads the namespace, changes its own keys and writes it back, so a write of one keeps the other.
 *
 * <p>Server-only: MobData is not synced. This class is the one place the tag is touched, so {@code Boss},
 * {@code Recovery} and {@code ArmorAnswer} see numbers and a record, never a tag.
 */
@PlatformBound(reason = "mob data namespace")
public final class BossData {

    public static final ResourceLocation NAMESPACE = new ResourceLocation("frontiermode", "boss");

    private static final String KEY_PLAYER_DAMAGE = "playerDamage";
    private static final String KEY_ARMOR_CAPABILITY = "armorCapability";
    private static final String KEY_ARMOR_MULTIPLIER = "armorMultiplier";

    private BossData() {
    }

    /** The tally saved with {@code mob}, or zero for a body that carries none. */
    public static double playerDamage(Mob mob) {
        return MobData.read(mob, NAMESPACE).getDouble(KEY_PLAYER_DAMAGE);
    }

    /** Saves the tally with {@code mob}, replacing the tally it held and keeping the namespace's other keys. */
    public static void writePlayerDamage(Mob mob, double playerDamage) {
        CompoundTag tag = MobData.read(mob, NAMESPACE);
        tag.putDouble(KEY_PLAYER_DAMAGE, playerDamage);
        MobData.write(mob, NAMESPACE, tag);
    }

    /** The armor reading saved with {@code mob}, empty for a body that holds no multiplier. */
    public static Optional<ArmorReading> readArmorReading(Mob mob) {
        CompoundTag tag = MobData.read(mob, NAMESPACE);
        if (!tag.contains(KEY_ARMOR_MULTIPLIER)) {
            return Optional.empty();
        }
        return Optional.of(new ArmorReading(tag.getDouble(KEY_ARMOR_CAPABILITY), tag.getDouble(KEY_ARMOR_MULTIPLIER)));
    }

    /** Saves the armor reading with {@code mob}, replacing the reading it held and keeping the namespace's other keys. */
    public static void writeArmorReading(Mob mob, ArmorReading reading) {
        CompoundTag tag = MobData.read(mob, NAMESPACE);
        tag.putDouble(KEY_ARMOR_CAPABILITY, reading.capability());
        tag.putDouble(KEY_ARMOR_MULTIPLIER, reading.multiplier());
        MobData.write(mob, NAMESPACE, tag);
    }

    /** Takes the armor reading off {@code mob}, keeping the namespace's other keys. */
    public static void clearArmorReading(Mob mob) {
        CompoundTag tag = MobData.read(mob, NAMESPACE);
        tag.remove(KEY_ARMOR_CAPABILITY);
        tag.remove(KEY_ARMOR_MULTIPLIER);
        MobData.write(mob, NAMESPACE, tag);
    }
}
