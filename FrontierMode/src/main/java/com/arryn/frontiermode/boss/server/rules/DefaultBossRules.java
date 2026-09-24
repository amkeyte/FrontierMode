package com.arryn.frontiermode.boss.server.rules;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.effects.common.EffectsAPI;
import com.arryn.satchel.common.tunable.Tunable;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
//import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Optional;

/**
 * Default, opinionated implementation of {@link BossRules} -- a safe baseline meant to be
 * replaced by real balance tuning later (Game Designer/playtest territory), same "safe baseline"
 * framing as {@code DefaultBorderRules.GROWTH_FACTOR}.
 */
public final class DefaultBossRules implements BossRules {

    // Placeholder tier -> mob type table (boss.md "Spawn algorithm"). Ordered as a ramp in what a
    // player has to deal with: passive (Rabbit), conditional (Spider), always-hostile melee
    // (Zombie), ranged (Skeleton), then the rest. Indexed by tier, not layer -- see
    // mobTypeForLayer(). Not a locked curve.
    private static final List<EntityType<? extends Mob>> LAYER_MOBS = List.of(
            EntityType.RABBIT,
            EntityType.SPIDER,
            EntityType.ZOMBIE,
            EntityType.SKELETON,
            EntityType.ZOMBIFIED_PIGLIN,
            EntityType.PILLAGER,
            EntityType.VINDICATOR,
            EntityType.RAVAGER
    );

    // Safe-baseline linear scale, same shape as DefaultBorderRules.GROWTH_FACTOR: +50% max health
    // per layer above the table's own baseline. Not a locked curve.
    private static final double HEALTH_SCALE_PER_LAYER = 0.5;

    // Border Pregeneration: how many candidate chunk-center positions choosePosition() scores
    // before picking a winner -- a tuning number ("safe baseline, replace later"), not settled by
    // border-pregeneration.md itself. Bumped from 5 -> 20 after a real playtest hit "all 5
    // candidates scored hazardous" on a radius-32 (3209-chunk) border and fell back to placing a
    // boss at the world floor -- 5 independent random draws just isn't enough coverage once a
    // disk gets that large, and a heightmap + block-state lookup is cheap enough (this only runs
    // once per boss, not per tick) that quadrupling it is effectively free insurance.
    private static final int POSITION_CANDIDATES = 20;

    // flatnessScore()'s own safe baseline: candidate height compared against four points this far
    // out in each cardinal direction. A tuning number, not an architecture decision.
    private static final int FLATNESS_SAMPLE_DISTANCE = 4;

    // flatnessScore(): a height delta at or above this many blocks scores 0 (steepest); below it
    // scales linearly toward 1 (flattest). A tuning number.
    private static final double FLATNESS_MAX_DELTA = 8.0;

    // FRO_072: each new border centers on wherever its own boss died (BossModule
    // .onLivingDeath -- deathLocation is the grow() center directly). A boss placed near its own
    // border's center barely moves the *next* border's center either, so successive growths were
    // nesting almost concentrically instead of the path actually "crawling" outward -- exactly
    // the reported symptom (heavy grouping, borders eventually overlapping enough to block
    // movement). Fix: bias placement toward this same border's own outer edge -- candidates only
    // sample the outer (1 - EDGE_BIAS_INNER_FRACTION) share of the radius, so wherever this boss
    // ends up (and the *next* border centers on) is reliably well away from this border's own
    // center. A tuning number, not a locked algorithm -- Game Designer/playtest territory once
    // there's a real path shape to tune against.
    private static final double EDGE_BIAS_INNER_FRACTION = 0.6;

    private final RandomSource rng;

    public DefaultBossRules() {
        this(RandomSource.create());
    }

    public DefaultBossRules(RandomSource rng) {
        this.rng = rng;
    }

    @Override
    /*
     * [Arryn] Review this. It may need reworked for NPE and algorithm robustness.
     */
    public BlockPos choosePosition(Level level, Border border) {
        BlockPos bestSafe = null;
        double bestSafeScore = Double.NEGATIVE_INFINITY;
        BlockPos bestAny = null;
        double bestAnyScore = Double.NEGATIVE_INFINITY;

        // FRO_072: bias every candidate toward border's own outer edge -- see
        // EDGE_BIAS_INNER_FRACTION's own doc for why. innerRadius clamps to border.radius() so a
        // small border (radius 0 or 1, shouldn't happen given BorderConstants.MIN_RADIUS but
        // cheap to guard) never produces an invalid inner > outer annulus.
        int innerRadius = Math.min(
                border.radius(), (int) Math.round(border.radius() * EDGE_BIAS_INNER_FRACTION));

        for (int i = 0; i < POSITION_CANDIDATES; i++) {
            BlockPos sample = BorderAPI.MATH.randomPointInAnnulus(rng, border.center(), innerRadius, border.radius());
            BlockPos chunkCenter = chunkCenterOf(sample);
            // FRO_156: snapping to a chunk center can move a candidate up to ~11 blocks, which on a
            // tiny border (layer 0 can be radius <= 16, i.e. one chunk) throws it outside the
            // border entirely. Keep the unsnapped sample -- already inside the annulus -- when the
            // snap would leave the border.
            if (BorderAPI.MATH.distanceOutside(chunkCenter, border.center(), border.radius()) > 0) {
                chunkCenter = sample;
            }
            // The whole border disk is already pregenerated by the time this runs (this method
            // is only ever called once BorderAPI.isPregenReady() has passed) -- reading real
            // height/block data here is safe, not a forced-generation side effect.
            BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, chunkCenter);

            double hazard = hazardScore(level, ground);
            double score = flatnessScore(level, ground) - hazard;

            if (score > bestAnyScore) {
                bestAnyScore = score;
                bestAny = ground;
            }
            // A disqualifying hazard (fluid, world-floor void, or no solid footing -- see
            // hazardScore's own doc) never wins a real spot, no matter how it scores -- an empty
            // column reads back as artificially flat (every sample lands on the same "nothing
            // found" sentinel height, so neighbor deltas are all zero), which is exactly what let
            // a hazardous candidate win outright before this fix caught a real playtest boss
            // landing at y=0 in open space.
            if (hazard < 1.0 && score > bestSafeScore) {
                bestSafeScore = score;
                bestSafe = ground;
            }
        }

        if (bestSafe != null) {
            return bestSafe;
        }

        // Every one of POSITION_CANDIDATES came back disqualified -- genuinely bad luck, or this
        // border's disk is thinner on safe ground than usual. POSITION_CANDIDATES is a
        // compile-time constant > 0 so bestAny is never null here; returning it (rather than
        // throwing) keeps this method's existing "always returns a position" contract, but it's
        // loud on purpose -- a boss placed from this branch is placed somewhere hazardous, and
        // that belongs in the log the moment it happens, not discovered later by someone
        // teleporting out to an empty location.
        OUT.warn("[Boss] choosePosition(): all " + POSITION_CANDIDATES + " candidates for border "
                + Ids.shortId(border.id()) + " scored hazardous -- placing at " + bestAny.getX() + ", "
                + bestAny.getY() + ", " + bestAny.getZ() + " anyway (best of a bad batch).");
        return bestAny;
    }

    private static BlockPos chunkCenterOf(BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        return new BlockPos(chunkX * 16 + 8, pos.getY(), chunkZ * 16 + 8);
    }

    @Override
    public double flatnessScore(Level level, BlockPos candidate) {
        // Safe baseline: compares candidate's own resolved height against its four cardinal
        // neighbors, FLATNESS_SAMPLE_DISTANCE blocks over -- a flat plateau scores near 1.0, a
        // sharp cliff edge scores near 0.0. Not a locked algorithm -- Game Designer/playtest
        // territory once there's something to tune.
        int centerY = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, candidate).getY();

        int maxDelta = 0;
        int[][] offsets = {
                {FLATNESS_SAMPLE_DISTANCE, 0}, {-FLATNESS_SAMPLE_DISTANCE, 0},
                {0, FLATNESS_SAMPLE_DISTANCE}, {0, -FLATNESS_SAMPLE_DISTANCE}
        };
        for (int[] offset : offsets) {
            BlockPos neighbor = new BlockPos(
                    candidate.getX() + offset[0], candidate.getY(), candidate.getZ() + offset[1]);
            int neighborY = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, neighbor).getY();
            maxDelta = Math.max(maxDelta, Math.abs(neighborY - centerY));
        }

        return Math.max(0.0, 1.0 - (maxDelta / FLATNESS_MAX_DELTA));
    }

    @Override
    public double hazardScore(Level level, BlockPos candidate) {
        // Safe baseline: 1.0 (worst, disqualifying in choosePosition) for any of three real
        // hazards --
        //   - the resolved position itself sits in a fluid (open water/lava -- same
        //     heightmap-lands-in-the-fluid signal the pre-Pregeneration materialize() used to
        //     check);
        //   - the column bottoms out at/near the world floor (a genuine "nothing found" void
        //     column reads back at the heightmap's own floor sentinel);
        //   - the block directly underfoot isn't actually solid. A ravine or cave mouth
        //     breaching the surface can pull MOTION_BLOCKING_NO_LEAVES's own result down into
        //     open space well above the world floor -- the two checks above can't see that at
        //     all, which is exactly how a real playtest boss ended up placed at y=0 in a
        //     -64..320 world, nowhere near either floor threshold.
        // 0.0 otherwise. Not a locked algorithm.
        if (!level.getBlockState(candidate).getFluidState().isEmpty()) {
            return 1.0;
        }
        if (candidate.getY() <= level.getMinBuildHeight() + 1) {
            return 1.0;
        }
        BlockPos below = candidate.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
            return 1.0;
        }
        return 0.0;
    }

    // FRO_120 (Cairn): safe-baseline flatness floor for footprintSuitable() below -- a tuning
    // number, not part of the shared BossRules contract (flatnessScore's own continuous score is
    // the contract; where to draw a boolean pass/fail line on it is this implementation's call,
    // same as every other threshold in this class).
    private static final double FOOTPRINT_MIN_FLATNESS = 0.5;

    @Override
    public boolean footprintSuitable(Level level, BlockPos center, int size, int clearance) {
        int half = size / 2;
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                BlockPos column = center.offset(dx, 0, dz);
                BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);

                if (hazardScore(level, ground) >= 1.0) {
                    return false;
                }
                if (flatnessScore(level, ground) < FOOTPRINT_MIN_FLATNESS) {
                    return false;
                }
                for (int dy = 1; dy <= clearance; dy++) {
                    if (!level.getBlockState(ground.above(dy)).isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public Optional<Mob> materialize(Level level, BlockPos position, int layer) {
        // Border Pregeneration: position was already validated (flatness/hazard-scored) against
        // real, pregenerated terrain when choosePosition() chose it -- no in-place Y-resolution
        // or liquid-column check left to do here. See border-pregeneration.md's "Worked example:
        // Boss placement, revised" and "What this changes in Boss" sections.
        EntityType<? extends Mob> type = mobTypeForLayer(layer);
        Mob mob = type.create(level);
        if (mob == null) {
            OUT.warn("[Boss] materialize(): EntityType.create returned null for " + type + " at " + position);
            return Optional.empty();
        }



        mob.moveTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5, rng.nextFloat() * 360.0F, 0.0F);
        if (!(level instanceof ServerLevel serverLevel)) {
            throw new IllegalStateException("Non server level");
        }

        mob.finalizeSpawn(serverLevel, level.getCurrentDifficultyAt(position), MobSpawnType.EVENT, null, null);

        // Never set anywhere for boss mobs until now -- a plain vanilla Mob spawned via
        // finalizeSpawn is still subject to natural despawn (instant if far from every player,
        // otherwise a random roll every so often) unless persistence is explicitly required.
        // Border Pregeneration alone can now run multiple real minutes before this method is
        // even reachable, and the player then still has to travel to find the boss -- more than
        // enough idle time for that despawn roll to claim it before anyone arrives. Same fix
        // Satchel's own health-check canary already uses (SatchelHealth's
        // setPersistenceRequired() call) for the identical reason: "deliberately never killable
        // or wandering off."
        mob.setPersistenceRequired();

        applyStatScaling(mob, layer);
        makeFireImmune(mob);
        tagVisibly(mob, layer);

        if (!level.addFreshEntity(mob)) {
            OUT.warn("[Boss] materialize(): addFreshEntity failed for " + type + " at " + position);
            return Optional.empty();
        }

        return Optional.of(mob);
    }

    // Tier 0 covers layers 0-1; from layer 2 on the tier steps every bandWidth layers, capped at
    // the table's last entry. bandWidth is the pacing dial (a CSV cell, not a rebuild); the linear
    // health/damage scale in applyStatScaling() is the separate difficulty dial.
    private EntityType<? extends Mob> mobTypeForLayer(int layer) {
        int bandWidth = Math.max(1, new Tunable("mobTierBandWidth", 1, 20, 3).intValue());
        int tier = layer <= 1 ? 0 : Math.min(1 + (layer - 2) / bandWidth, LAYER_MOBS.size() - 1);
        return LAYER_MOBS.get(tier);
    }

    // Boss invariant (boss.md Scope): no fire damage for any boss. A permanent Fire Resistance
    // effect covers everything in #minecraft:is_fire (burning, in-fire, lava, magma) with no
    // damage-event subscription. Infinite duration, no particles/icon; effects persist in the
    // entity's NBT, so this survives unload/reload without re-application. The burning visual may
    // still show in daylight -- Fire Resistance blocks the damage, not the flame render.
    private void makeFireImmune(Mob mob) {
        mob.addEffect(new MobEffectInstance(
                MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
    }

    private void applyStatScaling(Mob mob, int layer) {
        double factor = 1.0 + (HEALTH_SCALE_PER_LAYER * Math.max(layer, 0));

        var healthAttr = mob.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(healthAttr.getBaseValue() * factor);
            mob.setHealth(mob.getMaxHealth());
        }

        var damageAttr = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damageAttr != null) {
            damageAttr.setBaseValue(damageAttr.getBaseValue() * factor);
        }
    }

    // Project owner request (2026-09-08): live debug toggle for Boss/Guardian glow -- see
    // BossRules#glowEnabled's own doc for why this is mutable state on an otherwise
    // strategy/baseline interface. Not persisted -- resets to true (the pre-toggle default) on
    // server restart, same "off/on signal" ephemeral-state discipline as every other debug-only
    // flag in this codebase (WorldBordersRenderer.visible, BossTellFixture's rotation maps, etc.).
    private boolean glowEnabled = true;

    @Override
    public boolean glowEnabled() {
        return glowEnabled;
    }

    @Override
    public void setGlowEnabled(boolean value) {
        glowEnabled = value;
    }

    private void tagVisibly(Mob mob, int layer) {
        // "No discovery aids" (Tier 1) means findable by looking, not invisible until you already
        // know -- a lightweight visible marker, not a real discovery mechanic (that's Tier 2).
        mob.setCustomName(Component.literal("Boss (Layer " + layer + ")"));
        mob.setCustomNameVisible(true);
        mob.setGlowingTag(glowEnabled);
    }

    // FRO_160: non-player-death respawn tuning -- see BossRules for the meaning of each.
    @Override
    public int respawnDelayTicks() {
        return new Tunable("respawnDelayTicks", 1, 6000, 100).intValue();
    }

    @Override
    public int maxConsecutiveNonPlayerDeaths() {
        return new Tunable("maxConsecutiveNonPlayerDeaths", 1, 100, 5).intValue();
    }

    // ------------------------------------------------------------------
    // FRO_087 (Janice): BossTellFixture tunable baseline values
    // ------------------------------------------------------------------

    @Override
    public int tellTickInterval() {
        // ~1 second at 20 TPS. Safe baseline; playtest territory. FRO_142: Tunable-backed (SAT_074).
        return new Tunable("tellTickInterval", 1, 200, 20).intValue();
    }

    @Override
    public double tellParticleCoefficient() {
        // Was temporarily maxed to 1.0 as a location-debug aid alongside PARTICLE_COUNT's bump
        // in BossTellFixture (player call, 2026-09-06: "make it a ridiculous amount to see where
        // they're actually showing up") -- guaranteed a particle fire on every tick-interval
        // where intensity > 0, so a walk toward/around a boss revealed the tell radius without
        // waiting on RNG. Scatter shape confirmed good ("that's about where I want it"), so
        // dialed back to the original 70% tuned baseline -- roll is intensity x this coefficient,
        // peaking at 1.0 right at the boss and fading to 0.0 at the border edge. Playtest
        // territory, tune further if it now reads too sparse without the debug multiplier.
        // FRO_142: Tunable-backed (SAT_074) -- [0,1] bound, this coefficient is a probability roll.
        return new Tunable("tellParticleCoefficient", 0f, 1f, 0.7f).floatValue();
    }

    @Override
    public double tellSoundCoefficient() {
        // 5% roll at maximum intensity -- sounds rarer than particles by design.
        // Safe baseline; playtest territory. FRO_142: Tunable-backed (SAT_074).
        return new Tunable("tellSoundCoefficient", 0f, 1f, 0.05f).floatValue();
    }

    @Override
    public int tellSoundCooldownTicks() {
        // 30 seconds at 20 TPS -- see BossRules#tellSoundCooldownTicks's own doc for why this
        // exists (round-robin, 4x ~30s clips, project owner request 2026-09-08). Safe baseline;
        // playtest territory, and worth revisiting per-clip if the 4 supplied sounds aren't all
        // the same length. FRO_142: Tunable-backed (SAT_074).
        return new Tunable("tellSoundCooldownTicks", 0, 6000, 600).intValue();
    }

    // ------------------------------------------------------------------
    // RM_FRO_029 (Gloria): BossGuardiansFixture tunable baseline values
    // ------------------------------------------------------------------

    // FRO_142: GUARDIAN_PLACEMENT_COEFFICIENT/_RADIUS_FRACTION/_TIER_COUNT/_HEALTH_SCALE are now
    // Tunable-backed (SAT_074), constructed inside their own accessor methods below rather than
    // cached as fields -- a Tunable is constructed fresh at point of use, not held onto (see that
    // class's own doc). GUARDIAN_TEAM_NAME (String) below is Tunable-backed too;
    // GUARDIAN_TEAM_COLOR stays a plain field -- an enum isn't representable by Tunable's
    // float/int/String shape.

    // Single shared team every guardian regardless of tier joins -- see
    // wiki/frontiermode/architecture/effects.md#team-assignment-persistent-visual-state. Dark, to
    // read as distinct from Boss's own unteamed (vanilla white) glow at a glance, especially
    // underground. Safe baseline; playtest territory.
    private static final ChatFormatting GUARDIAN_TEAM_COLOR = ChatFormatting.DARK_PURPLE;

    @Override
    public double guardianPlacementCoefficient() {
        // 2026-09-07 playtest, fourth and (per Arryn) final bump for this round: 0.15 -> 0.4 -> 0.6.
        // Max 60% chance per spawn attempt at intensity 1.0 (right on top of the boss), decaying with
        // placementIntensity same as always. Still a roll, not a guarantee, so ordinary vanilla spawns
        // still happen near the boss too. Called "good for now" after this value -- further tuning is
        // a fresh playtest decision, not an assumed follow-up. [0,1] bound -- a probability roll.
        return new Tunable("guardianPlacementCoefficient", 0f, 1f, 0.6f).floatValue();
    }

    @Override
    public double guardianPlacementRadiusFraction() {
        // 2026-09-07 playtest, revised twice same session: started at the full border radius (read as
        // "clustered right on the boss, nothing beyond ~30 blocks" on a large border -- the LINEAR
        // ramp was mathematically correct but imperceptibly thin across the whole disc); briefly tried
        // FLAT/uniform across the whole border (reverted -- "doesn't act as a tell at all," no gradient
        // to notice at all). Settled: same LINEAR formula, same direction (climbs toward the boss), but
        // normalized against half the border's own radius instead of the whole thing -- a real,
        // noticeable ramp concentrated in a smaller ring around the boss. Safe baseline; playtest
        // territory, same as guardianPlacementCoefficient above. [0,1] bound -- a fraction of radius.
        return new Tunable("guardianPlacementRadiusFraction", 0f, 1f, 0.5f).floatValue();
    }

    @Override
    public int guardianTier(double difficultyIntensity) {
        // Difficulty-intensity tier bucket count -- difficultyIntensity in [0,1] split evenly into
        // this many tiers (0..tierCount-1). Safe baseline, same "flat table" framing as LAYER_MOBS
        // above. Read once per call, not per Math.floor/Math.min below, to avoid constructing two
        // separate Tunables for the same logical value in one invocation.
        int tierCount = new Tunable("guardianTierCount", 1, 20, 4).intValue();
        double d = Math.max(0.0, Math.min(1.0, difficultyIntensity));
        int tier = (int) Math.floor(d * tierCount);
        return Math.min(tier, tierCount - 1);
    }

    @Override
    public void tagGuardian(Mob mob, int tier, double difficultyIntensity, ServerLevel level) {
        mob.setCustomName(Component.literal(
                "GM-" + tier + "[" + Math.round(difficultyIntensity * 100) + "%]"));
        mob.setCustomNameVisible(true);

        // EffectsAPI.assignToTeam takes the Scoreboard explicitly rather than reading it off the
        // entity -- Entity has no getScoreboard() accessor in the real 1.20.1 API (checked against
        // real Forge/Mojang-mapped sources; the wiki page's own sketch assumed one existed via
        // entity.getScoreboard(), which doesn't compile).
        Scoreboard scoreboard = level.getScoreboard();
        String teamName = new Tunable("guardianTeamName", "guardian").stringValue();
        PlayerTeam team = EffectsAPI.ensureTeam(scoreboard, teamName, GUARDIAN_TEAM_COLOR);
        EffectsAPI.assignToTeam(mob, scoreboard, team);

        mob.setGlowingTag(glowEnabled);
    }

    @Override
    public void applyGuardianStatScaling(Mob mob, double difficultyIntensity) {
        // Same "+X% per unit of intensity" shape as HEALTH_SCALE_PER_LAYER, but keyed off a
        // continuous [0,1] intensity rather than a discrete layer -- +100% max health/attack damage
        // at difficultyIntensity == 1.0 (right on top of the boss), scaling down linearly toward the
        // border's edge. Safe baseline; playtest territory. FRO_142: Tunable-backed (SAT_074).
        double guardianHealthScale = new Tunable("guardianHealthScale", 0f, 10f, 1.0f).floatValue();
        double factor = 1.0 + (guardianHealthScale * difficultyIntensity);

        var healthAttr = mob.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(healthAttr.getBaseValue() * factor);
            mob.setHealth(mob.getMaxHealth());
        }
        var damageAttr = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damageAttr != null) {
            damageAttr.setBaseValue(damageAttr.getBaseValue() * factor);
        }
    }
}
