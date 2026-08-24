package com.arryn.satchel.client.lifecycle;

import com.arryn.satchel.common.jig.guts.ForgeEgress;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * Client-side {@link ForgeEgress}. {@code ClientLevel} has no UUID-keyed entity index the way
 * {@code ServerLevel} does (it tracks entities by network ID for rendering), so this iterates the
 * tracked-entity list ({@code ClientLevel#entitiesForRendering()}) for a UUID match instead --
 * correct, not free, but the cost is now fully local to this one class, which nothing else ever
 * sees or needs to know about. Paired with {@link ClientForgeIngress}, same package, same
 * "one file touches client-engine state" convention this codebase already follows -- see
 * RM_SAT_022 ("Roger").
 *
 * <p>
 * If iteration cost ever actually shows up in profiling, a {@code MobJig}-maintained UUID index
 * fed by entity add/remove events is a drop-in replacement for this class's internals alone --
 * zero change to {@link ForgeEgress} or its callers. Not built now against a cost nobody has
 * measured (SAT_037's own reasoning, carried forward).
 *
 * <p>
 * Installed once per client foundation, from {@code ClientFoundationBooter#installFoundation()}.
 */
public final class ClientForgeEgress implements ForgeEgress {

    @Override
    public Optional<Entity> getEntity(Level level, UUID uuid) {
        if (!(level instanceof ClientLevel clientLevel)) {
            return Optional.empty();
        }
        for (Entity entity : clientLevel.entitiesForRendering()) {
            if (entity.getUUID().equals(uuid)) {
                return Optional.of(entity);
            }
        }
        return Optional.empty();
    }
}
