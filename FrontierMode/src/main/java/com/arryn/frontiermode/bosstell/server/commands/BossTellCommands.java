package com.arryn.frontiermode.bosstell.server.commands;

import com.arryn.frontiermode.boss.BossAPI;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.frontiermode.boss.server.commands.BossSelector;
import com.arryn.frontiermode.boss.server.commands.BossSelectorArgumentType;
import com.arryn.frontiermode.boss.server.commands.BossSelectorResult;
import com.arryn.frontiermode.bosstell.BossTellModule;
import com.arryn.frontiermode.bosstell.common.CompassAttunement;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Registers {@code /boss debug compass <selector>} -- FRO_118's own command, merged into
 * {@code BossCommands}' existing {@code boss} -> {@code debug} node rather than a new root.
 */
public final class BossTellCommands {

    private BossTellCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Verified against the real Brigadier 1.1.8 bytecode (CommandNode.addChild), not
        // assumed: dispatcher.register() calls root.addChild(node), which looks up an existing
        // same-named child at every level of the tree and, if found, recursively merges the new
        // subtree's children into it rather than creating a duplicate/conflicting node. So
        // registering "boss" a second time here merges "compass" into BossCommands' existing
        // "boss" -> "debug" node. One real consequence of that merge worth knowing: addChild
        // only ever carries over a node's own command + children, never its requirement --
        // the .requires() below is therefore discarded, and BossCommands' original
        // requires(hasPermission(2)) on the shared "boss" node is what actually governs. Kept
        // here anyway for local readability, not because it does anything at runtime.
        dispatcher.register(
                Commands.literal("boss")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("debug")
                                .then(Commands.literal("compass")
                                        .then(Commands.argument("selector", BossSelectorArgumentType.selector())
                                                .executes(ctx -> compass(
                                                        ctx,
                                                        BossSelectorArgumentType.getSelector(ctx, "selector")
                                                ))
                                        )
                                )
                        )
        );
    }

    private static Component msg(String m) {
        return Component.literal("[Boss] " + m);
    }

    /**
     * Constructs an Admin Compass directly ({@code new ItemStack(...)} plus the NBT marking it
     * as the admin variant) and hands it to the caller -- never through the recipe book or
     * crafting grid, per special-compass.md's "The recipe produces only the Tier 0... Admin
     * Compass is never craftable" ruling. Requires the selector to resolve to exactly one boss --
     * a compass locks onto one target, so {@code @all} (or any selector matching more than one
     * record) is rejected rather than silently picking one.
     */
    private static int compass(CommandContext<CommandSourceStack> ctx, BossSelectorResult selector)
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
                    + " bosses -- the compass needs exactly one. Use a bare index or @nearest."));
            return 0;
        }

        BossRecord record = matches.get(0);

        ItemStack stack = new ItemStack(BossTellModule.AMETHYST_COMPASS.get());
        CompassAttunement.setLive(stack, record.bossId(), sp.getUUID(), true, BossAPI.levelOf(record.layer()));

        if (!sp.getInventory().add(stack)) {
            sp.drop(stack, false);
        }

        ctx.getSource().sendSuccess(
                () -> msg("Admin Compass locked onto boss " + record.displayBossId() + "."), false);
        return 1;
    }
}
