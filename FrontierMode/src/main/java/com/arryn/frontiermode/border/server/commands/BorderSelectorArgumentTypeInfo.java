package com.arryn.frontiermode.border.server.commands;

import com.arryn.frontiermode.FrontierMode;
import com.google.gson.JsonObject;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.network.FriendlyByteBuf;

public final class BorderSelectorArgumentTypeInfo
        implements ArgumentTypeInfo<
        BorderSelectorArgumentType,
        BorderSelectorArgumentTypeInfo.Template
        > {

    @Override
    public void serializeToNetwork(Template template, FriendlyByteBuf buffer) {}

    @Override
    public Template deserializeFromNetwork(FriendlyByteBuf buffer) {
        return new Template();
    }

    @Override
    public void serializeToJson(Template template, JsonObject json) {}

    @Override
    public Template unpack(BorderSelectorArgumentType type) {
        return new Template();
    }

    public static final class Template
            implements ArgumentTypeInfo.Template<BorderSelectorArgumentType> {

        @Override
        public BorderSelectorArgumentType instantiate(CommandBuildContext ctx) {
            return BorderSelectorArgumentType.selector();
        }

        @SuppressWarnings("unchecked")
        @Override
        public ArgumentTypeInfo<BorderSelectorArgumentType, ?> type() {
            return (ArgumentTypeInfo<BorderSelectorArgumentType, ?>)
                    FrontierMode.BORDER_SELECTOR.get();
        }
    }
}
