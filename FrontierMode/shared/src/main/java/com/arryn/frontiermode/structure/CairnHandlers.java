package com.arryn.frontiermode.structure;

import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** What the structure module does when a Cairn's cap is broken: the capstone explosion. */
final class CairnHandlers {

    // Capstone explosion power. 1.0F is effectively harmless -- Level.explode()'s
    // entity-damage/knockback pass only touches entities within (power * 2) blocks of the
    // center, so at 1.0F only something standing almost exactly on top of the cap would feel anything
    // at all; ExplosionInteraction.NONE (block-damage-free) doesn't affect that pass, only
    // whether blocks themselves get destroyed. 2.0F widens the hit radius to 4 blocks and scales
    // up damage/knockback with it -- still well under a Creeper's 3.0F or TNT's 4.0F. Damage
    // scales sharply with this value and further with distance/armor, so "a heart and some
    // knockback" at typical break range is an estimate, not a guarantee -- tune further
    // if it reads as too weak (or too strong) in practice.
    private static final float CAPSTONE_EXPLOSION_POWER = 2.0F;

    private CairnHandlers() {
    }

    /**
     * Breaking the cap is never prevented, only made costly (cairn.md): the event offers no veto. Only
     * the cap block reaches here; the base blocks and pad carry no reaction of their own by construction
     * (they're plain {@code Blocks.MOSSY_COBBLESTONE}, not this module's own block class).
     */
    static void onCapBroken(CairnCapBroken broken) {
        OUT.debug("[Structure] CairnCapBroken reached its consumer at " + broken.pos());

        Level level = broken.level();
        BlockPos pos = broken.pos();
        BlockPos anchorPos = pos.below(CairnPlacer.PILLAR_HEIGHT);

        // Extra "is this really a tracked Cairn" gate beyond the block-type check in the resolver --
        // defends against a stray world-placed cap (debug/creative give) triggering an explosion with
        // no real record behind it.
        boolean isTrackedCairn = StructureAPI.cairnFixture(level)
                .flatMap(f -> f.getByAnchor(anchorPos))
                .isPresent();
        if (!isTrackedCairn) {
            return;
        }

        // Block-damage-free (ExplosionInteraction.NONE) -- punishes the break without griefing
        // anything around it. See CAPSTONE_EXPLOSION_POWER's own doc for the damage/knockback
        // tuning this power value controls.
        level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                CAPSTONE_EXPLOSION_POWER, Level.ExplosionInteraction.NONE);
    }
}
