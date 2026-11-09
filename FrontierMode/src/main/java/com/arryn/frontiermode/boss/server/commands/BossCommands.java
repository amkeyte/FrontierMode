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
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.UUID;

/**
 * Brigadier registration for {@code /boss}. Mirrors {@code BorderCommands}' registration/
 * handler split and selector-application shape exactly; the full command-tree design space is
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
                        .then(defeat())
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
        return applySelector(ctx, selector, op, false);
    }

    /**
     * {@code livingOnly} drops already-defeated bosses from an {@code @all} match, for an operator
     * that has nothing to do to them. A boss named by an index is never dropped: asking for a
     * defeated one by name still gets the operator's own answer.
     */
    private static int applySelector(
            CommandContext<CommandSourceStack> ctx,
            BossSelectorResult selector,
            BossOperator op,
            boolean livingOnly
    ) throws CommandSyntaxException {

        int count = 0;
        for (BossRecord b : selected(ctx, selector, livingOnly)) {
            count += op.run(ctx, b.bossId());
        }

        return count;
    }

    /**
     * The bosses {@code selector} matches, in order; empty after telling the sender that none matched. Shared by
     * {@code applySelector}, which acts on each in turn, and {@code info}, which reports on all of them together.
     */
    private static List<BossRecord> selected(
            CommandContext<CommandSourceStack> ctx,
            BossSelectorResult selector,
            boolean livingOnly
    ) throws CommandSyntaxException {

        Player sp = ctx.getSource().getPlayerOrException();

        List<BossRecord> bosses = BossSelector.resolve(selector, sp);

        boolean filterToLiving = livingOnly && selector.mode == BossSelectorResult.Mode.ALL;
        if (filterToLiving) {
            bosses = bosses.stream().filter(BossRecord::alive).toList();
        }

        if (bosses.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(
                    filterToLiving ? "[Boss] No living bosses matched selector." : "[Boss] No bosses matched selector."));
        }
        return bosses;
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
                .executes(ctx -> infoSelected(ctx, BossSelectorResult.all()))
                .then(Commands.argument("selector", BossSelectorArgumentType.selector())
                        .executes(ctx -> infoSelected(ctx, BossSelectorArgumentType.getSelector(ctx, "selector")))
                );
    }

    // One report for every selected boss, not one per boss: the subjects go in a single InfoRequest.
    private static int infoSelected(CommandContext<CommandSourceStack> ctx, BossSelectorResult selector)
            throws CommandSyntaxException {
        List<BossRecord> bosses = selected(ctx, selector, false);
        return bosses.isEmpty() ? 0 : BossCommandHandler.info(ctx, bosses);
    }

    // ------------------------------------------------------------
    // ADD -- no selector, mirrors Border's own add() shape (pos/here x layer, no radius)
    // ------------------------------------------------------------

    private static ArgumentBuilder<CommandSourceStack, ?> add() {
        return Commands.literal("add")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.argument("layer", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
                                    int layer = IntegerArgumentType.getInteger(ctx, "layer");
                                    return BossCommandHandler.addExplicit(ctx, pos, layer);
                                })
                        )
                )
                .then(Commands.literal("here")
                        .then(Commands.argument("layer", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    int layer = IntegerArgumentType.getInteger(ctx, "layer");
                                    return BossCommandHandler.addHere(ctx, layer);
                                })
                        )
                );
    }

    // ------------------------------------------------------------
    // ATTACH -- nests BorderSelectorArgumentType directly, the same
    // way BorderCommandHandler.pathInsert/pathRemove already do -- see BossCommandHandler.attach
    // for why this isn't wired through BossSelectorResult's own four-mode-only chain.
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
    // DEFEAT
    // ------------------------------------------------------------

    /**
     * shortened from {@code /boss transform defeat <selector>} --
     * pure command-surface rename, no behavior change. Still forces
     * {@code BossCommandHandler::transformDefeat}'s own defeat-detection cascade (border growth,
     * etc.), same as a real in-game defeat. {@code @all} skips bosses already defeated, so
     * repeating it to walk the path forward defeats only the Keeper standing at the tip.
     */
    private static ArgumentBuilder<CommandSourceStack, ?> defeat() {
        return Commands.literal("defeat")
                .then(Commands.argument("selector", BossSelectorArgumentType.selector())
                        .executes(ctx -> applySelector(
                                ctx,
                                BossSelectorArgumentType.getSelector(ctx, "selector"),
                                BossCommandHandler::transformDefeat,
                                true
                        ))
                );
    }

    // ------------------------------------------------------------
    // DEBUG GOTO
    // ------------------------------------------------------------

    private static ArgumentBuilder<CommandSourceStack, ?> debug() {
        return Commands.literal("debug")
                .then(Commands.literal("goto")
                        .then(Commands.argument("selector", BossSelectorArgumentType.selector())
                                .executes(ctx -> applySelector(
                                        ctx,
                                        BossSelectorArgumentType.getSelector(ctx, "selector"),
                                        BossCommandHandler::debugGoto
                                ))
                        )
                )
                // see BossCommandHandler.debugDistance's own doc comment.
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
