package com.arryn.satchel.server.lifecycle;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.SatchelMod;
import com.arryn.satchel.common.identity.WorldIdentityContext;
import com.arryn.satchel.common.jig.guts.LogicalFoundation;
import com.arryn.satchel.common.net.SatchelNetwork;
import com.arryn.satchel.common.persistence.WorldIdentitySavedData;
import com.arryn.satchel.common.util.out.OUT;
import com.arryn.satchel.server.jig.guts.ServerFoundationBooter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

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

        // RM_SAT_019: establish the world-identity token BEFORE introducing the source below --
        // introduceSource() drives jig resolution synchronously, which on the server must always
        // see a token already bound (server never has the client's excuse to defer: it owns the
        // persisted record and can read it synchronously). Always anchored on the overworld
        // regardless of which dimension actually triggered this event -- see
        // WorldIdentitySavedData's class docs. bindServerToken is idempotent, so calling this on
        // every subsequent dimension load too is harmless: only the very first call (per
        // foundation lifetime) actually changes the bound value.
        ServerLevel overworld = level.getServer().overworld();
        UUID worldToken = WorldIdentitySavedData.getForWrite(overworld).token();
        WorldIdentityContext.bindServerToken(worldToken);

        // Introduce the LEVEL as a source.
        // Each requireJig independently decides whether it recognizes this source.
        foundation.introduceSource(level);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;

        BOOTER.bindFoundation();

        LogicalFoundation foundation = Satchel.require();

        // RM_SAT_014: this used to re-announce the source via introduceSource(level), which is
        // a no-op re-registration (the source hasn't changed) -- it never actually tore anything
        // down. tryRemoveSource is the real teardown call: it drives jig.onUnload(info) for every
        // jig that recognizes this source, which is what lets ScopeEngine_*.unload() run and the
        // scope get evicted from JigInfo. Without this, a new world reusing the same dimension
        // name (same deterministic scope UUID) found the old scope's state still resident and
        // reused it instead of starting fresh.
        foundation.tryRemoveSource(level);
    }

    /* =============================================================
     * World-identity token sync (RM_SAT_019)
     * ========================================================== */

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer player)) return;
        syncWorldIdentityToken(player);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer player)) return;
        syncWorldIdentityToken(player);
    }

    /**
     * Re-sent on every dimension change, not just at login -- cheap (one UUID), and per
     * RM_SAT_019's proposal this is deliberate robustness against a missed initial packet rather
     * than an optimization to skip.
     */
    private static void syncWorldIdentityToken(ServerPlayer player) {
        BOOTER.bindFoundation();

        WorldIdentityContext.current().ifPresentOrElse(
                token -> SatchelNetwork.sendToken(player, token),
                () -> OUT.warn(
                        "[ServerForgeIngress] No world-identity token bound yet for " +
                                player.getGameProfile().getName() +
                                " -- onLevelDiscover should have established one before any " +
                                "player could log in. Skipping sync this call."
                )
        );
    }

    /* =================================ss============================
     * Execution pulse
     * ========================================================== */

    @SubscribeEvent
    public static void onExecutionPulse(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;

        BOOTER.bindFoundation();
        ensureInstalled();

        // isReady() gate: no-op the actual jig tick pulse until Satchel is ready for this side.
        // In practice this never actually skips server-side -- the token is bound synchronously
        // in onLevelDiscover, before any source is introduced, so by the time any tick can fire
        // at all a level has already loaded and the server is ready. Kept for symmetry with the
        // client and as a real (not just documented) guarantee rather than an assumption.
        if (!Satchel.isReady()) return;

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
