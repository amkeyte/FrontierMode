package com.arryn.frontiermode.difficulty;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.ModuleSignal;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.AnimalTameEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * {@code neoforge-1.21.1}'s ingress for the difficulty module: NeoForge's join, incoming-damage and tame events
 * translated to {@link MobJoining}, {@link LadderHit} and {@link WolfTaming}, each with a resolver that bounces every
 * firing the module has no business with, with {@link DifficultyConsumers} installed. The incoming-damage event is the
 * stage before armor (Two Lines § Damage stages on each line). The mod entry registers {@link #build()} through a
 * lambda, so this class loads when the server side boots.
 */
public final class DifficultyIngress {

    private DifficultyIngress() {
    }

    public static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(FrontierKeys.DIFFICULTY_INGRESS);
        ingress.consumers(DifficultyConsumers.declare());
        ingress.translate(EntityJoinLevelEvent.class, MobJoining.class, DifficultyIngress::freshMob,
                ForgeMobJoining::new);
        ingress.translate(LivingIncomingDamageEvent.class, LadderHit.class, DifficultyIngress::damageMultiplier,
                ForgeLadderHit::new);
        ingress.translate(AnimalTameEvent.class, WolfTaming.class, DifficultyIngress::wolf, ForgeWolfTaming::new);
        return ingress;
    }

    /** The cheap filter for a join, which fires for every entity: only a fresh join of a mob gets an event built. */
    private static Optional<Mob> freshMob(EntityJoinLevelEvent event) {
        if (!MobJoining.passes(event.loadedFromDisk()) || !(event.getEntity() instanceof Mob mob)) {
            return Optional.empty();
        }
        return Optional.of(mob);
    }

    /**
     * The filter for a hit, which fires for every hit in the game: only one whose attacker carries a ladder damage
     * multiplier gets an event built. The multiplier is read here, by {@code Ladder}, because the consumer needs the
     * value itself, so a presence-only test would read the attacker's record twice.
     */
    private static Optional<Double> damageMultiplier(LivingIncomingDamageEvent event) {
        OptionalDouble multiplier = Ladder.damageMultiplierOf(event.getSource());
        return multiplier.isPresent() ? Optional.of(multiplier.getAsDouble()) : Optional.empty();
    }

    /** Only a wolf is tamed into an ally; every other animal is dropped. */
    private static Optional<Wolf> wolf(AnimalTameEvent event) {
        if (event.getAnimal() instanceof Wolf wolf) {
            return Optional.of(wolf);
        }
        return Optional.empty();
    }

    /** {@link MobJoining} over NeoForge's entity-join event, named as Satchel names its own. */
    static final class ForgeMobJoining extends ModuleSignal<EntityJoinLevelEvent, Mob> implements MobJoining {

        private final Level level;

        ForgeMobJoining(EntityJoinLevelEvent forgeEvent, Mob mob) {
            super(forgeEvent, mob);
            this.level = Objects.requireNonNull(forgeEvent.getLevel(), "level");
        }

        @Override
        public Level level() {
            return level;
        }
    }

    /** {@link LadderHit} over NeoForge's incoming-damage event. */
    static final class ForgeLadderHit extends ModuleSignal<LivingIncomingDamageEvent, Double> implements LadderHit {

        ForgeLadderHit(LivingIncomingDamageEvent forgeEvent, Double multiplier) {
            super(forgeEvent, multiplier);
        }

        @Override
        public float amount() {
            return nativeEvent().getAmount();
        }

        @Override
        public void setAmount(float amount) {
            nativeEvent().setAmount(amount);
        }
    }

    /** {@link WolfTaming} over NeoForge's animal-tame event. */
    static final class ForgeWolfTaming extends ModuleSignal<AnimalTameEvent, Wolf> implements WolfTaming {

        ForgeWolfTaming(AnimalTameEvent forgeEvent, Wolf wolf) {
            super(forgeEvent, wolf);
        }
    }
}
