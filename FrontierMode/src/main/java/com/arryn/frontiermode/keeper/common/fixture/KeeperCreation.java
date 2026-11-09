package com.arryn.frontiermode.keeper.common.fixture;

import com.arryn.frontiermode.boss.common.BossCreation;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * The Keeper's creation arguments, passed through {@code BossFixture.create("keeper", ...)} to
 * {@code KeeperKind.onCreate} (keeper.md § The Keeper record). {@code layer} and {@code pathPosition} are
 * copied from the originating Border once; {@code borderId} is empty for a hand-placed Keeper, and
 * {@code pathPosition} is empty off the path.
 */
public record KeeperCreation(int layer, Optional<UUID> borderId, OptionalInt pathPosition) implements BossCreation {
}
