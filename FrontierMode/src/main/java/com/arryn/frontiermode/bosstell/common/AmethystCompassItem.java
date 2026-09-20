package com.arryn.frontiermode.bosstell.common;

import com.arryn.frontiermode.boss.BossAPI;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The compass {@code Item} class, shared by the Admin Compass (built here, FRO_118) and the
 * later Amethyst Compass tier ladder (FRO_121) -- one {@code RegistryObject<Item>}, distinguished
 * per-stack by {@link CompassAttunement}'s NBT discriminator, per
 * wiki/frontiermode/architecture/special-compass.md#one-shared-core-several-independent-ways-to-set-the-same-lock.
 *
 * <p><b>Extends vanilla {@link CompassItem} directly</b> to reuse its needle rendering and
 * {@code LodestonePos}/{@code LodestoneDimension} fields entirely for free -- the needle already
 * points at whatever those fields say, no new rendering code needed.
 *
 * <p><b>Real API interaction worth being explicit about</b> (found reading
 * {@code CompassItem.inventoryTick} against the real 1.20.1-47.4.10 sources, not assumed):
 * vanilla's own {@code inventoryTick} strips {@code LodestonePos} the moment it can't find a real
 * Lodestone block-entity at that position in the current dimension -- but only when
 * {@code LodestoneTracked} is {@code true}; it returns immediately, untouched, when that flag is
 * {@code false}. {@link #refreshTarget} always writes {@code LodestoneTracked = false} for
 * exactly this reason: this compass's target is a boss position, never a real Lodestone POI, so
 * leaving the flag {@code true} would have vanilla's own tick logic erase the target on the very
 * next tick. This is what "self-refreshing" means here -- {@link #refreshTarget} is this item's
 * own refresh, called from {@code CompassPlayerFixture}'s throttled tick, not vanilla's.
 *
 * <p>{@link #useOn} is overridden to a no-op ({@code InteractionResult.PASS}) -- vanilla's own
 * right-click-a-Lodestone behavior would otherwise let a player overwrite this compass's boss
 * attunement with an ordinary Lodestone lock. Not specified on special-compass.md; a build
 * decision, not a design change (this compass's target is set only via
 * {@code /boss debug compass} for now, and later the tier-ladder assignment paths -- never by
 * right-clicking a block).
 */
public class AmethystCompassItem extends CompassItem {

    public AmethystCompassItem(Properties props) {
        super(props);
    }

    /**
     * Called from {@code CompassPlayerFixture}'s own throttled tick for every attuned stack a
     * player currently holds -- not vanilla's {@code inventoryTick}, see this class's own doc.
     * Resolves the stack's locked boss via {@link BossAPI#resolveLivePosition}, the shared
     * resolver special-compass.md's "Live position resolution" section describes (materialized
     * mob position, falling back to the recorded, possibly-unfinalized {@code position()}).
     * A no-op if the resolver has nothing at all yet (record not found, or unpositioned) --
     * whatever lodestone fields the stack already carries are left untouched rather than cleared,
     * matching this codebase's "standby, don't crash" convention.
     */
    public static void refreshTarget(ItemStack stack, Level level) {
        Optional<UUID> bossIdOpt = CompassAttunement.bossId(stack);
        if (bossIdOpt.isEmpty()) {
            return;
        }

        Optional<BlockPos> posOpt = BossAPI.resolveLivePosition(level, bossIdOpt.get());
        if (posOpt.isEmpty()) {
            return;
        }

        CompoundTag tag = stack.getOrCreateTag();
        tag.put(TAG_LODESTONE_POS, NbtUtils.writeBlockPos(posOpt.get()));
        Level.RESOURCE_KEY_CODEC.encodeStart(NbtOps.INSTANCE, level.dimension())
                .resultOrPartial(OUT::warn)
                .ifPresent(encoded -> tag.put(TAG_LODESTONE_DIMENSION, encoded));
        // See class doc -- false is deliberate, not a placeholder.
        tag.putBoolean(TAG_LODESTONE_TRACKED, false);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return InteractionResult.PASS;
    }

    /**
     * {@code Compass - Boss[N]}, where {@code N}'s meaning depends on the variant --
     * {@link CompassAttunement#isAdmin} selects between the Admin Compass's own raw selector
     * index (matching {@code /boss debug}'s own grammar, e.g. {@code /boss debug goto 3}) and,
     * later, the Amethyst Compass's boss-layer reading (FRO_121). Only the admin case is wired
     * here -- FRO_121 adds its own branch when the tier ladder lands.
     */
    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        Optional<UUID> bossIdOpt = CompassAttunement.bossId(stack);
        if (bossIdOpt.isEmpty()) {
            return;
        }

        if (CompassAttunement.isAdmin(stack) && level != null) {
            BossAPI.bosses(level).ifPresent(fixture ->
                    fixture.get(bossIdOpt.get()).ifPresent(record -> {
                        int index = fixture.all().indexOf(record);
                        tooltip.add(Component.literal("Compass - Boss[" + index + "]"));
                    }));
        }
    }
}
