//package com.arryn.satchel.common.jig.strap;
//
//import com.arryn.satchel.common.jig.guts.LogicalFoundation;
//import com.arryn.satchel.common.lifecycle.SatchelEvent;
//import net.minecraftforge.fml.LogicalSide;
//
//import java.util.*;
//
///**
// * Static registry for Satchel straps.
// *
// * <p>
// * Holds declarations made during mod initialization and activates them
// * into a {@link LogicalFoundation} during spool-up.
// *
// * This class:
// *  - is static-only
// *  - is side-aware
// *  - performs no instantiation except via declarations
// *  - does not bind logical side
// *  - does not own lifecycle
// */
//public final class SatchelStrapRegistrar {
//
//    private static final EnumMap<LogicalSide, List<SatchelStrap.Declaration>> REGISTRY =
//            new EnumMap<>(LogicalSide.class);
//
//    static {
//        for (LogicalSide side : LogicalSide.values()) {
//            REGISTRY.put(side, new ArrayList<>());
//        }
//    }
//
//    private SatchelStrapRegistrar() {}
//
//    /* =============================================================
//     * Registration (called during mod init)
//     * ========================================================== */
//
//    public static void register(
//            SatchelStrap.Declaration declaration
//    ) {
//        Objects.requireNonNull(declaration, "declaration");
//
//        // We don't know the side yet; store globally.
//        for (LogicalSide side : LogicalSide.values()) {
//            if (declaration.appliesTo(side)) {
//                REGISTRY.get(side).add(declaration);
//            }
//        }
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
//        if (foundation.side() != side) {
//            throw new IllegalArgumentException(
//                    "Foundation side mismatch: expected " + side +
//                            ", found " + foundation.side()
//            );
//        }
//
//        for (SatchelStrap.Declaration decl : REGISTRY.get(side)) {
//            installInto(foundation, decl);
//        }
//    }
//
//    /* =============================================================
//     * Internal installation
//     * ========================================================== */
//
//    private static void installInto(
//            LogicalFoundation foundation,
//            SatchelStrap.Declaration decl
//    ) {
//
//        SatchelStrap strap = decl.create();
//
//        if (!INSTALLED.add(strap)) {
//            return;
//        }
//
//        // Attach immediately
//        strap.attach(foundation);
//
//        // Hard-wire lifecycle observation
//        foundation.eventBus().subscribe(
//                SatchelEvent.Started.class,
//                e -> strap.lift(e.side())
//        );
//
//        foundation.eventBus().subscribe(
//                SatchelEvent.Stopped.class,
//                e -> strap.drop(e.side())
//        );
//    }
//
//
//    private static final Set<SatchelStrap> INSTALLED =
//            Collections.newSetFromMap(new IdentityHashMap<>());
//
//
//
//}
