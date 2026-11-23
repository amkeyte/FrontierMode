package com.arryn.frontiermode;

import com.arryn.frontiermode.admin.AdminToolsModule;
import com.arryn.frontiermode.border.BorderModule;
import com.arryn.frontiermode.boss.BossModule;
import com.arryn.frontiermode.bosstell.BossTellModule;
import com.arryn.frontiermode.feral.FeralModule;
import com.arryn.frontiermode.structure.StructureModule;
import com.arryn.satchel.common.platform.PlatformBound;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;

/**
 * Registers each module's commands on the dispatcher, in a fixed order (Two Lines § The mod entry). Each line's
 * {@code FrontierMode} calls {@link #register} from its command-registration listener.
 */
@PlatformBound(reason = "commands")
public final class FrontierCommands {

    private FrontierCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        BorderModule.registerCommands(dispatcher);
        // registers /boss alongside /border.
        BossModule.registerCommands(dispatcher);
        // merges /boss debug compass into the /boss -> debug node BossModule just registered above. Order matters
        // for readability only, not correctness (Brigadier's own addChild merge doesn't care which runs first).
        BossTellModule.registerCommands(dispatcher);
        // merges /boss structure debug goto into the same /boss root, same Brigadier merge behavior.
        StructureModule.registerCommands(dispatcher);
        // registers /feral alongside /border and /boss.
        FeralModule.registerCommands(dispatcher);
        // Admin tools: registers /admin as its own, separate root, not merged into /boss.
        AdminToolsModule.registerCommands(dispatcher);
    }
}
