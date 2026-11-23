package com.arryn.satchel.server.lifecycle;

import com.arryn.satchel.common.platform.PlatformBound;
import com.arryn.satchel.SatchelMod;
import com.arryn.satchel.common.util.MobData;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Concern-grouped ingress for {@link MobData}'s own lifecycle (SAT_094): the two raw NeoForge events
 * that carry a mob's data container across a conversion and copy it onto the damage carriers it
 * spawns. The container itself is a data attachment, which NeoForge attaches on first read, so no
 * event attaches it. Posts no signals -- this is Satchel maintaining its own storage, so module code
 * never sees these events.
 *
 * <p>
 * Not gated on {@code Satchel.isReady()}: containers attach to entities created during world
 * creation, before any scope is ready, and none of this reads Satchel state.
 */
@EventBusSubscriber(
        modid = SatchelMod.MODID,
        bus = EventBusSubscriber.Bus.GAME
)
@PlatformBound(reason = "ingress")
public final class ServerMobDataIngress {

    private ServerMobDataIngress() {}

    /**
     * A converted mob keeps its data (project owner, 2026-10-03): copied from the old mob to the
     * new one, recorded stat changes re-applied. {@code Post} fires after the new mob has joined the
     * level, so the old mob's data wins over anything a join handler already wrote on the new one.
     */
    @SubscribeEvent
    public static void onLivingConversion(LivingConversionEvent.Post e) {
        if (e.getEntity().level().isClientSide()) return;
        if (e.getEntity() instanceof Mob from && e.getOutcome() instanceof Mob to) {
            MobData.copyForConversion(from, to);
        }
    }

    /**
     * Stamps a newly spawned damage carrier with its owner's data. Runs first, so any later join
     * subscriber already sees the stamped carrier. A carrier loaded from disk already holds the
     * copy it was saved with.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityJoinLevel(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || e.loadedFromDisk()) return;
        MobData.stampCarrier(e.getEntity());
    }
}
