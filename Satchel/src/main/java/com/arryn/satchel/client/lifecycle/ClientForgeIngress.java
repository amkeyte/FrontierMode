package com.arryn.satchel.client.lifecycle;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.SatchelMod;
import com.arryn.satchel.client.jig.guts.ClientFoundationBooter;
import com.arryn.satchel.common.jig.guts.LogicalFoundation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge ingress for client-side scopeInfo discovery.
 * <p>
 * Mirrors server ingress but:
 * - tolerant of "no jigs installed / no jigs recognize this source"
 * - drives client-side foundation lifecycle pulses
 */
@Mod.EventBusSubscriber(
        modid = SatchelMod.MODID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT
)

public final class ClientForgeIngress {

    private static final ClientFoundationBooter BOOTER =
            ClientFoundationBooter.INSTANCE();

    private static boolean INSTALLED = false;

    private ClientForgeIngress() {
    }

    /* =============================================================
     * Bootstrap
     * ========================================================== */

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFirstLevelLoad(LevelEvent.Load e) {
        if (!(e.getLevel() instanceof ClientLevel)) return;

        BOOTER.bindFoundation();
        ensureInstalled();
    }

    /* =============================================================
     * Scope discovery — LEVEL (client)
     * ========================================================== */
    @SubscribeEvent
    public static void onLevelDiscover(LevelEvent.Load e) {
        if (!(e.getLevel() instanceof ClientLevel level)) return;


        BOOTER.bindFoundation();
        ensureInstalled();

        LogicalFoundation foundation = Satchel.require();

        // Introduce the LEVEL as a source.
        // Each requireJig independently decides whether it recognizes this source.
        foundation.introduceSource(level);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload e) {
        if (!(e.getLevel() instanceof ClientLevel level)) return;

        BOOTER.bindFoundation();

        LogicalFoundation foundation = Satchel.require();

        // Notify jigs that the source is gone.
        // ExecPulse + lifecycle dispatcher will handle per-requireJig unload correctly.
        foundation.introduceSource(level); // optional no-op for jigs that already removed
    }

    /* =============================================================
     * Execution pulse
     * ========================================================== */

    @SubscribeEvent
    public static void onExecutionPulse(TickEvent.ClientTickEvent e) {
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
