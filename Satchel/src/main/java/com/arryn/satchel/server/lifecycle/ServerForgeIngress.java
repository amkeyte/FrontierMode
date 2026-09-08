package com.arryn.satchel.server.lifecycle;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.SatchelMod;
import com.arryn.satchel.common.identity.WorldIdentityContext;
import com.arryn.satchel.common.jig.guts.LogicalFoundation;
import com.arryn.satchel.common.jig.mob.MobInterestRegistry;
import com.arryn.satchel.common.lifecycle.MobDied;
import com.arryn.satchel.common.net.SatchelNetwork;
import com.arryn.satchel.common.persistence.WorldIdentitySavedData;
import com.arryn.satchel.common.tracking.SatchelHealth;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import com.arryn.satchel.server.jig.guts.ServerFoundationBooter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
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
 *   <li>Post {@link MobDied} when a registered mob dies (SAT_044)</li>
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

        BOOTER.bindFoundation();
        ensureInstalled();

        // No PlayerJigConfig is registered by any module yet (RM_SAT_020 builds the jig kind
        // itself; RM_FRO_006 is the first planned real consumer) -- introduceSource() degrades
        // gracefully when no jig recognizes the source (see LogicalFoundation.introduceSource),
        // same as it would for any other as-yet-unconsumed source type. Safe to wire now, ahead
        // of a real consumer, exactly as RM_SAT_020 calls for.
        Satchel.require().introduceSource(player);
    }

    // Deliberately does NOT hook PlayerEvent.PlayerChangedDimensionEvent to
    // introduceSource/tryRemoveSource -- a PlayerScope has to survive a dimension change intact
    // (see RM_SAT_020's design log: buffs and border-compass attunement are identity-tied, not
    // level-derivable), unlike LevelScope, which is legitimately torn down and re-resolved per
    // dimension. Only login/logout affect this scope's lifecycle.
    @SubscribeEvent
    public static void onPlayerScopeUnload(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer player)) return;

        BOOTER.bindFoundation();

        Satchel.require().tryRemoveSource(player);
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

    /* =============================================================
     * Mob death signal — MobDied (SAT_044)
     * ========================================================== */

    /**
     * Posts {@link MobDied} to {@link com.arryn.satchel.common.lifecycle.SatchelEventBus} when
     * a mob that at least one registered supplier has interest in dies.
     *
     * <p>
     * Gate: checks the union of every {@link MobInterestRegistry} supplier's interest set for
     * the dying entity's UUID. No match -- the overwhelming common case -- returns immediately,
     * constructing nothing. A match anywhere posts one bare {@code MobDied} to the whole bus;
     * every subscriber checks its own relevance before acting.
     *
     * <p>
     * No {@code BOOTER.bindFoundation()} call: this fires mid-gameplay, long after the
     * foundation is established. The {@code Satchel.isReady()} guard is the real safety net.
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent e) {
        if (e.getEntity().level().isClientSide()) return;
        if (!Satchel.isReady()) {
            OUT.debug("[MobDied] gate: Satchel not ready -- " + Ids.shortId(e.getEntity().getUUID()));
            return;
        }

        UUID uuid = e.getEntity().getUUID();
        if (!MobInterestRegistry.isAnyInterested(uuid)) {
            OUT.debug("[MobDied] gate: not interested -- " + Ids.shortId(uuid));
            return;
        }

        OUT.debug("[MobDied] posting for " + Ids.shortId(uuid));
        Satchel.require().eventBus().post(new MobDied(e.getEntity().level(), uuid, e));
    }

    /* =================================ss============================
     * Execution pulse
     * ========================================================== */

    @SubscribeEvent
    public static void onExecutionPulse(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;

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
