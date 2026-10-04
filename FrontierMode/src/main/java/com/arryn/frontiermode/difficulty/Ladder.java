package com.arryn.frontiermode.difficulty;

import com.arryn.satchel.common.util.MobData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

/**
 * The ladder's data on a mob: the {@code frontiermode:ladder} MobData namespace, holding the rung and
 * the two multipliers (difficulty.md § Where the multipliers live). Module code touches a mob's
 * stats only through {@link MobData}; nothing here reads or writes a raw attribute.
 */
final class Ladder {

    static final ResourceLocation NAMESPACE = new ResourceLocation("frontiermode", "ladder");

    static final String RUNG_ORDINARY = "ordinary";
    static final String RUNG_ALLY = "ally";
    static final String RUNG_FERAL = "feral";
    static final String RUNG_SENTINEL = "sentinel";
    static final String RUNG_KEEPER = "keeper";
    static final String RUNG_KEEPER_SUMMON = "keeper_summon";

    private static final String KEY_RUNG = "rung";
    // Which step of the ladder the mob was put on (the Difficulty, or a Keeper's path position); 0 = none.
    private static final String KEY_STEP = "n";
    private static final String KEY_HEALTH = "health";
    private static final String KEY_DAMAGE = "damage";

    private Ladder() {
    }

    /** True if some rung has already reached this mob ("the first rung to reach a mob wins"). */
    static boolean claimed(Mob mob) {
        return !MobData.read(mob, NAMESPACE).isEmpty();
    }

    /**
     * Writes {@code rung} and its multipliers if no rung has reached {@code mob}, then scales its
     * maximum health and heals it to the new maximum. {@code false} if the mob was already claimed
     * and is left alone. {@code step} is what the mob's level was read as (0 if the rung has none, like
     * Feral): kept so the Inspect Stick can show it.
     */
    static boolean apply(Mob mob, String rung, int step, double healthMultiplier, double damageMultiplier) {
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY_RUNG, rung);
        tag.putInt(KEY_STEP, step);
        tag.putDouble(KEY_HEALTH, healthMultiplier);
        tag.putDouble(KEY_DAMAGE, damageMultiplier);
        if (!MobData.writeIfAbsent(mob, NAMESPACE, tag)) {
            return false;
        }
        MobData.scaleMaxHealth(mob, NAMESPACE, healthMultiplier);
        // Vanilla setup can raise max health without healing (a leader zombie), so the mob is healed
        // to its final maximum rather than trusted to be at full already.
        mob.setHealth(mob.getMaxHealth());
        return true;
    }

    /**
     * One line for the Inspect Stick: the mob's level, its rung and its two multipliers; empty if no rung
     * has reached it.
     */
    static java.util.Optional<String> describe(Mob mob) {
        CompoundTag tag = MobData.read(mob, NAMESPACE);
        if (tag.isEmpty()) {
            return java.util.Optional.empty();
        }
        int step = tag.getInt(KEY_STEP);
        return java.util.Optional.of(String.format("%s rung%s, health x%.3f, damage x%.3f",
                tag.getString(KEY_RUNG), step > 0 ? ", level " + step : "",
                tag.getDouble(KEY_HEALTH), tag.getDouble(KEY_DAMAGE)));
    }

    /** The rung that reached {@code mob}, or {@code ""} if none has. */
    static String rungOf(Mob mob) {
        return MobData.read(mob, NAMESPACE).getString(KEY_RUNG);
    }

    static boolean hasDamageMultiplier(CompoundTag ladderData) {
        return ladderData.contains(KEY_DAMAGE, Tag.TAG_DOUBLE);
    }

    /** The damage multiplier in {@code ladderData}; check {@link #hasDamageMultiplier} first. */
    static double damageMultiplier(CompoundTag ladderData) {
        return ladderData.getDouble(KEY_DAMAGE);
    }
}
