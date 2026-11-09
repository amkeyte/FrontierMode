package com.arryn.frontiermode.keeper;

import com.arryn.frontiermode.difficulty.DifficultyAPI;
import com.arryn.frontiermode.difficulty.common.LadderMath;
import com.arryn.frontiermode.keeper.common.fixture.KeeperRecord;
import com.arryn.frontiermode.core.MobSpawns;
import com.arryn.satchel.common.tunable.TuningTable;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * How a Keeper's body is set up when it materializes (wiki/frontiermode/architecture/keeper.md § Keeper stats): the
 * vanilla spawn, then the Keeper's own steps in the spawn's {@code postSetup}: the Killer Bunny's variant, the stat
 * step through Difficulty, and the name. Persistence is {@code Persistent}'s, and fire immunity and the sun ward are
 * {@code FireAndSunProof}'s; neither is set up here.
 */
public final class KeeperSetup {

    // The boss mob's player-facing name, and the text LogFilters matches in vanilla's death log line.
    // See FRO_182.
    public static final String BOSS_NAME = "Amethyst Keeper";

    private KeeperSetup() {
    }

    /**
     * Materialization -- called only once the caller ({@code BossRoster}) has already confirmed
     * {@code Level.isLoaded(position)}. {@code position} is already validated, real terrain by this point (chosen and
     * scored by {@link KeeperPlacement#choosePosition}) -- no in-place Y-resolution or liquid-column check happens
     * here, per border-pregeneration.md's "What this changes in Boss" section. Spawns the Keeper's body and puts it on
     * the Keeper rung of the stat ladder from the Keeper stat tables. Returns empty only if the entity itself couldn't
     * be created or added (not a terrain problem anymore) -- the caller retries next tick, same fixed
     * {@code position}.
     *
     * <p>Deliberately does not call {@code MobScope.getFor(mob)} or touch {@code BossFixture} -- both are Satchel
     * wiring concerns owned by the roster, not a "boss design" decision.
     */
    static Optional<Mob> materialize(Level level, BlockPos position, KeeperRecord keeper) {
        // Border Pregeneration: position was already validated (flatness/hazard-scored) against
        // real, pregenerated terrain when choosePosition() chose it -- no in-place Y-resolution
        // or liquid-column check left to do here. See border-pregeneration.md's "Worked example:
        // Boss placement, revised" and "What this changes in Boss" sections.
        int pathPosition = Math.max(KeeperAPI.pathPositionOf(keeper), 1);
        // A record saved before the body field existed has none: choose it now (not saved back).
        String bodyName = keeper.body().isEmpty() ? KeeperBodies.chooseBody(pathPosition) : keeper.body();
        TuningTable.Row body = KeeperBodies.body(bodyName);
        EntityType<?> type = body.getEntityType("entityType");
        if (!(level instanceof ServerLevel serverLevel)) {
            throw new IllegalStateException("Non server level");
        }

        // VANILLA setup: it is what arms a boss (a Skeleton's bow, a Pillager's crossbow). The boss's
        // own steps run after it, because setup can change max health (a leader zombie multiplies it
        // without healing), so scaling first would leave that boss at a fraction of its health.
        Optional<Mob> spawned = MobSpawns.spawn(serverLevel, type, position, MobSpawns.Setup.VANILLA,
                mob -> {
                }, mob -> {
                    // Vanilla setup rolls a Rabbit's variant, so the Killer Bunny is set after it.
                    if (bodyName.equals("killer_bunny") && mob instanceof Rabbit rabbit) {
                        rabbit.setVariant(Rabbit.Variant.EVIL);
                    }
                    applyKeeperRung(mob, pathPosition, body);
                    name(mob);
                });
        if (spawned.isEmpty()) {
            OUT.warn("[Keeper] materialize(): spawn failed for " + type + " at " + position);
        }
        return spawned;
    }

    /**
     * Puts the Keeper on the Keeper rung of the stat ladder, before it joins the level. Health: the
     * table's value divided by the body's vanilla maximum, so the Keeper has exactly the table's
     * health. Damage: the table's value divided by the body's reference hit (an ordinary Rabbit has
     * none, so no multiplier). Past path position 20 both are the formula, 1 + growth x (p - 1). Every
     * kind of damage scales when it lands, so one number covers swords, arrows, bolts and fangs.
     */
    private static void applyKeeperRung(Mob mob, int pathPosition, TuningTable.Row body) {
        float referenceHit = body.getFloat("referenceHit");
        double healthMultiplier;
        double damageMultiplier = 1.0;
        if (pathPosition <= KeeperBodies.AUTHORED_THROUGH) {
            healthMultiplier = KeeperBodies.authoredHealth(pathPosition)
                    / mob.getAttributeBaseValue(Attributes.MAX_HEALTH);
            if (referenceHit > 0f) {
                damageMultiplier = KeeperBodies.authoredDamage(pathPosition) / referenceHit;
            }
        } else {
            healthMultiplier = LadderMath.multiplier(DifficultyAPI.keeperHealthGrowthTotal(), pathPosition);
            if (referenceHit > 0f) {
                damageMultiplier = LadderMath.multiplier(DifficultyAPI.keeperDamageGrowthTotal(), pathPosition);
            }
        }
        DifficultyAPI.applyKeeperRung(mob, pathPosition, healthMultiplier, damageMultiplier);
    }

    // Players see the name in vanilla death messages ("slain by Amethyst Keeper"), never as a tag.
    private static void name(Mob mob) {
        mob.setCustomName(Component.literal(BOSS_NAME));
        // The tag's visibility is the client-side debug view's to turn on (AdminViewClient); the
        // name itself stays set.
        mob.setCustomNameVisible(false);
    }
}
