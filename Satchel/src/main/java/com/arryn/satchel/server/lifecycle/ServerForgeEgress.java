package com.arryn.satchel.server.lifecycle;

import com.arryn.satchel.common.jig.guts.ForgeEgress;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

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
}
