package com.arryn.frontiermode.border.common.navigator;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code TargetType -> (UUID -> BlockPos)} resolver registry, per
 * wiki/frontiermode/architecture/discovery-systems.md#navigation-lives-in-border -- mirrors
 * Satchel's {@code MobInterestRegistry} registration shape: whichever module owns a target type
 * registers its own resolver at init time (e.g. {@code BossModule.init()} registering
 * {@link TargetType#BOSS}), and {@code NavigatorFixture} looks it up by tag without ever
 * importing that module's types. Border hosts this registry without needing to know what any
 * given {@link TargetType} means.
 */
public final class TargetResolverRegistry {

    private TargetResolverRegistry() {}

    private static final Map<TargetType, TargetResolver> RESOLVERS = new ConcurrentHashMap<>();

    public static void register(TargetType type, TargetResolver resolver) {
        RESOLVERS.put(
                Objects.requireNonNull(type, "type"),
                Objects.requireNonNull(resolver, "resolver")
        );
    }

    public static void unregister(TargetType type) {
        RESOLVERS.remove(Objects.requireNonNull(type, "type"));
    }

    public static Optional<TargetResolver> get(TargetType type) {
        Objects.requireNonNull(type, "type");
        return Optional.ofNullable(RESOLVERS.get(type));
    }
}
