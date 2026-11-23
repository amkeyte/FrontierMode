package com.arryn.frontiermode.structure;

import com.arryn.satchel.common.ingress.ModuleEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A player is breaking a Cairn's cap block: the module's event for the loader's block-break event, on both lines. Its
 * context is the cap's position. Each line's resolver passes only a block that {@link #passes}, so every other block a
 * player breaks is dropped before an event is built. {@link StructureConsumers} holds its one consumer.
 *
 * <p>It offers no veto: breaking the cap is never prevented, only made costly. Valid only during dispatch: the three
 * facts a line copies at construction stay readable.
 */
public interface CairnCapBroken extends ModuleEvent<BlockPos> {

    /**
     * The resolver's test, the cheap filter: a block break fires for every block a player breaks, so only the Cairn cap
     * gets an event built. The base blocks and the pad carry no reaction of their own.
     */
    static boolean passes(BlockState state) {
        return state.is(StructureRegisters.CAIRN_CAP.get());
    }

    /** The level the block is broken in. */
    Level level();

    /** The cap's position. */
    default BlockPos pos() {
        return context();
    }

    /** The block state as it was immediately before breaking. */
    BlockState state();
}
