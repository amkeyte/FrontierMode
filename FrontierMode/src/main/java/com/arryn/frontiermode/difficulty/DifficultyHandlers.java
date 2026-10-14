package com.arryn.frontiermode.difficulty;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.server.rules.BorderRules;
import com.arryn.frontiermode.difficulty.common.LadderMath;
import com.arryn.frontiermode.difficulty.server.rules.DifficultyRules;
import com.arryn.satchel.common.lifecycle.AnimalTameSignal;
import com.arryn.satchel.common.lifecycle.EntityJoinLevelSignal;
import com.arryn.satchel.common.lifecycle.LivingHurtSignal;
import com.arryn.satchel.common.util.MobData;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The ladder's two signal handlers (difficulty.md): the ordinary rung as a hostile joins the level,
 * and damage scaling as a hit lands. Both signals are server-only and unscoped, so each handler
 * checks its own relevance.
 */
final class DifficultyHandlers {

    private DifficultyHandlers() {
    }

    /**
     * A fresh join (not {@code loadedFromDisk}: a reloaded mob already has its values) of a ladder
     * hostile that no rung has reached takes the ordinary rung, if the ambient Difficulty at its own
     * position is 2 or more; an iron golem or any wolf, wild or tame ({@link DifficultyAPI#isLadderAlly}),
     * takes the ally rung on the same condition. Joining the level, not spawn-finalize, catches spawners,
     * reinforcements, raids and slime splits as well as natural spawns.
     */
    static void onEntityJoinLevel(EntityJoinLevelSignal signal) {
        if (signal.loadedFromDisk()
                || !(signal.entity() instanceof Mob mob)
                || !(signal.level() instanceof ServerLevel level)
                || Ladder.claimed(mob)) {
            return;
        }

        // A vex an Evoker Keeper summoned: the Keeper-summon values, before the ordinary rung can
        // reach it. Vanilla sets a summoned vex's owner before adding it to the level.
        if (mob instanceof Vex vex && vex.getOwner() != null
                && Ladder.RUNG_KEEPER.equals(Ladder.rungOf(vex.getOwner()))) {
            DifficultyRules rules = DifficultyRules.ACTIVE;
            Ladder.apply(vex, Ladder.RUNG_KEEPER_SUMMON, 0, 1.0, rules.keeperVexDamage() / rules.vexReferenceHit());
            return;
        }

        String rung = DifficultyAPI.isLadderAlly(mob) ? Ladder.RUNG_ALLY
                : DifficultyAPI.isLadderHostile(mob) || isNetherPiglinKin(level, mob) ? Ladder.RUNG_ORDINARY : null;
        if (rung != null) {
            applyAmbientRung(level, mob, rung);
        }
    }

    /**
     * Taming a wolf (the signal fires just before it applies), difficulty.md § Allies. A wolf is on the
     * ally rung from the moment it joins the level, so this does two things and neither is the rung
     * change: it falls back to the rung for a wolf that has none (it spawned where there is no Relevant
     * Border and is tamed on claimed ground), and it heals every wolf to its real maximum health. Other
     * animals are not allies.
     */
    static void onAnimalTame(AnimalTameSignal signal) {
        if (!(signal.animal() instanceof Wolf wolf) || !(wolf.level() instanceof ServerLevel level)) {
            return;
        }
        if (!Ladder.claimed(wolf)) {
            applyAmbientRung(level, wolf, Ladder.RUNG_ALLY);
        }

        // Taming is still to come: vanilla then sets the wolf's base health and its health to 20, which
        // would leave a scaled wolf at 20 of a higher maximum -- claimed or not. One tick on, with the
        // tame done, heal it to its real maximum.
        MinecraftServer server = level.getServer();
        server.tell(new TickTask(server.getTickCount() + 1, () -> {
            if (wolf.isAlive() && wolf.isTame()) {
                wolf.setHealth(wolf.getMaxHealth());
            }
        }));
    }

    /**
     * The ordinary growth at the mob's own position: the ambient Difficulty there, if it is 2 or more.
     * Ordinary hostiles and allies share the numbers and differ only in the rung they record.
     */
    private static boolean applyAmbientRung(ServerLevel level, Mob mob, String rung) {
        OptionalInt difficulty = Ladder.RUNG_ORDINARY.equals(rung) && level.dimension() == Level.NETHER
                ? netherDifficulty(level)
                : ambientDifficulty(level, mob.blockPosition());
        if (difficulty.isEmpty() || difficulty.getAsInt() < 2) {
            return false;
        }

        DifficultyRules rules = DifficultyRules.ACTIVE;
        int n = difficulty.getAsInt();
        boolean applied = Ladder.apply(mob, rung, n,
                LadderMath.multiplier(rules.ordinaryHealthGrowth(), n),
                LadderMath.multiplier(rules.ordinaryDamageGrowth(), n));
        if (applied) {
            OUT.debug("[Difficulty] " + rung + " rung n=" + n + " on " + mob.getType() + " at " + mob.blockPosition());
        }
        return applied;
    }

    /**
     * Damage isn't a stat: it is scaled here, as it lands, by the multiplier on whatever dealt it --
     * melee, arrow, blast or fangs alike (difficulty.md § Damage scales when it lands). The signal
     * fires before armor, so armor still reduces a scaled hit.
     */
    static void onLivingHurt(LivingHurtSignal signal) {
        CompoundTag data = MobData.readAttacker(signal.source(), Ladder.NAMESPACE);
        if (!Ladder.hasDamageMultiplier(data)) {
            return;
        }
        signal.setAmount((float) (signal.amount() * Ladder.damageMultiplier(data)));
    }

    /**
     * Owner's ruling (FRO_230): "all Nether mobs" scale, so in the Nether the Piglin and the Hoglin
     * join the ladder hostiles. {@link DifficultyAPI#isLadderHostile} leaves both out (the Piglin is a
     * barterer, the Hoglin an {@code Animal}), and everywhere else they stay vanilla.
     */
    private static boolean isNetherPiglinKin(ServerLevel level, Mob mob) {
        return level.dimension() == Level.NETHER && (mob instanceof Piglin || mob instanceof Hoglin);
    }

    /**
     * The Nether has no Borders and so no ground to read (difficulty.md § The Nether): its hostiles
     * take the Overworld path tip's 1-based position as {@code n}. Empty with no path or before the
     * Overworld's Border facet is ready, so the mob stays vanilla.
     */
    private static OptionalInt netherDifficulty(ServerLevel nether) {
        return BorderAPI.PATH(nether.getServer().overworld())
                .map(path -> OptionalInt.of(path.size()))
                .orElseGet(OptionalInt::empty);
    }

    /** The ambient Difficulty at {@code pos}; empty with no Relevant Border or before Border is ready. */
    private static OptionalInt ambientDifficulty(ServerLevel level, BlockPos pos) {
        Optional<List<Border>> containing = BorderAPI.RULES(level).map(rules -> rules.containing(pos));
        if (containing.isEmpty()) {
            return OptionalInt.empty();
        }
        return BorderRules.ACTIVE.ambientDifficultyAt(containing.get(), pos);
    }
}
