package com.arryn.satchel.server.commands;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.JigInfo;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.trinket.Trinket;
import com.arryn.satchel.common.trinket.TrinketSet;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /satchel trinkets} -- lists, per jig and scope, every {@link Trinket} the scope's coupler
 * holds, its abilities, and its {@link Trinket#describe()} line (SAT_097). Read-only. Shows the
 * server side's jigs.
 */
public final class TrinketCommands {

    private TrinketCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("satchel")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("trinkets")
                                .executes(TrinketCommands::list))
        );
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        if (!Satchel.isReady()) {
            ctx.getSource().sendFailure(Component.literal("[Trinkets] Satchel is not ready."));
            return 0;
        }

        List<String> lines = new ArrayList<>();
        for (JigInfo ji : Satchel.require().jigInfos()) {
            for (ScopeInfo info : ji.scopeInfos()) {
                for (Trinket t : ji.coupler().trinkets(info)) {
                    String abilities = TrinketSet.abilitiesOf(t).toString();
                    String describe = t.describe();
                    lines.add(ji.key.name + " | " + info.debugName() + " | "
                            + t.getClass().getSimpleName() + " " + abilities
                            + (describe.isEmpty() ? "" : " | " + describe));
                }
            }
        }

        if (lines.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("[Trinkets] No Trinkets are attached."), false);
            return 0;
        }
        for (String line : lines) {
            ctx.getSource().sendSuccess(() -> Component.literal("[Trinkets] " + line), false);
        }
        return lines.size();
    }
}
