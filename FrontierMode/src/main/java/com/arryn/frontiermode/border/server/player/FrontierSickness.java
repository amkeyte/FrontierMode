package com.arryn.frontiermode.border.server.player;

import com.arryn.frontiermode.FrontierMode;
import com.arryn.frontiermode.border.common.player.BorderPlayerStatus;
import com.arryn.frontiermode.border.server.rules.PlayerRules;
import com.arryn.frontiermode.effects.common.EffectsAPI;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * Frontier Sickness on a player: the Entry Cue played on crossing out of the Frontier, the climbing tiered
 * effects, and the clear on respawn. Both the cue and the effects read off the
 * {@link BorderPlayerStatus} the player's tick already computed, so neither adds a second pass over the border
 * list. The debug switch is {@link SicknessSwitch}. Server side only.
 */
public final class FrontierSickness {

    private FrontierSickness() {
    }

    /**
     * Entry Cue (an edge-triggered bass-drop tone, sent to {@code player} only) and the climbing debuff (tiered
     * vanilla {@link MobEffectInstance}s, refreshed on a short cadence and otherwise left to run out once no
     * longer refreshed; see {@link PlayerRules} for why that satisfies "each active effect runs down and clears
     * independently" without any decay timer here).
     */
    public static void onTick(ServerPlayer player, BorderPlayerStatus previous, BorderPlayerStatus next) {
        boolean wasOutside = previous != null && previous.frontierDistance() > 0;
        boolean isOutside = next.frontierDistance() > 0;

        if (isOutside && !wasOutside) {
            // FrontierMode.ENTRY_CUE: assets/frontiermode/sounds/entry_cue.ogg, already tuned, so full speed.
            EffectsAPI.sendSoundToPlayer(
                    player, FrontierMode.ENTRY_CUE.get(), SoundSource.AMBIENT,
                    player.getX(), player.getY(), player.getZ(), 0.6f, 1.0f);
        }

        applySicknessEffects(player, next.sicknessSeverity());
    }

    private static void applySicknessEffects(ServerPlayer player, double severity) {
        if (severity < PlayerRules.SICKNESS_TIER_1_WEAKNESS) {
            // Below every tier: nothing to (re)apply. Whatever was granted runs down on its own duration.
            return;
        }

        // Cadence-gated: the tick runs every tick, but the effects only need refreshing every
        // PlayerRules.SICKNESS_EFFECT_REFRESH_INTERVAL_TICKS.
        if (player.tickCount % PlayerRules.SICKNESS_EFFECT_REFRESH_INTERVAL_TICKS != 0) {
            return;
        }

        // Read at refresh time only, off the Overworld's scope. Turning the switch off stops future refreshes;
        // whatever is already granted runs down on its own duration, with no instant clear.
        if (!SicknessSwitch.isOn(player.server)) {
            return;
        }

        int duration = PlayerRules.SICKNESS_EFFECT_BASE_DURATION_TICKS
                + player.getRandom().nextInt(PlayerRules.SICKNESS_EFFECT_JITTER_TICKS + 1);

        // Additive tiers: a higher tier keeps every lower tier's effects too. Weakness is the earliest tell and
        // nausea the last (owner direction, 2026-09-18). Amplifier 0 throughout; whether severity should scale
        // the amplifier is a playtest question.
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 0, false, true));

        if (severity >= PlayerRules.SICKNESS_TIER_2_FATIGUE) {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, duration, 0, false, true));
        }
        if (severity >= PlayerRules.SICKNESS_TIER_3_HUNGER_SLOWNESS) {
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, duration, 0, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 0, false, true));
        }
        if (severity >= PlayerRules.SICKNESS_TIER_4_BLINDNESS_DAMAGE_NAUSEA) {
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, duration, 0, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.HARM, 1, 0, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, duration, 0, false, true));
        }
    }

    /**
     * Clears the five lingering effects on respawn. Vanilla's death-respawn already drops active effects, so
     * this is an explicit guarantee rather than a reliance on that, and it matters for a player whose respawn
     * point is still in the Wilds, who would otherwise have the effects re-granted within one refresh interval.
     * The severity reset ({@code BorderAPI.resetSicknessSeverity}) is the other half: it restarts the climb that
     * drives the re-application. {@code HARM} is instantaneous and already consumed, so it needs no removal.
     * Removes unconditionally, whether or not sickness is enabled and whatever severity the new status computes.
     */
    public static void clearOnRespawn(Player player) {
        player.removeEffect(MobEffects.WEAKNESS);
        player.removeEffect(MobEffects.DIG_SLOWDOWN);
        player.removeEffect(MobEffects.HUNGER);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.removeEffect(MobEffects.BLINDNESS);
        player.removeEffect(MobEffects.CONFUSION);
    }
}
