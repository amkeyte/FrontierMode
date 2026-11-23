package com.arryn.frontiermode.boss.server.commands;

import com.arryn.frontiermode.FrontierMode;
import com.google.gson.JsonObject;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

/**
 * Forge registry glue for {@link BossSelectorArgumentType} -- mirrors
 * {@code BorderSelectorArgumentTypeInfo} exactly (registration lives on
 * {@code FrontierMode.BOSS_SELECTOR}).
 */
public final class BossSelectorArgumentTypeInfo
        implements ArgumentTypeInfo<BossSelectorArgumentType, BossSelectorArgumentTypeInfo.Template> {

    @Override
    public void serializeToNetwork(@NotNull Template template, @NotNull FriendlyByteBuf buffer) {
    }

    @Override
    public @NotNull Template deserializeFromNetwork(@NotNull FriendlyByteBuf buffer) {
        return new Template();
    }

    @Override
    public void serializeToJson(@NotNull Template template, @NotNull JsonObject json) {
    }

    @Override
    public @NotNull Template unpack(@NotNull BossSelectorArgumentType type) {
        return new Template();
    }

    public static final class Template
            implements ArgumentTypeInfo.Template<BossSelectorArgumentType> {

        @Override
        public @NotNull BossSelectorArgumentType instantiate(@NotNull CommandBuildContext ctx) {
            return BossSelectorArgumentType.selector();
        }

        @SuppressWarnings("unchecked")
        @Override
        public @NotNull ArgumentTypeInfo<BossSelectorArgumentType, ?> type() {
            return (ArgumentTypeInfo<BossSelectorArgumentType, ?>)
                    FrontierMode.BOSS_SELECTOR.get();
        }
    }
}
