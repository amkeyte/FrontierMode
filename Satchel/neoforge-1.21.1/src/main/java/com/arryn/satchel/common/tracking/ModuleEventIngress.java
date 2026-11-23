package com.arryn.satchel.common.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.IngressKey;
import com.arryn.satchel.common.ingress.ModuleSignal;
import com.arryn.satchel.common.ingress.Scopes;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.host.HostJigs;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Forge's half of {@link ModuleEventHealthCheck}: its events on {@code EntityJoinLevelEvent} and
 * {@code LevelEvent.CreateSpawnPosition}, and one unwatched event registered without an interface, so the form that
 * learns its class from the first event it builds is still exercised. The check is shared.
 */
public final class ModuleEventIngress {

    private ModuleEventIngress() {}

    private static final IngressKey INGRESS = new IngressKey("satchelhealth:module_events");

    public static void register() {
        Satchel.registerIngress(JigPolicies.SideApplicability.BOTH, () -> build());
    }

    static final class ForgeJoinInLevel extends ModuleSignal<EntityJoinLevelEvent, ScopeInfo>
            implements ModuleEventHealthCheck.JoinInLevel {
        private final boolean clientSide;   // fact, copied

        ForgeJoinInLevel(EntityJoinLevelEvent e, ScopeInfo level) {
            super(e, level);
            clientSide = e.getLevel().isClientSide();
        }

        @Override
        public boolean clientSide() {
            return clientSide;
        }
    }

    static final class ForgeJoinAny extends ModuleSignal<EntityJoinLevelEvent, Void>
            implements ModuleEventHealthCheck.JoinAny {
        private final List<String> trail = new ArrayList<>();
        private final boolean clientSide;   // fact, copied

        ForgeJoinAny(EntityJoinLevelEvent e) {
            super(e, null);
            clientSide = e.getLevel().isClientSide();
        }

        @Override
        public List<String> trail() {
            return trail;
        }

        @Override
        public boolean clientSide() {
            return clientSide;
        }

        @Override
        public Object readNativeNow() {
            return nativeEvent();
        }
    }

    static final class ForgeNever extends ModuleSignal<EntityJoinLevelEvent, Integer>
            implements ModuleEventHealthCheck.Never {
        ForgeNever(EntityJoinLevelEvent e, Integer context) {
            super(e, context);
            ModuleEventHealthCheck.onNeverBuilt();
        }
    }

    /** Unscoped, with no subscriber, and no interface: built once per side to learn its class, then never. */
    static final class Unwatched extends ModuleSignal<EntityJoinLevelEvent, Void> {
        Unwatched(EntityJoinLevelEvent e) {
            super(e, null);
            ModuleEventHealthCheck.onUnwatchedBuilt();
        }
    }

    static final class ForgeUnwatchedDeclared extends ModuleSignal<EntityJoinLevelEvent, Void>
            implements ModuleEventHealthCheck.UnwatchedDeclared {
        ForgeUnwatchedDeclared(EntityJoinLevelEvent e) {
            super(e, null);
            ModuleEventHealthCheck.onUnwatchedDeclaredBuilt();
        }
    }

    static final class ForgeWorldSpawn extends ModuleSignal<LevelEvent.CreateSpawnPosition, Void>
            implements ModuleEventHealthCheck.WorldSpawn {
        ForgeWorldSpawn(LevelEvent.CreateSpawnPosition e) {
            super(e, null);
        }
    }

    static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(INGRESS);
        ingress.translate(EntityJoinLevelEvent.class, ModuleEventHealthCheck.JoinInLevel.class,
                e -> Scopes.of(HostJigs.LEVEL_HOST, e.getLevel()), ForgeJoinInLevel::new);
        ingress.translate(EntityJoinLevelEvent.class, ModuleEventHealthCheck.JoinAny.class, ForgeJoinAny::new);
        ingress.translate(EntityJoinLevelEvent.class, ModuleEventHealthCheck.Never.class,
                e -> ModuleEventHealthCheck.bounce(), ForgeNever::new);
        ingress.translate(EntityJoinLevelEvent.class, Unwatched::new);
        ingress.translate(EntityJoinLevelEvent.class, ModuleEventHealthCheck.UnwatchedDeclared.class,
                ForgeUnwatchedDeclared::new);
        ingress.translate(LevelEvent.CreateSpawnPosition.class, ModuleEventHealthCheck.WorldSpawn.class,
                ForgeWorldSpawn::new);
        ingress.consumers(ModuleEventHealthCheck.consumers());
        return ingress;
    }
}
