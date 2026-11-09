package com.arryn.frontiermode.structure;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.satchel.common.ingress.IngressConfig;
import net.minecraft.core.BlockPos;
import net.minecraftforge.event.level.BlockEvent;

import java.util.Optional;

/**
 * The holder class for the structure ingress. {@link StructureModule#init()} registers {@link #build()}
 * through a lambda, so this class loads when the server side boots.
 */
final class StructureIngress {

    private StructureIngress() {
    }

    static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(FrontierKeys.STRUCTURE_INGRESS);
        ingress.translate(BlockEvent.BreakEvent.class, StructureIngress::resolveCap, CairnCapBroken::new);
        ingress.listenForAll(CairnCapBroken.class, CairnHandlers::onCapBroken);
        return ingress;
    }

    /**
     * The cheap filter: a block break fires for every block a player breaks, so only the Cairn cap gets
     * an event built, with its position as the context. The base blocks and the pad carry no reaction
     * of their own.
     */
    private static Optional<BlockPos> resolveCap(BlockEvent.BreakEvent event) {
        if (event.getState().is(StructureModule.CAIRN_CAP.get())) {
            return Optional.of(event.getPos());
        }
        return Optional.empty();
    }
}
