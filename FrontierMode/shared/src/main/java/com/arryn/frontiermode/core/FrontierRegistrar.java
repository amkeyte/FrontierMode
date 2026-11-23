package com.arryn.frontiermode.core;

import com.arryn.satchel.common.platform.PlatformBound;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * The one seam for registering things (Two Lines § Registration). Shared code registers an item, a block, an entity
 * type, a sound or a command argument type here and holds the {@link Supplier} it gets back; it never names a
 * loader's register or the holder type that register returns. Each line implements {@link Source} with its own
 * {@code DeferredRegister}s, and the line's mod entry installs it before any registers class loads and puts its
 * registers on the mod bus.
 *
 * <p>
 * Registering before a source is installed throws. That is a programming error, and it crashes in every build.
 * An enchantment is not registered here (Two Lines § Enchantments).
 */
@PlatformBound(reason = "registration")
public final class FrontierRegistrar {

    /** What a line registers with. Implemented once per line. */
    public interface Source {

        <T extends Item> Supplier<T> item(String name, Supplier<T> factory);

        <T extends Block> Supplier<T> block(String name, Supplier<T> factory);

        <T extends Entity> Supplier<EntityType<T>> entityType(String name, Supplier<EntityType<T>> factory);

        Supplier<SoundEvent> sound(String name, Supplier<SoundEvent> factory);

        Supplier<ArgumentTypeInfo<?, ?>> argumentType(String name, Supplier<ArgumentTypeInfo<?, ?>> factory);
    }

    private static volatile Source source;

    private FrontierRegistrar() {
    }

    /** Installs the line's registrar. Once: a second call throws. */
    public static synchronized void install(Source installed) {
        Objects.requireNonNull(installed, "installed");
        if (source != null) {
            throw new IllegalStateException(
                    "The registrar is already installed (" + source.getClass().getName() + ")");
        }
        source = installed;
    }

    /** Registers an item under {@code frontiermode:name}. */
    public static <T extends Item> Supplier<T> item(String name, Supplier<T> factory) {
        return required().item(name, factory);
    }

    /** Registers a block under {@code frontiermode:name}. */
    public static <T extends Block> Supplier<T> block(String name, Supplier<T> factory) {
        return required().block(name, factory);
    }

    /** Registers an entity type under {@code frontiermode:name}. */
    public static <T extends Entity> Supplier<EntityType<T>> entityType(String name, Supplier<EntityType<T>> factory) {
        return required().entityType(name, factory);
    }

    /** Registers a sound under {@code frontiermode:name}, a variable-range event of the same id. */
    public static Supplier<SoundEvent> sound(String name) {
        return required().sound(name, () -> SoundEvent.createVariableRangeEvent(FrontierIds.of(name)));
    }

    /** Registers a command argument type under {@code frontiermode:name}. */
    public static Supplier<ArgumentTypeInfo<?, ?>> argumentType(String name, Supplier<ArgumentTypeInfo<?, ?>> factory) {
        return required().argumentType(name, factory);
    }

    private static Source required() {
        Source installed = source;
        if (installed == null) {
            throw new IllegalStateException("No registrar is installed: the mod entry installs one first");
        }
        return installed;
    }
}
