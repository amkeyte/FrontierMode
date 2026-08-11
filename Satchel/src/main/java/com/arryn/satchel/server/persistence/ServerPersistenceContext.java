//package com.arryn.satchel.server.persistence;
//
//import net.minecraft.server.level.ServerLevel;
//
//import java.util.Optional;
//
//public class ServerPersistenceContext implements ServerPersistentStitch {
//    private final ServerLevel level;
//
//    public ServerPersistenceContext(ServerLevel level) {
//        this.level = level;
//    }
//
//    @Override
//    public boolean isPersistent() {
//        return true;
//    }
//
//    @Override
//    public Optional<ServerLevel> serverLevel() {
//        return Optional.of(level);
//    }
//}
