package com.arryn.frontiermode.siting;

import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/**
 * The starting-site consumer (siting.md § When it runs): once, as a brand-new Overworld picks its spawn
 * and before any player exists, run the search, claim the spawn, which also cancels vanilla's own choice,
 * and place the bonus chest vanilla would have. The first Border centers on {@code getSharedSpawnPos()},
 * so it follows with no change to Border. Existing worlds never see the event.
 *
 * <p>A throw from the search is a programming error and crashes world creation; nothing here catches it.
 */
final class StartingSiteHandlers {

    private static final ResourceKey<ConfiguredFeature<?, ?>> BONUS_CHEST =
            ResourceKey.create(Registries.CONFIGURED_FEATURE, new ResourceLocation("minecraft", "bonus_chest"));

    private StartingSiteHandlers() {
    }

    static void onWorldSpawnChoosing(WorldSpawnChoosing event) {
        OUT.debug("[Siting] WorldSpawnChoosing reached its consumer");
        if (!(event.level() instanceof ServerLevel level)) {
            return;
        }

        BlockPos site = StartingSite.search(level);
        event.claimSpawn(site);
        if (event.bonusChestRequested()) {
            // Claiming skips vanilla's bonus chest too, so it is placed here, as vanilla does.
            placeBonusChest(level, site);
        }
    }

    private static void placeBonusChest(ServerLevel level, BlockPos site) {
        level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolder(BONUS_CHEST)
                .ifPresentOrElse(
                        feature -> feature.value().place(
                                level, level.getChunkSource().getGenerator(), level.getRandom(), site),
                        () -> OUT.warn("[Siting] no bonus_chest feature registered -- the bonus chest is skipped."));
    }
}
