//package com.arryn.satchel.common.requireJig.player;
//
//import com.arryn.satchel.common.lifecycle.ISatchelLoadEvent;
//import com.arryn.satchel.common.lifecycle.Lifecycle;
//import net.minecraft.world.entity.player.Player;
//import net.minecraftforge.event.entity.player.PlayerEvent;
//
//public final class PlayerLoadEvent
//        implements ISatchelLoadEvent, Lifecycle.HasPlayer {
//
//    private final PlayerEvent.PlayerLoggedInEvent event;
//
//    public PlayerLoadEvent(PlayerEvent.PlayerLoggedInEvent event) {
//        this.event = event;
//    }
//
//    @Override
//    public Player player() {
//        return event.getEntity();
//    }
//
//    @Override
//    public Object scopeObject() {
//        return player();
//    }
//
//    @Override
//    public Lifecycle.Domain domain() {
//        return Lifecycle.Domain.PLAYER;
//    }
//}
