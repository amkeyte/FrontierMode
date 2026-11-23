package com.arryn.frontiermode.border.common.navigator;

/**
 * The {@link TargetRef} variants dispatched through {@link TargetResolverRegistry} -- one entry
 * per registry-backed kind, per
 * wiki/frontiermode/architecture/discovery-systems.md#navigation-lives-in-border.
 *
 * <p>{@code RawPos} deliberately has no entry here: it already carries its own answer (nothing to resolve)
 * -- see {@link TargetRef}'s own doc.
 */
public enum TargetType {
    BOSS,
    BORDER,
    STRUCTURE
}
