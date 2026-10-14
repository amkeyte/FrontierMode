package com.arryn.frontiermode.structure.server.commands;

import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.frontiermode.boss.server.commands.BossSelector;
import com.arryn.frontiermode.boss.server.commands.BossSelectorArgumentType;
import com.arryn.frontiermode.boss.server.commands.BossSelectorResult;
import com.arryn.frontiermode.structure.StructureModule;
import com.arryn.frontiermode.structure.common.CairnRecord;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/**
 * Registers {@code /boss structure debug goto <selector> [<cairn>]} -- teleports to a Cairn tracking a
 * selected boss (the first, or the 1-based {@code <cairn>}th: a boss can have several), mirroring
 * {@code BossCommandHandler.debugGoto}'s own shape. Merged into
 * {@code BossCommands}' existing {@code boss} root, same verified Brigadier merge behavior
 * {@code BossTellCommands} already relies on for {@code /boss debug compass}.
 */
public final class StructureCommands {

    private StructureCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("boss")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("structure")
                                .then(Commands.literal("debug")
                                        .then(Commands.literal("goto")
                                                .then(Commands.argument("selector", BossSelectorArgumentType.selector())
                                                        .executes(ctx -> goTo(
                                                                ctx,
                                                                BossSelectorArgumentType.getSelector(ctx, "selector"),
                                                                1
                                                        ))
                                                        .then(Commands.argument("cairn", IntegerArgumentType.integer(1))
                                                                .executes(ctx -> goTo(
                                                                        ctx,
                                                                        BossSelectorArgumentType.getSelector(ctx, "selector"),
                                                                        IntegerArgumentType.getInteger(ctx, "cairn")
                                                                ))
                                                        )
                                                )
                                        )
                                )
                        )
        );
    }

    private static Component msg(String m) {
        return Component.literal("[Structure] " + m);
    }

    private static int goTo(CommandContext<CommandSourceStack> ctx, BossSelectorResult selector, int cairnNumber)
            throws CommandSyntaxException {

        ServerPlayer sp = ctx.getSource().getPlayerOrException();

        List<BossRecord> matches;
        try {
            matches = BossSelector.resolve(selector, sp);
        } catch (CommandSyntaxException e) {
            throw e;
        } catch (Exception e) {
            ctx.getSource().sendFailure(msg("Selector resolution failed."));
            return 0;
        }

        if (matches.isEmpty()) {
            ctx.getSource().sendFailure(msg("No bosses matched selector."));
            return 0;
        }
        if (matches.size() > 1) {
            ctx.getSource().sendFailure(msg("Selector matched " + matches.size()
                    + " bosses -- goto needs exactly one. Use a bare index or @nearest."));
            return 0;
        }

        UUID bossId = matches.get(0).bossId();

        var fixtureOpt = StructureModule.cairnFixture(sp.serverLevel());
        if (fixtureOpt.isEmpty()) {
            ctx.getSource().sendFailure(msg("Structure data not available for this level yet."));
            return 0;
        }

        List<CairnRecord> cairns = fixtureOpt.get().forBoss(bossId);
        if (cairns.isEmpty()) {
            ctx.getSource().sendFailure(msg("Boss " + bossId + " has no Cairn record -- a Keeper earns its"
                    + " first at path position 20, or its Cairns gave up finding ground."));
            return 0;
        }
        if (cairnNumber > cairns.size()) {
            ctx.getSource().sendFailure(msg("Boss " + bossId + " has " + cairns.size()
                    + " Cairn(s); there is no number " + cairnNumber + "."));
            return 0;
        }
        CairnRecord record = cairns.get(cairnNumber - 1);

        if (!record.placed()) {
            ctx.getSource().sendFailure(msg("Boss " + bossId + "'s Cairn " + cairnNumber + " of "
                    + cairns.size() + " hasn't been placed yet -- still waiting on a valid candidate."));
            return 0;
        }

        // Bug fix (playtest): anchorPos is the pad's own center, at ground level -- teleporting
        // there dropped the player inside the solid pad block (suffocation). The pillar
        // (Mossy Cobblestone x2 + cap) sits directly above that same center column, so standing
        // on an *edge* cell of the 3x3 pad instead (offset by 1 in X, one block above the pad's
        // own surface) is guaranteed solid ground -- part of the pad StructureModule already
        // built -- while staying clear of the pillar's column entirely.
        BlockPos anchor = record.anchorPos();
        BlockPos standingSpot = anchor.offset(1, 1, 0);
        sp.teleportTo(standingSpot.getX() + 0.5, standingSpot.getY(), standingSpot.getZ() + 0.5);

        ctx.getSource().sendSuccess(
                () -> msg("Teleported to boss " + bossId + "'s Cairn " + cairnNumber + " of "
                        + cairns.size() + "."), false);
        return 1;
    }
}
