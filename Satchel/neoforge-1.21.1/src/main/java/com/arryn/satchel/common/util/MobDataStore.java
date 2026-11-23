package com.arryn.satchel.common.util;

import com.arryn.satchel.common.platform.PlatformBound;
import com.arryn.satchel.SatchelMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Satchel-internal storage behind {@link MobData} (SAT_094): the {@code satchel:mob_data} NeoForge
 * data attachment -- Satchel's own private slot on an entity, saved with it. Module code never touches
 * this class; it reads and writes through {@link MobData}. Public only so Satchel's registration
 * ({@code SatchelMod}) can reach it.
 *
 * <p>
 * One compound per namespace, keyed by the namespace's {@code ResourceLocation} text -- the same
 * layout as {@link StackData}.
 *
 * <p>
 * An entity {@link #attachesTo} holds a store from its first read, as NeoForge attaches the default
 * then; any other entity answers empty, as it held no container on Forge. A removed entity answers
 * empty too, as a Forge entity's invalidated capability did, although NeoForge keeps its attachment
 * readable; {@link #ofDiscarded} reads it anyway, for copying data off a mob that conversion has just
 * discarded.
 */
@PlatformBound(reason = "mob data namespace")
public final class MobDataStore implements INBTSerializable<CompoundTag> {

    public static final ResourceLocation ID = Ids.id(SatchelMod.MODID, "mob_data");

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SatchelMod.MODID);

    private static final Supplier<AttachmentType<MobDataStore>> TYPE =
            ATTACHMENT_TYPES.register(ID.getPath(), () -> AttachmentType.serializable(MobDataStore::new).build());

    private CompoundTag data = new CompoundTag();

    private MobDataStore() {
    }

    /** Registers the attachment type on the mod bus. Called once, from the mod constructor. */
    public static void register(IEventBus modBus) {
        ATTACHMENT_TYPES.register(modBus);
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

    static Optional<MobDataStore> of(Entity entity) {
        return attachesTo(entity) && !entity.isRemoved() ? Optional.of(entity.getData(TYPE)) : Optional.empty();
    }

    /**
     * The store of an entity that may have been removed, for a conversion's copy off the mob it has just
     * discarded: what Forge's {@code reviveCaps()} gave back. Attaches nothing to an entity that holds none.
     */
    static Optional<MobDataStore> ofDiscarded(Entity entity) {
        return attachesTo(entity) ? entity.getExistingData(TYPE) : Optional.empty();
    }

    /* =============================================================
     * Data, for MobData
     * ========================================================== */

    /**
     * Whether {@code key} holds a non-empty compound, so that {@code has(key)} is {@code
     * !read(key).isEmpty()}. Looked at in place: nothing is copied or created. An empty compound that
     * saved data carries under the key counts as absent, as {@link #write} would have removed it.
     */
    boolean has(String key) {
        return data.contains(key, Tag.TAG_COMPOUND) && !data.getCompound(key).isEmpty();
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

    /**
     * For the {@code MobData.has} health probe (SAT_179): puts {@code mob} in the state a saved mob is
     * in when its {@code namespace} compound came back empty. Saves the mob, replaces that namespace's
     * compound in the saved copy of this container with an empty one, and loads the copy back. Where the
     * capability's data sits in the entity's saved tag is known here and nowhere else, so the probe does
     * not name it.
     *
     * @throws IllegalStateException if the mob's save does not carry what was written under {@code
     *                               namespace}: the probe wrote it first, so the layout is not what
     *                               this method reads
     */
    public static void reloadWithEmptyCompound(Mob mob, ResourceLocation namespace) {
        CompoundTag saved = new CompoundTag();
        mob.saveWithoutId(saved);
        CompoundTag container = saved.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).getCompound(ID.toString());
        String key = namespace.toString();
        if (!container.contains(key, Tag.TAG_COMPOUND)) {
            throw new IllegalStateException("The save of " + mob + " does not carry what was written under " + key
                    + " in the " + ID + " container, so its empty-compound case cannot be staged");
        }
        container.put(key, new CompoundTag());
        mob.load(saved);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider registries) {
        return data.copy();
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
        data = tag.copy();
    }
}
