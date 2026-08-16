package com.arryn.frontiermode.border.server.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.suggestion.Suggestions;

import net.minecraft.commands.CommandSourceStack;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class BorderSelectorArgumentType
        implements ArgumentType<BorderSelectorResult> {

    public static BorderSelectorArgumentType selector() {
        return new BorderSelectorArgumentType();
    }

    @Override
    public BorderSelectorResult parse(StringReader reader)
            throws CommandSyntaxException {
        return BorderSelector.parse(reader);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(
            CommandContext<S> context,
            SuggestionsBuilder builder) {

        builder.suggest("@containing");
        builder.suggest("@coord");
        builder.suggest("@all");
        builder.suggest("@relevant");
        // RM_FRO_011: @none is a real mode BorderSelector.parse() accepts (resolves to an empty
        // list unconditionally) but never showed up in tab-complete -- the one selector mode
        // that never appeared here.
        builder.suggest("@none");

        return builder.buildFuture();
    }

    @Override
    public Collection<String> getExamples() {
        return List.of(
                "0",
                "@containing",
                "@coord 100 64 100",
                "@all"
        );
    }

    public static BorderSelectorResult getSelector(
            CommandContext<CommandSourceStack> ctx,
            String name) {
        return ctx.getArgument(name, BorderSelectorResult.class);
    }
}
