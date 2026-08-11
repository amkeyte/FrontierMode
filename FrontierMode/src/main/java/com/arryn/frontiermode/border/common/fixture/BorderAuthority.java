package com.arryn.frontiermode.border.common.fixture;

import java.util.UUID;

/**
 * Capability marker for authoritative ownership of Border instances.
 *
 * SECURITY:
 * - This is a capability, not an identifier.
 * - Equality MUST be reference-based (==), never UUID-based.
 * - Only fixtures in this package are allowed to implement this.
 */
public interface BorderAuthority {

    /**
     * Stable identity of the owning level (for diagnostics only).
     *
     * IMPORTANT:
     * - This UUID is NOT an authority token.
     * - It must never be used for authorization checks.
     */
    UUID authorityId();
}
