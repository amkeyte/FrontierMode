package com.arryn.frontiermode.feral.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Brigadier registration for {@code /feral} -- currently just the one debug test aid
 * ({@code FeralCommandHandler.spawnTestHostiles}), mirroring {@code BorderCommands}'/
 * {@code BossCommands}' registration split. A separate command tree from {@code /border}/
 * {@code /boss} on purpose, same containment reasoning as {@code FeralModule}'s own doc.
 */
public final class FeralCommands {

    private FeralCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("feral")
                        .requires(src -> src.hasPermission(2))
                        .then(debug())
        );
    }

    private static ArgumentBuilder<CommandSourceStack, ?> debug() {
        return Commands.literal("debug")
                // /feral debug spawn [count] -- same real-FinalizeSpawn shape as
                // /border debug spawn-animal, exercising Feral's own branch on demand.
                .then(Commands.literal("spawn")
                        .executes(ctx -> FeralCommandHandler.spawnTestHostiles(ctx, 10))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 200))
                                .executes(ctx -> FeralCommandHandler.spawnTestHostiles(
                                        ctx, IntegerArgumentType.getInteger(ctx, "count")))
                        )
                );
    }
}
