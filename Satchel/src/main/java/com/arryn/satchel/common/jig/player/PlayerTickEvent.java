//package com.arryn.satchel.common.requireJig.player;
//
//import com.arryn.satchel.common.lifecycle.ISatchelTickEvent;
//import com.arryn.satchel.common.lifecycle.Lifecycle;
//import net.minecraft.world.entity.player.Player;
//import net.minecraftforge.event.TickEvent;
//
//public final class PlayerTickEvent
//        implements ISatchelTickEvent, Lifecycle.HasPlayer {
//
//    private final TickEvent.PlayerTickEvent event;
//
//    public PlayerTickEvent(TickEvent.PlayerTickEvent event) {
//        this.event = event;
//    }
//
//    @Override
//    public Phase phase() {
//        return event.phase == TickEvent.Phase.START
//                ? Phase.START
//                : Phase.END;
//    }
//
//    @Override
//    public Player player() {
//        return (Player) event.player;
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
