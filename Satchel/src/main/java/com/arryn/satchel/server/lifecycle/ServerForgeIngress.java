package com.arryn.satchel.server.lifecycle;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.SatchelMod;
import com.arryn.satchel.common.identity.WorldIdentityContext;
import com.arryn.satchel.common.jig.guts.LogicalFoundation;
import com.arryn.satchel.common.jig.mob.MobInterestRegistry;
import com.arryn.satchel.common.lifecycle.BlockBreakSignal;
import com.arryn.satchel.common.lifecycle.ItemPickupSignal;
import com.arryn.satchel.common.lifecycle.LivingDropsSignal;
import com.arryn.satchel.common.lifecycle.LivingExperienceDropSignal;
import com.arryn.satchel.common.lifecycle.MobDied;
import com.arryn.satchel.common.lifecycle.MobRemoved;
import com.arryn.satchel.common.lifecycle.PlayerAttackSignal;
import com.arryn.satchel.common.lifecycle.PlayerRespawnSignal;
import com.arryn.satchel.common.net.SatchelNetwork;
import com.arryn.satchel.common.persistence.WorldIdentitySavedData;
import com.arryn.satchel.common.tracking.SatchelHealth;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import com.arryn.satchel.server.jig.guts.ServerFoundationBooter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
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
 *   <li>Post {@link ItemPickupSignal}/{@link BlockBreakSignal}/{@link LivingDropsSignal}/
 *   {@link LivingExperienceDropSignal} for item pickup, block break, and death drops
 *   (SAT_072)</li>
 *   <li>Post {@link PlayerRespawnSignal} when a player respawns (SAT_083)</li>
 *   <li>Post {@link PlayerAttackSignal} before a player's attack applies (SAT_087)</li>
 *   <li>Post {@link MobRemoved}, with the removal reason, when a mob something is interested
 *   in leaves its level (SAT_084)</li>
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
        // Each jig independently decides whether it recognizes this source.
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
     * Posts {@link PlayerRespawnSignal} to {@link com.arryn.satchel.common.lifecycle.SatchelEventBus}
     * (SAT_083). A per-player event, so it rides this general ingress class beside the other two
     * player handlers rather than a concern-grouped one. No {@code BOOTER.bindFoundation()}: a
     * respawn happens mid-gameplay, long after the foundation is established; the {@code
     * Satchel.isReady()} guard is the safety net.
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer)) return;
        if (!Satchel.isReady()) {
            OUT.debug("[PlayerRespawnSignal] gate: Satchel not ready -- skipping");
            return;
        }

        Satchel.require().eventBus().post(new PlayerRespawnSignal(e));
    }

    /**
     * Posts {@link PlayerAttackSignal} to {@link com.arryn.satchel.common.lifecycle.SatchelEventBus}
     * (SAT_087) before any of the attack applies, so a subscriber can cancel the whole hit. A
     * per-player event, so it rides this general ingress class beside the other player handlers.
     *
     * <p>
     * {@link AttackEntityEvent} fires on both logical sides -- {@code Player#attack} also runs for
     * the local player's predicted attack -- so the client firing is dropped explicitly, ahead of
     * the {@code Satchel.isReady()} gate. No {@code BOOTER.bindFoundation()}: an attack happens
     * mid-gameplay, long after the foundation is established.
     */
    @SubscribeEvent
    public static void onPlayerAttack(AttackEntityEvent e) {
        if (e.getEntity().level().isClientSide()) return;
        if (!Satchel.isReady()) {
            OUT.debug("[PlayerAttackSignal] gate: Satchel not ready -- skipping");
            return;
        }

        Satchel.require().eventBus().post(new PlayerAttackSignal(e));
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
        if (!MobInterestRegistry.isAnyInterested(uuid)) return;

        OUT.debug("[MobDied] posting for " + Ids.shortId(uuid));
        Satchel.require().eventBus().post(new MobDied(e.getEntity().level(), uuid, e));
    }

    /* =============================================================
     * Mob removal signal — MobRemoved (SAT_084)
     * ========================================================== */

    /**
     * Posts {@link MobRemoved} when a mob some registered supplier has interest in leaves its
     * level, with the reason captured at the exact moment it leaves -- {@code Entity#setRemoved}
     * records the reason before the level's tracking-end callback posts this event, so {@code
     * getRemovalReason()} is already set here.
     *
     * <p>
     * {@link EntityLeaveLevelEvent} fires for every entity on both logical sides, far more often
     * than a death, so the cheap filters come first, in order: client side dropped, non-{@link Mob}
     * dropped (items and orbs can't be {@code MobJig} scopes), then the interest gate. No debug
     * line on the dropped paths -- they are nearly every call.
     */
    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent e) {
        if (e.getLevel().isClientSide()) return;
        Entity entity = e.getEntity();
        if (!(entity instanceof Mob)) return;
        if (!Satchel.isReady()) return;

        UUID uuid = entity.getUUID();
        if (!MobInterestRegistry.isAnyInterested(uuid)) return;

        Entity.RemovalReason reason = entity.getRemovalReason();
        if (reason == null) {
            OUT.warn("[MobRemoved] interested mob " + Ids.shortId(uuid)
                    + " left its level with no removal reason -- not posting.");
            return;
        }

        OUT.debug("[MobRemoved] posting for " + Ids.shortId(uuid) + " reason=" + reason);
        Satchel.require().eventBus().post(new MobRemoved(e.getLevel(), uuid, entity, reason));
    }

    /* =============================================================
     * Item pickup signal — ItemPickupSignal (SAT_072)
     * ========================================================== */

    /**
     * Posts {@link ItemPickupSignal} to {@link com.arryn.satchel.common.lifecycle.SatchelEventBus}
     * before a pickup completes, so a subscriber can veto it. No {@code isClientSide} guard
     * needed: {@link EntityItemPickupEvent} is only ever fired from {@code ItemEntity.playerTouch}
     * behind that class's own server-side check, so this handler never sees a client-side firing.
     *
     * <p>
     * No interest-registry gate, unlike {@link #onLivingDeath}: nothing here proposes narrowing by
     * item/player up front, so every pickup posts, and each subscriber checks its own relevance —
     * the same discipline {@link MobDied} established, cheap regardless since {@code
     * SatchelEventBus.post()} is a no-op when nothing's subscribed.
     */
    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent e) {
        if (!Satchel.isReady()) {
            OUT.debug("[ItemPickupSignal] gate: Satchel not ready -- skipping");
            return;
        }

        Satchel.require().eventBus().post(new ItemPickupSignal(e));
    }

    /* =============================================================
     * Block break signal — BlockBreakSignal (SAT_072)
     * ========================================================== */

    /**
     * Posts {@link BlockBreakSignal} to {@link com.arryn.satchel.common.lifecycle.SatchelEventBus}
     * synchronously, off the real {@link BlockEvent.BreakEvent} — not queued or tick-delayed, so a
     * consumer reacting to it (Cairn's capstone explosion) reads as an immediate reaction to the
     * player's own break.
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;

        if (!Satchel.isReady()) {
            OUT.debug("[BlockBreakSignal] gate: Satchel not ready -- skipping");
            return;
        }

        Satchel.require().eventBus().post(new BlockBreakSignal(level, e));
    }

    /* =============================================================
     * Living drops signal — LivingDropsSignal (SAT_072)
     * ========================================================== */

    /**
     * Posts {@link LivingDropsSignal} to {@link com.arryn.satchel.common.lifecycle.SatchelEventBus}
     * before a dying entity's drops are spawned into the world, so a subscriber can veto them
     * entirely or mutate the drop list directly.
     *
     * <p>
     * No {@code isClientSide} guard needed: {@link LivingDropsEvent} is only ever posted from
     * inside {@code LivingEntity.dropAllDeathLoot}, itself only reached from {@code die()}'s own
     * {@code level instanceof ServerLevel} branch -- unlike {@link LivingDeathEvent}, which fires
     * unconditionally at the top of {@code die()} and needs the explicit guard {@link
     * #onLivingDeath} has.
     */
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent e) {
        if (!Satchel.isReady()) {
            OUT.debug("[LivingDropsSignal] gate: Satchel not ready -- skipping");
            return;
        }

        Satchel.require().eventBus().post(new LivingDropsSignal(e));
    }

    /* =============================================================
     * Living experience drop signal — LivingExperienceDropSignal (SAT_072)
     * ========================================================== */

    /**
     * Posts {@link LivingExperienceDropSignal} to
     * {@link com.arryn.satchel.common.lifecycle.SatchelEventBus} before a dying entity's
     * experience orb is awarded, so a subscriber can veto it entirely.
     *
     * <p>
     * No {@code isClientSide} guard needed, same reasoning as {@link #onLivingDrops}: {@link
     * LivingExperienceDropEvent} is only ever posted from inside {@code
     * LivingEntity.dropExperience}'s own {@code level instanceof ServerLevel} check.
     */
    @SubscribeEvent
    public static void onLivingExperienceDrop(LivingExperienceDropEvent e) {
        if (!Satchel.isReady()) {
            OUT.debug("[LivingExperienceDropSignal] gate: Satchel not ready -- skipping");
            return;
        }

        Satchel.require().eventBus().post(new LivingExperienceDropSignal(e));
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
