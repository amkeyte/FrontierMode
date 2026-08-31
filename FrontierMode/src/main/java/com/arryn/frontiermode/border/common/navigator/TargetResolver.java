package com.arryn.frontiermode.border.common.navigator;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves one {@link TargetType}'s {@code id} to a live position, for whichever {@code level}
 * the lookup is happening in -- registered by whichever module owns that type (see
 * {@link TargetResolverRegistry}).
 *
 * <p><b>Deviates from the architecture page's own shorthand:</b>
 * wiki/frontiermode/architecture/discovery-systems.md#navigation-lives-in-border describes the
 * registry as {@code TargetType -> (UUID -> BlockPos)}, with no {@code Level} in the signature.
 * Every registry-backed target this codebase actually has (bosses, borders) is stored in a
 * level-scoped fixture ({@code BossFixture}, {@code BordersFixture} are both {@code LevelScope}-
 * hosted, same as {@code NavigatorFixture} itself) -- there is no cross-level UUID space to
 * resolve against without knowing which level's fixture to ask. Flagged back to the Architect
 * (see FRO_065's log) rather than guessed at silently.
 *
 * <p>Returns {@code Optional.empty()} for "not resolvable right now" (e.g. no record for this id
 * in this level) -- a normal, expected outcome, not an error, same idiom {@code BorderAPI}/
 * {@code BossAPI} already use throughout this codebase.
 */
@FunctionalInterface
public interface TargetResolver {
    Optional<BlockPos> resolve(Level level, UUID id);
}
