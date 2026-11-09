package com.arryn.frontiermode.border.common.navigator;

import net.minecraft.core.BlockPos;

import java.util.Objects;
import java.util.UUID;

/**
 * Opaque target reference that {@code BorderAPI.resolveTarget} resolves to a live {@link BlockPos} -- a
 * small tagged union, per
 * wiki/frontiermode/architecture/discovery-systems.md#navigation-lives-in-border. Navigator never
 * has to interpret what a reference means; only the module that owns a given variant's identifier
 * does, via {@link TargetResolverRegistry}.
 *
 * <p>{@link RawPos} is the escape hatch -- a remembered position with nothing else attached, which answers
 * itself. There is no live-supplier variant: a consumer that needs a live position asks the module that
 * owns it.
 *
 * <p><b>{@link Structure}'s exact shape is not settled by the architecture page</b> (written
 * there as {@code Structure(...)}) -- represented here as a bare {@link UUID} id to match
 * {@link TargetResolverRegistry}'s stated {@code TargetType -> (UUID -> BlockPos)} shape, the same
 * identifier shape {@link Boss} and {@link Border} already use. Not guessed at further.
 */
public sealed interface TargetRef
        permits TargetRef.Boss, TargetRef.Border, TargetRef.Structure, TargetRef.RawPos {

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
}
