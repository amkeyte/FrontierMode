package com.arryn.satchel.server.lifecycle;

import com.arryn.satchel.SatchelMod;
import com.arryn.satchel.common.util.MobData;
import com.arryn.satchel.common.util.MobDataStore;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingConversionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Concern-grouped ingress for {@link MobData}'s own lifecycle (SAT_094): the three raw Forge events
 * that keep a mob's data container attached, carried across a conversion, and copied onto the
 * damage carriers it spawns. Posts no signals -- this is Satchel maintaining its own storage, so
 * module code never sees these events.
 *
 * <p>
 * Not gated on {@code Satchel.isReady()}: containers attach to entities created during world
 * creation, before any scope is ready, and none of this reads Satchel state.
 */
@Mod.EventBusSubscriber(
        modid = SatchelMod.MODID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class ServerMobDataIngress {

    private ServerMobDataIngress() {}

    /** Gives every server-side mob and damage carrier its container, as the entity is constructed. */
    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> e) {
        if (MobDataStore.attachesTo(e.getObject())) {
            e.addCapability(MobDataStore.ID, MobDataStore.newProvider());
        }
    }

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
