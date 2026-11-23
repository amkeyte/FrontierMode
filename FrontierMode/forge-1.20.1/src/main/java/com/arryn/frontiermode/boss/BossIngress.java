package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.ModuleSignal;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.Optional;

/**
 * {@code forge-1.20.1}'s ingress for the boss module: Forge's mount, damage and death events translated to
 * {@link BossMount}, {@link BossTakingDamage}, {@link BossDamaged} and {@link BossDied}, with {@link BossConsumers}
 * installed. Forge has one damage event, so the two damage stages are two translations of it at two priorities (Two
 * Lines § Damage stages on each line). The mod entry registers {@link #build()} through a lambda, so this class loads
 * when the server side boots.
 */
public final class BossIngress {

    private BossIngress() {
    }

    public static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(FrontierKeys.BOSS_INGRESS);
        ingress.consumers(BossConsumers.declare());
        ingress.translate(EntityMountEvent.class, BossMount.class, BossIngress::resolveMountingBoss,
                ForgeBossMount::new);
        // Normal priority: the stage where the amount can still be changed.
        ingress.translate(LivingDamageEvent.class, BossTakingDamage.class, BossIngress::resolveDamagedBoss,
                ForgeBossTakingDamage::new);
        // Lowest, so the amount is what every other listener left.
        ingress.priority(EventPriority.LOWEST).translate(
                LivingDamageEvent.class, BossDamaged.class, BossIngress::resolveDamagedBoss, ForgeBossDamaged::new);
        ingress.translate(LivingDeathEvent.class, BossDied.class, BossIngress::resolveDyingBody, ForgeBossDied::new);
        return ingress;
    }

    /** The boss's mob scope, for the entity mounting or the entity being mounted; empty when neither is a boss. */
    private static Optional<ScopeInfo> resolveMountingBoss(EntityMountEvent event) {
        return BossMarkers.bossScope(event.getEntityMounting())
                .or(() -> BossMarkers.bossScope(event.getEntityBeingMounted()));
    }

    private static Optional<ScopeInfo> resolveDamagedBoss(LivingDamageEvent event) {
        return BossMarkers.bossScope(event.getEntity());
    }

    /** The filter for a death, which fires for every entity: the mob for a mob of a kind some boss can have as its body. */
    private static Optional<Mob> resolveDyingBody(LivingDeathEvent event) {
        if (event.getEntity() instanceof Mob mob && BossMarkers.isEligible(mob)) {
            return Optional.of(mob);
        }
        return Optional.empty();
    }

    /** {@link BossMount} over Forge's mount event. */
    static final class ForgeBossMount extends ModuleSignal<EntityMountEvent, ScopeInfo> implements BossMount {

        ForgeBossMount(EntityMountEvent forgeEvent, ScopeInfo bossScope) {
            super(forgeEvent, bossScope);
        }

        @Override
        public boolean isMounting() {
            return nativeEvent().isMounting();
        }

        @Override
        public void cancel() {
            nativeEvent().setCanceled(true);
        }
    }

    /** {@link BossTakingDamage} over Forge's damage event, as it stands at normal priority. */
    static final class ForgeBossTakingDamage extends ModuleSignal<LivingDamageEvent, ScopeInfo>
            implements BossTakingDamage {

        ForgeBossTakingDamage(LivingDamageEvent forgeEvent, ScopeInfo bossScope) {
            super(forgeEvent, bossScope);
        }

        @Override
        public DamageSource source() {
            return nativeEvent().getSource();
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

    /** {@link BossDamaged} over Forge's damage event, as it stands at the lowest priority. */
    static final class ForgeBossDamaged extends ModuleSignal<LivingDamageEvent, ScopeInfo> implements BossDamaged {

        ForgeBossDamaged(LivingDamageEvent forgeEvent, ScopeInfo bossScope) {
            super(forgeEvent, bossScope);
        }

        @Override
        public DamageSource source() {
            return nativeEvent().getSource();
        }

        /** Forge's event fires before the health changes and does not cap the amount, so the cap is applied here. */
        @Override
        public float healthRemoved() {
            LivingEntity entity = nativeEvent().getEntity();
            return Math.max(0f, Math.min(nativeEvent().getAmount(), entity.getHealth()));
        }
    }

    /** {@link BossDied} over Forge's living-death event. */
    static final class ForgeBossDied extends ModuleSignal<LivingDeathEvent, Mob> implements BossDied {

        ForgeBossDied(LivingDeathEvent forgeEvent, Mob mob) {
            super(forgeEvent, mob);
        }
    }
}
