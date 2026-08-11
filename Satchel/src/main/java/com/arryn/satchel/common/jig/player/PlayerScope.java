//package com.arryn.satchel.common.requireJig.player;
//
//
//import com.arryn.satchel.common.requireJig.guts.SatchelScope;
//import com.arryn.satchel.common.requireJig.guts.ScopeCoupler;
//import com.arryn.satchel.common.stitch.LogicalSideStitch;
//import com.arryn.satchel.common.stitch.PersistStitch;
//import net.minecraft.world.entity.player.Player;
//
//import java.util.Objects;
//import java.util.UUID;
//
///**
// * SatchelScope representing a Minecraft player.
// */
//public final class PlayerScope
//        implements SatchelScope,
//        PersistStitch,
//        LogicalSideStitch {
//
//    private final Player player;
//    private final UUID scopeId;
//
//    PlayerScope(Player player) {
//        this.player = Objects.requireNonNull(player);
//        this.scopeId = player.getUUID();
//    }
//
//    public Player player() {
//        return player;
//    }
//
//    //@Override
//    public PlayerJig requireJig() {
//        return PlayerJig.INSTANCE;
//    }
//
//    @Override
//    public UUID uuid() {
//        return scopeId;
//    }
//
//    @Override
//    public String debugName() {
//        return this.getClass().getSimpleName()
//                + "[" + player.getGameProfile().getName() + "]"
//                + uuid()
//                + "@" + System.identityHashCode(this);
//    }
//
//    //@Override
//    @SuppressWarnings("unchecked")
//    public ScopeCoupler<? super PlayerScope> coupler() {
//        return PlayerScopeCoupler.INSTANCE;
//    }
//
//    @Override
//    public boolean equals(Object o) {
//        if (this == o) return true;
//        if (!(o instanceof PlayerScope other)) return false;
//        return scopeId.equals(other.scopeId);
//    }
//
//    @Override
//    public int hashCode() {
//        return scopeId.hashCode();
//    }
//
//
//    @Override
//    public boolean isServerSide() {
//        return false;
//    }
//
//    @Override
//    public boolean isClientSide() {
//        return false;
//    }
//
//    @Override
//    public boolean isPersistent() {
//        return false;
//    }
//}
