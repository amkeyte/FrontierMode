package com.arryn.satchel.server.lifecycle;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.SatchelMod;
import com.arryn.satchel.common.lifecycle.EntityJoinLevelSignal;
import com.arryn.satchel.common.lifecycle.MobSpawnFinalizeSignal;
import com.arryn.satchel.common.lifecycle.SpawnPlacementCheckSignal;
import com.arryn.satchel.common.lifecycle.SpawnPositionCheckSignal;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge ingress for the mob/entity spawn-lifecycle concern (SAT_076) -- a genuinely new,
 * concern-grouped ingress class per [Forge Integration & Sidedness Contract §
 * "Growing Satchel's own ingress isn't limited to adding another handler onto
 * ServerForgeIngress/ClientForgeIngress themselves"], not a fifth handler bolted onto {@code
 * ServerForgeIngress}. {@link MobSpawnEvent.FinalizeSpawn}, {@link
 * MobSpawnEvent.SpawnPlacementCheck}, {@link MobSpawnEvent.PositionCheck}, and {@link
 * EntityJoinLevelEvent} are one real cluster, not a
 * grab-bag -- {@code MobSpawnEvent}'s own class doc names {@link EntityJoinLevelEvent} as the tail
 * end of the same spawn flow ("if the spawn was not cancelled... EntityJoinLevelEvent is fired as
 * the entity enters the world").
 *
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Post {@link MobSpawnFinalizeSignal}/{@link SpawnPlacementCheckSignal}/{@link
 *   SpawnPositionCheckSignal}/{@link EntityJoinLevelSignal} for the four raw Forge events in this
 *   cluster</li>
 * </ul>
 *
 * <p>
 * This class does NOT:
 * <ul>
 *   <li>Bootstrap the foundation, or bind {@code LogicalSideContext}</li>
 *   <li>Advance scopeInfo lifecycle state</li>
 * </ul>
 * No {@code BOOTER.bindFoundation()} call anywhere here, same reasoning as {@code
 * ServerForgeIngress.onLivingDeath}: every event in this cluster fires mid-gameplay, long after
 * the foundation is established -- the {@link Satchel#isReady()} guard on each handler is the
 * real safety net.
 */
@Mod.EventBusSubscriber(
        modid = SatchelMod.MODID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class ServerSpawnIngress {

    private ServerSpawnIngress() {}

    /* =============================================================
     * Mob spawn finalize — MobSpawnFinalizeSignal
     * ========================================================== */

    /**
     * Posts {@link MobSpawnFinalizeSignal} to {@link com.arryn.satchel.common.lifecycle.SatchelEventBus}
     * just before {@code Mob#finalizeSpawn} runs. No {@code isClientSide} guard needed: {@link
     * MobSpawnEvent.FinalizeSpawn}'s own doc says it's "only fired on the logical server."
     */
    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn e) {
        if (!Satchel.isReady()) {
            OUT.debug("[MobSpawnFinalizeSignal] gate: Satchel not ready -- skipping");
            return;
        }

        Satchel.require().eventBus().post(new MobSpawnFinalizeSignal(e));
    }

    /* =============================================================
     * Spawn placement check — SpawnPlacementCheckSignal
     * ========================================================== */

    /**
     * Posts {@link SpawnPlacementCheckSignal} to {@link com.arryn.satchel.common.lifecycle.SatchelEventBus}
     * before a mob attempts to spawn, when spawn-placement rules are checked. No {@code
     * isClientSide} guard needed, same reasoning as {@link #onFinalizeSpawn}: {@link
     * MobSpawnEvent.SpawnPlacementCheck}'s own doc says it's "only fired on the logical server."
     */
    @SubscribeEvent
    public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck e) {
        if (!Satchel.isReady()) {
            OUT.debug("[SpawnPlacementCheckSignal] gate: Satchel not ready -- skipping");
            return;
        }

        Satchel.require().eventBus().post(new SpawnPlacementCheckSignal(e));
    }

    /* =============================================================
     * Spawn position check — SpawnPositionCheckSignal (SAT_086)
     * ========================================================== */

    /**
     * Posts {@link SpawnPositionCheckSignal} to {@link com.arryn.satchel.common.lifecycle.SatchelEventBus}
     * when a freshly created mob's position is checked -- the second gate in the spawn flow, after
     * {@link #onSpawnPlacementCheck}'s. No {@code isClientSide} guard needed: {@link
     * MobSpawnEvent.PositionCheck}'s own doc says it's "only fired on the logical server."
     */
    @SubscribeEvent
    public static void onSpawnPositionCheck(MobSpawnEvent.PositionCheck e) {
        if (!Satchel.isReady()) {
            OUT.debug("[SpawnPositionCheckSignal] gate: Satchel not ready -- skipping");
            return;
        }

        Satchel.require().eventBus().post(new SpawnPositionCheckSignal(e));
    }

    /* =============================================================
     * Entity join level — EntityJoinLevelSignal
     * ========================================================== */

    /**
     * Posts {@link EntityJoinLevelSignal} to {@link com.arryn.satchel.common.lifecycle.SatchelEventBus}
     * when an entity is about to join a level. Unlike the other two handlers in this class, {@link
     * EntityJoinLevelEvent} fires on both logical sides (its own doc) -- explicitly guarded to
     * server-only here, same idiom {@code ServerForgeIngress.onLivingDeath} uses for the identical
     * shape of problem ({@code LivingDeathEvent} also fires on both sides).
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide()) return;

        if (!Satchel.isReady()) {
            OUT.debug("[EntityJoinLevelSignal] gate: Satchel not ready -- skipping");
            return;
        }

        Satchel.require().eventBus().post(new EntityJoinLevelSignal(e));
    }
}
