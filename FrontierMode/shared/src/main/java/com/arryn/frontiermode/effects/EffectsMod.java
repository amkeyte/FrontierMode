package com.arryn.frontiermode.effects;

import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.particles.DustParticleOptions;
import org.joml.Vector3f;

/**
 * Entry point for the Effects module -- cross-cutting particle/sound dispatch that other modules
 * (Boss, Border, Discovery, future systems) call into via
 * {@link com.arryn.frontiermode.effects.server.EffectsAPI} /
 * {@link com.arryn.frontiermode.effects.client.ClientEffectsAPI} rather than rolling their own.
 * See wiki/frontiermode/architecture/effects.md.
 *
 * <p>Deliberately near-empty: no fixture, no bundle, no {@code JigConfig} -- effect dispatch has
 * no persisted or scope-bound state identified so far. The module-shape convention (packages,
 * facade, {@code init()}) is followed for consistency and a stable place to grow into, matching
 * {@code BorderModule}/{@code BossModule}'s own {@code init()} call convention -- not because
 * there's jig/bundle machinery to register today. The trigger for this module to grow real
 * Satchel wiring is a future per-effect-type throttle/cooldown, per effects.md's own "Module
 * shape" section.
 */
public final class EffectsMod {

    private EffectsMod() {
    }

    /**
     * The shared amethyst particle until a custom type is registered: vanilla dust in an amethyst
     * purple, so every consumer (a sentinel's trail, Cairn, the boss's flash) draws the same colour.
     * Dust has no gravity, so a consumer that wants it to stay put can zero its speed.
     */
    public static final DustParticleOptions AMETHYST_DUST =
            new DustParticleOptions(new Vector3f(0.65F, 0.45F, 0.9F), 1.0F);

    /** The Wilds' colour, as {@link #AMETHYST_DUST} is the boss's: vanilla dust in the colour of the poison effect. */
    public static final DustParticleOptions POISON_DUST =
            new DustParticleOptions(new Vector3f(0.31F, 0.58F, 0.19F), 1.0F);

    public static void init() {
        OUT.info("[Effects] init(): Module initiating.");
    }
}
