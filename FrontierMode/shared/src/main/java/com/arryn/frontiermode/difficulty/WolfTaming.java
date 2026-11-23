package com.arryn.frontiermode.difficulty;

import com.arryn.satchel.common.ingress.ModuleEvent;
import net.minecraft.world.entity.animal.Wolf;

/**
 * A player is about to tame a wolf: the module's event for the loader's animal-tame event, on both lines
 * (difficulty.md § Allies). Its context is the wolf. Each line's resolver passes only a {@link Wolf}, so every other
 * animal is dropped before an event is built. {@link DifficultyHandlers#onWolfTaming} is its one consumer.
 *
 * <p>It fires just before the tame applies, so the wolf is not yet owned. It offers no veto.
 */
public interface WolfTaming extends ModuleEvent<Wolf> {

    /** The wolf that is about to be tamed. */
    default Wolf wolf() {
        return context();
    }
}
