package com.arryn.frontiermode.structure;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.ModuleSignal;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;

import java.util.Objects;
import java.util.Optional;

/**
 * {@code forge-1.20.1}'s ingress for the structure module: Forge's {@link BlockEvent.BreakEvent} translated to
 * {@link CairnCapBroken}, with {@link StructureConsumers} installed. The mod entry registers {@link #build()} through
 * a lambda, so this class loads when the server side boots.
 */
public final class StructureIngress {

    private StructureIngress() {
    }

    public static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(FrontierKeys.STRUCTURE_INGRESS);
        ingress.translate(BlockEvent.BreakEvent.class, CairnCapBroken.class, StructureIngress::resolveCap,
                ForgeCairnCapBroken::new);
        ingress.consumers(StructureConsumers.declare());
        return ingress;
    }

    /** The cheap filter: only the Cairn cap gets an event built, with its position as the context. */
    private static Optional<BlockPos> resolveCap(BlockEvent.BreakEvent event) {
        return CairnCapBroken.passes(event.getState()) ? Optional.of(event.getPos()) : Optional.empty();
    }

    /** {@link CairnCapBroken} over Forge's block-break event. */
    static final class ForgeCairnCapBroken extends ModuleSignal<BlockEvent.BreakEvent, BlockPos>
            implements CairnCapBroken {

        private final Level level;
        private final BlockState state;

        ForgeCairnCapBroken(BlockEvent.BreakEvent forgeEvent, BlockPos pos) {
            super(forgeEvent, pos);
            this.level = Objects.requireNonNull(forgeEvent.getPlayer().level(), "level");
            this.state = Objects.requireNonNull(forgeEvent.getState(), "state");
        }

        @Override
        public Level level() {
            return level;
        }

        @Override
        public BlockState state() {
            return state;
        }
    }
}
