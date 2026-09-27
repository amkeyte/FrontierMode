package com.arryn.frontiermode.bosstell.common;

import com.arryn.frontiermode.FrontierMode;
import com.arryn.satchel.common.util.StackData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * The compass's attunement data on its item stack, per
 * wiki/frontiermode/architecture/special-compass.md#the-item-nbt-attunement-wrapper. It lives in one
 * namespaced compound ({@link #NAMESPACE}) read and written only through Satchel's {@link StackData}
 * -- nothing outside this class sees a key or a tag, so the vanilla lodestone fields
 * ({@code AmethystCompassItem} writes those through {@code StackData} too) can't collide with it.
 *
 * <p>Two cases, per special-compass.md's own table: a <b>live</b> case (a boss UUID -- the admin
 * path, and later Resonance) and a <b>frozen</b> case (a raw {@code BlockPos}, no boss
 * reference at all -- the bedrock pad and Intuition). Exactly one is set at a time; setting one
 * clears the other, since a stack is never both.
 *
 * <p>{@link #lockedEnchantLevel}/{@link #setLockedEnchantLevel} back FRO_121's detection
 * mechanism (special-compass.md's "Intuition and Resonance" section): no dedicated "just
 * enchanted" hook exists, so the item's own throttled refresh compares the stack's actual
 * current enchant level against this stored value -- a mismatch is the trigger to re-roll/
 * re-lock and update it.
 */
public final class CompassAttunement {

    private static final ResourceLocation NAMESPACE =
            ResourceLocation.fromNamespaceAndPath(FrontierMode.MODID, "compass");
    private static final String KEY_BOSS_ID = "bossId";
    private static final String KEY_FROZEN_POS = "frozenPos";
    private static final String KEY_ADMIN = "admin";
    private static final String KEY_OWNER_ID = "ownerId";
    private static final String KEY_LOCKED_ENCHANT_LEVEL = "lockedEnchantLevel";
    private static final String KEY_BOSS_LEVEL = "bossLevel";

    private CompassAttunement() {
    }

    /**
     * Sets (or replaces) this stack's attunement to a live boss reference. {@code admin}
     * distinguishes the Admin Compass variant (command-constructed, never craftable) from the
     * player-facing Amethyst Compass sharing the same item class -- see special-compass.md's
     * "One shared core" section. {@code ownerId} is the player this stack is bound to for the
     * ownership rule -- "blocked, never destroyed" for anyone else. {@code bossLevel} is the locked
     * boss's player-facing level, for the tooltip (a snapshot; a boss's layer never changes).
     */
    public static void setLive(ItemStack stack, UUID bossId, UUID ownerId, boolean admin, int bossLevel) {
        Objects.requireNonNull(bossId, "bossId");
        Objects.requireNonNull(ownerId, "ownerId");
        CompoundTag root = new CompoundTag();
        root.putUUID(KEY_BOSS_ID, bossId);
        root.putUUID(KEY_OWNER_ID, ownerId);
        root.putBoolean(KEY_ADMIN, admin);
        root.putInt(KEY_BOSS_LEVEL, bossLevel);
        preserveLockedEnchantLevel(stack, root);
        StackData.write(stack, NAMESPACE, root);
    }

    /**
     * FRO_121: sets (or replaces) this stack's attunement to a frozen raw position -- the
     * bedrock pad (Tier 1) and Intuition (Tiers 2-4) both resolve to a stored coordinate once
     * and never touch a boss reference again afterward, per special-compass.md's "One shared
     * core" table. Never {@code admin} -- the Admin Compass is always the live case.
     * {@code bossLevel} as for {@link #setLive}: the level of the boss the position was taken from.
     */
    public static void setFrozen(ItemStack stack, BlockPos pos, UUID ownerId, int bossLevel) {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(ownerId, "ownerId");
        CompoundTag root = new CompoundTag();
        root.putIntArray(KEY_FROZEN_POS, new int[]{pos.getX(), pos.getY(), pos.getZ()});
        root.putUUID(KEY_OWNER_ID, ownerId);
        root.putBoolean(KEY_ADMIN, false);
        root.putInt(KEY_BOSS_LEVEL, bossLevel);
        preserveLockedEnchantLevel(stack, root);
        StackData.write(stack, NAMESPACE, root);
    }

    /** Carries the old locked-enchant-level forward across a re-lock -- see class doc. */
    private static void preserveLockedEnchantLevel(ItemStack stack, CompoundTag newRoot) {
        CompoundTag old = StackData.read(stack, NAMESPACE);
        if (old.contains(KEY_LOCKED_ENCHANT_LEVEL)) {
            newRoot.putInt(KEY_LOCKED_ENCHANT_LEVEL, old.getInt(KEY_LOCKED_ENCHANT_LEVEL));
        }
    }

    /**
     * The player this stack is bound to -- {@link Optional#empty()} for a stack with no
     * attunement at all (never for an attuned one; {@link #setLive}/{@link #setFrozen} always
     * write it).
     */
    public static Optional<UUID> ownerId(ItemStack stack) {
        CompoundTag root = StackData.read(stack, NAMESPACE);
        return root.hasUUID(KEY_OWNER_ID) ? Optional.of(root.getUUID(KEY_OWNER_ID)) : Optional.empty();
    }

    /** Empty unless this stack is in the live case. */
    public static Optional<UUID> bossId(ItemStack stack) {
        CompoundTag root = StackData.read(stack, NAMESPACE);
        return root.hasUUID(KEY_BOSS_ID) ? Optional.of(root.getUUID(KEY_BOSS_ID)) : Optional.empty();
    }

    /** Empty unless this stack is in the frozen case. */
    public static Optional<BlockPos> frozenPos(ItemStack stack) {
        CompoundTag root = StackData.read(stack, NAMESPACE);
        if (!root.contains(KEY_FROZEN_POS)) {
            return Optional.empty();
        }
        int[] xyz = root.getIntArray(KEY_FROZEN_POS);
        if (xyz.length != 3) {
            return Optional.empty();
        }
        return Optional.of(new BlockPos(xyz[0], xyz[1], xyz[2]));
    }

    /**
     * The locked boss's level, written at attunement; empty for an unattuned stack or one attuned
     * before the level was stored.
     */
    public static OptionalInt bossLevel(ItemStack stack) {
        CompoundTag root = StackData.read(stack, NAMESPACE);
        return root.contains(KEY_BOSS_LEVEL) ? OptionalInt.of(root.getInt(KEY_BOSS_LEVEL)) : OptionalInt.empty();
    }

    public static boolean isAdmin(ItemStack stack) {
        return StackData.read(stack, NAMESPACE).getBoolean(KEY_ADMIN);
    }

    public static boolean isAttuned(ItemStack stack) {
        return !StackData.read(stack, NAMESPACE).isEmpty();
    }

    /** 0 if never locked against any enchant level yet. */
    public static int lockedEnchantLevel(ItemStack stack) {
        return StackData.read(stack, NAMESPACE).getInt(KEY_LOCKED_ENCHANT_LEVEL);
    }

    /**
     * Updates just the locked-enchant-level bookkeeping, without touching the live/frozen target
     * itself -- the detection mechanism's own write, separate from actually re-rolling/re-locking
     * the target (that's the caller's job, via {@link #setLive}/{@link #setFrozen} immediately
     * after reading the new level off the stack). A no-op on a stack with no attunement.
     */
    public static void setLockedEnchantLevel(ItemStack stack, int level) {
        CompoundTag root = StackData.read(stack, NAMESPACE);
        if (root.isEmpty()) {
            return;
        }
        root.putInt(KEY_LOCKED_ENCHANT_LEVEL, level);
        StackData.write(stack, NAMESPACE, root);
    }
}
