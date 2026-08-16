package com.arryn.satchel.client.lifecycle;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.SatchelMod;
import com.arryn.satchel.client.jig.guts.ClientFoundationBooter;
import com.arryn.satchel.common.identity.WorldIdentityContext;
import com.arryn.satchel.common.jig.guts.LogicalFoundation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

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

    // RM_SAT_019: tracks the last world-identity token this class has already reacted to, so
    // the re-announce below (see onExecutionPulse) fires exactly once per token, not every tick.
    private static UUID lastAnnouncedWorldToken = null;

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

        // RM_SAT_014: same fix as ServerForgeIngress -- introduceSource(level) here was a
        // no-op re-announcement, not a teardown. tryRemoveSource drives jig.onUnload(info) for
        // every jig that recognizes this source, tearing the scope down for real (including the
        // client jig's ScopeEvent.Tick subscription, which used to survive past disconnect --
        // see FRO_016, a symptom of this same missing call).
        foundation.tryRemoveSource(level);
    }

    /* =============================================================
     * World-identity token sync (RM_SAT_019)
     * ========================================================== */

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut e) {
        // Session-scoped by design (see WorldIdentityContext's class docs) -- clear so a later
        // connection, to this server or a different one, never starts with a stale token left
        // over from before.
        WorldIdentityContext.clearClientToken();
        lastAnnouncedWorldToken = null;
    }

    /* =============================================================
     * Execution pulse
     * ========================================================== */

    @SubscribeEvent
    public static void onExecutionPulse(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;

        BOOTER.bindFoundation();
        ensureInstalled();

        // Exception path, per the isReady() gate below: this is what MAKES the client ready --
        // it must run every tick regardless, since it's the thing that detects the token
        // transitioning from absent to present and reacts to it.
        reannounceLevelIfTokenJustArrived();

        // isReady() gate: no-op the actual jig tick pulse until Satchel is ready for this side.
        // Nothing meaningful would happen anyway (no scope has been registered yet if we're not
        // ready), but this makes "nothing ticks until Satchel is good" a real, enforced
        // guarantee rather than something that happens to be true because nothing's registered.
        if (!Satchel.isReady()) return;

        LogicalFoundation foundation = Satchel.require();

        // Drive Satchel-internal execution.
        // This is where readiness convergence and lifecycle promotion occur.
        foundation.foundationLifecycle().pulse();
    }

    /**
     * RM_SAT_019: onLevelDiscover() may have run before the S2cWorldIdentityToken packet
     * arrived, in which case LevelResolver.resolveScope() deferred (returned null) and no
     * LevelJig ever recognized the source -- introduceSource() is only ever called from level
     * load/unload events, so without this, that source would simply never be retried for the
     * rest of the session. introduceSource() is a safe, idempotent re-announcement when nothing's
     * actually changed (see RM_SAT_014's fix notes above), so re-calling it here once the token
     * transitions from absent to present is cheap and correct.
     */
    private static void reannounceLevelIfTokenJustArrived() {
        WorldIdentityContext.current().ifPresent(token -> {
            if (token.equals(lastAnnouncedWorldToken)) return;
            lastAnnouncedWorldToken = token;

            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                // No level loaded yet -- nothing to re-announce. That level's own
                // onLevelDiscover firing later will see the token already cached and resolve
                // correctly on the first attempt.
                return;
            }

            Satchel.require().introduceSource(level);
        });
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
