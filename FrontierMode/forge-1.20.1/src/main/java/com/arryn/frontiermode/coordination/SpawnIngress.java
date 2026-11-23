package com.arryn.frontiermode.coordination;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.ModuleSignal;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.MobSpawnEvent;

import java.util.Objects;

/**
 * {@code forge-1.20.1}'s ingress for the spawn coordination module: Forge's {@link MobSpawnEvent.FinalizeSpawn}
 * translated to {@link MobSpawnFinalizing}, with {@link SpawnConsumers} installed. The mod entry
 * registers {@link #build()} through a lambda, so this class loads when the server side boots.
 */
public final class SpawnIngress {

    private SpawnIngress() {
    }

    /**
     * Unscoped, so no resolver: nothing filters a spawn before the event is built, and the one consumer is the module's
     * only one. Forge listener priority and bus priority stay at normal.
     */
    public static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(FrontierKeys.SPAWN_COORDINATION);
        ingress.translate(MobSpawnEvent.FinalizeSpawn.class, MobSpawnFinalizing.class, ForgeMobSpawnFinalizing::new);
        ingress.consumers(SpawnConsumers.declare());
        return ingress;
    }

    /** {@link MobSpawnFinalizing} over Forge's finalize-spawn event. */
    static final class ForgeMobSpawnFinalizing extends ModuleSignal<MobSpawnEvent.FinalizeSpawn, Void>
            implements MobSpawnFinalizing {

        private final Mob mob;
        private final Level level;
        private final MobSpawnType spawnType;

        ForgeMobSpawnFinalizing(MobSpawnEvent.FinalizeSpawn forgeEvent) {
            super(forgeEvent, null);
            this.mob = Objects.requireNonNull(forgeEvent.getEntity(), "mob");
            this.level = Objects.requireNonNull(mob.level(), "level");
            this.spawnType = Objects.requireNonNull(forgeEvent.getSpawnType(), "spawnType");
        }

        @Override
        public Mob mob() {
            return mob;
        }

        @Override
        public Level level() {
            return level;
        }

        @Override
        public MobSpawnType spawnType() {
            return spawnType;
        }

        @Override
        public void denySpawn() {
            nativeEvent().setSpawnCancelled(true);
        }
    }
}
