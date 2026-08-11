//package com.arryn.satchel.common.requireJig.player;
//
//import com.arryn.satchel.common.requireJig.guts.ASatchelJig;
//import com.arryn.satchel.common.requireJig.guts.ScopeCoupler;
//
//import java.util.UUID;
//
//public final class PlayerJig extends ASatchelJig<PlayerScope> {
//
//    public static final PlayerJig INSTANCE = new PlayerJig();
//
//    private PlayerJig() {
//        registerLoadEvent();
//        registerUnloadEvent();
//        registerTickEvent();
//    }
//
//    @Override
//    public void registerLoadEvent() {
//        //SatchelEventHub.INSTANCE.subscribeLoad(Lifecycle.Domain.PLAYER, this);
//    }
//
//    @Override
//    public void registerUnloadEvent() {
//        //SatchelEventHub.INSTANCE.subscribeUnload(Lifecycle.Domain.PLAYER,this);
//    }
//
//    @Override
//    public void registerTickEvent() {
//        //SatchelEventHub.INSTANCE.subscribeTick(Lifecycle.Domain.PLAYER,this);
//    }
//
//    @Override
//    public PlayerScope resolveScopescopeResolver(Object source) {
//        return PlayerResolver.resolveScopescopeResolver(source);
//    }
//
//    @Override
//    public UUID UUIDDeterminer(Object source) {
//        return PlayerResolver.UUIDDeterminer(source);
//    }
//
//    @Override
//    public Lifecycle.Domain domain() {
//        return Lifecycle.Domain.PLAYER;
//    }
//
//    @Override
//    public Class<? extends ScopeCoupler<? super PlayerScope>> couplerClass() {
//        return null;
//    }
//}
