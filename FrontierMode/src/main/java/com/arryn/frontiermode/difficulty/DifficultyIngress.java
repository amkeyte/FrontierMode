package com.arryn.frontiermode.difficulty;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.core.InfoRequest;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.util.MobData;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.AnimalTameEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.Optional;

/**
 * The holder class for the difficulty module's ingress: the three module events that carry the stat ladder
 * ({@link MobJoining}, {@link LadderHit}, {@link WolfTaming}), each with a resolver that bounces every
 * firing the module has no business with, and the consumer of the Info report's {@link InfoRequest}, which
 * adds the {@code ladder:} line for a mob a rung has reached. {@link DifficultyModule#init()} registers
 * {@link #build()} through a lambda, so this class loads when the server side boots.
 */
final class DifficultyIngress {

    private DifficultyIngress() {
    }

    static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(FrontierKeys.DIFFICULTY_INGRESS);
        ingress.translate(EntityJoinLevelEvent.class, DifficultyIngress::freshMob, MobJoining::new);
        ingress.translate(LivingHurtEvent.class, DifficultyIngress::ladderRecord, LadderHit::new);
        ingress.translate(AnimalTameEvent.class, DifficultyIngress::wolf, WolfTaming::new);
        ingress.listenForAll(MobJoining.class, DifficultyHandlers::onMobJoining);
        ingress.listenForAll(LadderHit.class, DifficultyHandlers::onLadderHit);
        ingress.listenForAll(WolfTaming.class, DifficultyHandlers::onWolfTaming);
        ingress.listenForAll(InfoRequest.class, DifficultyIngress::onInfo);
        return ingress;
    }

    /**
     * The cheap filter for a join, which fires for every entity: only a fresh join of a mob on the server
     * gets an event built. A mob reloaded with its chunk already has its values.
     */
    private static Optional<Mob> freshMob(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob)) {
            return Optional.empty();
        }
        return Optional.of(mob);
    }

    /**
     * The filter for a hit, which fires for every hit in the game: only one whose attacker carries a ladder
     * damage multiplier gets an event built. The attacker's record is read here because the consumer needs
     * the multiplier itself, so a presence-only test would read the compound twice.
     */
    private static Optional<CompoundTag> ladderRecord(LivingHurtEvent event) {
        CompoundTag ladder = MobData.readAttacker(event.getSource(), Ladder.NAMESPACE);
        return Ladder.hasDamageMultiplier(ladder) ? Optional.of(ladder) : Optional.empty();
    }

    /** Only a wolf on the server is tamed into an ally; every other animal is dropped. */
    private static Optional<Wolf> wolf(AnimalTameEvent event) {
        if (event.getAnimal() instanceof Wolf wolf && !wolf.level().isClientSide()) {
            return Optional.of(wolf);
        }
        return Optional.empty();
    }

    /** Registers the ladder answer; the work happens in the supplier, once per report. */
    private static void onInfo(InfoRequest request) {
        OUT.debug("[Difficulty] InfoRequest reached its consumer");

        for (Object subject : request.subjects()) {
            if (subject instanceof Mob mob) {
                request.add(subject, "difficulty", () -> ladderLine(mob));
            }
        }
    }

    /** The rung, its level when there is one, and the two multipliers; null when no rung has reached the mob. */
    private static String ladderLine(Mob mob) {
        return DifficultyAPI.describe(mob).map(ladder -> "  ladder: " + ladder).orElse(null);
    }
}
