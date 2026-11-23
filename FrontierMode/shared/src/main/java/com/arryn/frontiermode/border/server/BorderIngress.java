package com.arryn.frontiermode.border.server;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.core.InfoRequest;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.Scopes;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.Optional;

/**
 * The holder class for the border module's ingress, server side. Two things reach it: the Info report's
 * {@link InfoRequest}, which one global consumer answers with the {@code frontier distance} line for an entity
 * (0 = inside the Frontier), and Forge's player respawn, translated into the scoped {@link SicknessRespawn}
 * that {@code BorderPlayerTick} hears on the player's own scope.
 * {@link com.arryn.frontiermode.border.BorderModule#init()} registers {@link #build()} through a lambda, so
 * this class loads when the server side boots.
 */
public final class BorderIngress {

    private BorderIngress() {
    }

    public static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(FrontierKeys.BORDER_INGRESS);
        ingress.listenForAll(InfoRequest.class, BorderIngress::onInfo);

        // Scoped to the player's own scope; the Trinket listens for it, so there is no global consumer.
        ingress.translate(PlayerEvent.PlayerRespawnEvent.class, BorderIngress::resolveRespawnedScope,
                SicknessRespawn::new);
        return ingress;
    }

    /** A respawn: the respawned player's scope on the border player jig, if it is ready. */
    private static Optional<ScopeInfo> resolveRespawnedScope(PlayerEvent.PlayerRespawnEvent event) {
        return Scopes.of(FrontierKeys.BORDER_PLAYER_JIG, event.getEntity());
    }

    /** Registers the border answer; the work happens in the supplier, once per report. */
    private static void onInfo(InfoRequest request) {
        OUT.debug("[Border] InfoRequest reached its consumer");

        Level level = request.level();
        for (Object subject : request.subjects()) {
            if (subject instanceof Entity entity) {
                request.add(subject, "border",
                        () -> "  frontier distance " + BorderAPI.frontierDistanceAt(level, entity.blockPosition()));
            }
        }
    }
}
