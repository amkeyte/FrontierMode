package com.arryn.frontiermode.border.common.fixture;

import com.arryn.frontiermode.border.common.navigator.TargetRef;
import com.arryn.frontiermode.border.common.navigator.TargetResolverRegistry;
import com.arryn.frontiermode.border.common.navigator.TargetType;
import com.arryn.satchel.common.fixture.SatchelFixture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * Sibling fixture to {@link BordersFixture} inside
 * {@link com.arryn.frontiermode.border.common.bundle.BordersBundle} -- Navigator's
 * position-resolution surface for a {@link TargetRef}, per RM_FRO_026 ("Dorothy") /
 * wiki/frontiermode/architecture/discovery-systems.md#navigation-lives-in-border. Registered
 * alongside {@link BordersFixture} in {@code BorderModule.init()} -- the first fixture this
 * codebase has actually put two of in one bundle (the architecture page's "already-precedented
 * shape" claim about {@code JigConfigValidator}'s {@code FixtureDecl -> BundleDecl -> Schema}
 * chain becomes real with this fixture, not before it).
 *
 * <p>Deliberately no persisted state yet -- this node's own done bar is the
 * {@code TargetRef}/registry/resolution machinery itself, not the attunement records the
 * architecture page's "Attunement" section describes for later consumers (the compass, trails).
 * Those are a separate {@code LevelScope}-hosted record type for a later node to add, not a
 * reason to hold this one back.
 */
public final class NavigatorFixture extends SatchelFixture {

    public NavigatorFixture() {
    }

    /**
     * Resolves {@code ref} to a live position for {@code level}.
     *
     * <p>{@link TargetRef.RawPos} needs no resolver at all -- it already is the answer.
     * {@link TargetRef.Dynamic} bypasses {@link TargetResolverRegistry} entirely and calls its
     * embedded supplier directly, per that variant's own contract. Every other variant dispatches
     * through {@link TargetResolverRegistry} by its {@link TargetType}, keyed by whatever
     * identifier that variant carries.
     *
     * <p>Empty when nothing is registered for the type yet, or the registered resolver itself
     * can't currently resolve the id (e.g. a boss id with no matching record in this level) --
     * both non-error, "try again later" outcomes, same idiom {@code BorderAPI}/{@code BossAPI}
     * already use throughout this codebase.
     */
    public Optional<BlockPos> resolve(Level level, TargetRef ref) {
        if (ref instanceof TargetRef.RawPos raw) {
            return Optional.of(raw.pos());
        }

        if (ref instanceof TargetRef.Dynamic dynamic) {
            return Optional.ofNullable(dynamic.supplier().get());
        }

        if (ref instanceof TargetRef.Boss boss) {
            return TargetResolverRegistry.get(TargetType.BOSS)
                    .flatMap(resolver -> resolver.resolve(level, boss.bossId()));
        }

        if (ref instanceof TargetRef.Border border) {
            return TargetResolverRegistry.get(TargetType.BORDER)
                    .flatMap(resolver -> resolver.resolve(level, border.borderId()));
        }

        if (ref instanceof TargetRef.Structure structure) {
            return TargetResolverRegistry.get(TargetType.STRUCTURE)
                    .flatMap(resolver -> resolver.resolve(level, structure.structureId()));
        }

        throw new IllegalStateException("Unhandled TargetRef variant: " + ref);
    }
}
