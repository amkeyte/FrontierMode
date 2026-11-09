package com.arryn.frontiermode.structure;

import com.arryn.satchel.common.ingress.ModuleSignal;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;

import java.util.Objects;

/**
 * A player is breaking a Cairn's cap block: the module's event for Forge's {@link BlockEvent.BreakEvent}.
 * Its context is the cap's position. The ingress's resolver passes only a block that is the cap, so every
 * other block a player breaks is dropped before an event is built. {@link StructureIngress} holds its one
 * consumer.
 *
 * <p>It offers no veto: breaking the cap is never prevented, only made costly. Valid only during
 * dispatch: after the consumer returns, a read of the native event throws, and the three facts copied at
 * construction stay readable.
 */
public final class CairnCapBroken extends ModuleSignal<BlockEvent.BreakEvent, BlockPos> {

    private final Level level;
    private final BlockState state;

    CairnCapBroken(BlockEvent.BreakEvent forgeEvent, BlockPos pos) {
        super(forgeEvent, pos);
        this.level = Objects.requireNonNull(forgeEvent.getPlayer().level(), "level");
        this.state = Objects.requireNonNull(forgeEvent.getState(), "state");
    }

    /** The level the block is broken in. */
    public Level level() {
        return level;
    }

    /** The cap's position. */
    public BlockPos pos() {
        return context();
    }

    /** The block state as it was immediately before breaking. */
    public BlockState state() {
        return state;
    }
}
