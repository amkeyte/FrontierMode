//package com.arryn.satchel.common.requireJig.player;
//
//import net.minecraft.server.MinecraftServer;
//import net.minecraft.server.level.ServerPlayer;
//import net.minecraftforge.server.ServerLifecycleHooks;
//
//import java.nio.charset.StandardCharsets;
//import java.util.UUID;
//
//public final class PlayerResolver {
//
//    private PlayerResolver() {
//    }
//
//    public static PlayerScope resolveScopescopeResolver(Object source) {
//        if (source instanceof ServerPlayer player) {
//            return new PlayerScope(player);
//        } else if (source instanceof UUID id) {
//            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
//            if (server == null) return null;
//
//            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
//                if (UUIDDeterminer(player).equals(id)) {
//                    return new PlayerScope(player);
//                }
//            }
//        }
//
//        return null;
//    }
//
//    public static UUID UUIDDeterminer(Object source) {
//        if (!(source instanceof ServerPlayer player)) {
//            throw new IllegalStateException(
//                    "PlayerJig source must be ServerPlayer, got: "
//                            + source.getClass().getName()
//            );
//        }
//
//        // Deterministic, stable across reconnects
//        return UUID.nameUUIDFromBytes(
//                player.getUUID().toString()
//                        .getBytes(StandardCharsets.UTF_8)
//        );
//    }
//}
