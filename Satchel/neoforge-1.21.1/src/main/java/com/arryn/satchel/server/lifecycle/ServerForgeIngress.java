package com.arryn.satchel.server.lifecycle;

import com.arryn.satchel.common.platform.PlatformBound;
import com.arryn.satchel.SatchelMod;
import com.arryn.satchel.server.persistence.WorldIdentitySavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.UUID;

/**
 * Forge ingress for server-side scopeInfo discovery: one listener per event, each of which reads the
 * values out of the event, applies the event's own guard and calls {@link ServerLifecycle} (SAT_180),
 * where the ordering lives. A listener holds no logic of its own.
 *
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Bootstrap the server foundation</li>
 *   <li>Introduce host sources to Satchel</li>
 *   <li>Drive execution pulses</li>
 *   <li>Unload a mob's scope when the mob leaves its level</li>
 *   <li>Push a level's networked bundles to a player who arrives (login, dimension change, respawn)</li>
 *   <li>Re-read Tunables before each server starts, and save them as it stops (SAT_093)</li>
 * </ul>
 *
 * <p>
 * This class does NOT:
 * <ul>
 *   <li>Advance scopeInfo lifecycle state</li>
 *   <li>Perform readiness checks</li>
 *   <li>Emit Satchel lifecycle events</li>
 *   <li>Infer jig participation</li>
 * </ul>
 */
@EventBusSubscriber(
        modid = SatchelMod.MODID,
        bus = EventBusSubscriber.Bus.GAME
)
@PlatformBound(reason = "ingress")
public final class ServerForgeIngress {

    private ServerForgeIngress() {}

    /* =============================================================
     * Bootstrap
     * ========================================================== */

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFirstLevelLoad(LevelEvent.Load e) {
        if (!(e.getLevel() instanceof ServerLevel)) return;

        ServerLifecycle.firstLevelLoad();
    }

    /* =============================================================
     * Tunables -- re-read before each server, saved as it stops (SAT_093)
     * ========================================================== */

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerAboutToStart(ServerAboutToStartEvent e) {
        ServerLifecycle.serverAboutToStart();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent e) {
        ServerLifecycle.serverStopped();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent e) {
        ServerLifecycle.serverStopping();
    }

    /* =============================================================
     * Scope discovery — LEVEL
     * ========================================================== */

    @SubscribeEvent
    public static void onLevelDiscover(LevelEvent.Load e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;

        // Always anchored on the overworld, whichever dimension triggered this event.
        UUID worldToken = WorldIdentitySavedData.getForWrite(level.getServer().overworld()).token();
        ServerLifecycle.levelLoaded(level, worldToken);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;

        ServerLifecycle.levelUnloaded(level);
    }

    /* =============================================================
     * Scope discovery — PLAYER (RM_SAT_020)
     * ========================================================== */

    // Two separate @SubscribeEvent methods listen to PlayerLoggedInEvent (this one, and
    // onPlayerLoggedIn below for the world-identity token sync) -- deliberately kept as two
    // clearly-named handlers for two different concerns, rather than folding scope discovery into
    // the token-sync method, per this project's "one clearly-named place per concern" convention
    // (see Forge Integration & Sidedness Contract). Forge dispatches to both without issue.
    @SubscribeEvent
    public static void onPlayerScopeDiscover(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer player)) return;

        ServerLifecycle.introducePlayer(player);
    }

    // Deliberately does NOT hook PlayerEvent.PlayerChangedDimensionEvent to the scope: see
    // ServerLifecycle.removePlayer.
    @SubscribeEvent
    public static void onPlayerScopeUnload(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer player)) return;

        ServerLifecycle.removePlayer(player);
    }

    /* =============================================================
     * World-identity token sync (RM_SAT_019)
     * ========================================================== */

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer player)) return;

        ServerLifecycle.playerArrived(player);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer player)) return;

        ServerLifecycle.playerArrived(player);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer player)) return;

        ServerLifecycle.playerRespawned(player);
    }

    /**
     * A mob left its level (SAT_123). At LOW priority, so another listener of the same event still
     * finds the scope it was tracking.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onMobLeaveScopes(EntityLeaveLevelEvent e) {
        if (e.getLevel().isClientSide()) return;
        if (!(e.getEntity() instanceof Mob mob)) return;

        ServerLifecycle.mobLeft(mob);
    }

    /* =============================================================
     * Execution pulse
     * ========================================================== */

    @SubscribeEvent
    public static void onExecutionPulse(ServerTickEvent.Post e) {
        ServerLifecycle.tick();
    }
}
