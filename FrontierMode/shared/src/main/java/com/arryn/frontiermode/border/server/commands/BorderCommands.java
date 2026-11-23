package com.arryn.frontiermode.border.server.commands;

import com.arryn.frontiermode.border.common.fixture.Border;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.UUID;

public final class BorderCommands {

    private BorderCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("border")
                        .requires(src -> src.hasPermission(2))
                        .then(add())
                        .then(delete())
                        .then(info())
                        .then(debug())
        );
    }

    // ------------------------------------------------------------
    // ADD (unchanged – no selector involved)
    // ------------------------------------------------------------

    private static ArgumentBuilder<CommandSourceStack, ?> add() {
        return Commands.literal("add")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                                .then(Commands.argument("layer", IntegerArgumentType.integer(0))
                                        .executes(ctx -> {
                                            BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
                                            int r = IntegerArgumentType.getInteger(ctx, "radius");
                                            int l = IntegerArgumentType.getInteger(ctx, "layer");
                                            return BorderCommandHandler.addExplicit(ctx, pos, r, l);
                                        })
                                )
                        )
                )
                .then(Commands.literal("here")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                                .then(Commands.argument("layer", IntegerArgumentType.integer(0))
                                        .executes(ctx -> {
                                            int r = IntegerArgumentType.getInteger(ctx, "radius");
                                            int l = IntegerArgumentType.getInteger(ctx, "layer");
                                            return BorderCommandHandler.addHere(ctx, r, l);
                                        })
                                )
                        )
                );
    }

    // ------------------------------------------------------------
    // Shared selector application (unchanged)
    // ------------------------------------------------------------

    private static int applySelector(
            CommandContext<CommandSourceStack> ctx,
            BorderSelectorResult selector,
            BorderOperator op
    ) throws CommandSyntaxException {

        Player sp = ctx.getSource().getPlayerOrException();

        List<Border> borders = BorderSelector.resolve(selector, sp);

        if (borders.isEmpty()) {
            // @relevant resolves against a real per-player BorderPlayerStatusFixture snapshot (see
            // BorderAPI.getRelevant/BorderSelector.resolveRelevant), so an empty result here is a real,
            // honest "no relevant border" outcome (e.g. an empty dimension), same as every other
            // selector mode.
            ctx.getSource().sendFailure(
                    Component.literal("[Border] No borders matched selector.")
            );
            return 0;
        }

        int count = 0;
        for (Border b : borders) {
            count += op.run(ctx, b.id());
        }

        return count;
    }

    @FunctionalInterface
    private interface BorderOperator {
        int run(CommandContext<CommandSourceStack> ctx, UUID id);
    }

    // ------------------------------------------------------------
    // DELETE
    // ------------------------------------------------------------

    private static ArgumentBuilder<CommandSourceStack, ?> delete() {
        return Commands.literal("delete")
//                // /border delete
//                .executes(ctx ->
//                        applySelector(
//                                ctx,
//                                BorderSelectorResult.all(),
//                                BorderCommandHandler::delete
//                        )
//                )
                // /border delete <selector>
                .then(Commands.argument("selector", BorderSelectorArgumentType.selector())
                        .executes(ctx ->
                                applySelector(
                                        ctx,
                                        BorderSelectorArgumentType.getSelector(ctx, "selector"),
                                        BorderCommandHandler::delete
                                )
                        )
                );
    }

    // ------------------------------------------------------------
    // INFO
    // ------------------------------------------------------------

    private static ArgumentBuilder<CommandSourceStack, ?> info() {
        return Commands.literal("info")
                // /border info
                .executes(ctx ->
                        applySelector(
                                ctx,
                                BorderSelectorResult.all(),
                                BorderCommandHandler::info
                        )
                )
                // /border info <selector>
                .then(Commands.argument("selector", BorderSelectorArgumentType.selector())
                        .executes(ctx ->
                                applySelector(
                                        ctx,
                                        BorderSelectorArgumentType.getSelector(ctx, "selector"),
                                        BorderCommandHandler::info
                                )
                        )
                );
    }

    private static ArgumentBuilder<CommandSourceStack, ?> debug() {
        return Commands.literal("debug")
                .executes(BorderCommandHandler::debug)
                // Test aid: /border debug spawn-animal [count] -- see
                // BorderCommandHandler.spawnTestAnimals's own doc for why this exists alongside
                // the datapack function's /summon-based approach (this one actually fires
                // MobSpawnEvent.FinalizeSpawn; /summon never does).
                .then(Commands.literal("spawn-animal")
                        .executes(ctx -> BorderCommandHandler.spawnTestAnimals(ctx, 10))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 200))
                                .executes(ctx -> BorderCommandHandler.spawnTestAnimals(
                                        ctx, IntegerArgumentType.getInteger(ctx, "count")))
                        )
                )
                // Project owner request: "/border debug sickness <true|false>" --
                // toggles whether Frontier Sickness's own debuff (FrontierSickness.onTick)
                // applies at all. Same simple global-flag shape as Boss's own "/boss debug glow".
                .then(Commands.literal("sickness")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> BorderCommandHandler.setSicknessEnabled(
                                        ctx, BoolArgumentType.getBool(ctx, "value")))
                        )
                );
    }



}
