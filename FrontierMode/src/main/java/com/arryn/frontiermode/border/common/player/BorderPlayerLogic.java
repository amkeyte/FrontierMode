//package com.arryn.frontiermode.border.common.player;
//
//import com.arryn.frontiermode.border.common.BorderMath;
//import com.arryn.frontiermode.border.common.level.Border;
//import net.minecraft.core.BlockPos;
//
//import java.util.Comparator;
//import java.util.List;
//
///**
// * Stateless evaluator producing derived player-border information.
// *
// * This class:
// * - performs no mutation
// * - enforces no authority
// * - produces no authoritative objects
// * - returns pure evaluation results only
// */
//final class BorderPlayerLogic {
//
//    public BorderPlayerEval evaluate(
//            List<Border> borders,
//            BlockPos pos
//    ) {
//        if (pos == null || borders == null || borders.isEmpty()) {
//            return new BorderPlayerEval(
//                    null,
//                    Integer.MAX_VALUE,
//                    false,
//                    -1
//            );
//        }
//
//        Border nearest = borders.stream()
//                .min(Comparator.comparingInt(
//                        b -> BorderMath.distanceToSurface(b, pos)
//                ))
//                .orElseThrow();
//
//        int dist = BorderMath.distanceToSurface(nearest, pos);
//        boolean inside = BorderMath.isInside(nearest, pos);
//
//        return new BorderPlayerEval(
//                nearest.id(),
//                dist,
//                inside,
//                nearest.layerIndex()
//        );
//    }
//}
