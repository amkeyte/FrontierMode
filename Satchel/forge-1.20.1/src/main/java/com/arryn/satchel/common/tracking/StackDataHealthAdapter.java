package com.arryn.satchel.common.tracking;

import com.arryn.satchel.common.platform.PlatformBound;
import com.arryn.satchel.common.util.StackData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.Map;

/**
 * This line's half of {@link StackDataHealthCheck}: its {@link StackData} on an {@link ItemStack}, called as the check
 * asks and holding no expectation of its own. The check is shared.
 */
@PlatformBound(reason = "stack data namespace")
public final class StackDataHealthAdapter {

    private StackDataHealthAdapter() {}

    public static void register() {
        StackDataHealthCheck.register(new LineStacks());
    }

    static final class LineStacks implements StackDataHealthCheck.Stacks<ItemStack> {
        @Override
        public ItemStack newStack() {
            return new ItemStack(Items.STICK);
        }

        @Override
        public int level(ItemStack stack, ResourceLocation enchantment) {
            return StackData.enchantmentLevel(stack, enchantment);
        }

        @Override
        public Map<ResourceLocation, Integer> all(ItemStack stack) {
            return StackData.enchantmentsById(stack);
        }

        @Override
        public void enchant(ItemStack stack, Level level, ResourceLocation enchantment, int enchantLevel) {
            StackData.enchant(stack, level, enchantment, enchantLevel);
        }

        @Override
        public void replace(ItemStack stack, Level level, Map<ResourceLocation, Integer> enchantments) {
            StackData.setEnchantmentsById(stack, level, enchantments);
        }
    }
}
