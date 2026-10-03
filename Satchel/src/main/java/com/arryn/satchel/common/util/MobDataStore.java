package com.arryn.satchel.common.util;

import com.arryn.satchel.SatchelMod;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Satchel-internal storage behind {@link MobData} (SAT_094): the {@code satchel:mob_data} Forge
 * capability -- Satchel's own private slot on an entity, saved with it. Module code never touches
 * this class; it reads and writes through {@link MobData}. Public only so Satchel's registration
 * ({@code SatchelMod}) and ingress ({@code ServerMobDataIngress}) can reach it.
 *
 * <p>
 * One compound per namespace, keyed by the namespace's {@code ResourceLocation} text -- the same
 * layout as {@link StackData}.
 *
 * <p>
 * The provider hands out a fresh {@code LazyOptional} on every query rather than one it
 * invalidates later. Forge's own provider already answers empty while the entity's capabilities
 * are invalid; doing it this way means {@code reviveCaps()} really brings the data back, which is
 * what copying data off a mob that conversion has just discarded relies on.
 */
public final class MobDataStore {

    public static final ResourceLocation ID = new ResourceLocation(SatchelMod.MODID, "mob_data");

    public static final Capability<MobDataStore> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {});

    private CompoundTag data = new CompoundTag();

    private MobDataStore() {
    }

    /**
     * Whether an entity gets a store: every server-side {@link Mob}, and every server-side
     * non-mob entity that reports an owner (projectiles, Evoker fangs, primed TNT, effect clouds),
     * except item entities, which deal no damage.
     */
    public static boolean attachesTo(Entity entity) {
        if (entity.level() == null || entity.level().isClientSide()) return false;
        if (entity instanceof Mob) return true;
        return entity instanceof TraceableEntity && !(entity instanceof ItemEntity);
    }

    /** A new, empty store wrapped as the provider {@code AttachCapabilitiesEvent} takes. */
    public static ICapabilitySerializable<CompoundTag> newProvider() {
        return new Provider(new MobDataStore());
    }

    static Optional<MobDataStore> of(Entity entity) {
        return entity.getCapability(CAPABILITY).resolve();
    }

    /* =============================================================
     * Data, for MobData
     * ========================================================== */

    boolean has(String key) {
        return data.contains(key, Tag.TAG_COMPOUND);
    }

    CompoundTag read(String key) {
        return has(key) ? data.getCompound(key).copy() : new CompoundTag();
    }

    void write(String key, CompoundTag compound) {
        if (compound.isEmpty()) {
            data.remove(key);
        } else {
            data.put(key, compound.copy());
        }
    }

    boolean isEmpty() {
        return data.isEmpty();
    }

    /** A copy of every namespace. */
    CompoundTag copyAll() {
        return data.copy();
    }

    private record Provider(MobDataStore store) implements ICapabilitySerializable<CompoundTag> {

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
            return CAPABILITY.orEmpty(cap, LazyOptional.of(() -> store));
        }

        @Override
        public CompoundTag serializeNBT() {
            return store.data.copy();
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            store.data = tag.copy();
        }
    }
}
