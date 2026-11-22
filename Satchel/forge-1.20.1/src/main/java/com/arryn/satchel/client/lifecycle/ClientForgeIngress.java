package com.arryn.satchel.client.lifecycle;

import com.arryn.satchel.common.platform.PlatformBound;
import com.arryn.satchel.SatchelMod;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge ingress for client-side scopeInfo discovery -- {@code Level} sources (every jig kind
 * whose config opts into {@code BOTH}) and, since SAT_063, {@code Player} sources for {@code
 * PlayerJig} consumers that do the same: one listener per event, each of which reads the values out
 * of the event, applies the event's own guard and calls {@link ClientLifecycle} (SAT_180), where the
 * ordering lives. A listener holds no logic of its own.
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

@PlatformBound(reason = "ingress")
public final class ClientForgeIngress {

    private ClientForgeIngress() {
    }

    /* =============================================================
     * Bootstrap
     * ========================================================== */

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFirstLevelLoad(LevelEvent.Load e) {
        if (!(e.getLevel() instanceof ClientLevel)) return;

        ClientLifecycle.firstLevelLoad();
    }

    /* =============================================================
     * Scope discovery — LEVEL (client)
     * ========================================================== */
    @SubscribeEvent
    public static void onLevelDiscover(LevelEvent.Load e) {
        if (!(e.getLevel() instanceof ClientLevel level)) return;

        ClientLifecycle.levelLoaded(level);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload e) {
        if (!(e.getLevel() instanceof ClientLevel level)) return;

        ClientLifecycle.levelUnloaded(level);
    }

    /* =============================================================
     * World-identity token sync (RM_SAT_019)
     * ========================================================== */

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut e) {
        ClientLifecycle.loggingOut();
    }

    /* =============================================================
     * Scope discovery — PLAYER (client, SAT_063)
     * ========================================================== */

    // Two separate @SubscribeEvent methods listen to LoggingIn/LoggingOut for two different
    // concerns (this section for PlayerJig scope discovery, onLoggingOut above for
    // world-identity token clearing) -- mirrors ServerForgeIngress's own "one clearly-named
    // place per concern" split between onPlayerScopeDiscover and onPlayerLoggedIn.

    @SubscribeEvent
    public static void onPlayerScopeDiscover(ClientPlayerNetworkEvent.LoggingIn e) {
        LocalPlayer player = e.getPlayer();
        if (player == null) return;

        ClientLifecycle.introducePlayer(player);
    }

    @SubscribeEvent
    public static void onPlayerScopeUnload(ClientPlayerNetworkEvent.LoggingOut e) {
        // @Nullable on this specific event -- may be null when creating a new integrated server
        // (singleplayer) or connecting to a multiplayer server, per the event's own doc.
        LocalPlayer player = e.getPlayer();
        if (player == null) return;

        ClientLifecycle.removePlayer(player);
    }

    /* =============================================================
     * Mob scope teardown (SAT_123)
     * ========================================================== */

    /**
     * A mob left the client's level, whether it died or walked out of tracking range (which arrives
     * as a discard). At LOW priority, after other listeners have seen the event.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onMobLeaveScopes(EntityLeaveLevelEvent e) {
        if (!e.getLevel().isClientSide()) return;
        if (!(e.getEntity() instanceof Mob mob)) return;

        ClientLifecycle.mobLeft(mob);
    }

    /* =============================================================
     * Execution pulse
     * ========================================================== */

    @SubscribeEvent
    public static void onExecutionPulse(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;

        ClientLifecycle.tick();
    }

}
