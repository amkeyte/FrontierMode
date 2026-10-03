package com.arryn.satchel.common.lifecycle;

import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.AnimalTameEvent;

import java.util.Objects;

/**
 * Posted to {@link SatchelEventBus} when a player is about to tame an animal (a wolf, cat, parrot,
 * horse, ...), wrapping {@link AnimalTameEvent} (SAT_094). Fires just before the tame applies, so
 * the animal isn't owned yet; {@link #cancel()} stops the tame.
 *
 * <p>
 * Producer: {@code ServerForgeIngress.onAnimalTame}. Server-side; a client-side firing is dropped at
 * ingress.
 */
public final class AnimalTameSignal extends SatchelSignal<AnimalTameEvent> {

    private final Animal animal;
    private final Player tamer;

    public AnimalTameSignal(AnimalTameEvent forgeEvent) {
        super(forgeEvent);
        this.animal = Objects.requireNonNull(forgeEvent.getAnimal(), "animal");
        this.tamer = Objects.requireNonNull(forgeEvent.getTamer(), "tamer");
    }

    /** The animal being tamed. */
    public Animal animal() {
        return animal;
    }

    /** The player taming it. */
    public Player tamer() {
        return tamer;
    }
}
