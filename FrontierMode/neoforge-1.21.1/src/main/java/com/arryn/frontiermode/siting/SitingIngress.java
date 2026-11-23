package com.arryn.frontiermode.siting;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.ModuleSignal;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.Objects;

/**
 * {@code neoforge-1.21.1}'s ingress for the siting module: NeoForge's {@link LevelEvent.CreateSpawnPosition}
 * translated to {@link WorldSpawnChoosing}, with {@link SitingConsumers} installed. Unscoped and with no resolver: the
 * event fires once per world, so there is nothing to filter before it is built. The mod entry registers
 * {@link #build()} through a lambda, so this class loads when the server side boots.
 */
public final class SitingIngress {

    private SitingIngress() {
    }

    public static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(FrontierKeys.SITING_INGRESS);
        ingress.translate(LevelEvent.CreateSpawnPosition.class, WorldSpawnChoosing.class, ForgeWorldSpawnChoosing::new);
        ingress.consumers(SitingConsumers.declare());
        return ingress;
    }

    /** {@link WorldSpawnChoosing} over NeoForge's spawn-position event, named as Satchel names its own. */
    static final class ForgeWorldSpawnChoosing extends ModuleSignal<LevelEvent.CreateSpawnPosition, Void>
            implements WorldSpawnChoosing {

        private final Level level;
        private final MinecraftServer server;
        private final boolean bonusChestRequested;

        ForgeWorldSpawnChoosing(LevelEvent.CreateSpawnPosition forgeEvent) {
            super(forgeEvent, null);
            this.level = (Level) Objects.requireNonNull(forgeEvent.getLevel(), "level");
            // The loader's event does not say whether the world was created with the bonus chest; the server's
            // world options do.
            this.server = Objects.requireNonNull(level.getServer(), "server");
            this.bonusChestRequested = server.getWorldData().worldGenOptions().generateBonusChest();
        }

        @Override
        public Level level() {
            return level;
        }

        @Override
        public boolean bonusChestRequested() {
            return bonusChestRequested;
        }

        @Override
        public void claimSpawn(BlockPos site) {
            LevelEvent.CreateSpawnPosition event = nativeEvent();
            event.getSettings().setSpawn(site, 0.0F);
            event.getSettings().getGameRules().getRule(GameRules.RULE_SPAWN_RADIUS).set(0, server);
            event.setCanceled(true);
        }
    }
}
