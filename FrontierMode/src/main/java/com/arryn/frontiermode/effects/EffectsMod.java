package com.arryn.frontiermode.effects;

import com.arryn.satchel.common.util.out.OUT;

/**
 * Entry point for the Effects module -- cross-cutting particle/sound dispatch that other modules
 * (Boss, Border, Discovery, future systems) call into via
 * {@link com.arryn.frontiermode.effects.common.EffectsAPI} /
 * {@link com.arryn.frontiermode.effects.client.ClientEffectsAPI} rather than rolling their own.
 * See wiki/frontiermode/architecture/effects.md and FRO_089.
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

    public static void init() {
        OUT.info("FrontierMode - Effects Module Initiating.");
    }
}
