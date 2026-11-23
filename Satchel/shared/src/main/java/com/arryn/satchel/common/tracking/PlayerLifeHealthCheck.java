package com.arryn.satchel.common.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.ingress.ModuleEvent;
import com.arryn.satchel.common.ingress.Scopes;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.guts.ScopeInfoImpl;
import com.arryn.satchel.common.jig.host.HostJigs;
import com.arryn.satchel.common.jig.player.PlayerScope;
import com.arryn.satchel.common.trinket.Order;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

/**
 * A player scope across death, respawn and a dimension change (SAT_131 probes 3 and 4) -- see {@link SatchelHealth}.
 * {@code PlayerList.respawn} replaces the {@code ServerPlayer} instance for a connection, and a player scope outlives
 * that: it is made at login and unloaded at logout. A server-only player-host Trinket attached before the death
 * confirms, on a real death and respawn (passive, or {@code /kill}):
 * <ul>
 *   <li><b>the same Trinket</b> -- no detach and no second attach (the {@link HostProbe} checks), it keeps ticking
 *   afterwards, and {@code PlayerScope.player()} returns the respawned instance, not the dead one;</li>
 *   <li><b>a scoped event after the respawn</b> -- a module event resolved with
 *   {@code Scopes.of(HostJigs.PLAYER_HOST, respawnedPlayer)} reaches the consumer the Trinket holds through
 *   {@code info.listenFor};</li>
 *   <li><b>a scoped event for a dying player</b> -- a module event on the death-drops event, resolved to the player's
 *   scope, reaches the Trinket's consumer during the death, while the player is dead and not yet respawned. FRO_256's
 *   soulbound stash depends on the scope still being ready then;</li>
 *   <li><b>a dimension change</b> -- the same Trinket survives it and keeps ticking.</li>
 * </ul>
 * One line per kind of event, per world.
 *
 * <p>
 * The check is shared. Each line's {@code PlayerLifeIngress} implements the three event interfaces on its loader's
 * respawn, dimension-change and death-drops events, with {@link PlayerLifeHealthCheck#resolvePlayer(Entity)} as the
 * resolver of each.
 */
final class PlayerLifeHealthCheck {

    private PlayerLifeHealthCheck() {}

    private static final String MODULE = "satchel_health";

    /** Contributes the probe. Each line registers the ingress. */
    static void register() {
        Satchel.contributePlayerTrinket(MODULE, JigPolicies.SideApplicability.SERVER, Order.NORMAL,
                LifeProbe.class, LifeProbe::new);
    }

    // =================================================================
    // The module events, scoped to the player host's scope for the player, and the resolver
    // =================================================================

    /** A player was recreated after a death (or the End's exit). */
    interface Respawned extends ModuleEvent<ScopeInfo> {

        /** The respawned instance, copied when the event was built. */
        Player player();

        /** Whether this was the End's exit rather than a death, copied when the event was built. */
        boolean endConquered();
    }

    /** A player arrived in another dimension. */
    interface ChangedDimension extends ModuleEvent<ScopeInfo> {
    }

    /** A player's death drops: fired from {@code die}, so the player is dead and not yet respawned. */
    interface DyingDrops extends ModuleEvent<ScopeInfo> {

        /** The dying player, copied when the event was built. */
        Player player();

        /** Whether the player was dead or dying when the event was built. */
        boolean dead();

        /** Whether the player had been removed when the event was built. */
        boolean removed();
    }

    /**
     * The resolver of all three: the player host's scope for a player, else empty. A drops event is fired for every
     * living thing that dies, so this is a type test before the lookup.
     */
    static Optional<ScopeInfo> resolvePlayer(Entity entity) {
        return entity instanceof Player player ? Scopes.of(HostJigs.PLAYER_HOST, player) : Optional.empty();
    }

    // =================================================================
    // The Trinket
    // =================================================================

    /**
     * On the player host, server only. Holds three scope-bound consumers from {@code onAttach}, and the facts they
     * compare across a death.
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
            if (!e.dead()) violation("the scope's consumer got the drops event for a player that is not dead: "
                    + info.debugName());
            if (e.removed()) violation("the drops event reached the consumer after the dying player was removed: "
                    + info.debugName());
            if (scoped != e.player()) violation("the scope resolved a different player than the dying one while "
                    + "its drops event was delivered: " + info.debugName());
            if (!info.isReady()) violation("the scope was not ready while its dying player's drops event was "
                    + "delivered: " + info.debugName());

            dropsThisLife = true;
            SatchelHealth.logOnce("life:drops", "[SatchelHealth] Player-life probe: a scoped event on "
                    + "the death-drops event reached the player scope's consumer during the death, with the player "
                    + "dead and not yet respawned (" + info.debugName() + ")");
        }

        private void onRespawn(ScopeInfo info, Respawned e) {
            Player scoped = ((PlayerScope) info.scope()).player();

            if (scoped != e.player()) violation("PlayerScope.player() did not return the respawned instance: "
                    + info.debugName());
            if (lastSeen != null && scoped == lastSeen) violation("PlayerScope.player() still returns the "
                    + "instance from before the respawn: " + info.debugName());
            if (attaches() != 1 || detaches() != 0) violation("the Trinket attached " + attaches() + " and detached "
                    + detaches() + " time(s) across a respawn, expected 1 and 0: " + info.debugName());

            // A death drops the player's loot unless they were a spectator; the End's exit is no death.
            if (!e.endConquered() && diedThisLife && !diedAsSpectator && !dropsThisLife) {
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
