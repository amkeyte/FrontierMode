package com.arryn.frontiermode.border.server.rules;

/**
 * Player-side interpretation rules for border evaluation.
 *
 * <p>RM_FRO_037 ("Brenda," Frontier Sickness epoch 1 / FRO_099): this class's own placeholder
 * doc called it "intentionally minimal in early versions" with nothing real wired in yet --
 * Frontier Sickness is the first real consumer. Same "safe baseline, replace later" convention
 * {@code BorderConstants}/{@code DefaultBossRules}' own tunables already follow -- none of these
 * numbers are playtest-tuned; wiki/frontiermode/design/exterior.md's own "Open questions" section
 * explicitly leaves exact thresholds and climb/decay rates to Lead Dev to pick and playtest
 * against, which is exactly what this pass does.
 */
public final class PlayerRules {

    private PlayerRules() {}

    // ------------------------------------------------------------------
    // Frontier Sickness severity -- see FrontierSicknessLogic.severityTarget/climbSeverity.
    // ------------------------------------------------------------------

    /** Severity target = frontierDistance * this. Placeholder: roughly 1 severity per block. */
    public static final double SICKNESS_TARGET_SCALE = 1.0;

    /**
     * Fraction of the remaining gap to the target closed per tick -- small, so a quick dash past
     * the edge to scout stays cheap and only camping actually climbs toward the target, per the
     * design page's own framing. At 0.01/tick (20 ticks/sec), severity closes roughly two-thirds
     * of the gap to a fixed target in about 3-4 real seconds -- a starting point, not tuned.
     */
    public static final double SICKNESS_CLIMB_RATE = 0.01;

    // ------------------------------------------------------------------
    // Effect tiers -- illustrative, non-binding sketch from
    // wiki/frontiermode/design/exterior.md#frontier-sickness, turned into concrete severity
    // breakpoints. Each tier is additive (a higher tier keeps every lower tier's effects too),
    // matching that page's own "nausea shows up first... weakness and mining fatigue follow...
    // hunger drain and slowness... and past that, blindness and direct damage" progression.
    // ------------------------------------------------------------------

    public static final double SICKNESS_TIER_1_NAUSEA = 10.0;
    public static final double SICKNESS_TIER_2_WEAKNESS_FATIGUE = 30.0;
    public static final double SICKNESS_TIER_3_HUNGER_SLOWNESS = 60.0;
    public static final double SICKNESS_TIER_4_BLINDNESS_DAMAGE = 100.0;

    /**
     * How often (in ticks) an active tier's effects get refreshed on the afflicted player.
     * Matches {@code BossRules.tellTickInterval()}'s own cadence-gate shape -- no need to touch
     * {@code MobEffectInstance}s every single tick.
     */
    public static final int SICKNESS_EFFECT_REFRESH_INTERVAL_TICKS = 20;

    /**
     * Base duration (ticks) granted to each refreshed effect instance, plus a jitter range added
     * on top -- "each active effect runs down and clears independently, after its own short
     * randomized delay" (design page). Deliberately short: once a tier stops being refreshed
     * (severity has decayed below it), the last-granted duration is what actually runs out, so
     * "short" here is what makes the independent per-effect clearing feel prompt rather than
     * lingering.
     */
    public static final int SICKNESS_EFFECT_BASE_DURATION_TICKS = 100; // 5s
    public static final int SICKNESS_EFFECT_JITTER_TICKS = 40; // up to +2s

    // ------------------------------------------------------------------
    // Sick Wildlife -- density multiplier (FrontierSicknessLogic.wildlifeDensityMultiplier) and
    // the cosmetic tell (particle/sound roll on nearby passive mobs).
    // ------------------------------------------------------------------

    /** Distance (blocks) at which the density boost is already ~63% of the way to its max. */
    public static final double WILDLIFE_DENSITY_FALLOFF_DISTANCE = 15.0;

    /** Flat multiplier the density boost saturates toward far past the Frontier edge. */
    public static final double WILDLIFE_DENSITY_MAX_MULTIPLIER = 2.0;

    /** How often (ticks) the cosmetic tell rolls per player -- mirrors tellTickInterval(). */
    public static final int WILDLIFE_TELL_INTERVAL_TICKS = 20;

    /** Radius (blocks) around a player the cosmetic tell searches for passive mobs. */
    public static final double WILDLIFE_TELL_RADIUS = 16.0;

    /** Independent per-mob roll chance (per tell interval) for the particle and the sound. */
    public static final double WILDLIFE_TELL_PARTICLE_CHANCE = 0.5;
    public static final double WILDLIFE_TELL_SOUND_CHANCE = 0.15;
}
