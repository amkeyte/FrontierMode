package com.arryn.frontiermode.border.server;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.ModuleSignal;
import com.arryn.satchel.common.ingress.Scopes;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.Optional;

/**
 * {@code forge-1.20.1}'s ingress for the border module, server side: Forge's player respawn translated to
 * the scoped {@link SicknessRespawn} that {@code BorderPlayerTick} hears on the player's own scope, with
 * {@link BorderConsumers} installed. The mod entry registers {@link #build()} through a lambda, so this class loads
 * when the server side boots.
 */
public final class BorderIngress {

    private BorderIngress() {
    }

    public static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(FrontierKeys.BORDER_INGRESS);
        ingress.consumers(BorderConsumers.declare());

        // Scoped to the player's own scope; the Trinket listens for it, so there is no global consumer.
        ingress.translate(PlayerEvent.PlayerRespawnEvent.class, SicknessRespawn.class, BorderIngress::resolveRespawnedScope,
                ForgeSicknessRespawn::new);
        return ingress;
    }

    /** A respawn: the respawned player's scope on the border player jig, if it is ready. */
    private static Optional<ScopeInfo> resolveRespawnedScope(PlayerEvent.PlayerRespawnEvent event) {
        return Scopes.of(FrontierKeys.BORDER_PLAYER_JIG, event.getEntity());
    }

    /** {@link SicknessRespawn} over Forge's player-respawn event. */
    static final class ForgeSicknessRespawn extends ModuleSignal<PlayerEvent.PlayerRespawnEvent, ScopeInfo>
            implements SicknessRespawn {

        private final Player player;

        ForgeSicknessRespawn(PlayerEvent.PlayerRespawnEvent forgeEvent, ScopeInfo scope) {
            super(forgeEvent, scope);
            this.player = forgeEvent.getEntity();
        }

        @Override
        public Player player() {
            return player;
        }
    }
}
