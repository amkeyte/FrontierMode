package com.arryn.satchel.common.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.IngressKey;
import com.arryn.satchel.common.ingress.ModuleSignal;
import com.arryn.satchel.common.ingress.Scopes;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.guts.ScopeInfoImpl;
import com.arryn.satchel.common.jig.host.HostJigs;
import com.arryn.satchel.common.jig.player.PlayerScope;
import com.arryn.satchel.common.trinket.Order;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.Optional;

/**
 * A player scope across death, respawn and a dimension change (SAT_131 probes 3 and 4) -- see {@link
 * SatchelHealth}. {@code PlayerList.respawn} replaces the {@code ServerPlayer} instance for a
 * connection, and a player scope outlives that: it is made at login and unloaded at logout. A
 * server-only player-host Trinket attached before the death confirms, on a real death and respawn
 * (passive, or {@code /kill}):
 * <ul>
 *   <li><b>the same Trinket</b> -- no detach and no second attach (the {@link HostProbe} checks), it
 *   keeps ticking afterwards, and {@code PlayerScope.player()} returns the respawned instance, not the
 *   dead one;</li>
 *   <li><b>a scoped event after the respawn</b> -- a module event resolved with {@code
 *   Scopes.of(HostJigs.PLAYER_HOST, respawnedPlayer)} reaches the consumer the Trinket holds through
 *   {@code info.listenFor};</li>
 *   <li><b>a scoped event for a dying player</b> -- a module event on {@code LivingDropsEvent},
 *   resolved to the player's scope, reaches the Trinket's consumer during the death, while the player
 *   is dead and not yet respawned. FRO_256's soulbound stash depends on the scope still being ready
 *   then;</li>
 *   <li><b>a dimension change</b> -- the same Trinket survives it and keeps ticking.</li>
 * </ul>
 * One line per kind of event, per world.
 */
final class PlayerLifeHealthCheck {

    private PlayerLifeHealthCheck() {}

    private static final String MODULE = "satchel_health";

    private static final IngressKey INGRESS = new IngressKey("satchelhealth:player_life");

    static void register() {
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> build());
        Satchel.contributePlayerTrinket(MODULE, JigPolicies.SideApplicability.SERVER, Order.NORMAL,
                LifeProbe.class, LifeProbe::new);
    }

    // =================================================================
    // The module events: scoped to the player host's scope for the player
    // =================================================================

    /** A player was recreated after a death (or the End's exit). */
    static final class Respawned extends ModuleSignal<PlayerEvent.PlayerRespawnEvent, ScopeInfo> {
        final Player player;          // fact, copied: the respawned instance
        final boolean endConquered;   // fact, copied

        Respawned(PlayerEvent.PlayerRespawnEvent e, ScopeInfo scope) {
            super(e, scope);
            player = e.getEntity();
            endConquered = e.isEndConquered();
        }
    }

    /** A player arrived in another dimension. */
    static final class ChangedDimension extends ModuleSignal<PlayerEvent.PlayerChangedDimensionEvent, ScopeInfo> {
        ChangedDimension(PlayerEvent.PlayerChangedDimensionEvent e, ScopeInfo scope) {
            super(e, scope);
        }
    }

    /** A player's death drops: fired from {@code die}, so the player is dead and not yet respawned. */
    static final class DyingDrops extends ModuleSignal<LivingDropsEvent, ScopeInfo> {
        final Player player;        // fact, copied
        final boolean dead;         // fact, copied: as of the event
        final boolean removed;      // fact, copied: as of the event

        DyingDrops(LivingDropsEvent e, ScopeInfo scope) {
            super(e, scope);
            player = (Player) e.getEntity();
            dead = e.getEntity().isDeadOrDying();
            removed = e.getEntity().isRemoved();
        }
    }

    static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(INGRESS);

        // Registered across three natives; what each proves is in the class doc. A drops event is
        // fired for every living thing that dies, so its resolver is a type test before the lookup.
        ingress.translate(PlayerEvent.PlayerRespawnEvent.class,
                e -> Scopes.of(HostJigs.PLAYER_HOST, e.getEntity()), Respawned::new);
        ingress.translate(PlayerEvent.PlayerChangedDimensionEvent.class,
                e -> Scopes.of(HostJigs.PLAYER_HOST, e.getEntity()), ChangedDimension::new);
        ingress.translate(LivingDropsEvent.class,
                e -> e.getEntity() instanceof ServerPlayer player
                        ? Scopes.of(HostJigs.PLAYER_HOST, player)
                        : Optional.<ScopeInfo>empty(),
                DyingDrops::new);

        return ingress;
    }

    // =================================================================
    // The Trinket
    // =================================================================

    /**
     * On the player host, server only. Holds three scope-bound consumers from {@code onAttach}, and
     * the facts they compare across a death.
     */
    public static final class LifeProbe extends HostProbe {

        /** The player instance the scope resolved on the last tick. */
        private Player lastSeen;

        /** The tick a respawn or a dimension change was handled in, to confirm ticking resumed after. */
        private long respawnedInTick = -1;
        private long changedInTick = -1;

        /** Whether the player was seen dead this life, whether drops arrived for it, and as a spectator. */
        private boolean diedThisLife;
        private boolean diedAsSpectator;
        private boolean dropsThisLife;

        public LifeProbe() {
            super("player life probe", JigPolicies.SideApplicability.SERVER);
        }

        @Override
        public void onAttach(ScopeInfo info) {
            super.onAttach(info);

            info.listenFor(Respawned.class, e -> onRespawn(info, e));
            info.listenFor(ChangedDimension.class, e -> onDimension(info, e));
            info.listenFor(DyingDrops.class, e -> onDrops(info, e));
        }

        @Override
        public void onTickAfter(ScopeInfo info) {
            super.onTickAfter(info);

            Player now = ((PlayerScope) info.scope()).player();
            lastSeen = now;
            if (now.isDeadOrDying()) {
                diedThisLife = true;
                diedAsSpectator = now.isSpectator();
            }

            long tick = tick(info);
            if (respawnedInTick >= 0 && tick > respawnedInTick) {
                SatchelHealth.logOnce("life:respawn:ticking", "[SatchelHealth] Player-life probe: the same Trinket "
                        + "kept ticking after a respawn (tick " + respawnedInTick + " to " + tick + ")");
                respawnedInTick = -1;
            }
            if (changedInTick >= 0 && tick > changedInTick) {
                SatchelHealth.logOnce("life:dimension:ticking", "[SatchelHealth] Player-life probe: the same Trinket "
                        + "kept ticking after a dimension change (tick " + changedInTick + " to " + tick + ")");
                changedInTick = -1;
            }
        }

        private static long tick(ScopeInfo info) {
            return ScopeInfoImpl.of(info).jigInfo().trinketTick().tickNumber();
        }

        private void onDrops(ScopeInfo info, DyingDrops e) {
            Player scoped = ((PlayerScope) info.scope()).player();
            if (!e.dead) violation("the scope's consumer got the drops event for a player that is not dead: "
                    + info.debugName());
            if (e.removed) violation("the drops event reached the consumer after the dying player was removed: "
                    + info.debugName());
            if (scoped != e.player) violation("the scope resolved a different player than the dying one while "
                    + "its drops event was delivered: " + info.debugName());
            if (!info.isReady()) violation("the scope was not ready while its dying player's drops event was "
                    + "delivered: " + info.debugName());

            dropsThisLife = true;
            SatchelHealth.logOnce("life:drops", "[SatchelHealth] Player-life probe: a scoped event on "
                    + "LivingDropsEvent reached the player scope's consumer during the death, with the player dead "
                    + "and not yet respawned (" + info.debugName() + ")");
        }

        private void onRespawn(ScopeInfo info, Respawned e) {
            Player scoped = ((PlayerScope) info.scope()).player();

            if (scoped != e.player) violation("PlayerScope.player() did not return the respawned instance: "
                    + info.debugName());
            if (lastSeen != null && scoped == lastSeen) violation("PlayerScope.player() still returns the "
                    + "instance from before the respawn: " + info.debugName());
            if (attaches() != 1 || detaches() != 0) violation("the Trinket attached " + attaches() + " and detached "
                    + detaches() + " time(s) across a respawn, expected 1 and 0: " + info.debugName());

            // A death drops the player's loot unless they were a spectator; the End's exit is no death.
            if (!e.endConquered && diedThisLife && !diedAsSpectator && !dropsThisLife) {
                violation("a player died and respawned but their drops event never reached the scope's consumer: "
                        + info.debugName());
            }
            diedThisLife = false;
            diedAsSpectator = false;
            dropsThisLife = false;

            lastSeen = scoped;
            respawnedInTick = tick(info);
            SatchelHealth.logOnce("life:respawn", "[SatchelHealth] Player-life probe: after a respawn the player "
                    + "scope's Trinket is the same instance (attached " + attaches() + ", detached " + detaches()
                    + "), PlayerScope.player() returns the respawned player and not the dead one, and a scoped "
                    + "event resolved for the new player reached its consumer (" + info.debugName() + ")");
        }

        private void onDimension(ScopeInfo info, ChangedDimension e) {
            if (attaches() != 1 || detaches() != 0) violation("the Trinket attached " + attaches() + " and detached "
                    + detaches() + " time(s) across a dimension change, expected 1 and 0: " + info.debugName());

            changedInTick = tick(info);
            SatchelHealth.logOnce("life:dimension", "[SatchelHealth] Player-life probe: the player scope's Trinket "
                    + "survived a dimension change as the same instance and a scoped event reached its consumer ("
                    + info.debugName() + ")");
        }
    }
}
