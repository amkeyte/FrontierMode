package com.arryn.frontiermode.border.common.navigator;

/**
 * The {@link TargetRef} variants dispatched through {@link TargetResolverRegistry} -- one entry
 * per registry-backed kind, per
 * wiki/frontiermode/architecture/discovery-systems.md#navigation-lives-in-border.
 *
 * <p>{@code RawPos} and {@code Dynamic} deliberately have no entry here: {@code RawPos} already
 * carries its own answer (nothing to resolve), and {@code Dynamic} bypasses the registry entirely
 * by calling its embedded supplier directly -- see {@link TargetRef}'s own doc.
 */
public enum TargetType {
    BOSS,
    BORDER,
    STRUCTURE
}
