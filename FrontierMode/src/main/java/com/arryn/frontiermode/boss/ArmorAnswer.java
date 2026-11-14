package com.arryn.frontiermode.boss;

import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.mob.MobScope;
import com.arryn.satchel.common.trinket.Trinket;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * A boss that players meet better armored than it expects is tougher (wiki/frontiermode/architecture/boss.md § The
 * ArmorAnswer ability): its health is multiplied by {@code m}, which the boss does by letting each hit land at
 * {@code 1/m} of its amount. Nothing is set on the body. The ability is the mechanism; the kind passes the numbers: a
 * {@code range} in blocks, and a {@code rule} from the players in the fight to an {@link ArmorReading}.
 *
 * <p>It hears {@link BossTakingDamage}, where the amount is after armor and can still be changed. At each hit: on
 * Peaceful, nothing; a body at full health lets go of any reading it holds; with no reading held and a player as the
 * hit's causing entity, the players in the fight are read once and the reading is held; and a held reading divides the
 * amount, whatever dealt the hit. The reading is the {@link Boss}'s, saved with the body, and is held until the body
 * has fully healed, so a player who hits bare and then armors up is still measured bare.
 *
 * <p>Server only: the kind subscribes it under {@code Satchel.isServer()}. It keeps no state of its own.
 */
public final class ArmorAnswer implements Trinket {

    private final Boss boss;
    private final double range;
    private final Function<List<Player>, ArmorReading> rule;

    public ArmorAnswer(Boss boss, double range, Function<List<Player>, ArmorReading> rule) {
        this.boss = Objects.requireNonNull(boss, "boss");
        this.range = range;
        this.rule = Objects.requireNonNull(rule, "rule");
    }

    @Override
    public void onAttach(ScopeInfo info) {
        MobScope scope = info.scopeAs();
        Mob body = scope.mob();
        info.listenFor(BossTakingDamage.class, event -> onTakingDamage(event, body));
    }

    private void onTakingDamage(BossTakingDamage event, Mob body) {
        Level level = body.level();
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return;
        }
        if (body.getHealth() >= body.getMaxHealth() && boss.armorReading().isPresent()) {
            boss.releaseArmorReading();
            OUT.debug("[Boss] armor reading released");
        }
        if (boss.armorReading().isEmpty() && event.source().getEntity() instanceof Player hitter) {
            ArmorReading reading = rule.apply(playersInFight(level, body, hitter));
            if (!(reading.multiplier() >= 1.0)) {
                throw new IllegalStateException("[Boss] ArmorAnswer: the rule answered a multiplier below 1 ("
                        + reading.multiplier() + ") for bossId=" + boss.bossId());
            }
            boss.holdArmorReading(reading);
            OUT.debug("[Boss] armor read capability=" + reading.capability() + " multiplier=" + reading.multiplier());
        }
        // The stored multiplier, not the in-force one: at the first hit the body is still at full health.
        boss.armorReading().ifPresent(reading -> event.setAmount((float) (event.amount() / reading.multiplier())));
    }

    // Every player within range of the body, spectators left out, and the hitting player whatever their distance, so
    // a bow shot from beyond the range still counts.
    private List<Player> playersInFight(Level level, Mob body, Player hitter) {
        double rangeSqr = range * range;
        List<Player> players = new ArrayList<>();
        for (Player player : level.players()) {
            if (!player.isSpectator() && player.distanceToSqr(body) <= rangeSqr) {
                players.add(player);
            }
        }
        if (!players.contains(hitter)) {
            players.add(hitter);
        }
        return players;
    }
}
