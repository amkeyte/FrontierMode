package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.ModuleSignal;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.Optional;

/**
 * {@code neoforge-1.21.1}'s ingress for the boss module: NeoForge's mount, damage and death events translated to
 * {@link BossMount}, {@link BossTakingDamage}, {@link BossDamaged} and {@link BossDied}, with {@link BossConsumers}
 * installed. NeoForge has a damage event for each stage: {@code Pre}, where the amount can still change (before
 * absorption, which a boss does not carry), and {@code Post}, after the health changed (Two Lines § Damage stages on
 * each line). The mod entry registers {@link #build()} through a lambda, so this class loads when the server side
 * boots.
 */
public final class BossIngress {

    private BossIngress() {
    }

    public static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(FrontierKeys.BOSS_INGRESS);
        ingress.consumers(BossConsumers.declare());
        ingress.translate(EntityMountEvent.class, BossMount.class, BossIngress::resolveMountingBoss,
                ForgeBossMount::new);
        ingress.translate(LivingDamageEvent.Pre.class, BossTakingDamage.class, BossIngress::resolveDamagingBoss,
                ForgeBossTakingDamage::new);
        ingress.translate(LivingDamageEvent.Post.class, BossDamaged.class, BossIngress::resolveDamagedBoss,
                ForgeBossDamaged::new);
        ingress.translate(LivingDeathEvent.class, BossDied.class, BossIngress::resolveDyingBody, ForgeBossDied::new);
        return ingress;
    }

    /** The boss's mob scope, for the entity mounting or the entity being mounted; empty when neither is a boss. */
    private static Optional<ScopeInfo> resolveMountingBoss(EntityMountEvent event) {
        return BossMarkers.bossScope(event.getEntityMounting())
                .or(() -> BossMarkers.bossScope(event.getEntityBeingMounted()));
    }

    private static Optional<ScopeInfo> resolveDamagingBoss(LivingDamageEvent.Pre event) {
        return BossMarkers.bossScope(event.getEntity());
    }

    private static Optional<ScopeInfo> resolveDamagedBoss(LivingDamageEvent.Post event) {
        return BossMarkers.bossScope(event.getEntity());
    }

    /** The filter for a death, which fires for every entity: the mob for a mob of a kind some boss can have as its body. */
    private static Optional<Mob> resolveDyingBody(LivingDeathEvent event) {
        if (event.getEntity() instanceof Mob mob && BossMarkers.isEligible(mob)) {
            return Optional.of(mob);
        }
        return Optional.empty();
    }

    /** {@link BossMount} over NeoForge's mount event, named as Satchel names its own. */
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

    /** {@link BossTakingDamage} over NeoForge's {@code LivingDamageEvent.Pre}. */
    static final class ForgeBossTakingDamage extends ModuleSignal<LivingDamageEvent.Pre, ScopeInfo>
            implements BossTakingDamage {

        ForgeBossTakingDamage(LivingDamageEvent.Pre forgeEvent, ScopeInfo bossScope) {
            super(forgeEvent, bossScope);
        }

        @Override
        public DamageSource source() {
            return nativeEvent().getSource();
        }

        @Override
        public float amount() {
            return nativeEvent().getNewDamage();
        }

        @Override
        public void setAmount(float amount) {
            nativeEvent().setNewDamage(amount);
        }
    }

    /** {@link BossDamaged} over NeoForge's {@code LivingDamageEvent.Post}. */
    static final class ForgeBossDamaged extends ModuleSignal<LivingDamageEvent.Post, ScopeInfo>
            implements BossDamaged {

        ForgeBossDamaged(LivingDamageEvent.Post forgeEvent, ScopeInfo bossScope) {
            super(forgeEvent, bossScope);
        }

        @Override
        public DamageSource source() {
            return nativeEvent().getSource();
        }

        /**
         * The event fires after the health changed. A hit that left the boss alive removed exactly the new damage. On
         * a killing blow the health before the hit is not on the event, so the capped amount is {@code TBD.}
         * ({@code PORT_011}); until it is answered a killing blow throws, so the gap cannot pass unseen.
         */
        @Override
        public float healthRemoved() {
            if (nativeEvent().getEntity().getHealth() <= 0f) {
                throw new UnsupportedOperationException(
                        "BossDamaged.healthRemoved on a killing blow is TBD on neoforge-1.21.1 (PORT_011)");
            }
            return Math.max(0f, nativeEvent().getNewDamage());
        }
    }

    /** {@link BossDied} over NeoForge's living-death event. */
    static final class ForgeBossDied extends ModuleSignal<LivingDeathEvent, Mob> implements BossDied {

        ForgeBossDied(LivingDeathEvent forgeEvent, Mob mob) {
            super(forgeEvent, mob);
        }
    }
}
