package com.arryn.satchel.common.util;

import com.arryn.satchel.SatchelMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

/**
 * The only place module code reads or writes its own data on a mob, and the only place a mob's
 * vanilla state is changed for a module (SAT_094) -- {@link StackData} for mobs. Spec: wiki
 * {@code satchel/architecture/mob-data.md}.
 *
 * <p>
 * <b>A module's own data</b> is one {@link CompoundTag} per {@link ResourceLocation} namespace
 * ({@code frontiermode:level}), stored in Satchel's own capability on the mob ({@link
 * MobDataStore}) and saved with it: kept through chunk unload, restart, and changing ground; gone
 * when the mob is; copied to the new mob when it converts. Callers never receive a live tag. The
 * {@code satchel} namespace is Satchel's own and can't be written here.
 *
 * <p>
 * <b>Vanilla-owned state</b> changes only through named methods -- today {@link #scaleMaxHealth}.
 * Each records what it did under Satchel's namespace, so a conversion can re-apply it to the new
 * mob (vanilla's conversion copies no attributes).
 *
 * <p>
 * Server-side only. A client-side mob has no container: {@link #read} returns an empty compound,
 * and a write throws. Not synced -- anything the client must see goes through a synced fixture.
 */
public final class MobData {

    /** Satchel's own namespace: the stat changes it has made, for re-application on conversion. */
    private static final String STATS_KEY = new ResourceLocation(SatchelMod.MODID, "stats").toString();
    private static final String MAX_HEALTH = "max_health";

    private MobData() {
    }

    /* =============================================================
     * A module's own data
     * ========================================================== */

    /** A copy of {@code namespace}'s compound on {@code mob}, or an empty one. Never the live tag. */
    public static CompoundTag read(Mob mob, ResourceLocation namespace) {
        Objects.requireNonNull(mob, "mob");
        String key = moduleKey(namespace);
        return MobDataStore.of(mob).map(s -> s.read(key)).orElseGet(CompoundTag::new);
    }

    /** Replaces {@code namespace}'s compound with a copy of {@code compound}; an empty one removes it. */
    public static void write(Mob mob, ResourceLocation namespace, CompoundTag compound) {
        Objects.requireNonNull(compound, "compound");
        require(mob).write(moduleKey(namespace), compound);
    }

    /**
     * Writes only if {@code namespace} holds nothing on {@code mob} yet -- "the first rung to reach a
     * mob wins".
     *
     * @return {@code true} if it wrote
     */
    public static boolean writeIfAbsent(Mob mob, ResourceLocation namespace, CompoundTag compound) {
        Objects.requireNonNull(compound, "compound");
        String key = moduleKey(namespace);
        MobDataStore store = require(mob);
        if (store.has(key)) return false;
        store.write(key, compound);
        return true;
    }

    /**
     * The data of whatever dealt {@code source}'s damage: the direct entity's own copy if it is a
     * stamped carrier holding {@code namespace} (an arrow, Evoker fangs), otherwise the causing
     * entity's if that is a mob. Empty if neither applies -- a player, the environment, a carrier
     * whose owner had nothing.
     */
    public static CompoundTag readAttacker(DamageSource source, ResourceLocation namespace) {
        Objects.requireNonNull(source, "source");
        String key = moduleKey(namespace);
        Entity direct = source.getDirectEntity();
        if (direct != null && !(direct instanceof Mob)) {
            Optional<MobDataStore> carrier = MobDataStore.of(direct);
            if (carrier.isPresent() && carrier.get().has(key)) return carrier.get().read(key);
        }
        if (source.getEntity() instanceof Mob cause) return read(cause, namespace);
        return new CompoundTag();
    }

    /* =============================================================
     * Vanilla-owned state
     * ========================================================== */

    /**
     * Sets {@code mob}'s max health to {@code factor} x its base, as a vanilla attribute modifier
     * owned by {@code key}: one modifier per key, so setting it again replaces it and never stacks;
     * a factor of 1 removes it. A modifier rather than a changed base value, so it is safe to set
     * twice and survives a Slime or Phantom resetting its own base when its size changes. Vanilla
     * saves the modifier with the mob. A mob at full health stays at full health; otherwise its
     * health is only capped at the new maximum.
     *
     * @throws IllegalArgumentException if {@code factor} isn't a positive, finite number
     */
    public static void scaleMaxHealth(Mob mob, ResourceLocation key, double factor) {
        Objects.requireNonNull(mob, "mob");
        Objects.requireNonNull(key, "key");
        if (!(factor > 0) || !Double.isFinite(factor)) {
            throw new IllegalArgumentException("max-health factor must be positive and finite, was " + factor);
        }
        MobDataStore store = require(mob);
        AttributeInstance maxHealth = Objects.requireNonNull(mob.getAttribute(Attributes.MAX_HEALTH), "max health");

        boolean wasFull = mob.getHealth() >= mob.getMaxHealth();
        UUID id = modifierId(key);
        maxHealth.removeModifier(id);
        if (factor != 1.0) {
            maxHealth.addPermanentModifier(new AttributeModifier(id, "satchel:" + key, factor - 1.0,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        mob.setHealth(wasFull ? mob.getMaxHealth() : mob.getHealth());

        CompoundTag stats = store.read(STATS_KEY);
        CompoundTag scales = stats.getCompound(MAX_HEALTH);
        if (factor == 1.0) {
            scales.remove(key.toString());
        } else {
            scales.putDouble(key.toString(), factor);
        }
        if (scales.isEmpty()) {
            stats.remove(MAX_HEALTH);
        } else {
            stats.put(MAX_HEALTH, scales);
        }
        store.write(STATS_KEY, stats);
    }

    /** The max-health factor {@code key} last set on {@code mob}, or empty. */
    public static OptionalDouble maxHealthScale(Mob mob, ResourceLocation key) {
        Objects.requireNonNull(mob, "mob");
        Objects.requireNonNull(key, "key");
        CompoundTag scales = MobDataStore.of(mob)
                .map(s -> s.read(STATS_KEY).getCompound(MAX_HEALTH))
                .orElseGet(CompoundTag::new);
        String k = key.toString();
        return scales.contains(k, Tag.TAG_DOUBLE) ? OptionalDouble.of(scales.getDouble(k)) : OptionalDouble.empty();
    }

    /* =============================================================
     * Satchel-internal -- called by ServerMobDataIngress
     * ========================================================== */

    /**
     * Copies {@code from}'s data onto {@code to}, namespace by namespace (the old mob's wins, so a
     * converted mob keeps its level), then re-applies every recorded stat change to {@code to}.
     * {@code from} has already been discarded, which invalidates its capabilities, so they are
     * revived for the read and invalidated again after -- Forge's documented copy pattern.
     */
    public static void copyForConversion(Mob from, Mob to) {
        CompoundTag data;
        from.reviveCaps();
        try {
            data = MobDataStore.of(from).map(MobDataStore::copyAll).orElse(null);
        } finally {
            from.invalidateCaps();
        }
        if (data == null || data.isEmpty()) return;
        Optional<MobDataStore> target = MobDataStore.of(to);
        if (target.isEmpty()) return;

        for (String key : data.getAllKeys()) {
            if (data.contains(key, Tag.TAG_COMPOUND)) target.get().write(key, data.getCompound(key));
        }
        CompoundTag scales = data.getCompound(STATS_KEY).getCompound(MAX_HEALTH);
        for (String key : scales.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null) scaleMaxHealth(to, id, scales.getDouble(key));
        }
    }

    /**
     * Gives a newly spawned damage carrier (a projectile, Evoker fangs, primed TNT, an effect cloud)
     * a copy of its owner mob's data, so the carrier still says who it hits for after the owner is
     * gone. Does nothing if the carrier already holds data (it was saved with a copy) or its owner
     * isn't a mob with any.
     */
    public static void stampCarrier(Entity carrier) {
        if (carrier instanceof Mob || !(carrier instanceof TraceableEntity traceable)) return;
        if (!(traceable.getOwner() instanceof Mob owner)) return;
        Optional<MobDataStore> store = MobDataStore.of(carrier);
        if (store.isEmpty() || !store.get().isEmpty()) return;
        CompoundTag data = MobDataStore.of(owner).map(MobDataStore::copyAll).orElse(null);
        if (data == null || data.isEmpty()) return;
        for (String key : data.getAllKeys()) {
            if (data.contains(key, Tag.TAG_COMPOUND)) store.get().write(key, data.getCompound(key));
        }
    }

    /* =============================================================
     * Helpers
     * ========================================================== */

    private static MobDataStore require(Mob mob) {
        Objects.requireNonNull(mob, "mob");
        return MobDataStore.of(mob).orElseThrow(() -> new IllegalStateException(
                "MobData: " + mob + " has no data container -- client side, or removed"));
    }

    private static String moduleKey(ResourceLocation namespace) {
        Objects.requireNonNull(namespace, "namespace");
        if (namespace.getNamespace().equals(SatchelMod.MODID)) {
            throw new IllegalArgumentException("MobData: the '" + SatchelMod.MODID + "' namespace is Satchel's own");
        }
        return namespace.toString();
    }

    private static UUID modifierId(ResourceLocation key) {
        return UUID.nameUUIDFromBytes(("satchel:max_health/" + key).getBytes(StandardCharsets.UTF_8));
    }
}
