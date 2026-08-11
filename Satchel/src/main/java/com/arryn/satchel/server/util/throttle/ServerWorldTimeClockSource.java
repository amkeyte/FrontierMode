package com.arryn.satchel.server.util.throttle;

import com.arryn.satchel.common.util.throttle.ThrottleClockSource;
import net.minecraft.server.MinecraftServer;

import java.util.function.LongSupplier;


public class ServerWorldTimeClockSource implements ThrottleClockSource {

    private final MinecraftServer server;

    public ServerWorldTimeClockSource() {
        server = null;// ServerLifecycleHooks.getCurrentServer();
    }

    @Override
    public LongSupplier elapsedSupplier() {
        return () -> server.overworld().getGameTime();
    }

    public long elapsed() {
        return elapsedSupplier().getAsLong();
    }
}
