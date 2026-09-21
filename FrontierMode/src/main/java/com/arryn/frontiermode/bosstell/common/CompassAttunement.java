package com.arryn.frontiermode.bosstell.common;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Thin managed wrapper around the compass item's own NBT access -- the item-carried half of
 * Attunement, per
 * wiki/frontiermode/architecture/special-compass.md#the-item-nbt-attunement-wrapper. Grouped
 * under one nested compound tag rather than flat top-level keys, so it can't collide with
 * vanilla's own {@code LodestonePos}/{@code LodestoneDimension}/{@code LodestoneTracked} fields
 * on the same stack (see {@code AmethystCompassItem}, which writes those directly).
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

    private static final String ROOT_TAG = "frontiermode_compass_attunement";
    private static final String KEY_BOSS_ID = "bossId";
    private static final String KEY_FROZEN_POS = "frozenPos";
    private static final String KEY_ADMIN = "admin";
    private static final String KEY_OWNER_ID = "ownerId";
    private static final String KEY_LOCKED_ENCHANT_LEVEL = "lockedEnchantLevel";

    private CompassAttunement() {
    }

    /**
     * Sets (or replaces) this stack's attunement to a live boss reference. {@code admin}
     * distinguishes the Admin Compass variant (command-constructed, never craftable) from the
     * player-facing Amethyst Compass sharing the same item class -- see special-compass.md's
     * "One shared core" section. {@code ownerId} is the player this stack is bound to for the
     * ownership rule -- "blocked, never destroyed" for anyone else.
     */
    public static void setLive(ItemStack stack, UUID bossId, UUID ownerId, boolean admin) {
        Objects.requireNonNull(bossId, "bossId");
        Objects.requireNonNull(ownerId, "ownerId");
        CompoundTag root = new CompoundTag();
        root.putUUID(KEY_BOSS_ID, bossId);
        root.putUUID(KEY_OWNER_ID, ownerId);
        root.putBoolean(KEY_ADMIN, admin);
        preserveLockedEnchantLevel(stack, root);
        stack.getOrCreateTag().put(ROOT_TAG, root);
    }

    /**
     * FRO_121: sets (or replaces) this stack's attunement to a frozen raw position -- the
     * bedrock pad (Tier 1) and Intuition (Tiers 2-4) both resolve to a stored coordinate once
     * and never touch a boss reference again afterward, per special-compass.md's "One shared
     * core" table. Never {@code admin} -- the Admin Compass is always the live case.
     */
    public static void setFrozen(ItemStack stack, BlockPos pos, UUID ownerId) {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(ownerId, "ownerId");
        CompoundTag root = new CompoundTag();
        root.putIntArray(KEY_FROZEN_POS, new int[]{pos.getX(), pos.getY(), pos.getZ()});
        root.putUUID(KEY_OWNER_ID, ownerId);
        root.putBoolean(KEY_ADMIN, false);
        preserveLockedEnchantLevel(stack, root);
        stack.getOrCreateTag().put(ROOT_TAG, root);
    }

    /** Carries the old locked-enchant-level forward across a re-lock -- see class doc. */
    private static void preserveLockedEnchantLevel(ItemStack stack, CompoundTag newRoot) {
        CompoundTag old = root(stack);
        if (old != null && old.contains(KEY_LOCKED_ENCHANT_LEVEL)) {
            newRoot.putInt(KEY_LOCKED_ENCHANT_LEVEL, old.getInt(KEY_LOCKED_ENCHANT_LEVEL));
        }
    }

    /**
     * The player this stack is bound to -- {@link Optional#empty()} for a stack with no
     * attunement at all (never for an attuned one; {@link #setLive}/{@link #setFrozen} always
     * write it).
     */
    public static Optional<UUID> ownerId(ItemStack stack) {
        CompoundTag root = root(stack);
        if (root == null || !root.hasUUID(KEY_OWNER_ID)) {
            return Optional.empty();
        }
        return Optional.of(root.getUUID(KEY_OWNER_ID));
    }

    /** Empty unless this stack is in the live case. */
    public static Optional<UUID> bossId(ItemStack stack) {
        CompoundTag root = root(stack);
        if (root == null || !root.hasUUID(KEY_BOSS_ID)) {
            return Optional.empty();
        }
        return Optional.of(root.getUUID(KEY_BOSS_ID));
    }

    /** Empty unless this stack is in the frozen case. */
    public static Optional<BlockPos> frozenPos(ItemStack stack) {
        CompoundTag root = root(stack);
        if (root == null || !root.contains(KEY_FROZEN_POS)) {
            return Optional.empty();
        }
        int[] xyz = root.getIntArray(KEY_FROZEN_POS);
        if (xyz.length != 3) {
            return Optional.empty();
        }
        return Optional.of(new BlockPos(xyz[0], xyz[1], xyz[2]));
    }

    public static boolean isAdmin(ItemStack stack) {
        CompoundTag root = root(stack);
        return root != null && root.getBoolean(KEY_ADMIN);
    }

    public static boolean isAttuned(ItemStack stack) {
        return root(stack) != null;
    }

    /** 0 if never locked against any enchant level yet. */
    public static int lockedEnchantLevel(ItemStack stack) {
        CompoundTag root = root(stack);
        return root == null ? 0 : root.getInt(KEY_LOCKED_ENCHANT_LEVEL);
    }

    /**
     * Updates just the locked-enchant-level bookkeeping in place, without touching the live/
     * frozen target itself -- the detection mechanism's own write, separate from actually
     * re-rolling/re-locking the target (that's the caller's job, via {@link #setLive}/
     * {@link #setFrozen} immediately after reading the new level off the stack).
     */
    public static void setLockedEnchantLevel(ItemStack stack, int level) {
        CompoundTag root = root(stack);
        if (root == null) {
            return;
        }
        root.putInt(KEY_LOCKED_ENCHANT_LEVEL, level);
    }

    private static CompoundTag root(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(ROOT_TAG)) {
            return null;
        }
        return tag.getCompound(ROOT_TAG);
    }
}
