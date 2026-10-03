package com.arryn.frontiermode.boss.common.items;

import net.minecraft.world.item.Item;

/**
 * The sun ward: an invisible item a sentinel wears in its head slot so vanilla's sun-ignition check
 * (which skips any Zombie or Skeleton with something on its head) never burns it. See
 * sentinels.md's "Sentinels in daylight".
 *
 * <p>Everything that makes it a ward is elsewhere. Invisible: its model has no geometry, so the
 * head-slot renderer draws nothing. Never wears out: it declares no durability, so vanilla's helmet
 * path takes its no-damage branch and still suppresses ignition. Never drops, never displaced, and
 * equipped at conversion: {@code DefaultBossRules#equipSunWard}. Not obtainable: no recipe, no
 * creative tab.
 */
public final class SunWardItem extends Item {

    public SunWardItem() {
        super(new Item.Properties().stacksTo(1));
    }
}
