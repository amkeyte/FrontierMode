package com.arryn.frontiermode.admin;

import com.arryn.satchel.common.ingress.ModuleSignal;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.event.entity.living.LivingDropsEvent;

import java.util.Collection;

/**
 * A player's death drops are about to become item entities: the module's event for Forge's
 * {@link LivingDropsEvent}. Its context is the dying player's host scope, so the stash Trinket on that
 * scope hears it through its own subscription; the resolver passes only a player.
 */
public final class SoulboundDeathDrops extends ModuleSignal<LivingDropsEvent, ScopeInfo> {

    SoulboundDeathDrops(LivingDropsEvent forgeEvent, ScopeInfo scope) {
        super(forgeEvent, scope);
    }

    /**
     * The live drop collection: removing an entry keeps it from becoming an item entity. Vanilla's own
     * death handling has already cleared the player's real inventory by this point, so this is the only
     * place left to intercept an item.
     */
    public Collection<ItemEntity> drops() {
        return nativeEvent().getDrops();
    }
}
