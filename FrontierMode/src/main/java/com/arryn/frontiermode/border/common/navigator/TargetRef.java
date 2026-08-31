package com.arryn.frontiermode.border.common.navigator;

import net.minecraft.core.BlockPos;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Opaque target reference {@code NavigatorFixture} resolves to a live {@link BlockPos} -- a small
 * tagged union, per
 * wiki/frontiermode/architecture/discovery-systems.md#navigation-lives-in-border. Navigator never
 * has to interpret what a reference means; only the module that owns a given variant's identifier
 * does, via {@link TargetResolverRegistry}.
 *
 * <p>{@link RawPos} is the escape hatch -- a remembered position with nothing else attached.
 * {@link Dynamic} is a different kind of escape hatch (added for the Special Compass's
 * tip-pointing case, e.g. {@code () -> BordersPathFacet.getTipCenter()}): it bypasses
 * {@link TargetResolverRegistry} entirely -- the fixture calls its embedded supplier directly --
 * and by construction cannot be serialized, since a {@link Supplier} is a closure, not data.
 * Anything that needs a {@code Dynamic} reference to survive a save/reload is responsible for
 * reconstructing it fresh from its own lightweight persisted discriminator every time it's needed
 * (see the architecture page's "Special Compass" section) -- {@code Dynamic} itself is never
 * persisted, and nothing here gives it a persistence story.
 *
 * <p><b>{@link Structure}'s exact shape is not settled by the architecture page</b> (written
 * there as {@code Structure(...)}) -- represented here as a bare {@link UUID} id to match
 * {@link TargetResolverRegistry}'s stated {@code TargetType -> (UUID -> BlockPos)} shape, the same
 * identifier shape {@link Boss} and {@link Border} already use. Flagged back to the Architect
 * (see FRO_065's log) rather than guessed at further -- not exercised by RM_FRO_026's own done
 * bar, which only requires {@link Boss} to have a real registered resolver.
 */
public sealed interface TargetRef
        permits TargetRef.Boss, TargetRef.Border, TargetRef.Structure, TargetRef.RawPos,
        TargetRef.Dynamic {

    record Boss(UUID bossId) implements TargetRef {
        public Boss {
            Objects.requireNonNull(bossId, "bossId");
        }
    }

    record Border(UUID borderId) implements TargetRef {
        public Border {
            Objects.requireNonNull(borderId, "borderId");
        }
    }

    record Structure(UUID structureId) implements TargetRef {
        public Structure {
            Objects.requireNonNull(structureId, "structureId");
        }
    }

    record RawPos(BlockPos pos) implements TargetRef {
        public RawPos {
            Objects.requireNonNull(pos, "pos");
        }
    }

    record Dynamic(Supplier<BlockPos> supplier) implements TargetRef {
        public Dynamic {
            Objects.requireNonNull(supplier, "supplier");
        }
    }
}
