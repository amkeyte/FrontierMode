package com.arryn.frontiermode.border.common.player;

import java.util.OptionalInt;
import java.util.UUID;

/**
 * Authoritative snapshot of border-related state for a player.
 *
 * Constructed only by BorderPlayerStatusFixture.
 *
 * {@code relevantLayer}/{@code nearestLayer} were split from a single overloaded {@code
 * layer} field (Border Vocabulary Conformance Checklist item 2.4) -- see {@link
 * BorderPlayerEval}'s own doc comment for the full reasoning. {@code relevantLayer} is the one
 * safe to feed into {@code BorderRules.ACTIVE.layerToDifficulty(...)} for an ambient-difficulty
 * reading; {@code nearestLayer} is not a Relevance result and should not be used for that.
 */
public final class BorderPlayerStatus {

    private final BorderPlayerStatusFixture authority;

    private final UUID playerId;
    private final UUID nearestBorderId;
    private final int distanceToNearest;
    private final boolean insideNearest;
    private final OptionalInt relevantLayer;
    private final int nearestLayer;
    private final int frontierDistance;
    private final double sicknessSeverity;

    BorderPlayerStatus(
            BorderPlayerStatusFixture authority,
            UUID playerId,
            UUID nearestBorderId,
            int distanceToNearest,
            boolean insideNearest,
            OptionalInt relevantLayer,
            int nearestLayer,
            int frontierDistance,
            double sicknessSeverity
    ) {
        this.authority = authority;
        this.playerId = playerId;
        this.nearestBorderId = nearestBorderId;
        this.distanceToNearest = distanceToNearest;
        this.insideNearest = insideNearest;
        this.relevantLayer = relevantLayer;
        this.nearestLayer = nearestLayer;
        this.frontierDistance = frontierDistance;
        this.sicknessSeverity = sicknessSeverity;
    }

    // ----------------------------
    // Identity / authority
    // ----------------------------

    public UUID playerId() {
        return playerId;
    }

    BorderPlayerStatusFixture authority() {
        return authority;
    }

    // ----------------------------
    // Read-only state
    // ----------------------------

    public UUID nearestBorderId() {
        return nearestBorderId;
    }

    public int distanceToNearest() {
        return distanceToNearest;
    }

    public boolean insideNearest() {
        return insideNearest;
    }

    public OptionalInt relevantLayer() {
        return relevantLayer;
    }

    public int nearestLayer() {
        return nearestLayer;
    }

    /**
     * RM_FRO_037: raw distance past the nearest point on the Frontier's boundary -- {@code 0}
     * when the player isn't in the Exterior at all. See
     * wiki/frontiermode/architecture/exterior.md#the-distance-to-frontier-query.
     */
    public int frontierDistance() {
        return frontierDistance;
    }

    /**
     * RM_FRO_037: Frontier Sickness's current climbing severity -- see
     * {@code FrontierSicknessLogic}. Uncapped, per
     * wiki/frontiermode/design/exterior.md#frontier-sickness's "no designed ceiling."
     */
    public double sicknessSeverity() {
        return sicknessSeverity;
    }
}
