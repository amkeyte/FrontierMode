package com.arryn.frontiermode.feral;

import com.arryn.frontiermode.difficulty.DifficultyAPI;
import com.arryn.frontiermode.effects.common.EffectsAPI;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * What makes a hostile that joined the Wilds Feral: its name and scoreboard team, and its place on
 * the {@code feral} rung of the stat ladder. The decision to tag stays in {@link FeralModule}.
 */
final class FeralTagging {

    private FeralTagging() {
    }

    /**
     * Name tag + a shared {@code feral} scoreboard team, then the {@code feral} rung. The tag shows
     * the raw frontier-distance {@code mob} spawned at, not a bucketed tier -- per the design page's
     * explicit "no bucketed tiers picked from a table, just steadily more twisted." Team color is
     * {@code DARK_GREEN} for the design page's "sickly green," distinct from Sentinel's
     * {@code DARK_PURPLE} and Boss's unteamed white glow.
     *
     * <p>The rung's health and damage come from the path tip's position, not from the distance
     * ({@link DifficultyAPI#applyFeralRung}).
     */
    static void tag(Mob mob, int frontierDistance, ServerLevel level) {
        mob.setCustomName(Component.literal("Feral [" + frontierDistance + "]"));
        // Visible only in the client-side debug view (AdminViewClient); the name itself stays set.
        mob.setCustomNameVisible(false);

        Scoreboard scoreboard = level.getScoreboard();
        PlayerTeam team = EffectsAPI.ensureTeam(scoreboard, "feral", ChatFormatting.DARK_GREEN);
        EffectsAPI.assignToTeam(mob, scoreboard, team);

        DifficultyAPI.applyFeralRung(mob);
    }
}
