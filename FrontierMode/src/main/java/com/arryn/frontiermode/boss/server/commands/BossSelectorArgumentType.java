package com.arryn.frontiermode.boss.server.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Brigadier {@link ArgumentType} for {@code /boss}'s selector chain -- mirrors
 * {@code BorderSelectorArgumentType} exactly.
 */
public final class BossSelectorArgumentType implements ArgumentType<BossSelectorResult> {

    public static BossSelectorArgumentType selector() {
        return new BossSelectorArgumentType();
    }

    @Override
    public BossSelectorResult parse(StringReader reader) throws CommandSyntaxException {
        return BossSelector.parse(reader);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(
            CommandContext<S> context,
            SuggestionsBuilder builder) {

        builder.suggest("@nearest");
        builder.suggest("@all");
        builder.suggest("@none");

        return builder.buildFuture();
    }

    @Override
    public Collection<String> getExamples() {
        return List.of("0", "@nearest", "@all", "@none");
    }

    public static BossSelectorResult getSelector(
            CommandContext<CommandSourceStack> ctx,
            String name) {
        return ctx.getArgument(name, BossSelectorResult.class);
    }
}
