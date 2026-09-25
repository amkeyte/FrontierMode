package com.arryn.frontiermode.bosstell.common;

import com.arryn.frontiermode.border.common.BorderMath;
import com.arryn.frontiermode.boss.BossAPI;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.frontiermode.boss.common.fixture.BossTellFixture;
import com.arryn.frontiermode.bosstell.BossTellModule;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The compass {@code Item} class, shared by the Admin Compass (FRO_118) and the Amethyst
 * Compass tier ladder (FRO_121) -- one {@code RegistryObject<Item>}, distinguished per-stack by
 * {@link CompassAttunement}'s NBT discriminator, per
 * wiki/frontiermode/architecture/special-compass.md#one-shared-core-several-independent-ways-to-set-the-same-lock.
 *
 * <p><b>Extends vanilla {@link CompassItem} directly</b> to reuse its needle rendering and
 * {@code LodestonePos}/{@code LodestoneDimension} fields entirely for free.
 *
 * <p><b>Real API interaction worth being explicit about</b> (checked against the real
 * 1.20.1-47.4.10 sources): vanilla's own {@code inventoryTick} strips {@code LodestonePos} the
 * moment it can't find a real Lodestone block-entity at that position -- but only when
 * {@code LodestoneTracked} is {@code true}. {@link #writeLodestoneFields} always writes
 * {@code LodestoneTracked = false} for exactly this reason.
 *
 * <p><b>Refresh model, per special-compass.md's "Intuition and Resonance" section:</b> one
 * throttled tick ({@link #refreshOrRelock}, called from {@code CompassPlayerFixture}) does two
 * jobs at once -- ordinary live-position refresh for whichever case is currently live (the
 * admin path and Resonance), and re-lock detection for Intuition/Resonance (comparing the
 * stack's actual current enchant level against {@link CompassAttunement#lockedEnchantLevel} --
 * a mismatch is the trigger to re-roll/re-lock, the same tick doubling as both "just enchanted"
 * detection and the ordinary periodic refresh).
 */
public class AmethystCompassItem extends CompassItem {

    /**
     * FRO_121: shared by Intuition and Resonance, restricting both to this item class only --
     * not compasses generically, not any other item, per special-compass.md's "Intuition and
     * Resonance" section.
     */
    public static final EnchantmentCategory ENCHANT_CATEGORY =
            EnchantmentCategory.create("amethyst_compass", item -> item instanceof AmethystCompassItem);

    /**
     * FRO_121 playtest: Intuition didn't show up at the enchanting table for the compass at
     * all. Root cause, checked against the real sources -- {@code EnchantmentMenu.slotsChanged}
     * gates the whole item slot on {@code ItemStack.isEnchantable()}, which calls
     * {@code Item.isEnchantable(ItemStack)}; the default there is
     * {@code getMaxStackSize()==1 && isDamageable()}, and this item is neither (no durability
     * ever set) -- so the table rejected it before even reaching {@link #ENCHANT_CATEGORY}'s own
     * check. This is the exact same problem vanilla's own {@code BookItem} has (also not
     * damageable) and fixes the same way: drop the damageable requirement entirely. The anvil's
     * own book-combine path was never affected by this -- it checks {@code
     * EnchantmentCategory.canEnchant} directly, independent of {@code isEnchantable()}.
     */
    @Override
    public boolean isEnchantable(ItemStack stack) {
        return stack.getCount() == 1;
    }

    /**
     * Playtest: "Limited Enchantability" at the table, even with lapis present. Traced the real
     * math (`EnchantmentHelper.selectEnchantment`): the roll's bonus term is
     * {@code 1 + rng.nextInt(getEnchantmentValue()/4 + 1) * 2}, so the earlier value of 1
     * (copied from {@code BookItem}, matching its own default) makes {@code nextInt(1)} always
     * return 0 -- a flat, RNG-proof {@code +1} bonus, never more. Raised to 10 (a diamond-tool-
     * tier value) so it actually contributes a real, variable bonus (roughly +1 to +5) instead
     * of being functionally inert. This alone won't guarantee reaching Intuition's own cost
     * floor (15, copied from vanilla's Lure) on a table with few or no bookshelves, though --
     * the base roll before any bonus caps around 8 at zero bookshelf power, the same real
     * constraint vanilla's own Lure has on an unshelved table.
     */
    @Override
    public int getEnchantmentValue() {
        return 10;
    }

    // CompassAttunement.lockedEnchantLevel()'s own encoding -- 0 = no enchant, 1-3 = Intuition
    // I-III, 4 = Resonance. Internal to this class; never exposed or persisted as anything but
    // this one int.
    private static final int RESONANCE_CODE = 4;

    // Ring-jitter radii per Intuition tier (index 0=I, 1=II, 2=III), tighter at higher tiers --
    // "safe baseline, replace later," same framing as every other tunable in this cluster.
    private static final int[] INTUITION_OUTER_RADIUS = {64, 32, 12};
    private static final int[] INTUITION_INNER_RADIUS = {56, 28, 10};

    private static final RandomSource RNG = RandomSource.create();

    public AmethystCompassItem(Properties props) {
        super(props);
    }

    /**
     * Writes the actual {@code LodestonePos}/{@code LodestoneDimension}/{@code LodestoneTracked}
     * fields the needle renders off of. Shared by the live-refresh path (every throttled tick)
     * and the frozen-lock path (written once, at lock time, then never touched again).
     */
    private static void writeLodestoneFields(ItemStack stack, Level level, BlockPos pos) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.put(TAG_LODESTONE_POS, NbtUtils.writeBlockPos(pos));
        Level.RESOURCE_KEY_CODEC.encodeStart(NbtOps.INSTANCE, level.dimension())
                .resultOrPartial(OUT::warn)
                .ifPresent(encoded -> tag.put(TAG_LODESTONE_DIMENSION, encoded));
        tag.putBoolean(TAG_LODESTONE_TRACKED, false);
    }

    /**
     * Live-position refresh only -- resolves the stack's locked boss via
     * {@link BossAPI#resolveLivePosition} and rewrites the lodestone fields. A no-op if the
     * resolver has nothing yet, matching this codebase's "standby, don't crash" convention.
     * Used directly by the admin path (always live) and, via {@link #refreshOrRelock}, by
     * Resonance.
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
        writeLodestoneFields(stack, level, posOpt.get());
    }

    /**
     * FRO_121: the real per-tick entry point for a non-admin stack -- called from
     * {@code CompassPlayerFixture}. See class doc for the two-job shape.
     */
    public static void refreshOrRelock(ItemStack stack, ServerLevel level, ServerPlayer holder) {
        if (CompassAttunement.isAdmin(stack)) {
            refreshTarget(stack, level);
            return;
        }

        enforceSingleEnchant(stack);

        int intuitionLevel = EnchantmentHelper.getTagEnchantmentLevel(BossTellModule.INTUITION.get(), stack);
        int resonanceLevel = EnchantmentHelper.getTagEnchantmentLevel(BossTellModule.RESONANCE.get(), stack);
        // Mutually exclusive by checkCompatibility(); Resonance wins if somehow both are present.
        int currentCode = resonanceLevel > 0 ? RESONANCE_CODE : intuitionLevel;

        // Bug fix (playtest): a completely fresh, never-attuned compass enchanted directly with
        // Intuition/Resonance used to fall through this whole method untouched, since the old
        // guard here was `!isAttuned() -> return` -- checked before ever looking at the enchant
        // level. That's wrong: each tier (bedrock pad, Intuition, Resonance) is its own
        // independent way to set the lock, per special-compass.md's "One shared core" table --
        // enchanting a fresh compass must be sufficient on its own, not conditional on some
        // other path having attuned it first. Only truly skip when there's neither a real
        // enchant *nor* any prior attunement (e.g. a bedrock-pad lock with no enchant) to act on.
        if (currentCode == 0 && !CompassAttunement.isAttuned(stack)) {
            return;
        }

        int lockedCode = CompassAttunement.lockedEnchantLevel(stack);

        if (currentCode != lockedCode) {
            relock(stack, level, holder, currentCode);
            return;
        }

        if (currentCode == RESONANCE_CODE) {
            // Keeps reading a moving target -- the structural difference from Intuition's own
            // frozen point, per special-compass.md's "Live position resolution" section.
            refreshTarget(stack, level);
        }
        // Tier 1 (bedrock pad, code 0 but frozen via useOn) and Intuition (1-3) never refresh
        // further once locked -- that's the whole point of "frozen."
    }

    /**
     * Playtest report: enchantments duplicating on one compass. Checked the real
     * {@code ItemStack.enchant(Enchantment, int)} -- it unconditionally {@code ListTag.add()}s a
     * new NBT entry with no check for an existing one of the same enchantment, so calling it
     * twice for the same enchantment (at any level) leaves both entries in the list, and the
     * tooltip renders all of them. Vanilla's own anvil combine path is <em>not</em> the source
     * of this -- checked {@code AnvilMenu.createResult()} directly: it merges into a
     * {@code Map<Enchantment, Integer>} first (which can't hold two entries for the same key by
     * construction) and applies it via {@code EnchantmentHelper.setEnchantments}, which rebuilds
     * the whole tag fresh rather than appending. The duplicate is coming from somewhere else --
     * creative mode's own enchantment-editing screen, or hand-authored `/give` NBT, are the
     * likely candidates, neither of which goes through the anvil's own safe path.
     *
     * <p>Rather than chase every possible entry point, this compass enforces its own
     * single-enchantment invariant on every throttled refresh, regardless of how a duplicate
     * got there: keeps exactly one (Resonance over Intuition, matching {@link #refreshOrRelock}'s
     * own precedence; the higher level if somehow more than one of the same kind is present) and
     * rewrites the tag via {@code EnchantmentHelper.setEnchantments} -- the same safe,
     * whole-tag-replace call the anvil itself uses, not another {@code enchant()} append.
     *
     * <p><b>Bug in this method's own first version, found via playtest screenshot:</b> it
     * bailed early whenever {@code EnchantmentHelper.getEnchantments(stack).size() <= 1} --
     * but that method builds a {@code Map}, and {@code Map.put()} silently collapses two raw
     * NBT entries for the <em>same</em> enchantment (e.g. "Intuition II" appearing twice) down
     * to one key. So a same-enchantment duplicate never tripped the size check at all, and the
     * underlying list -- which the tooltip renders directly, duplicates included -- was never
     * touched. Fix: rewrite unconditionally whenever anything is present, not just when the
     * *map's* size looks wrong; {@code setEnchantments} rebuilding the whole tag fresh is what
     * actually clears a same-key duplicate, regardless of what the map's own size suggested.
     */
    private static void enforceSingleEnchant(ItemStack stack) {
        Map<Enchantment, Integer> current = EnchantmentHelper.getEnchantments(stack);
        if (current.isEmpty()) {
            return;
        }

        Enchantment keep = null;
        int keepLevel = 0;
        for (Map.Entry<Enchantment, Integer> entry : current.entrySet()) {
            if (entry.getKey() == BossTellModule.RESONANCE.get()) {
                keep = entry.getKey();
                keepLevel = entry.getValue();
                break;
            }
        }
        if (keep == null) {
            for (Map.Entry<Enchantment, Integer> entry : current.entrySet()) {
                if (entry.getValue() > keepLevel) {
                    keep = entry.getKey();
                    keepLevel = entry.getValue();
                }
            }
        }

        EnchantmentHelper.setEnchantments(keep == null ? Map.of() : Map.of(keep, keepLevel), stack);
    }

    /**
     * The actual re-lock, triggered by {@link #refreshOrRelock} finding a mismatch. Resolves
     * the current Path Tip's boss identity ({@link BossAPI#pathTipBossId}) exactly once, per
     * special-compass.md's own wording -- from there, Intuition freezes a ring-jitter point and
     * Resonance stays live on that boss UUID.
     */
    private static void relock(ItemStack stack, ServerLevel level, ServerPlayer holder, int newCode) {
        if (newCode == 0) {
            // Enchant removed (or never applied, and this isn't a bedrock-pad lock either) --
            // nothing to lock onto. Leave whatever the stack already carries untouched; only
            // the bookkeeping itself needs to catch up so this stops re-triggering every tick.
            CompassAttunement.setLockedEnchantLevel(stack, 0);
            return;
        }

        Optional<UUID> bossIdOpt = BossAPI.pathTipBossId(level);
        if (bossIdOpt.isEmpty()) {
            return; // Nothing to lock onto yet -- try again next throttled tick.
        }
        UUID bossId = bossIdOpt.get();
        UUID ownerId = holder.getUUID();

        if (newCode == RESONANCE_CODE) {
            CompassAttunement.setLive(stack, bossId, ownerId, false);
            refreshTarget(stack, level);
        } else {
            Optional<BlockPos> posOpt = BossAPI.resolveLivePosition(level, bossId);
            if (posOpt.isEmpty()) {
                return;
            }
            int tierIndex = newCode - 1;
            BlockPos jitter = BorderMath.randomPointInAnnulus(
                    RNG, posOpt.get(), INTUITION_INNER_RADIUS[tierIndex], INTUITION_OUTER_RADIUS[tierIndex]);
            CompassAttunement.setFrozen(stack, jitter, ownerId);
            writeLodestoneFields(stack, level, jitter);
        }
        CompassAttunement.setLockedEnchantLevel(stack, newCode);
    }

    /**
     * Two jobs, branched by variant. Admin: no-op ({@code InteractionResult.PASS}) -- vanilla's
     * own right-click-a-Lodestone behavior would otherwise let a player overwrite the admin
     * attunement with an ordinary Lodestone lock (not specified on special-compass.md, a build
     * decision). Amethyst (non-admin): FRO_121's bedrock-pad detection (Tier 1) -- per
     * special-compass.md's own "no new index needed" ruling, resolves whether the clicked block
     * belongs to some boss's own Environmental-Tells platform (the 3x3 bedrock area at
     * {@code boss.position().below()}, per discovery-systems.md) by comparing the clicked
     * position directly against every boss's own recorded {@code position()} -- no new
     * persisted index, just a runtime scan (bounded by however many bosses exist on the level).
     */
    @Override
    public @NotNull InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ItemStack stack = context.getItemInHand();
        if (CompassAttunement.isAdmin(stack)) {
            return InteractionResult.PASS;
        }

        BlockPos clicked = context.getClickedPos();
        if (!level.getBlockState(clicked).is(Blocks.BEDROCK)) {
            return InteractionResult.PASS;
        }

        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        Optional<UUID> bossIdOpt = BossAPI.bosses(level).flatMap(fixture ->
                fixture.all().stream()
                        .filter(record -> record.positioned() && matchesPlatform(record.position(), clicked,
                                BossTellFixture.platformHalfWidth(fixture.RULES.scaleForLayer(record.layer()))))
                        .map(BossRecord::bossId)
                        .findFirst());
        if (bossIdOpt.isEmpty()) {
            return InteractionResult.PASS;
        }

        Optional<BlockPos> posOpt = BossAPI.resolveLivePosition(level, bossIdOpt.get());
        if (posOpt.isEmpty()) {
            return InteractionResult.PASS;
        }

        CompassAttunement.setFrozen(stack, posOpt.get(), player.getUUID());
        writeLodestoneFields(stack, level, posOpt.get());
        return InteractionResult.CONSUME;
    }

    /**
     * Is {@code clicked} inside the square bedrock cell centered at {@code bossPos.below()}?
     * {@code halfWidth} is 1 for the base 3x3 and grows with the boss's size (FRO_148) -- see
     * {@code BossTellFixture.platformHalfWidth}.
     */
    private static boolean matchesPlatform(BlockPos bossPos, BlockPos clicked, int halfWidth) {
        BlockPos platformCenter = bossPos.below();
        return clicked.getY() == platformCenter.getY()
                && Math.abs(clicked.getX() - platformCenter.getX()) <= halfWidth
                && Math.abs(clicked.getZ() - platformCenter.getZ()) <= halfWidth;
    }

    /**
     * Bug fix: {@code CompassItem.getDescriptionId(ItemStack)} overrides the display name to
     * vanilla's own {@code "item.minecraft.lodestone_compass"} whenever
     * {@code isLodestoneCompass(stack)} is true -- which is always true here, since this item
     * always writes the identical {@code LodestonePos}/{@code LodestoneDimension} fields.
     * Bypasses {@code CompassItem}'s override and falls back to {@code Item}'s own default
     * (registry-name-derived) description id.
     */
    @Override
    public @NotNull String getDescriptionId(@NotNull ItemStack stack) {
        return getDescriptionId();
    }

    /**
     * {@code Compass - Boss[N]} -- {@code N}'s meaning depends on the variant, per
     * special-compass.md's "Tooltip" section: the Admin Compass's own raw selector index
     * (matching {@code /boss debug}'s own grammar), or the Amethyst Compass's boss-layer
     * reading (a placeholder display decision, not final, per that section).
     */
    @Override
    public void appendHoverText(@NotNull ItemStack stack, Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        Optional<UUID> bossIdOpt = CompassAttunement.bossId(stack);
        if (bossIdOpt.isEmpty() || level == null) {
            return;
        }

        BossAPI.bosses(level).ifPresent(fixture ->
                fixture.get(bossIdOpt.get()).ifPresent(record -> {
                    if (CompassAttunement.isAdmin(stack)) {
                        int index = fixture.all().indexOf(record);
                        tooltip.add(Component.literal("Compass - Boss[" + index + "]"));
                    } else {
                        tooltip.add(Component.literal("Compass - Boss[" + record.layer() + "]"));
                    }
                }));
    }
}
