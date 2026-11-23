package com.arryn.frontiermode.structure;

import com.arryn.frontiermode.core.FrontierRegistrar;
import com.arryn.frontiermode.structure.common.blocks.CairnCapBlock;
import com.arryn.satchel.common.platform.PlatformBound;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.function.Supplier;

/**
 * What the structure module registers: the Cairn cap, through {@link FrontierRegistrar}. Chiseled Smooth Basalt is a
 * new asset, out of scope (cairn.md); the real block copies vanilla Chiseled Stone Bricks' properties as the stand-in.
 */
@PlatformBound(reason = "registration")
public final class StructureRegisters {

    private StructureRegisters() {
    }

    /** Loads this class, which registers on load. {@code FrontierBoot} calls it once the registrar is installed. */
    public static void load() {
    }

    public static final Supplier<Block> CAIRN_CAP =
            FrontierRegistrar.block("cairn_cap", () -> new CairnCapBlock(
                    BlockBehaviour.Properties.copy(Blocks.CHISELED_STONE_BRICKS).noLootTable()));
}
