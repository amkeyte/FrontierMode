//package com.arryn.satchel.common.jig.guts;
//
//import com.arryn.satchel.common.identity.JigKey;
//import com.arryn.satchel.common.util.out.OUT;
//import net.minecraftforge.fml.LogicalSide;
//
//import java.util.ArrayList;
//import java.util.EnumMap;
//import java.util.List;
//import java.util.Objects;
//import java.util.function.Supplier;
//
//public final class SatchelJigRegistrar2 {
//
//    private static final EnumMap<LogicalSide, List<Entry<?>>> REGISTRY =
//            new EnumMap<>(LogicalSide.class);
//
//    static {
//        for (LogicalSide side : LogicalSide.values()) {
//            REGISTRY.put(side, new ArrayList<>());
//        }
//    }
//
//    private SatchelJigRegistrar2() {
//    }
//
//    /* =============================================================
//     * Registration (called during mod init)
//     * ========================================================== */
//
//    //  Call-site uses LevelJig directly
//    public static <S extends SatchelScope, J extends SatchelJig<S>> void register(
//            LogicalSide side,
//            JigKey<J> key,
//            Supplier<? extends J> jigSupplier
//    ) {
//        Objects.requireNonNull(side, "side");
//        Objects.requireNonNull(key, "key");
//        Objects.requireNonNull(jigSupplier, "jigSupplier");
//
//        // No cast needed: JigKey<J> fits JigKey<? extends SatchelJig<S>>
//        // and Supplier<? extends J> fits Supplier<? extends SatchelJig<S>>.
//        REGISTRY.get(side).add(new Entry<>(key, jigSupplier));
//    }
//
//    /* =============================================================
//     * Activation (called during spool-up)
//     * ========================================================== */
//
//    public static void activateForSide(
//            LogicalSide side,
//            LogicalFoundation foundation
//    ) {
//        Objects.requireNonNull(side, "side");
//        Objects.requireNonNull(foundation, "foundation");
//
//        OUT.debug("Activating registrations for side: " + side);
//        if (foundation.side() != side) {
//            throw new IllegalArgumentException(
//                    "Foundation side mismatch: expected " + side +
//                            ", found " + foundation.side()
//            );
//        }
//
//        for (Entry<?> entry : REGISTRY.get(side)) {
//            entry.installInto(foundation);
//        }
//    }
//
//    /* =============================================================
//     * Internal entry (scope-typed so installKey matches)
//     * ========================================================== */
//
//    private static final class Entry<S extends SatchelScope> {
//
//        private final JigKey<? extends SatchelJig<S>> key;
//        private final Supplier<? extends SatchelJig<S>> jigSupplier;
//
//        Entry(
//                JigKey<? extends SatchelJig<S>> key,
//                Supplier<? extends SatchelJig<S>> jigSupplier
//        ) {
//            this.key = key;
//            this.jigSupplier = jigSupplier;
//        }
//
//        void installInto(LogicalFoundation foundation) {
//            SatchelJig<S> jig = jigSupplier.get();
//            jig.installKey(key);
//            foundation.registerJig(key, jig);
//            OUT.debug("\t\t..." + key.name);
//        }
//    }
//}
