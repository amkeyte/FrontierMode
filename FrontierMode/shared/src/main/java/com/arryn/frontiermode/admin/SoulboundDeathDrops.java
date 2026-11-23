package com.arryn.frontiermode.admin;

import com.arryn.satchel.common.ingress.ModuleEvent;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.Collection;

/**
 * A player's death drops are about to become item entities: the module's event for the loader's living-drops event, on
 * both lines. Its context is the dying player's host scope, so the stash Trinket on that scope hears it through its own
 * subscription; each line's resolver passes only a player.
 */
public interface SoulboundDeathDrops extends ModuleEvent<ScopeInfo> {

    /**
     * The live drop collection: removing an entry keeps it from becoming an item entity. Vanilla's own death handling has
     * already cleared the player's real inventory by this point, so this is the only place left to intercept an item.
     */
    Collection<ItemEntity> drops();
}
