package com.arryn.frontiermode.feral;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.core.InfoRequest;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;

import java.util.Optional;

/**
 * The holder class for the feral module's ingress (feral.md § How Feral hooks the game): the five module
 * events ({@link HostileJoining}, {@link WildsPlacementCheck}, {@link WildsPositionCheck},
 * {@link FeralDrops}, {@link FeralExperience}), each with a resolver that bounces every firing the module
 * has no business with, so the hot Forge events cost a type test and not an event, and the consumer of the
 * Info report's {@link InfoRequest}, which adds the Feral record a mob carries.
 * {@link FeralModule#init()} registers {@link #build()} through a lambda, so this class loads when the
 * server side boots.
 */
final class FeralIngress {

    private FeralIngress() {
    }

    static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(FrontierKeys.FERAL_INGRESS);
        ingress.translate(EntityJoinLevelEvent.class, FeralIngress::hostile, HostileJoining::new);
        ingress.translate(MobSpawnEvent.SpawnPlacementCheck.class, FeralIngress::wildsPlacement, WildsPlacementCheck::new);
        ingress.translate(MobSpawnEvent.PositionCheck.class, FeralIngress::wildsPosition, WildsPositionCheck::new);
        ingress.translate(LivingDropsEvent.class, FeralIngress::feralDrops, FeralDrops::new);
        ingress.translate(LivingExperienceDropEvent.class, FeralIngress::feralExperience, FeralExperience::new);
        ingress.listenForAll(HostileJoining.class, FeralTagging::onHostileJoining);
        ingress.listenForAll(WildsPlacementCheck.class, FeralSpawnGateHandlers::onPlacementCheck);
        ingress.listenForAll(WildsPositionCheck.class, FeralSpawnGateHandlers::onPositionCheck);
        ingress.listenForAll(FeralDrops.class, FeralLootHandlers::onFeralDrops);
        ingress.listenForAll(FeralExperience.class, FeralLootHandlers::onFeralExperience);
        ingress.listenForAll(InfoRequest.class, FeralIngress::onInfo);
        return ingress;
    }

    /**
     * The cheap filter for a join, which fires for every entity: only a {@code MobCategory.MONSTER} mob gets
     * an event built. {@code MobCategory.MONSTER}, not {@code instanceof Monster}: a Slime and a Magma Cube
     * are real hostiles that do not extend {@code Monster}. The ingress is {@code SERVER}, so a join on the
     * client's thread never reaches here.
     */
    private static Optional<Mob> hostile(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.getType().getCategory() != MobCategory.MONSTER) {
            return Optional.empty();
        }
        return Optional.of(mob);
    }

    /**
     * The filter for the placement gate, which fires for every natural spawn attempt: only a
     * {@code MobCategory.MONSTER} type at a position in the Wilds gets an event built. The level is the
     * context, because the consumer needs it and the filter has already resolved it.
     */
    private static Optional<ServerLevel> wildsPlacement(MobSpawnEvent.SpawnPlacementCheck event) {
        if (event.getEntityType().getCategory() != MobCategory.MONSTER) {
            return Optional.empty();
        }
        ServerLevel level = event.getLevel().getLevel();
        if (BorderAPI.frontierDistanceAt(level, event.getPos()) <= 0) {
            return Optional.empty();
        }
        return Optional.of(level);
    }

    /** The filter for the position gate: only a {@code MobCategory.MONSTER} mob standing in the Wilds. */
    private static Optional<Mob> wildsPosition(MobSpawnEvent.PositionCheck event) {
        Mob mob = event.getEntity();
        if (mob.getType().getCategory() != MobCategory.MONSTER) {
            return Optional.empty();
        }
        //noinspection resource
        if (BorderAPI.frontierDistanceAt(mob.level(), mob.blockPosition()) <= 0) {
            return Optional.empty();
        }
        return Optional.of(mob);
    }

    /**
     * The filter for a death's drops, which fires for every death in the game: only a mob that carries the
     * Feral record gets an event built.
     */
    private static Optional<Mob> feralDrops(LivingDropsEvent event) {
        return feralMob(event.getEntity());
    }

    /** The filter for a death's experience; the same record test as {@link #feralDrops}. */
    private static Optional<Mob> feralExperience(LivingExperienceDropEvent event) {
        return feralMob(event.getEntity());
    }

    private static Optional<Mob> feralMob(LivingEntity dying) {
        return dying instanceof Mob mob && FeralData.has(mob) ? Optional.of(mob) : Optional.empty();
    }

    /** Registers the Feral answer; the work happens in the supplier, once per report. */
    private static void onInfo(InfoRequest request) {
        OUT.debug("[Feral] InfoRequest reached its consumer");

        for (Object subject : request.subjects()) {
            if (subject instanceof Mob mob) {
                request.add(subject, "feral", () -> feralLine(mob));
            }
        }
    }

    /** The distance the mob was tagged at and whether it is a pack member, or null for a mob with no record. */
    private static String feralLine(Mob mob) {
        return FeralData.read(mob)
                .map(facts -> "  Feral: distance " + facts.distance() + (facts.pack() ? ", pack member" : ""))
                .orElse(null);
    }
}
