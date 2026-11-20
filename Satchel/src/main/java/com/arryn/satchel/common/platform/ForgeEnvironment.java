package com.arryn.satchel.common.platform;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Forge's answers to {@link SatchelEnvironment}'s questions, read from {@code FMLEnvironment}, {@code FMLPaths},
 * {@code ModList} and {@code ServerLifecycleHooks}. {@code SatchelMod} installs it first in its constructor, so
 * every later ask finds it. The one place Satchel reads these four.
 */
@PlatformBound(reason = "environment")
public final class ForgeEnvironment implements SatchelEnvironment.Source {

    @Override
    public boolean isDedicatedServer() {
        return FMLEnvironment.dist.isDedicatedServer();
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public Optional<Path> modResource(String modId, String... segments) {
        IModFileInfo mod = ModList.get().getModFileById(modId);
        return mod == null ? Optional.empty() : Optional.of(mod.getFile().findResource(segments));
    }

    @Override
    public Object serverRun() {
        return ServerLifecycleHooks.getCurrentServer();
    }
}
