package com.arryn.satchel.server.commands;

import com.arryn.satchel.common.tunable.TunableManager;
import com.arryn.satchel.common.tunable.TunableOutcome;
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

import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /satchel tunables} -- tune, reload, and save {@link TunableManager}'s registry without
 * restarting the server (SAT_090). The commands are a thin chat surface; every rule (validation,
 * what a reload discards, what a write saves) lives in {@link TunableManager}.
 *
 * <p>
 * <ul>
 *   <li>{@code tune <name>} -- the row's current value, type, range, default, and shipped value.</li>
 *   <li>{@code tune <name> <value>} -- sets the running value.</li>
 *   <li>{@code reset <name>} / {@code reset <Class>:*} -- sets one row, or one Rules class's rows,
 *   back to their default. No "every row" form, on purpose.</li>
 *   <li>{@code reload} / {@code reload <name>} / {@code reload <Class>:*} -- re-applies values from
 *   the live file: every row, one row, or one Rules class's rows.</li>
 *   <li>{@code write} -- saves the registry to the live file now.</li>
 * </ul>
 *
 * <p>
 * Tunable names carry a {@code ':'} ({@code Class:name}), which Brigadier's unquoted-string
 * argument doesn't accept, so {@code tune} takes one greedy argument and splits the name off at the
 * first space; the rest of the line is the value, so a string value may contain spaces. Names
 * autocomplete from the registry, which holds every row loaded at start.
 */
public final class TunableCommands {

    private TunableCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("satchel")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("tunables")
                                .then(Commands.literal("tune")
                                        .then(Commands.argument("nameAndValue", StringArgumentType.greedyString())
                                                .suggests(TunableCommands::suggestNames)
                                                .executes(TunableCommands::tune)))
                                .then(Commands.literal("reset")
                                        .then(Commands.argument("target", StringArgumentType.greedyString())
                                                .suggests(TunableCommands::suggestTargets)
                                                .executes(ctx -> report(ctx, TunableManager.reset(
                                                        StringArgumentType.getString(ctx, "target").trim())))))
                                .then(Commands.literal("reload")
                                        .executes(ctx -> report(ctx, TunableManager.reload(null)))
                                        .then(Commands.argument("target", StringArgumentType.greedyString())
                                                .suggests(TunableCommands::suggestTargets)
                                                .executes(ctx -> report(ctx, TunableManager.reload(
                                                        StringArgumentType.getString(ctx, "target").trim())))))
                                .then(Commands.literal("write")
                                        .executes(ctx -> report(ctx, TunableManager.write())))
                        )
        );
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static int tune(CommandContext<CommandSourceStack> ctx) {
        String input = StringArgumentType.getString(ctx, "nameAndValue").strip();
        int space = input.indexOf(' ');
        if (space < 0) {
            return report(ctx, TunableManager.describe(input));
        }
        String name = input.substring(0, space);
        String value = input.substring(space + 1).stripLeading();
        return report(ctx, TunableManager.tune(name, value));
    }

    private static int report(CommandContext<CommandSourceStack> ctx, TunableOutcome outcome) {
        Component line = Component.literal("[Tunables] " + outcome.message());
        if (outcome.isOk()) {
            ctx.getSource().sendSuccess(() -> line, false);
            return 1;
        }
        ctx.getSource().sendFailure(line);
        return 0;
    }

    /** Names only while the name is still being typed; nothing once the value has started. */
    private static CompletableFuture<Suggestions> suggestNames(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        if (builder.getRemaining().contains(" ")) {
            return builder.buildFuture();
        }
        return SharedSuggestionProvider.suggest(TunableManager.names(), builder);
    }

    /** Every name, plus a {@code Class:*} entry per class that owns rows. */
    private static CompletableFuture<Suggestions> suggestTargets(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        Set<String> targets = new TreeSet<>(TunableManager.names());
        for (String name : TunableManager.names()) {
            int colon = name.indexOf(':');
            if (colon > 0) targets.add(name.substring(0, colon) + ":*");
        }
        return SharedSuggestionProvider.suggest(targets, builder);
    }
}
