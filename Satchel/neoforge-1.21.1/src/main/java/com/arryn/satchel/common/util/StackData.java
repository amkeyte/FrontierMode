package com.arryn.satchel.common.util;

import com.arryn.satchel.common.platform.PlatformBound;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The only place mod code reads or writes data on an {@link ItemStack} (SAT_088). On 1.21 a stack
 * carries typed data components in place of a tag, and this class is where that difference lives.
 *
 * <p>
 * Two kinds of data, two shapes of method:
 * <ul>
 *   <li><b>A mod's own data</b> -- {@link #read}/{@link #write}, one {@link CompoundTag} per
 *   namespace, each a sub-compound of the stack's {@code CUSTOM_DATA} component.</li>
 *   <li><b>Vanilla-owned data</b> -- enchantments, the lodestone target, the custom name, each its
 *   own component ({@code ENCHANTMENTS}, {@code LODESTONE_TRACKER}, {@code CUSTOM_NAME}), each with a
 *   named, typed accessor, never a generic getter. Further vanilla-owned data gets a named method
 *   here when a caller needs one.</li>
 * </ul>
 *
 * <p>
 * Callers never receive a tag that is live on the stack, and never see where anything is stored.
 * Domain meaning (key names, typed accessors) stays in the calling module; Satchel knows nothing
 * about what a namespace holds.
 *
 * <p>
 * <b>Namespaces are {@link ResourceLocation}s, not strings,</b> as on the Forge 1.20.1 line, so a
 * namespace key always carries a {@code ':'}.
 *
 * <p>
 * Enchantments are data-driven registry entries on 1.21: a caller holds a {@code Holder<Enchantment>},
 * got from a registry reached through the level's {@code RegistryAccess}.
 *
 * <p>
 * Side-agnostic: client code reads stacks too. Pure static utility -- no fixture, no bundle, no
 * {@code JigConfig}.
 */
@PlatformBound(reason = "stack data namespace")
public final class StackData {

    private StackData() {
    }

    /* =============================================================
     * A mod's own data
     * ========================================================== */

    /**
     * A copy of {@code namespace}'s compound on {@code stack}, or a fresh empty compound if it has
     * none. Never the live tag: changing the result changes nothing until it is passed to
     * {@link #write}. Adds nothing to the stack.
     */
    public static CompoundTag read(ItemStack stack, ResourceLocation namespace) {
        Objects.requireNonNull(stack, "stack");
        String key = key(namespace);
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).getUnsafe();
        if (!tag.contains(key, Tag.TAG_COMPOUND)) {
            return new CompoundTag();
        }
        return tag.getCompound(key).copy();
    }

    /**
     * Replaces {@code namespace}'s compound on {@code stack} with a copy of {@code compound}. An
     * empty compound removes the namespace, and the custom-data component goes with it if nothing
     * else is left in it -- a stack left holding an empty component no longer stacks with a fresh one.
     */
    public static void write(ItemStack stack, ResourceLocation namespace, CompoundTag compound) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(compound, "compound");
        String key = key(namespace);
        if (compound.isEmpty()) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(key));
            return;
        }
        CompoundTag copy = compound.copy();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(key, copy));
    }

    private static String key(ResourceLocation namespace) {
        return Objects.requireNonNull(namespace, "namespace").toString();
    }

    /* =============================================================
     * Vanilla-owned: lodestone target (LODESTONE_TRACKER)
     * ========================================================== */

    /**
     * The vanilla lodestone target of a compass stack, or empty if it has none. A
     * {@link GlobalPos} rather than a {@code BlockPos}, because vanilla's compass needle takes one
     * (it only points when the target is in the viewer's dimension). Adds nothing to the stack.
     */
    public static Optional<GlobalPos> lodestonePosition(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
        return tracker == null ? Optional.empty() : tracker.target();
    }

    /**
     * Writes the vanilla lodestone target. {@code tracked} is vanilla's own flag: when
     * {@code true}, vanilla clears the target once the block there is no longer a lodestone;
     * when {@code false}, the target stands regardless of what is at it.
     */
    public static void writeLodestone(ItemStack stack, GlobalPos target, boolean tracked) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(target, "target");
        stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(target), tracked));
    }

    /* =============================================================
     * Vanilla-owned: enchantments (ENCHANTMENTS, STORED_ENCHANTMENTS on a book)
     * ========================================================== */

    /** The stack's level of {@code enchantment}, 0 if absent. */
    public static int enchantmentLevel(ItemStack stack, Holder<Enchantment> enchantment) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(enchantment, "enchantment");
        return EnchantmentHelper.getTagEnchantmentLevel(enchantment, stack);
    }

    /**
     * Every enchantment on the stack with its level, as a fresh map the caller may change freely. On
     * an enchanted book, its stored enchantments.
     */
    public static Map<Holder<Enchantment>, Integer> enchantments(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        Map<Holder<Enchantment>, Integer> result = new HashMap<>();
        EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet()
                .forEach(e -> result.put(e.getKey(), e.getIntValue()));
        return result;
    }

    /**
     * Replaces the stack's whole enchantment list with {@code enchantments}; an empty map removes
     * every enchantment. On an enchanted book it replaces the stored enchantments.
     */
    public static void setEnchantments(ItemStack stack, Map<Holder<Enchantment>, Integer> enchantments) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(enchantments, "enchantments");
        ItemEnchantments.Mutable list = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.forEach(list::set);
        EnchantmentHelper.setEnchantments(stack, list.toImmutable());
    }

    /**
     * Sets {@code enchantment} on the stack to {@code level}, replacing any level it already has.
     *
     * @throws IllegalArgumentException if {@code level} is below 1 -- use
     *                                  {@link #setEnchantments} to remove one.
     */
    public static void enchant(ItemStack stack, Holder<Enchantment> enchantment, int level) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(enchantment, "enchantment");
        if (level < 1) {
            throw new IllegalArgumentException("level must be >= 1, was " + level);
        }
        EnchantmentHelper.updateEnchantments(stack, list -> list.set(enchantment, level));
    }

    /* =============================================================
     * Vanilla-owned: custom name (CUSTOM_NAME)
     * ========================================================== */

    /** Sets the stack's display name. */
    public static void setCustomName(ItemStack stack, Component name) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(name, "name");
        stack.set(DataComponents.CUSTOM_NAME, name);
    }
}
