package com.arryn.satchel.server.lifecycle;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.identity.WorldIdentityContext;
import com.arryn.satchel.common.jig.guts.LogicalFoundation;
import com.arryn.satchel.common.jig.guts.LogicalSide;
import com.arryn.satchel.common.tracking.SatchelHealth;
import com.arryn.satchel.common.tunable.TunableManager;
import com.arryn.satchel.common.util.out.OUT;
import com.arryn.satchel.server.jig.guts.ServerFoundationBooter;
import com.arryn.satchel.server.net.ServerNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

import java.util.UUID;

/**
 * What the server's lifecycle does, and in what order, once the loader has said something happened
 * (SAT_180). {@code ServerForgeIngress} listens for the loader's events; it reads the values out of
 * each, applies the event's own guard ({@code instanceof}, the tick phase) and calls one method here
 * with plain values. The ordering therefore lives in one place that every line compiles, and a second
 * line's listeners call it rather than copy it.
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
 *   <li>Emit Satchel lifecycle events</li>
 *   <li>Infer jig participation</li>
 * </ul>
 */
public final class ServerLifecycle {

    private static final ServerFoundationBooter BOOTER =
            ServerFoundationBooter.INSTANCE();

    private static boolean INSTALLED = false; //probably move to booter

    private ServerLifecycle() {}

    /* =============================================================
     * Bootstrap
     * ========================================================== */

    /**
     * A server level has begun to load: bind the thread and install the foundation, once. SatchelHealth reads
     * what the loader posted before the first level, first, while nothing is bound.
     */
    public static void firstLevelLoad() {
        SatchelHealth.onServerFirstLevelLoad();
        BOOTER.bindFoundation();
        ensureInstalled();
    }

    /* =============================================================
     * Tunables -- re-read before each server, saved as it stops (SAT_093)
     * ========================================================== */

    /**
     * Re-reads Tunables before this server creates any level. The first load happens at Satchel's
     * common setup; this one picks up a CSV edited between two worlds of the same game, which
     * would otherwise be overwritten when this server stops. Nothing unsaved is lost: the previous
     * server's stop already wrote the registry.
     */
    public static void serverAboutToStart() {
        TunableManager.load();
        SatchelHealth.onServerAboutToStart();
    }

    public static void serverStopping() {
        TunableManager.save();

        // Every jig resets as the server begins to stop, while its levels and storage are still up, so
        // a mob scope's Unloaded handlers and terminal flush run against a live world and nothing a
        // stopped world held reaches the next integrated session (SAT_122).
        BOOTER.bindFoundation();
        Satchel.foundationFor(LogicalSide.SERVER).ifPresent(LogicalFoundation::reset);
    }

    /**
     * After the last level has unloaded: SatchelHealth drops this server's state, including the
     * checks that unload queued, which can never be pumped now. It touches no foundation, so it does
     * not bind.
     */
    public static void serverStopped() {
        SatchelHealth.onServerStopped();
    }

    /* =============================================================
     * Scope discovery — LEVEL
     * ========================================================== */

    /**
     * A server level has loaded. {@code worldToken} is the world's persisted identity token, read by the
     * listener from the overworld's saved data (always the overworld, whichever dimension triggered the
     * event; see {@code WorldIdentitySavedData}'s class docs).
     */
    public static void levelLoaded(ServerLevel level, UUID worldToken) {
        BOOTER.bindFoundation();
        ensureInstalled();

        LogicalFoundation foundation = Satchel.require();

        // RM_SAT_019: establish the world-identity token BEFORE introducing the source below --
        // introduceSource() drives jig resolution synchronously, which on the server must always
        // see a token already bound (server never has the client's excuse to defer: it owns the
        // persisted record and can read it synchronously). bindServerToken is idempotent, so calling
        // this on every subsequent dimension load too is harmless: only the very first call (per
        // foundation lifetime) actually changes the bound value.
        WorldIdentityContext.bindServerToken(worldToken);

        // Introduce the LEVEL as a source.
        // Each jig independently decides whether it recognizes this source.
        foundation.introduceSource(level);
    }

    public static void levelUnloaded(ServerLevel level) {
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
     * Scope discovery — PLAYER (RM_SAT_020)
     * ========================================================== */

    // Two separate listener methods hear PlayerLoggedInEvent (introducePlayer, and playerArrived below for
    // the world-identity token sync) -- deliberately kept as two clearly-named concerns, rather than
    // folding scope discovery into the token-sync method, per this project's "one clearly-named place
    // per concern" convention (see Forge Integration & Sidedness Contract).
    public static void introducePlayer(ServerPlayer player) {
        BOOTER.bindFoundation();
        ensureInstalled();

        // No PlayerJigConfig is registered by any module yet (RM_SAT_020 builds the jig kind
        // itself; RM_FRO_006 is the first planned real consumer) -- introduceSource() degrades
        // gracefully when no jig recognizes the source (see LogicalFoundation.introduceSource),
        // same as it would for any other as-yet-unconsumed source type. Safe to wire now, ahead
        // of a real consumer, exactly as RM_SAT_020 calls for.
        Satchel.require().introduceSource(player);
    }

    // Deliberately not called for a dimension change -- a PlayerScope has to survive a dimension change
    // intact (see RM_SAT_020's design log: buffs and border-compass attunement are identity-tied, not
    // level-derivable), unlike LevelScope, which is legitimately torn down and re-resolved per
    // dimension. Only login/logout affect this scope's lifecycle.
    public static void removePlayer(ServerPlayer player) {
        BOOTER.bindFoundation();

        Satchel.require().tryRemoveSource(player);
    }

    /* =============================================================
     * World-identity token sync (RM_SAT_019)
     * ========================================================== */

    /**
     * A player has arrived in a level, at login or on a dimension change: they get the world-identity
     * token and that level's networked bundles.
     */
    public static void playerArrived(ServerPlayer player) {
        syncWorldIdentityToken(player);
        pushLevelBundles(player);
    }

    /**
     * Pushes the respawned player the level bundles of the level they land in. No {@code
     * BOOTER.bindFoundation()}: a respawn happens mid-gameplay, long after the foundation is
     * established; the {@code Satchel.isReady()} guard is the safety net.
     */
    public static void playerRespawned(ServerPlayer player) {
        if (!Satchel.isReady()) return;

        // A respawn fires no dimension-change event, and the event does not say where the player was, so
        // every respawn is pushed: a duplicate of an earlier send is harmless.
        pushLevelBundles(player);
    }

    /**
     * SAT_144, the arrival push: the player has joined a level's audience, so they get that level's
     * networked bundles now and do not wait for the timer. To that player only.
     */
    private static void pushLevelBundles(ServerPlayer player) {
        if (!Satchel.isReady()) return;

        Satchel.require().pushLevelBundlesTo(player);
    }

    /**
     * Re-sent on every dimension change, not just at login -- cheap (one UUID), and per
     * RM_SAT_019's proposal this is deliberate robustness against a missed initial packet rather
     * than an optimization to skip.
     */
    private static void syncWorldIdentityToken(ServerPlayer player) {
        BOOTER.bindFoundation();

        WorldIdentityContext.current().ifPresentOrElse(
                token -> ServerNetwork.sendToken(player, token),
                () -> OUT.warn(
                        "[ServerLifecycle] No world-identity token bound yet for " +
                                player.getGameProfile().getName() +
                                " -- onLevelDiscover should have established one before any " +
                                "player could log in. Skipping sync this call."
                )
        );
    }

    /**
     * Unloads a mob's scope the moment the mob leaves its level (SAT_123): whatever the reason, a
     * kill, a chunk unload, a level change, and with the interest left as it is. The listener runs at
     * LOW priority, so another listener of the same event still finds the scope it was tracking.
     */
    public static void mobLeft(Mob mob) {
        if (!Satchel.isReady()) return;

        Satchel.require().mobLeft(mob);
    }

    /* =============================================================
     * Execution pulse
     * ========================================================== */

    /** One server tick, at its end. */
    public static void tick() {
        BOOTER.bindFoundation();
        ensureInstalled();

        // SAT_040: drain SatchelHealth's pending-health-check queue once per server tick,
        // unconditionally -- deliberately ahead of the isReady() gate below, since these checks
        // don't depend on LogicalFoundation being ready, and pumping them from a call chain
        // genuinely later than whatever queued them is the entire point (see
        // SatchelHealth#pumpPendingHealthChecks for the full reasoning).
        SatchelHealth.pumpPendingHealthChecks();

        // isReady() gate: no-op the actual jig tick pulse until Satchel is ready for this side.
        // In practice this never actually skips server-side -- the token is bound synchronously
        // in levelLoaded, before any source is introduced, so by the time any tick can fire
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
