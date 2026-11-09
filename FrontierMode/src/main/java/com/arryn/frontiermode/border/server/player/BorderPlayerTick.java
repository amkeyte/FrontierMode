package com.arryn.frontiermode.border.server.player;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.common.fixture.BordersCrudFacet;
import com.arryn.frontiermode.border.common.player.BorderPlayerBundle;
import com.arryn.frontiermode.border.common.player.BorderPlayerStatus;
import com.arryn.frontiermode.border.common.player.BorderPlayerStatusFixture;
import com.arryn.frontiermode.border.common.player.BorderPlayerStatusProposal;
import com.arryn.frontiermode.border.server.SicknessRespawn;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.player.PlayerScope;
import com.arryn.satchel.common.trinket.Ticking;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * The module's player tick (wiki/frontiermode/architecture/border.md § Trinket ticks): a Trinket declared on
 * {@code BORDER_PLAYER_JIG}, which is {@code SERVER} only, so it needs no side guard. Each tick it recomputes the
 * player's {@link BorderPlayerStatusFixture} against the level's live borders, then runs Frontier Sickness off the
 * transition: the Entry Cue on crossing out of the Frontier and the effects on their cadence. It hears
 * {@link SicknessRespawn} on the player's own scope and clears the sickness as a fresh start.
 */
public final class BorderPlayerTick implements Ticking {

    @Override
    public void onAttach(ScopeInfo info) {
        OUT.debug("[Border] BorderPlayerTick attached");
        info.listenFor(SicknessRespawn.class, this::onRespawn);
    }

    @Override
    public void onTickAfter(ScopeInfo info) {
        PlayerScope scope = (PlayerScope) info.scope();
        ServerPlayer player = (ServerPlayer) scope.player();

        BorderPlayerBundle bundle = info.getOrCreate(FrontierKeys.BORDER_PLAYER_BUNDLE);
        BorderPlayerStatusFixture fixture =
                bundle.getOrCreateFixture(FrontierKeys.BORDER_PLAYER_STATUS, BorderPlayerStatusFixture::new);

        List<Border> borders =
                BorderAPI.CRUD(player.level())
                        .map(BordersCrudFacet::all)
                        .orElseGet(List::of);

        // The previous status is read before accept() overwrites it: the Entry Cue is edge-triggered off the
        // transition, and accept() itself stays side-effect-free.
        BorderPlayerStatus previous = fixture.status();

        BorderPlayerStatus next = fixture.accept(
                new BorderPlayerStatusProposal(player.getUUID()),
                borders,
                player.blockPosition()
        );

        FrontierSickness.onTick(player, previous, next);
    }

    /**
     * Clears the lingering effects and restarts the severity climb ({@link FrontierSickness#clearOnRespawn} and
     * {@link BorderAPI#resetSicknessSeverity}), as a fresh start. The respawn event fires after a death, and when
     * a player leaves the End after the dragon; not on a dimension change.
     */
    private void onRespawn(SicknessRespawn event) {
        OUT.debug("[Border] SicknessRespawn reached BorderPlayerTick");

        Player player = event.player();
        FrontierSickness.clearOnRespawn(player);
        BorderAPI.resetSicknessSeverity(player);
    }
}
