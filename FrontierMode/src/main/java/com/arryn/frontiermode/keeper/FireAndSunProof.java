package com.arryn.frontiermode.keeper;

import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.mob.MobScope;
import com.arryn.satchel.common.trinket.Trinket;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;

/**
 * Keepers take no fire damage and never ignite in sunlight (wiki/frontiermode/architecture/keeper.md § Fire and sun),
 * a boss invariant. The Keeper kind subscribes it on the server; at attach it applies two mechanisms, neither a
 * damage-event subscription, and checks before it applies so a reload or a cleared effect is repaired:
 * <ul>
 *   <li>A permanent Fire Resistance effect covers everything in {@code #minecraft:is_fire} (burning, in-fire, lava,
 *   magma) with no listener. Infinite duration, no particles or icon; effects persist in the entity's NBT, so this
 *   survives unload and reload.</li>
 *   <li>The sun ward ({@link KeeperItems#equipSunWard}) stops the ignition itself, so a Zombie or Skeleton Keeper
 *   doesn't visibly burn in daylight.</li>
 * </ul>
 */
final class FireAndSunProof implements Trinket {

    @Override
    public void onAttach(ScopeInfo info) {
        MobScope scope = info.scopeAs();
        Mob mob = scope.mob();

        if (!mob.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            mob.addEffect(new MobEffectInstance(
                    MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
        }
        if (!mob.getItemBySlot(EquipmentSlot.HEAD).is(KeeperItems.SUN_WARD.get())) {
            KeeperItems.equipSunWard(mob);
        }
    }
}
