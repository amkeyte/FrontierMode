package com.arryn.frontiermode.boss.server.commands;

import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.frontiermode.border.server.commands.BorderSelectorArgumentType;

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
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/**
 * Brigadier registration for {@code /boss} -- FRO_057's build of RM_FRO_022 ("Joyce"), the six
 * items scoped on that ticket and on FRO_056. Mirrors {@code BorderCommands}' registration/
 * handler split and selector-application shape exactly; the full command-tree design space this
 * build's six items are drawn from (out of scope this pass) is
 * wiki/frontiermode/architecture/boss-commands.md.
 */
public final class BossCommands {

    private BossCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("boss")
                        .requires(src -> src.hasPermission(2))
                        .then(info())
                        .then(add())
                        .then(attach())
                        .then(delete())
                        .then(mob())
                        .then(transform())
                        .then(debug())
        );
    }

    // ------------------------------------------------------------
    // Shared selector application (mirrors BorderCommands.applySelector)
    // ------------------------------------------------------------

    private static int applySelector(
            CommandContext<CommandSourceStack> ctx,
            BossSelectorResult selector,
            BossOperator op
    ) throws CommandSyntaxException {

        ServerPlayer sp = ctx.getSource().getPlayerOrException();

        List<BossRecord> bosses;
        try {
            bosses = BossSelector.resolve(selector, sp);
        } catch (CommandSyntaxException e) {
            throw e;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("[Boss] Selector resolution failed."));
            return 0;
        }

        if (bosses.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("[Boss] No bosses matched selector."));
            return 0;
        }

        int count = 0;
        for (BossRecord b : bosses) {
            try {
                count += op.run(ctx, b.bossId());
            } catch (Exception e) {
                ctx.getSource().sendFailure(
                        Component.literal("[Boss] Operation failed for boss " + b.bossId())
                );
            }
        }

        return count;
    }

    @FunctionalInterface
    private interface BossOperator {
        int run(CommandContext<CommandSourceStack> ctx, UUID id) throws CommandSyntaxException;
    }

    // ------------------------------------------------------------
    // INFO -- default @all, matching Border's own no-arg default
    // ------------------------------------------------------------

    private static ArgumentBuilder<CommandSourceStack, ?> info() {
        return Commands.literal("info")
                .executes(ctx ->
                        applySelector(ctx, BossSelectorResult.all(), BossCommandHandler::info)
                )
                .then(Commands.argument("selector", BossSelectorArgumentType.selector())
                        .executes(ctx -> applySelector(
                                ctx,
                                BossSelectorArgumentType.getSelector(ctx, "selector"),
                                BossCommandHandler::info
                        ))
                );
    }

    // ------------------------------------------------------------
    // ADD -- no selector, mirrors Border's own add() shape (pos/here x layer, no radius)
    // ------------------------------------------------------------

    private static ArgumentBuilder<CommandSourceStack, ?> add() {
        return Commands.literal("add")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.argument("layer", IntegerArgumentType.integer(0))
                                .executes(ctx -> {
                                    BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
                                    int layer = IntegerArgumentType.getInteger(ctx, "layer");
                                    return BossCommandHandler.addExplicit(ctx, pos, layer);
                                })
                        )
                )
                .then(Commands.literal("here")
                        .then(Commands.argument("layer", IntegerArgumentType.integer(0))
                                .executes(ctx -> {
                                    int layer = IntegerArgumentType.getInteger(ctx, "layer");
                                    return BossCommandHandler.addHere(ctx, layer);
                                })
                        )
                );
    }

    // ------------------------------------------------------------
    // ATTACH -- FRO_082 (FRO_063's ruling). Nests BorderSelectorArgumentType directly, the same
    // way BorderCommandHandler.pathInsert/pathRemove already do -- see BossCommandHandler.attach
    // for why this isn't wired through BossSelectorResult's own (still four-mode-only) chain.
    // ------------------------------------------------------------

    private static ArgumentBuilder<CommandSourceStack, ?> attach() {
        return Commands.literal("attach")
                .then(Commands.argument("selector", BorderSelectorArgumentType.selector())
                        .executes(ctx -> BossCommandHandler.attach(
                                ctx,
                                BorderSelectorArgumentType.getSelector(ctx, "selector")
                        ))
                );
    }

    // ------------------------------------------------------------
    // DELETE -- selector required, no bare default (mirrors Border's own delete())
    // ------------------------------------------------------------

    private static ArgumentBuilder<CommandSourceStack, ?> delete() {
        return Commands.literal("delete")
                .then(Commands.argument("selector", BossSelectorArgumentType.selector())
                        .executes(ctx -> applySelector(
                                ctx,
                                BossSelectorArgumentType.getSelector(ctx, "selector"),
                                BossCommandHandler::delete
                        ))
                );
    }

    // ------------------------------------------------------------
    // MOB SPAWN
    // ------------------------------------------------------------

    private static ArgumentBuilder<CommandSourceStack, ?> mob() {
        return Commands.literal("mob")
                .then(Commands.literal("spawn")
                        .then(Commands.argument("selector", BossSelectorArgumentType.selector())
                                .executes(ctx -> applySelector(
                                        ctx,
                                        BossSelectorArgumentType.getSelector(ctx, "selector"),
                                        BossCommandHandler::mobSpawn
                                ))
                        )
                );
    }

    // ------------------------------------------------------------
    // TRANSFORM DEFEAT
    // ------------------------------------------------------------

    private static ArgumentBuilder<CommandSourceStack, ?> transform() {
        return Commands.literal("transform")
                .then(Commands.literal("defeat")
                        .then(Commands.argument("selector", BossSelectorArgumentType.selector())
                                .executes(ctx -> applySelector(
                                        ctx,
                                        BossSelectorArgumentType.getSelector(ctx, "selector"),
                                        BossCommandHandler::transformDefeat
                                ))
                        )
                );
    }

    // ------------------------------------------------------------
    // DEBUG GOTO
    // ------------------------------------------------------------

    private static ArgumentBuilder<CommandSourceStack, ?> debug() {
        return Commands.literal("debug")
                // Project owner request (2026-09-08): "/boss debug glow <true|false>" -- toggles
                // whether Boss/Guardian tagging applies Entity#setGlowingTag, and immediately
                // re-applies that value to every currently-materialized boss on this level (see
                // BossCommandHandler.setGlow's own doc for why guardians can't be retroactively
                // updated the same way).
                .then(Commands.literal("glow")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> BossCommandHandler.setGlow(
                                        ctx, BoolArgumentType.getBool(ctx, "value")))
                        )
                )
                .then(Commands.literal("goto")
                        .then(Commands.argument("selector", BossSelectorArgumentType.selector())
                                .executes(ctx -> applySelector(
                                        ctx,
                                        BossSelectorArgumentType.getSelector(ctx, "selector"),
                                        BossCommandHandler::debugGoto
                                ))
                        )
                )
                // Added mid-playtest, not in FRO_057's original six -- see BossCommandHandler
                // .debugDistance's own doc comment.
                .then(Commands.literal("distance")
                        .then(Commands.argument("selector", BossSelectorArgumentType.selector())
                                .executes(ctx -> applySelector(
                                        ctx,
                                        BossSelectorArgumentType.getSelector(ctx, "selector"),
                                        BossCommandHandler::debugDistance
                                ))
                        )
                );
    }
}
