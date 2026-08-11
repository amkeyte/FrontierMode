package com.arryn.frontiermode.border.server.commands;

import com.arryn.frontiermode.border.common.fixture.Border;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

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
                        .then(transform())
                        .then(path())
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

        ServerPlayer sp = ctx.getSource().getPlayerOrException();

        List<Border> borders;
        try {
            borders = BorderSelector.resolve(selector, sp);
        } catch (CommandSyntaxException e) {
            throw e;
        } catch (Exception e) {
            ctx.getSource().sendFailure(
                    Component.literal("[Border] Selector resolution failed.")
            );
            return 0;
        }

        if (borders.isEmpty()) {
            ctx.getSource().sendFailure(
                    Component.literal("[Border] No borders matched selector.")
            );
            return 0;
        }

        int count = 0;
        for (Border b : borders) {
            try {
                count += op.run(ctx, b.id());
            } catch (Exception e) {
                ctx.getSource().sendFailure(
                        Component.literal("[Border] Operation failed for border " + b.id())
                );
            }
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

    // ------------------------------------------------------------
    // TRANSFORM (selector REQUIRED, no default)
    // ------------------------------------------------------------

    private static ArgumentBuilder<CommandSourceStack, ?> transform() {
        return Commands.literal("transform")
                .then(Commands.argument("selector", BorderSelectorArgumentType.selector())

                        .then(Commands.literal("here")
                                .executes(ctx -> {
                                    ServerPlayer sp = ctx.getSource().getPlayerOrException();
                                    return applySelector(
                                            ctx,
                                            BorderSelectorArgumentType.getSelector(ctx, "selector"),
                                            (c, id) ->
                                                    BorderCommandHandler.transform(
                                                            c, id,
                                                            sp.blockPosition(),
                                                            null)
                                    );
                                })
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            ServerPlayer sp = ctx.getSource().getPlayerOrException();
                                            int r = IntegerArgumentType.getInteger(ctx, "radius");
                                            return applySelector(
                                                    ctx,
                                                    BorderSelectorArgumentType.getSelector(ctx, "selector"),
                                                    (c, id) ->
                                                            BorderCommandHandler.transform(
                                                                    c, id,
                                                                    sp.blockPosition(),
                                                                    r)
                                            );
                                        })
                                )
                        )

                        .then(Commands.literal("radius")
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            int r = IntegerArgumentType.getInteger(ctx, "radius");
                                            return applySelector(
                                                    ctx,
                                                    BorderSelectorArgumentType.getSelector(ctx, "selector"),
                                                    (c, id) ->
                                                            BorderCommandHandler.transform(
                                                                    c, id,
                                                                    null,
                                                                    r)
                                            );
                                        })
                                )
                        )

                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> {
                                    BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
                                    return applySelector(
                                            ctx,
                                            BorderSelectorArgumentType.getSelector(ctx, "selector"),
                                            (c, id) ->
                                                    BorderCommandHandler.transform(
                                                            c, id,
                                                            pos,
                                                            null)
                                    );
                                })
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
                                            int r = IntegerArgumentType.getInteger(ctx, "radius");
                                            return applySelector(
                                                    ctx,
                                                    BorderSelectorArgumentType.getSelector(ctx, "selector"),
                                                    (c, id) ->
                                                            BorderCommandHandler.transform(
                                                                    c, id,
                                                                    pos,
                                                                    r)
                                            );
                                        })
                                )
                        )
                );
    }

    // ---------------------------------------------------------------------
// PATH
// ---------------------------------------------------------------------

    private static ArgumentBuilder<CommandSourceStack, ?> path() {
        return Commands.literal("path")

                // ------------------------------------------------------------
                // path grow
                // ------------------------------------------------------------
                .then(Commands.literal("grow")
                        .executes(BorderCommandHandler::pathGrow)
                )

                // ------------------------------------------------------------
                // path insert <selector>
                // (selector must resolve to exactly one border; enforced later)
                // ------------------------------------------------------------
                .then(Commands.literal("insert")
                        .then(Commands.argument(
                                "selector",
                                BorderSelectorArgumentType.selector()
                        ).executes(ctx ->
                                BorderCommandHandler.pathInsert(
                                        ctx,
                                        BorderSelectorArgumentType.getSelector(ctx, "selector")
                                )
                        ))
                )

                // ------------------------------------------------------------
                // path remove <selector>
                // (selector must resolve to exactly one border; enforced later)
                // ------------------------------------------------------------
                .then(Commands.literal("remove")
                        .then(Commands.argument(
                                "selector",
                                BorderSelectorArgumentType.selector()
                        ).executes(ctx ->
                                BorderCommandHandler.pathRemove(
                                        ctx,
                                        BorderSelectorArgumentType.getSelector(ctx, "selector")
                                )
                        ))
                )

                // ------------------------------------------------------------
                // FUTURE: ordered path operations
                // ------------------------------------------------------------

                // path moveup <selector>
                .then(Commands.literal("moveup")
                        .then(Commands.argument(
                                "selector",
                                BorderSelectorArgumentType.selector()
                        ).executes(ctx ->
                                BorderCommandHandler.pathMoveUp(
                                        ctx,
                                        BorderSelectorArgumentType.getSelector(ctx, "selector")
                                )
                        ))
                )

                // path movedown <selector>
                .then(Commands.literal("movedown")
                        .then(Commands.argument(
                                "selector",
                                BorderSelectorArgumentType.selector()
                        ).executes(ctx ->
                                BorderCommandHandler.pathMoveDown(
                                        ctx,
                                        BorderSelectorArgumentType.getSelector(ctx, "selector")
                                )
                        ))
                )

                // path fixlayers
                .then(Commands.literal("fixlayers")
                        .executes(BorderCommandHandler::pathFixLayers)
                );
    }
    private static ArgumentBuilder<CommandSourceStack, ?> debug() {
        return Commands.literal("debug")
                .executes(BorderCommandHandler::debug)
                .then(Commands.literal("create")
                        .executes(BorderCommandHandler::debugCreate)
                );
    }



}
