//package com.arryn.frontiermode.border.client.render.level;
//
//import com.arryn.frontiermode.border.common.level.Border;
//
///**
// * Client-side view wrapper for an immutable Border.
// *
// * Holds render- and view-specific state without duplicating
// * authoritative domain data.
// */
//public final class BorderView {
//
//    private final Border border;
//
//    // ---- Future view state lives here ----
//    // Examples:
//    // - cached distance to camera
//    // - LOD bucket
//    // - precomputed ring geometry handle
//    // - highlight / debug flags
//    // - fade alpha
//    // - lastVisibleFrame
//
//    public BorderView(Border border) {
//        this.border = border;
//    }
//
//    /** Authoritative immutable border */
//    public Border border() {
//        return border;
//    }
//
//    // Convenience passthroughs (optional)
//    public int radius() {
//        return border.radius();
//    }
//
//    public int layer() {
//        return border.layerIndex();
//    }
//}
