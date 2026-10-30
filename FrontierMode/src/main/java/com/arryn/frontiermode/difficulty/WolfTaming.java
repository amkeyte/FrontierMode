package com.arryn.frontiermode.difficulty;

import com.arryn.satchel.common.ingress.ModuleSignal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraftforge.event.entity.living.AnimalTameEvent;

/**
 * A player is about to tame a wolf: the module's event for Forge's {@link AnimalTameEvent}
 * (difficulty.md § Allies). Its context is the wolf. The ingress's resolver passes only a {@link Wolf}, so
 * every other animal is dropped before an event is built.
 * {@link DifficultyHandlers#onWolfTaming} is its one consumer.
 *
 * <p>It fires just before the tame applies, so the wolf is not yet owned. It offers no veto.
 */
public final class WolfTaming extends ModuleSignal<AnimalTameEvent, Wolf> {

    WolfTaming(AnimalTameEvent forgeEvent, Wolf wolf) {
        super(forgeEvent, wolf);
    }

    /** The wolf that is about to be tamed. */
    public Wolf wolf() {
        return context();
    }
}
