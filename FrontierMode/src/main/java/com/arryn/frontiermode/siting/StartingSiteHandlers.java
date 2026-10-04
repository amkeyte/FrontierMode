package com.arryn.frontiermode.siting;

import com.arryn.satchel.common.lifecycle.CreateWorldSpawnSignal;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/**
 * The starting-site handler (siting.md § When it runs): once, as a brand-new Overworld picks its spawn
 * and before any player exists, run the search, set the world spawn on the level data, and cancel
 * vanilla's own choice. The first Border centers on {@code getSharedSpawnPos()}, so it follows with no
 * change to Border. Existing worlds never see the signal.
 */
final class StartingSiteHandlers {

    private static final ResourceKey<ConfiguredFeature<?, ?>> BONUS_CHEST =
            ResourceKey.create(Registries.CONFIGURED_FEATURE, new ResourceLocation("minecraft", "bonus_chest"));

    private StartingSiteHandlers() {
    }

    static void onCreateWorldSpawn(CreateWorldSpawnSignal signal) {
        if (!(signal.level() instanceof ServerLevel level)) {
            return;
        }

        BlockPos site;
        try {
            site = StartingSite.search(level);
        } catch (RuntimeException e) {
            // A throw here fails world creation. The search is an improvement on vanilla's spawn, not a
            // requirement, so on any trouble leave the signal alone and let vanilla choose.
            OUT.warn("[Siting] starting-site search failed -- leaving vanilla's spawn choice alone: " + e);
            return;
        }

        signal.levelData().setSpawn(site, 0.0F);
        if (signal.bonusChestRequested()) {
            // Cancelling skips vanilla's bonus chest too, so it is placed here, as vanilla does.
            placeBonusChest(level, site);
        }
        signal.cancel();
    }

    private static void placeBonusChest(ServerLevel level, BlockPos site) {
        level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolder(BONUS_CHEST)
                .ifPresentOrElse(
                        feature -> feature.value().place(
                                level, level.getChunkSource().getGenerator(), level.getRandom(), site),
                        () -> OUT.warn("[Siting] no bonus_chest feature registered -- the bonus chest is skipped."));
    }
}
