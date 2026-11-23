package com.arryn.frontiermode.feral;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.ModuleSignal;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

import java.util.Objects;
import java.util.Optional;

/**
 * {@code neoforge-1.21.1}'s ingress for the feral module (feral.md § How Feral hooks the game): NeoForge's join,
 * placement-check, position-check, drops and experience events translated to {@link HostileJoining},
 * {@link WildsPlacementCheck}, {@link WildsPositionCheck}, {@link FeralDrops} and {@link FeralExperience}, with
 * {@link FeralConsumers} installed. Each resolver bounces every firing the module has no business with, by the test its
 * interface holds, so the hot loader events cost a type test and not an event. The two spawn checks each carry their own
 * result type, which their classes bind in the constructor. The mod entry registers {@link #build()} through a lambda,
 * so this class loads when the server side boots.
 */
public final class FeralIngress {

    private FeralIngress() {
    }

    public static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(FrontierKeys.FERAL_INGRESS);
        ingress.consumers(FeralConsumers.declare());
        ingress.translate(EntityJoinLevelEvent.class, HostileJoining.class, FeralIngress::hostile,
                ForgeHostileJoining::new);
        ingress.translate(MobSpawnEvent.SpawnPlacementCheck.class, WildsPlacementCheck.class,
                FeralIngress::wildsPlacement, ForgeWildsPlacementCheck::new);
        ingress.translate(MobSpawnEvent.PositionCheck.class, WildsPositionCheck.class, FeralIngress::wildsPosition,
                ForgeWildsPositionCheck::new);
        ingress.translate(LivingDropsEvent.class, FeralDrops.class, FeralIngress::feralDrops, ForgeFeralDrops::new);
        ingress.translate(LivingExperienceDropEvent.class, FeralExperience.class, FeralIngress::feralExperience,
                ForgeFeralExperience::new);
        return ingress;
    }

    private static Optional<Mob> hostile(EntityJoinLevelEvent event) {
        return event.getEntity() instanceof Mob mob && HostileJoining.passes(mob.getType())
                ? Optional.of(mob) : Optional.empty();
    }

    private static Optional<Level> wildsPlacement(MobSpawnEvent.SpawnPlacementCheck event) {
        Level level = event.getLevel().getLevel();
        return WildsPlacementCheck.passes(event.getEntityType(), level, event.getPos())
                ? Optional.of(level) : Optional.empty();
    }

    private static Optional<Mob> wildsPosition(MobSpawnEvent.PositionCheck event) {
        Mob mob = event.getEntity();
        return WildsPositionCheck.passes(mob) ? Optional.of(mob) : Optional.empty();
    }

    private static Optional<Mob> feralDrops(LivingDropsEvent event) {
        return event.getEntity() instanceof Mob mob && FeralDrops.passes(mob) ? Optional.of(mob) : Optional.empty();
    }

    private static Optional<Mob> feralExperience(LivingExperienceDropEvent event) {
        return event.getEntity() instanceof Mob mob && FeralExperience.passes(mob)
                ? Optional.of(mob) : Optional.empty();
    }

    /** {@link HostileJoining} over NeoForge's entity-join event, named as Satchel names its own. */
    static final class ForgeHostileJoining extends ModuleSignal<EntityJoinLevelEvent, Mob> implements HostileJoining {

        private final Level level;
        private final boolean loadedFromDisk;

        ForgeHostileJoining(EntityJoinLevelEvent forgeEvent, Mob mob) {
            super(forgeEvent, mob);
            this.level = Objects.requireNonNull(forgeEvent.getLevel(), "level");
            this.loadedFromDisk = forgeEvent.loadedFromDisk();
        }

        @Override
        public Level level() {
            return level;
        }

        @Override
        public boolean loadedFromDisk() {
            return loadedFromDisk;
        }
    }

    /** {@link WildsPlacementCheck} over NeoForge's placement check, binding its own result type. */
    static final class ForgeWildsPlacementCheck extends ModuleSignal<MobSpawnEvent.SpawnPlacementCheck, Level>
            implements WildsPlacementCheck {

        private final EntityType<?> entityType;
        private final MobSpawnType spawnType;
        private final BlockPos pos;

        ForgeWildsPlacementCheck(MobSpawnEvent.SpawnPlacementCheck forgeEvent, Level level) {
            super(forgeEvent, level);
            this.entityType = Objects.requireNonNull(forgeEvent.getEntityType(), "entityType");
            this.spawnType = Objects.requireNonNull(forgeEvent.getSpawnType(), "spawnType");
            this.pos = Objects.requireNonNull(forgeEvent.getPos(), "pos");
            bindResult(() -> switch (forgeEvent.getResult()) {
                case SUCCEED -> Verdict.ALLOW;
                case FAIL -> Verdict.DENY;
                case DEFAULT -> Verdict.DEFAULT;
            }, verdict -> forgeEvent.setResult(switch (verdict) {
                case ALLOW -> MobSpawnEvent.SpawnPlacementCheck.Result.SUCCEED;
                case DENY -> MobSpawnEvent.SpawnPlacementCheck.Result.FAIL;
                case DEFAULT -> MobSpawnEvent.SpawnPlacementCheck.Result.DEFAULT;
            }));
        }

        @Override
        public EntityType<?> entityType() {
            return entityType;
        }

        @Override
        public MobSpawnType spawnType() {
            return spawnType;
        }

        @Override
        public BlockPos pos() {
            return pos;
        }
    }

    /** {@link WildsPositionCheck} over NeoForge's position check, binding its own result type. */
    static final class ForgeWildsPositionCheck extends ModuleSignal<MobSpawnEvent.PositionCheck, Mob>
            implements WildsPositionCheck {

        ForgeWildsPositionCheck(MobSpawnEvent.PositionCheck forgeEvent, Mob mob) {
            super(forgeEvent, mob);
            bindResult(() -> switch (forgeEvent.getResult()) {
                case SUCCEED -> Verdict.ALLOW;
                case FAIL -> Verdict.DENY;
                case DEFAULT -> Verdict.DEFAULT;
            }, verdict -> forgeEvent.setResult(switch (verdict) {
                case ALLOW -> MobSpawnEvent.PositionCheck.Result.SUCCEED;
                case DENY -> MobSpawnEvent.PositionCheck.Result.FAIL;
                case DEFAULT -> MobSpawnEvent.PositionCheck.Result.DEFAULT;
            }));
        }
    }

    /** {@link FeralDrops} over NeoForge's living-drops event. */
    static final class ForgeFeralDrops extends ModuleSignal<LivingDropsEvent, Mob> implements FeralDrops {

        ForgeFeralDrops(LivingDropsEvent forgeEvent, Mob mob) {
            super(forgeEvent, mob);
        }

        @Override
        public void suppress() {
            nativeEvent().setCanceled(true);
        }
    }

    /** {@link FeralExperience} over NeoForge's experience-drop event. */
    static final class ForgeFeralExperience extends ModuleSignal<LivingExperienceDropEvent, Mob>
            implements FeralExperience {

        ForgeFeralExperience(LivingExperienceDropEvent forgeEvent, Mob mob) {
            super(forgeEvent, mob);
        }

        @Override
        public void suppress() {
            nativeEvent().setCanceled(true);
        }
    }
}
