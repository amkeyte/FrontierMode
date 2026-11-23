package com.arryn.satchel.common.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.IngressKey;
import com.arryn.satchel.common.ingress.ModuleSignal;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Forge's half of {@link PlayerLifeHealthCheck}: its events on {@code PlayerRespawnEvent},
 * {@code PlayerChangedDimensionEvent} and {@code LivingDropsEvent}. The check is shared.
 */
public final class PlayerLifeIngress {

    private PlayerLifeIngress() {}

    private static final IngressKey INGRESS = new IngressKey("satchelhealth:player_life");

    public static void register() {
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> build());
    }

    static final class ForgeRespawned extends ModuleSignal<PlayerEvent.PlayerRespawnEvent, ScopeInfo>
            implements PlayerLifeHealthCheck.Respawned {
        private final Player player;          // fact, copied: the respawned instance
        private final boolean endConquered;   // fact, copied

        ForgeRespawned(PlayerEvent.PlayerRespawnEvent e, ScopeInfo scope) {
            super(e, scope);
            player = e.getEntity();
            endConquered = e.isEndConquered();
        }

        @Override
        public Player player() {
            return player;
        }

        @Override
        public boolean endConquered() {
            return endConquered;
        }
    }

    static final class ForgeChangedDimension extends ModuleSignal<PlayerEvent.PlayerChangedDimensionEvent, ScopeInfo>
            implements PlayerLifeHealthCheck.ChangedDimension {
        ForgeChangedDimension(PlayerEvent.PlayerChangedDimensionEvent e, ScopeInfo scope) {
            super(e, scope);
        }
    }

    static final class ForgeDyingDrops extends ModuleSignal<LivingDropsEvent, ScopeInfo>
            implements PlayerLifeHealthCheck.DyingDrops {
        private final Player player;     // fact, copied
        private final boolean dead;      // fact, copied: as of the event
        private final boolean removed;   // fact, copied: as of the event

        ForgeDyingDrops(LivingDropsEvent e, ScopeInfo scope) {
            super(e, scope);
            player = (Player) e.getEntity();
            dead = e.getEntity().isDeadOrDying();
            removed = e.getEntity().isRemoved();
        }

        @Override
        public Player player() {
            return player;
        }

        @Override
        public boolean dead() {
            return dead;
        }

        @Override
        public boolean removed() {
            return removed;
        }
    }

    static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(INGRESS);
        ingress.translate(PlayerEvent.PlayerRespawnEvent.class, PlayerLifeHealthCheck.Respawned.class,
                e -> PlayerLifeHealthCheck.resolvePlayer(e.getEntity()), ForgeRespawned::new);
        ingress.translate(PlayerEvent.PlayerChangedDimensionEvent.class, PlayerLifeHealthCheck.ChangedDimension.class,
                e -> PlayerLifeHealthCheck.resolvePlayer(e.getEntity()), ForgeChangedDimension::new);
        ingress.translate(LivingDropsEvent.class, PlayerLifeHealthCheck.DyingDrops.class,
                e -> PlayerLifeHealthCheck.resolvePlayer(e.getEntity()), ForgeDyingDrops::new);
        return ingress;
    }
}
