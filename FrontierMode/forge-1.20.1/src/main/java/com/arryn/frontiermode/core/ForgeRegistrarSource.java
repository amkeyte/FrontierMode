package com.arryn.frontiermode.core;

import com.arryn.satchel.common.platform.PlatformBound;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

/**
 * {@code forge-1.20.1}'s {@link FrontierRegistrar.Source}: one {@code DeferredRegister} for each kind of thing the
 * mod registers. {@code FrontierMode} installs it and puts the registers on the mod bus. The holder each returns
 * ({@code RegistryObject}) is a {@link Supplier}.
 */
@PlatformBound(reason = "registration")
public final class ForgeRegistrarSource implements FrontierRegistrar.Source {

    private final DeferredRegister<Item> items = DeferredRegister.create(ForgeRegistries.ITEMS, FrontierIds.MOD_ID);
    private final DeferredRegister<Block> blocks = DeferredRegister.create(ForgeRegistries.BLOCKS, FrontierIds.MOD_ID);
    private final DeferredRegister<EntityType<?>> entityTypes =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, FrontierIds.MOD_ID);
    private final DeferredRegister<SoundEvent> sounds =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, FrontierIds.MOD_ID);
    private final DeferredRegister<ArgumentTypeInfo<?, ?>> argumentTypes =
            DeferredRegister.create(ForgeRegistries.COMMAND_ARGUMENT_TYPES, FrontierIds.MOD_ID);

    @Override
    public <T extends Item> Supplier<T> item(String name, Supplier<T> factory) {
        return items.register(name, factory);
    }

    @Override
    public <T extends Block> Supplier<T> block(String name, Supplier<T> factory) {
        return blocks.register(name, factory);
    }

    @Override
    public <T extends Entity> Supplier<EntityType<T>> entityType(String name, Supplier<EntityType<T>> factory) {
        return entityTypes.register(name, factory);
    }

    @Override
    public Supplier<SoundEvent> sound(String name, Supplier<SoundEvent> factory) {
        return sounds.register(name, factory);
    }

    @Override
    public Supplier<ArgumentTypeInfo<?, ?>> argumentType(String name, Supplier<ArgumentTypeInfo<?, ?>> factory) {
        return argumentTypes.register(name, factory);
    }

    /** Puts every register on the mod bus. */
    public void register(IEventBus modBus) {
        items.register(modBus);
        blocks.register(modBus);
        entityTypes.register(modBus);
        sounds.register(modBus);
        argumentTypes.register(modBus);
    }
}
