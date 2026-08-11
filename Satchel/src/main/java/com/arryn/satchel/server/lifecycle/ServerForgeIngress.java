package com.arryn.satchel.server.lifecycle;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.SatchelMod;
import com.arryn.satchel.common.jig.guts.LogicalFoundation;
import com.arryn.satchel.server.jig.guts.ServerFoundationBooter;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge ingress for server-side scopeInfo discovery.
 *
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Bootstrap the server foundation</li>
 *   <li>Introduce host sources to Satchel</li>
 *   <li>Drive execution pulses</li>
 * </ul>
 *
 * <p>
 * This class does NOT:
 * <ul>
 *   <li>Advance scopeInfo lifecycle state</li>
 *   <li>Perform readiness checks</li>
 *   <li>Emit Satchel lifecycle events</li>
 *   <li>Infer requireJig participation</li>
 * </ul>
 */
@Mod.EventBusSubscriber(
        modid = SatchelMod.MODID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class ServerForgeIngress {

    private static final ServerFoundationBooter BOOTER =
            ServerFoundationBooter.INSTANCE();

    private static boolean INSTALLED = false; //probably move to booter

    private ServerForgeIngress() {}

    /* =============================================================
     * Bootstrap
     * ========================================================== */

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFirstLevelLoad(LevelEvent.Load e) {
        if (!(e.getLevel() instanceof ServerLevel)) return;

        BOOTER.bindFoundation();
        ensureInstalled();

    }

    /* =============================================================
     * Scope discovery — LEVEL
     * ========================================================== */

    @SubscribeEvent
    public static void onLevelDiscover(LevelEvent.Load e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;

        BOOTER.bindFoundation();
        ensureInstalled();

        LogicalFoundation foundation = Satchel.require();

        // Introduce the LEVEL as a source.
        // Each requireJig independently decides whether it recognizes this source.
        foundation.introduceSource(level);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;

        BOOTER.bindFoundation();

        LogicalFoundation foundation = Satchel.require();

        // Notify jigs that the source is gone.
        // ExecPulse + lifecycle dispatcher will handle per-requireJig unload correctly.
        foundation.introduceSource(level); // optional no-op for jigs that already removed
    }

    /* =================================ss============================
     * Execution pulse
     * ========================================================== */

    @SubscribeEvent
    public static void onExecutionPulse(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;

        BOOTER.bindFoundation();
        ensureInstalled();

        LogicalFoundation foundation = Satchel.require();

        // Drive Satchel-internal execution.
        // This is where readiness convergence and lifecycle promotion occur.
        foundation.foundationLifecycle().pulse();
    }

    /**
     * boot events may happen at unexpected times or out of order.
     */
    private static void ensureInstalled() {
        if (!INSTALLED) {
            BOOTER.installFoundation();
            INSTALLED = true;
        }
    }
}
