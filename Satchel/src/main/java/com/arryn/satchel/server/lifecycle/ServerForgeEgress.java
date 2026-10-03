package com.arryn.satchel.server.lifecycle;

import com.arryn.satchel.common.jig.guts.ForgeEgress;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side {@link ForgeEgress}: today's exact {@code MobJig} lookup logic
 * ({@code ServerLevel#getEntity(UUID)}, backed by the server's own entity index), relocated here
 * rather than rewritten. Paired with {@link ServerForgeIngress}, same package, same "one file
 * touches this Forge/engine capability" convention -- see RM_SAT_022 ("Roger").
 *
 * <p>
 * Installed once per server foundation, from {@code ServerFoundationBooter#installFoundation()}.
 */
public final class ServerForgeEgress implements ForgeEgress {

    @Override
    public Optional<Entity> getEntity(Level level, UUID uuid) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return Optional.empty();
        }
        return Optional.ofNullable(serverLevel.getEntity(uuid));
    }

    /**
     * SAT_071: reaches the online-player list directly via {@link ServerLifecycleHooks} --
     * verified real against the mapped MC jar, not assumed -- rather than through any specific
     * {@link ServerLevel}, since a player's current dimension is exactly the thing a caller
     * resolving fresh every call doesn't have cached anywhere to ask.
     */
    @Override
    public Optional<Player> getPlayer(UUID uuid) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(server.getPlayerList().getPlayer(uuid));
    }

    @Override
    public Iterable<Level> levels() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return List.of();
        }
        List<Level> levels = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            levels.add(level);
        }
        return levels;
    }
}
