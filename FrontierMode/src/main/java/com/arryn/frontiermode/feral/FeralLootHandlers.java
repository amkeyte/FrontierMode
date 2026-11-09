package com.arryn.frontiermode.feral;

import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/**
 * Feral's no-free-loot rule (feral.md § No free loot): a Feral mob drops nothing, however it dies. Both
 * drop paths are closed: the re-entry kill is the obvious exploit ("lure it across the border, let the
 * sweep kill it, no risk taken"), and a combat kill drops through the same vanilla death path, so gating
 * on cause of death would need extra state to tell them apart. XP counts as free loot too.
 *
 * <p>The consumers of {@link FeralDrops} and {@link FeralExperience}. The ingress's resolvers are the
 * Feral record test, so ordinary hostiles, Boss and Sentinels never reach here, and a consumer only
 * vetoes.
 */
final class FeralLootHandlers {

    private FeralLootHandlers() {
    }

    static void onFeralDrops(FeralDrops event) {
        Mob mob = event.mob();
        event.suppress();
        OUT.debug("[Feral] vetoed the drops of " + EntityType.getKey(mob.getType()));
    }

    static void onFeralExperience(FeralExperience event) {
        Mob mob = event.mob();
        event.suppress();
        OUT.debug("[Feral] vetoed the experience of " + EntityType.getKey(mob.getType()));
    }
}
