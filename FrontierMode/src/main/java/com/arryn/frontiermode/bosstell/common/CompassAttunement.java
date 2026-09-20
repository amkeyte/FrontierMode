package com.arryn.frontiermode.bosstell.common;

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
 * <p>Only the "live" case is built here -- a boss UUID, per FRO_118's scope. The frozen case
 * (a raw {@code BlockPos}, for Tier 1/Intuition) is FRO_121's to add once the tier ladder is
 * built; this class's own shape (one root tag, a discriminator) is meant to grow to hold that
 * case as a sibling, not be replaced.
 */
public final class CompassAttunement {

    private static final String ROOT_TAG = "frontiermode_compass_attunement";
    private static final String KEY_BOSS_ID = "bossId";
    private static final String KEY_ADMIN = "admin";
    private static final String KEY_OWNER_ID = "ownerId";

    private CompassAttunement() {
    }

    /**
     * Sets (or replaces) this stack's attunement to a live boss reference. {@code admin}
     * distinguishes the Admin Compass variant (command-constructed, never craftable) from the
     * player-facing Amethyst Compass sharing the same item class -- see special-compass.md's
     * "One shared core" section. {@code ownerId} is the player this stack is bound to for the
     * ownership rule (FRO_118, now unblocked by SAT_072's real pickup-veto signal) --
     * "blocked, never destroyed" for anyone else, per special-compass.md's "PlayerScope fixture"
     * section.
     */
    public static void setLive(ItemStack stack, UUID bossId, UUID ownerId, boolean admin) {
        Objects.requireNonNull(bossId, "bossId");
        Objects.requireNonNull(ownerId, "ownerId");
        CompoundTag root = new CompoundTag();
        root.putUUID(KEY_BOSS_ID, bossId);
        root.putUUID(KEY_OWNER_ID, ownerId);
        root.putBoolean(KEY_ADMIN, admin);
        stack.getOrCreateTag().put(ROOT_TAG, root);
    }

    /**
     * The player this stack is bound to -- {@link Optional#empty()} for a stack with no
     * attunement at all (never for an attuned one; {@link #setLive} always writes it).
     */
    public static Optional<UUID> ownerId(ItemStack stack) {
        CompoundTag root = root(stack);
        if (root == null || !root.hasUUID(KEY_OWNER_ID)) {
            return Optional.empty();
        }
        return Optional.of(root.getUUID(KEY_OWNER_ID));
    }

    public static Optional<UUID> bossId(ItemStack stack) {
        CompoundTag root = root(stack);
        if (root == null || !root.hasUUID(KEY_BOSS_ID)) {
            return Optional.empty();
        }
        return Optional.of(root.getUUID(KEY_BOSS_ID));
    }

    public static boolean isAdmin(ItemStack stack) {
        CompoundTag root = root(stack);
        return root != null && root.getBoolean(KEY_ADMIN);
    }

    public static boolean isAttuned(ItemStack stack) {
        return root(stack) != null;
    }

    private static CompoundTag root(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(ROOT_TAG)) {
            return null;
        }
        return tag.getCompound(ROOT_TAG);
    }
}
