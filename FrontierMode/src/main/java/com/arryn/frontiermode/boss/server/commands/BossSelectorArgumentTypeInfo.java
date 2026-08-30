package com.arryn.frontiermode.boss.server.commands;

import com.arryn.frontiermode.FrontierMode;
import com.google.gson.JsonObject;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Forge registry glue for {@link BossSelectorArgumentType} -- mirrors
 * {@code BorderSelectorArgumentTypeInfo} exactly (registration lives on
 * {@code FrontierMode.BOSS_SELECTOR}).
 */
public final class BossSelectorArgumentTypeInfo
        implements ArgumentTypeInfo<BossSelectorArgumentType, BossSelectorArgumentTypeInfo.Template> {

    @Override
    public void serializeToNetwork(Template template, FriendlyByteBuf buffer) {
    }

    @Override
    public Template deserializeFromNetwork(FriendlyByteBuf buffer) {
        return new Template();
    }

    @Override
    public void serializeToJson(Template template, JsonObject json) {
    }

    @Override
    public Template unpack(BossSelectorArgumentType type) {
        return new Template();
    }

    public static final class Template
            implements ArgumentTypeInfo.Template<BossSelectorArgumentType> {

        @Override
        public BossSelectorArgumentType instantiate(CommandBuildContext ctx) {
            return BossSelectorArgumentType.selector();
        }

        @SuppressWarnings("unchecked")
        @Override
        public ArgumentTypeInfo<BossSelectorArgumentType, ?> type() {
            return (ArgumentTypeInfo<BossSelectorArgumentType, ?>)
                    FrontierMode.BOSS_SELECTOR.get();
        }
    }
}
