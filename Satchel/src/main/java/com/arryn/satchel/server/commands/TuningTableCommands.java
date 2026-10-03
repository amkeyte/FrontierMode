package com.arryn.satchel.server.commands;

import com.arryn.satchel.common.tunable.TunableOutcome;
import com.arryn.satchel.common.tunable.TuningTables;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /satchel tables} -- list and reload {@link com.arryn.satchel.common.tunable.TuningTable}s
 * without restarting the server (SAT_092). The table files are the editing surface; these commands
 * only show where they are and re-read them. Every rule lives in {@link TuningTables}.
 *
 * <ul>
 *   <li>{@code list} -- every declared table, its live file, and its row count once loaded.</li>
 *   <li>{@code reload} / {@code reload <table>} -- re-reads every table, or one, from its files.</li>
 * </ul>
 *
 * <p>
 * Table ids carry a {@code ':'}, which Brigadier's unquoted-string argument doesn't accept, so the
 * table is one greedy argument, as in {@link TunableCommands}.
 */
public final class TuningTableCommands {

    private TuningTableCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("satchel")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("tables")
                                .then(Commands.literal("list")
                                        .executes(TuningTableCommands::list))
                                .then(Commands.literal("reload")
                                        .executes(ctx -> report(ctx, TuningTables.reload(null)))
                                        .then(Commands.argument("table", StringArgumentType.greedyString())
                                                .suggests(TuningTableCommands::suggestTables)
                                                .executes(ctx -> report(ctx, TuningTables.reload(
                                                        StringArgumentType.getString(ctx, "table").trim())))))
                        )
        );
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        List<String> lines = TuningTables.describe();
        if (lines.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("[TuningTables] No tuning tables are declared."), false);
            return 0;
        }
        for (String line : lines) {
            ctx.getSource().sendSuccess(() -> Component.literal("[TuningTables] " + line), false);
        }
        return lines.size();
    }

    private static int report(CommandContext<CommandSourceStack> ctx, TunableOutcome outcome) {
        Component line = Component.literal("[TuningTables] " + outcome.message());
        if (outcome.isOk()) {
            ctx.getSource().sendSuccess(() -> line, false);
            return 1;
        }
        ctx.getSource().sendFailure(line);
        return 0;
    }

    private static CompletableFuture<Suggestions> suggestTables(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(TuningTables.names(), builder);
    }
}
