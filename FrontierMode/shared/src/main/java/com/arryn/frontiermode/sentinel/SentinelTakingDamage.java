package com.arryn.frontiermode.sentinel;

import com.arryn.satchel.common.ingress.ModuleSignal;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDamageEvent;

import java.util.Optional;
import java.util.UUID;

/**
 * A Sentinel is about to take damage, and the amount can still be changed: the Sentinel module's event for Forge's
 * {@link LivingDamageEvent}, server only, at Forge's normal priority (sentinels.md § Sentinels answer armor too). The
 * amount is after armor, protection and absorption and is open to {@link #setAmount}. Its context is the boss id of
 * the Sentinel's Keeper. The Forge event fires for every entity that takes damage, so {@link SentinelTakingDamage#resolve} is the filter:
 * it answers only for a mob that carries the Sentinel record. The ingress's one consumer divides the amount by the
 * Keeper's armor multiplier while that is in force.
 *
 * <p>This class names the Forge event, so the ingress holder takes the event type and the resolver from here.
 * {@link #amount()} reads the live event. Valid only during dispatch.
 */
public final class SentinelTakingDamage extends ModuleSignal<LivingDamageEvent, UUID> {

    /** The Forge event this module event reflects, for the ingress's translate. */
    static final Class<LivingDamageEvent> NATIVE = LivingDamageEvent.class;

    SentinelTakingDamage(LivingDamageEvent forgeEvent, UUID bossId) {
        super(forgeEvent, bossId);
    }

    /** The translate's resolver: the Keeper's boss id for a mob that carries the Sentinel record, empty for anything else. */
    static Optional<UUID> resolve(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) {
            return Optional.empty();
        }
        return SentinelData.read(mob).map(SentinelData.Facts::bossId);
    }

    /** The boss id of the Sentinel's Keeper. */
    public UUID bossId() {
        return context();
    }

    /** The level the Sentinel is in. */
    public Level level() {
        return nativeEvent().getEntity().level();
    }

    /** The damage about to come off, after armor. Live. */
    public float amount() {
        return nativeEvent().getAmount();
    }

    public void setAmount(float amount) {
        nativeEvent().setAmount(amount);
    }
}
