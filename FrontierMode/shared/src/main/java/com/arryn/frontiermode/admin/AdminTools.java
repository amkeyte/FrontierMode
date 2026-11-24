package com.arryn.frontiermode.admin;

import com.arryn.satchel.common.util.StackData;
import net.minecraft.world.item.ItemStack;

/**
 * "Is this an admin tool": the stack carries any admin enchantment. The one check the soulbound paths
 * (the toss cancel and the death-drop stash) share, so a new tool is soulbound by adding its
 * enchantment here, not by adding a path. See wiki admin-tools.md, "Soulbound".
 */
public final class AdminTools {

    private AdminTools() {
    }

    public static boolean isAdminTool(ItemStack stack) {
        return StackData.enchantmentLevel(stack, AdminToolsRegisters.DEATH_TOUCH) > 0
                || StackData.enchantmentLevel(stack, AdminToolsRegisters.INSPECT) > 0;
    }
}
