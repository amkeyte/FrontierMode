package com.arryn.satchel.common.util;

import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The only place mod code reads or writes data on an {@link ItemStack} (SAT_088). Stack NBT is
 * replaced by data components in 1.20.5, so every raw {@code getTag}/{@code getOrCreateTag}/
 * {@code setTag} call is a rewrite at the port; routing them all through here makes the port one
 * class.
 *
 * <p>
 * Two kinds of data, two shapes of method:
 * <ul>
 *   <li><b>A mod's own data</b> -- {@link #read}/{@link #write}, one {@link CompoundTag} per
 *   namespace. The port target is the {@code CUSTOM_DATA} component, which still wraps a single
 *   {@code CompoundTag}, so every namespace becomes a sub-compound of it and these two methods
 *   survive nearly unchanged.</li>
 *   <li><b>Vanilla-owned data</b> -- enchantments, the lodestone target, the custom name. Its
 *   shape changes at the port ({@code ENCHANTMENTS}, {@code LODESTONE_TRACKER},
 *   {@code CUSTOM_NAME} components), so each gets a named, typed accessor, never a generic tag
 *   getter. Further vanilla-owned data gets a named method here when a caller needs one.</li>
 * </ul>
 *
 * <p>
 * Callers never receive a tag that is live on the stack, and never see where anything is stored.
 * Domain meaning (key names, typed accessors) stays in the calling module; Satchel knows nothing
 * about what a namespace holds.
 *
 * <p>
 * <b>Namespaces are {@link ResourceLocation}s, not strings.</b> In 1.20.1 every namespace
 * compound sits in the stack's root tag beside vanilla's own keys ({@code display},
 * {@code Enchantments}, {@code LodestonePos}, {@code Damage}); a bare-string namespace could
 * collide with one and silently overwrite vanilla data. A {@code ResourceLocation} always
 * carries a {@code ':'}, which no vanilla root key does.
 *
 * <p>
 * Enchantment <em>classes</em> become data-driven registry entries in 1.21, a porting job of
 * their own that this class can't hide; it only consolidates the stack-side reads and writes.
 *
 * <p>
 * Side-agnostic: client code reads stacks too. Pure static utility -- no fixture, no bundle, no
 * {@code JigConfig}.
 */
public final class StackData {

    private StackData() {
    }

    /* =============================================================
     * A mod's own data
     * ========================================================== */

    /**
     * A copy of {@code namespace}'s compound on {@code stack}, or a fresh empty compound if it has
     * none. Never the live tag: changing the result changes nothing until it is passed to
     * {@link #write}. Creates no tag on the stack.
     */
    public static CompoundTag read(ItemStack stack, ResourceLocation namespace) {
        Objects.requireNonNull(stack, "stack");
        String key = key(namespace);
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(key, Tag.TAG_COMPOUND)) {
            return new CompoundTag();
        }
        return tag.getCompound(key).copy();
    }

    /**
     * Replaces {@code namespace}'s compound on {@code stack} with a copy of {@code compound}. An
     * empty compound removes the namespace, and drops the stack's tag entirely if nothing else is
     * left on it -- a stack left holding an empty tag no longer stacks with a fresh one.
     */
    public static void write(ItemStack stack, ResourceLocation namespace, CompoundTag compound) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(compound, "compound");
        String key = key(namespace);
        if (compound.isEmpty()) {
            stack.removeTagKey(key);
            return;
        }
        stack.getOrCreateTag().put(key, compound.copy());
    }

    private static String key(ResourceLocation namespace) {
        return Objects.requireNonNull(namespace, "namespace").toString();
    }

    /* =============================================================
     * Vanilla-owned: lodestone target (1.20.5: LODESTONE_TRACKER)
     * ========================================================== */

    /**
     * The vanilla lodestone target of a compass stack, or empty if it has none. A
     * {@link GlobalPos} rather than a {@code BlockPos}, because vanilla's compass needle takes one
     * (it only points when the target is in the viewer's dimension). Creates no tag on the stack.
     */
    public static Optional<GlobalPos> lodestonePosition(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(CompassItem.getLodestonePosition(tag));
    }

    /**
     * Writes the vanilla lodestone target. {@code tracked} is vanilla's own flag: when
     * {@code true}, vanilla clears the target once the block there is no longer a lodestone;
     * when {@code false}, the target stands regardless of what is at it.
     */
    public static void writeLodestone(ItemStack stack, GlobalPos target, boolean tracked) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(target, "target");
        CompoundTag tag = stack.getOrCreateTag();
        tag.put(CompassItem.TAG_LODESTONE_POS, NbtUtils.writeBlockPos(target.pos()));
        Level.RESOURCE_KEY_CODEC.encodeStart(NbtOps.INSTANCE, target.dimension())
                .resultOrPartial(OUT::warn)
                .ifPresent(encoded -> tag.put(CompassItem.TAG_LODESTONE_DIMENSION, encoded));
        tag.putBoolean(CompassItem.TAG_LODESTONE_TRACKED, tracked);
    }

    /* =============================================================
     * Vanilla-owned: enchantments (1.20.5: ENCHANTMENTS)
     * ========================================================== */

    /** The stack's level of {@code enchantment}, 0 if absent. */
    public static int enchantmentLevel(ItemStack stack, Enchantment enchantment) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(enchantment, "enchantment");
        return EnchantmentHelper.getTagEnchantmentLevel(enchantment, stack);
    }

    /**
     * Every enchantment on the stack with its level, as a fresh map the caller may change freely.
     * Two raw entries for the same enchantment collapse to one key here.
     */
    public static Map<Enchantment, Integer> enchantments(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        return EnchantmentHelper.getEnchantments(stack);
    }

    /**
     * Replaces the stack's whole enchantment list with {@code enchantments}; an empty map removes
     * every enchantment. Rebuilding the list clears any duplicate raw entries. On an enchanted
     * book this is vanilla's behavior: it adds to the book's stored enchantments and never removes
     * one.
     */
    public static void setEnchantments(ItemStack stack, Map<Enchantment, Integer> enchantments) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(enchantments, "enchantments");
        EnchantmentHelper.setEnchantments(enchantments, stack);
    }

    /**
     * Sets {@code enchantment} on the stack to {@code level}, replacing any level it already has.
     * Deliberately not vanilla's {@code ItemStack#enchant}, which appends a new entry on every
     * call and so leaves duplicates behind; this is the set-semantics 1.20.5's enchantment
     * component has.
     *
     * @throws IllegalArgumentException if {@code level} is below 1 -- use
     *                                  {@link #setEnchantments} to remove one.
     */
    public static void enchant(ItemStack stack, Enchantment enchantment, int level) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(enchantment, "enchantment");
        if (level < 1) {
            throw new IllegalArgumentException("level must be >= 1, was " + level);
        }
        Map<Enchantment, Integer> current = EnchantmentHelper.getEnchantments(stack);
        current.put(enchantment, level);
        EnchantmentHelper.setEnchantments(current, stack);
    }

    /* =============================================================
     * Vanilla-owned: custom name (1.20.5: CUSTOM_NAME)
     * ========================================================== */

    /** Sets the stack's display name. */
    public static void setCustomName(ItemStack stack, Component name) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(name, "name");
        stack.setHoverName(name);
    }
}
