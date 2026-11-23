package com.arryn.frontiermode.boss;

import com.arryn.satchel.common.ingress.ModuleSignal;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import java.util.Objects;
import java.util.Optional;

/**
 * A mob of a kind a boss can have as its body has died: the module's event for Forge's {@link LivingDeathEvent}
 * (boss.md § What counts as a defeat). Its context is the mob, not a scope: a boss's body spends most of its life with
 * no mob scope attached, so a death is routine in that window and a scoped event would bounce it. The ingress's
 * resolver passes only an entity whose type is some boss's body; {@link BossJigHandlers#onBossDied} looks the mob up
 * in the roster and ignores one that is not a boss. The ingress is {@code SERVER}, so a death on the client's thread
 * never reaches it.
 *
 * <p>It offers no veto. Valid only during dispatch: after the consumer returns, a read of the native event throws, and
 * the level copied at construction stays readable.
 */
public final class BossDied extends ModuleSignal<LivingDeathEvent, Mob> {

    /** The Forge event this module event reflects, for the ingress's translate. */
    static final Class<LivingDeathEvent> NATIVE = LivingDeathEvent.class;

    private final Level level;

    BossDied(LivingDeathEvent forgeEvent, Mob mob) {
        super(forgeEvent, mob);
        //noinspection resource
        this.level = Objects.requireNonNull(mob.level(), "level");
    }

    /**
     * The translate's resolver, the filter for a death, which fires for every entity: the mob for a mob of a kind some
     * boss can have as its body, empty for anything else.
     */
    static Optional<Mob> resolve(LivingDeathEvent event) {
        if (event.getEntity() instanceof Mob mob && BossMarkers.isEligible(mob)) {
            return Optional.of(mob);
        }
        return Optional.empty();
    }

    /** The mob that died. */
    public Mob mob() {
        return context();
    }

    /** The level it died in. */
    public Level level() {
        return level;
    }
}
