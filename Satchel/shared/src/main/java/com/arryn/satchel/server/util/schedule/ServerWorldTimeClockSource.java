package com.arryn.satchel.server.util.schedule;

import com.arryn.satchel.common.util.schedule.SchedulerClock;
import net.minecraft.server.MinecraftServer;

import java.util.function.LongSupplier;


public class ServerWorldTimeClockSource implements SchedulerClock {

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
