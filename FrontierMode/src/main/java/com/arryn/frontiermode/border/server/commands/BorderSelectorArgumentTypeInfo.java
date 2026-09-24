package com.arryn.frontiermode.border.server.commands;

import com.arryn.frontiermode.FrontierMode;
import com.google.gson.JsonObject;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

public final class BorderSelectorArgumentTypeInfo
        implements ArgumentTypeInfo<
        BorderSelectorArgumentType,
        BorderSelectorArgumentTypeInfo.Template
        > {

    @Override
    public void serializeToNetwork(@NotNull Template template, @NotNull FriendlyByteBuf buffer) {}

    @Override
    public @NotNull Template deserializeFromNetwork(@NotNull FriendlyByteBuf buffer) {
        return new Template();
    }

    @Override
    public void serializeToJson(@NotNull Template template, @NotNull JsonObject json) {}

    @Override
    public @NotNull Template unpack(@NotNull BorderSelectorArgumentType type) {
        return new Template();
    }

    public static final class Template
            implements ArgumentTypeInfo.Template<BorderSelectorArgumentType> {

        @Override
        public @NotNull BorderSelectorArgumentType instantiate(@NotNull CommandBuildContext ctx) {
            return BorderSelectorArgumentType.selector();
        }

        @SuppressWarnings("unchecked")
        @Override
        public @NotNull ArgumentTypeInfo<BorderSelectorArgumentType, ?> type() {
            return (ArgumentTypeInfo<BorderSelectorArgumentType, ?>)
                    FrontierMode.BORDER_SELECTOR.get();
        }
    }
}
