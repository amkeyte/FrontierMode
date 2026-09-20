package com.arryn.frontiermode.structure.common;

import com.arryn.frontiermode.structure.StructureModule;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Cairn's own cap block -- the top of the 3-block pillar, and the only block in a Cairn that
 * reacts to anything (the two Mossy Cobblestone base blocks and the 3x3 pad are freely minable,
 * no consequence). Right-click and redstone both trigger the identical pulse handler, per
 * wiki/frontiermode/architecture/cairn.md#the-pulse -- both hooks come free on a custom
 * {@code Block} subclass, no Forge event or Satchel signal needed (see cairn.md#redstone).
 *
 * <p>Redstone input shape (plain power, not comparator-style) and the exact powered-state
 * bookkeeping are this class's own build decision -- cairn.md's own "Redstone" section leaves
 * the exact input shape open ("Lead Dev's to confirm"). {@link #POWERED} exists purely to
 * edge-detect "just became powered" inside {@link #neighborChanged} -- redstone dust holding a
 * charge fires {@code neighborChanged} repeatedly while nothing about the actual signal changes,
 * and re-triggering the pulse on every one of those would read as spam, not a single event.
 */
public class CairnCapBlock extends Block {

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public CairnCapBlock(Properties props) {
        super(props);
        registerDefaultState(defaultBlockState().setValue(POWERED, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    public InteractionResult use(
            BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit
    ) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        StructureModule.triggerPulse(level, pos);
        return InteractionResult.CONSUME;
    }

    @Override
    public void neighborChanged(
            BlockState state, Level level, BlockPos pos,
            Block neighborBlock, BlockPos neighborPos, boolean movedByPiston
    ) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.isClientSide) {
            return;
        }

        boolean powered = level.hasNeighborSignal(pos);
        boolean wasPowered = state.getValue(POWERED);
        if (powered == wasPowered) {
            return;
        }

        level.setBlock(pos, state.setValue(POWERED, powered), 2);
        if (powered) {
            StructureModule.triggerPulse(level, pos);
        }
    }

    /** Reads neighbor signal only -- never emits one of its own. */
    @Override
    public boolean isSignalSource(BlockState state) {
        return false;
    }
}
