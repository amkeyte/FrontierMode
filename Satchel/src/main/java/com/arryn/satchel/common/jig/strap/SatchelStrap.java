//package com.arryn.satchel.common.jig.strap;
//
//import com.arryn.satchel.common.jig.guts.LogicalFoundation;
//import net.minecraftforge.fml.LogicalSide;
//
//public interface SatchelStrap {
//    interface Declaration {
//        boolean appliesTo(LogicalSide side);
//        SatchelStrap create();
//    }
//
//
//    /**
//     * Called once when a LogicalFoundation is installed
//     * and safe to interact with.
//     */
//    void attach(LogicalFoundation foundation);
//
//    /**
//     * Observational: Satchel is operational.
//     */
//    void lift(LogicalSide side);
//
//    /**
//     * Observational: Satchel is stopping.
//     */
//    void drop(LogicalSide side);
//}
