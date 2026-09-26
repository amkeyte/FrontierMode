package com.arryn.frontiermode.admin.server.commands;

import com.arryn.frontiermode.admin.AdminToolsModule;
import com.arryn.satchel.common.util.StackData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * {@code /admin}: hands the caller an admin tool. Each is a plain vanilla stick carrying an
 * admin-only enchantment, named so it reads clearly as the tool it is -- direct construction, never
 * through the recipe book or a creative tab. Permission level 2.
 */
public final class AdminCommands {

    private AdminCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("admin")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("deathstick")
                                .executes(ctx -> giveStick(ctx, AdminToolsModule.DEATH_TOUCH.get(), "Death Stick")))
                        .then(Commands.literal("inspectstick")
                                .executes(ctx -> giveStick(ctx, AdminToolsModule.INSPECT.get(), "Inspect Stick")))
        );
    }

    private static int giveStick(CommandContext<CommandSourceStack> ctx, Enchantment enchantment, String name)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        ItemStack stick = new ItemStack(Items.STICK);
        StackData.enchant(stick, enchantment, 1);
        StackData.setCustomName(stick, Component.literal(name));

        if (!player.getInventory().add(stick)) {
            player.drop(stick, false);
        }

        ctx.getSource().sendSuccess(() -> Component.literal("[Admin] Gave a " + name + "."), false);
        return 1;
    }
}
