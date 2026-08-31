package com.arryn.frontiermode.border.common.fixture;

/**
 * A {@link BorderCurve}'s shape tag -- what kind of falloff curve it evaluates as, via
 * {@code BorderMath.intensityAt}. See {@code BorderCurveMath}'s own doc for each shape's exact
 * function; "to start, extensible" per
 * wiki/frontiermode/architecture/border-curve.md#data-model.
 */
public enum Shape {
    LINEAR,
    LOG,
    SQUARE
}
