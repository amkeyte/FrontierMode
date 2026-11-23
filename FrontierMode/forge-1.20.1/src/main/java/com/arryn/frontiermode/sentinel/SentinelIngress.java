package com.arryn.frontiermode.sentinel;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.ModuleSignal;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingConversionEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code forge-1.20.1}'s ingress for the Sentinel module (wiki/frontiermode/architecture/sentinels.md § Class map):
 * Forge's join, damage, placement-check, position-check and pre-conversion events translated to
 * {@link SentinelJoining}, {@link SentinelTakingDamage}, {@link SentinelPlacementCheck}, {@link SentinelPositionCheck}
 * and {@link SentinelConverting}, with {@link SentinelConsumers} installed. Each resolver is the cheap filter and calls
 * the test its interface holds. The mod entry registers {@link #build()} through a lambda, so this class loads when
 * the server side boots.
 */
public final class SentinelIngress {

    private SentinelIngress() {
    }

    public static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(FrontierKeys.SENTINEL_INGRESS);
        ingress.consumers(SentinelConsumers.declare());
        ingress.translate(LivingDamageEvent.class, SentinelTakingDamage.class, SentinelIngress::resolveDamaged,
                ForgeSentinelTakingDamage::new);
        ingress.translate(EntityJoinLevelEvent.class, SentinelJoining.class, SentinelIngress::resolveJoining,
                ForgeSentinelJoining::new);
        ingress.translate(MobSpawnEvent.SpawnPlacementCheck.class, SentinelPlacementCheck.class,
                SentinelIngress::resolvePlacement, ForgeSentinelPlacementCheck::new);
        ingress.translate(MobSpawnEvent.PositionCheck.class, SentinelPositionCheck.class,
                SentinelIngress::resolvePosition, ForgeSentinelPositionCheck::new);
        ingress.translate(LivingConversionEvent.Pre.class, SentinelConverting.class, SentinelIngress::resolveConverting,
                ForgeSentinelConverting::new);
        return ingress;
    }

    private static Optional<UUID> resolveDamaged(LivingDamageEvent event) {
        return SentinelTakingDamage.bossIdOf(event.getEntity());
    }

    private static Optional<Mob> resolveJoining(EntityJoinLevelEvent event) {
        return event.getEntity() instanceof Mob mob && SentinelJoining.passes(mob) ? Optional.of(mob) : Optional.empty();
    }

    private static Optional<Level> resolvePlacement(MobSpawnEvent.SpawnPlacementCheck event) {
        return SentinelPlacementCheck.passes(event.getEntityType(), event.getSpawnType(), event.getDefaultResult())
                ? Optional.of(event.getLevel().getLevel()) : Optional.empty();
    }

    private static Optional<Mob> resolvePosition(MobSpawnEvent.PositionCheck event) {
        return SentinelPositionCheck.passes(event.getSpawnType()) ? Optional.of(event.getEntity()) : Optional.empty();
    }

    private static Optional<LivingEntity> resolveConverting(LivingConversionEvent.Pre event) {
        return SentinelConverting.passes(event.getEntity()) ? Optional.of(event.getEntity()) : Optional.empty();
    }

    /** {@link SentinelTakingDamage} over Forge's damage event, at normal priority. */
    static final class ForgeSentinelTakingDamage extends ModuleSignal<LivingDamageEvent, UUID>
            implements SentinelTakingDamage {

        ForgeSentinelTakingDamage(LivingDamageEvent forgeEvent, UUID bossId) {
            super(forgeEvent, bossId);
        }

        @Override
        public Level level() {
            return nativeEvent().getEntity().level();
        }

        @Override
        public float amount() {
            return nativeEvent().getAmount();
        }

        @Override
        public void setAmount(float amount) {
            nativeEvent().setAmount(amount);
        }
    }

    /** {@link SentinelJoining} over Forge's entity-join event. */
    static final class ForgeSentinelJoining extends ModuleSignal<EntityJoinLevelEvent, Mob>
            implements SentinelJoining {

        private final Level level;
        private final boolean loadedFromDisk;

        ForgeSentinelJoining(EntityJoinLevelEvent forgeEvent, Mob mob) {
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

    /** {@link SentinelPlacementCheck} over Forge's placement check, whose result Satchel binds. */
    static final class ForgeSentinelPlacementCheck extends ModuleSignal<MobSpawnEvent.SpawnPlacementCheck, Level>
            implements SentinelPlacementCheck {

        private final EntityType<?> entityType;
        private final MobSpawnType spawnType;
        private final BlockPos pos;

        ForgeSentinelPlacementCheck(MobSpawnEvent.SpawnPlacementCheck forgeEvent, Level level) {
            super(forgeEvent, level);
            this.entityType = Objects.requireNonNull(forgeEvent.getEntityType(), "entityType");
            this.spawnType = Objects.requireNonNull(forgeEvent.getSpawnType(), "spawnType");
            this.pos = Objects.requireNonNull(forgeEvent.getPos(), "pos");
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

    /** {@link SentinelPositionCheck} over Forge's position check, whose result Satchel binds. */
    static final class ForgeSentinelPositionCheck extends ModuleSignal<MobSpawnEvent.PositionCheck, Mob>
            implements SentinelPositionCheck {

        ForgeSentinelPositionCheck(MobSpawnEvent.PositionCheck forgeEvent, Mob mob) {
            super(forgeEvent, mob);
        }
    }

    /** {@link SentinelConverting} over Forge's pre-conversion event. */
    static final class ForgeSentinelConverting extends ModuleSignal<LivingConversionEvent.Pre, LivingEntity>
            implements SentinelConverting {

        ForgeSentinelConverting(LivingConversionEvent.Pre forgeEvent, LivingEntity mob) {
            super(forgeEvent, mob);
        }

        @Override
        public void cancel() {
            nativeEvent().setCanceled(true);
        }
    }
}
