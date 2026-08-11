//package com.arryn.frontiermode.border.server;
//
//import net.minecraftforge.event.TickEvent;
//import net.minecraftforge.eventbus.api.SubscribeEvent;
//import net.minecraftforge.fml.common.Mod;
//
///**
// * Server-side player border status ticking.
// *
// * Responsibilities:
// * - iterate active server players
// * - gather level border inputs
// * - route evaluation requests to player fixtures
// *
// * This handler performs NO logic and NO mutation itself.
// * All authority flows through fixtures.
// */
//@Mod.EventBusSubscriber(modid = "frontiermode")
//public final class PlayerTickHandler {
//
//    private PlayerTickHandler() {}
//
//    @SubscribeEvent
//    public static void onServerTick(TickEvent.ServerTickEvent event) {
////        if (event.phase != TickEvent.Phase.END) return;
////
////        MinecraftServer server =
////                ServerLifecycleHooks.getCurrentServer();
////        if (server == null) return;
////
////        for (ServerLevel level : server.getAllLevels()) {
////
////            // World-side authoritative borders (read-only)
////            List<Border> borders = BorderAPI.all(level);
////            if (borders.isEmpty()) continue;
//
////            for (ServerPlayer player : level.players()) {
////
////                BorderPlayerStatusFixture playerFx =
////                        BorderAPI.playerFixture(player);
////
////                var proposal =
////                        new BorderPlayerStatusProposal(player.getUUID());
////
////                playerFx.accept(
////                        proposal,
////                        borders,
////                        player.blockPosition()
////                );
////            }
////        }
//    }
//}
